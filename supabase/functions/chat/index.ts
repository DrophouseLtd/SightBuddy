/**
 * Sight Buddy — OpenAI proxy + daily token quota.
 *
 * Deploy:
 *   npx supabase secrets set OPENAI_API_KEY=sk-...
 *   npx supabase secrets set GOOGLE_SERVICE_ACCOUNT_KEY='{ ... }'
 *   npx supabase db push
 *   npx supabase functions deploy chat
 *
 * Test (dev — uses test-token bypass):
 *   curl -sS -X POST "$SUPABASE_URL/functions/v1/chat" \
 *     -H "Content-Type: application/json" \
 *     -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
 *     -d '{"install_id":"dev-install-1","integrity_token":"test-token","model":"gpt-4o-mini","messages":[{"role":"user","content":"Say hi in one word."}],"max_tokens":50}'
 *
 * Integrity: "test-token" only when ALLOW_TEST_INTEGRITY_BYPASS=true (dev project).
 * Real tokens are verified via the Play Integrity API.
 */
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.8";
import { assertIntegrityAllowed } from "../_shared/integrity.ts";
import {
  activeInstallRestriction,
  installRestrictedResponse,
} from "../_shared/restrictions.ts";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

// ── In-memory burst rate limiter (per Edge instance) ────────────────────────

const RATE_WINDOW_MS = 60_000;
const RATE_MAX_REQUESTS = 12;
const rateBuckets = new Map<string, number[]>();

function isRateLimited(installId: string): boolean {
  const now = Date.now();
  let timestamps = rateBuckets.get(installId) ?? [];
  timestamps = timestamps.filter((t) => now - t < RATE_WINDOW_MS);
  if (timestamps.length >= RATE_MAX_REQUESTS) {
    rateBuckets.set(installId, timestamps);
    return true;
  }
  timestamps.push(now);
  rateBuckets.set(installId, timestamps);
  return false;
}

function utcMidnight(d = new Date()): Date {
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
}

function nextUtcMidnightIso(): string {
  const now = new Date();
  const next = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1),
  );
  return next.toISOString();
}

type QuotaRow = {
  install_id: string;
  tier: string;
  daily_tokens_used: number;
  daily_token_limit: number;
  last_reset_at: string;
};

async function getOrCreateQuotaRow(
  supabase: ReturnType<typeof createClient>,
  installId: string,
): Promise<QuotaRow> {
  const first = await supabase
    .from("llm_quota")
    .select("*")
    .eq("install_id", installId)
    .maybeSingle();
  if (first.error) throw first.error;
  if (first.data) return first.data as QuotaRow;

  const ins = await supabase.from("llm_quota").insert({ install_id: installId }).select()
    .single();
  if (ins.error) {
    if (ins.error.code === "23505") {
      const retry = await supabase.from("llm_quota").select("*").eq(
        "install_id",
        installId,
      ).single();
      if (retry.error) throw retry.error;
      return retry.data as QuotaRow;
    }
    throw ins.error;
  }
  return ins.data as QuotaRow;
}

/** Load quota row and apply lazy UTC-midnight reset (writes to DB when a new day starts). */
async function loadQuotaAfterUtcDailyReset(
  supabase: ReturnType<typeof createClient>,
  installId: string,
): Promise<QuotaRow> {
  let row = await getOrCreateQuotaRow(supabase, installId);
  const boundary = utcMidnight();
  const last = new Date(row.last_reset_at);
  if (last < boundary) {
    await supabase.from("llm_quota").update({
      daily_tokens_used: 0,
      last_reset_at: new Date().toISOString(),
    }).eq("install_id", installId);
    const again = await supabase.from("llm_quota").select("*").eq(
      "install_id",
      installId,
    ).single();
    if (again.error) throw again.error;
    row = again.data as QuotaRow;
  }
  return row;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "method_not_allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return new Response(JSON.stringify({ error: "invalid_json" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const installId = typeof body.install_id === "string"
    ? body.install_id.trim()
    : "";
  const integrityToken = typeof body.integrity_token === "string"
    ? body.integrity_token
    : "";

  if (!installId || installId.length > 128) {
    return new Response(JSON.stringify({ error: "invalid_install_id" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  if (isRateLimited(installId)) {
    return new Response(
      JSON.stringify({ error: "rate_limited", detail: "Too many requests. Please wait a moment." }),
      {
        status: 429,
        headers: { ...corsHeaders, "Content-Type": "application/json", "Retry-After": "10" },
      },
    );
  }

  const integrity = await assertIntegrityAllowed(integrityToken);
  if (!integrity.ok) {
    return new Response(
      JSON.stringify({ error: "integrity_rejected", detail: integrity.message }),
      {
        status: 403,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  }

  const openaiKey = Deno.env.get("OPENAI_API_KEY");
  if (!openaiKey) {
    return new Response(
      JSON.stringify({
        error: "server_misconfigured",
        detail: "OPENAI_API_KEY secret is not set",
      }),
      {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceKey) {
    return new Response(JSON.stringify({ error: "supabase_env_missing" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const supabase = createClient(supabaseUrl, serviceKey);

  const restriction = await activeInstallRestriction(supabase, installId);
  if (restriction) {
    return installRestrictedResponse(restriction.restricted_until);
  }

  const row = await loadQuotaAfterUtcDailyReset(supabase, installId);

  if (row.daily_tokens_used >= row.daily_token_limit) {
    return new Response(
      JSON.stringify({
        error: "quota_exhausted",
        resets_at: nextUtcMidnightIso(),
      }),
      {
        status: 429,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  }

  const forward: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(body)) {
    if (k === "install_id" || k === "integrity_token") continue;
    forward[k] = v;
  }

  if (typeof forward.model !== "string" || !forward.model.length) {
    forward.model = "gpt-4o-mini";
  }

  if (
    !forward.messages || !Array.isArray(forward.messages) ||
    forward.messages.length === 0
  ) {
    return new Response(
      JSON.stringify({
        error: "invalid_openai_body",
        detail: "Include OpenAI-style messages[] (plus optional vision content).",
      }),
      {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  }

  const oaiRes = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${openaiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(forward),
  });

  const oaiText = await oaiRes.text();
  let oaiJson: Record<string, unknown>;
  try {
    oaiJson = JSON.parse(oaiText);
  } catch {
    return new Response(
      JSON.stringify({
        error: "openai_non_json",
        status: oaiRes.status,
        bodyPreview: oaiText.slice(0, 500),
      }),
      {
        status: 502,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  }

  if (!oaiRes.ok) {
    return new Response(JSON.stringify(oaiJson), {
      status: oaiRes.status,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const usage = oaiJson.usage as { total_tokens?: number } | undefined;
  const totalTokens = typeof usage?.total_tokens === "number"
    ? usage.total_tokens
    : 0;

  const newUsed = row.daily_tokens_used + totalTokens;
  await supabase.from("llm_quota").update({ daily_tokens_used: newUsed }).eq(
    "install_id",
    installId,
  );

  const remaining = Math.max(0, row.daily_token_limit - newUsed);

  const headers = new Headers(corsHeaders);
  headers.set("Content-Type", "application/json");
  headers.set("X-SightBuddy-Remaining-Tokens", String(remaining));
  headers.set("X-SightBuddy-Daily-Limit", String(row.daily_token_limit));
  headers.set("X-SightBuddy-Resets-At", nextUtcMidnightIso());

  const payload = {
    ...oaiJson,
    sightbuddy_quota: {
      remaining_tokens: remaining,
      daily_limit: row.daily_token_limit,
      resets_at: nextUtcMidnightIso(),
    },
  };

  return new Response(JSON.stringify(payload), { status: 200, headers });
});
