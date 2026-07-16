/**
 * Sight Buddy — anonymous feedback ingest + per-install cap.
 *
 * Deploy: npx supabase functions deploy feedback
 *
 * Cap: at most MAX_FEEDBACK_PER_INSTALL rows per install_id. Further submissions
 * return silent 200 (app still shows thank-you); nothing is deleted or blocked.
 */
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.8";
import { assertIntegrityAllowed } from "../_shared/integrity.ts";
import {
  activeInstallRestriction,
  installRestrictedResponse,
} from "../_shared/restrictions.ts";

const MAX_FEEDBACK_PER_INSTALL = 20;
const MAX_FEEDBACK_CHARS = 4000;

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

function jsonOk(): Response {
  return new Response(JSON.stringify({ ok: true }), {
    status: 200,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
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
  const feedbackRaw = typeof body.feedback === "string" ? body.feedback : "";
  const feedback = feedbackRaw.trim().slice(0, MAX_FEEDBACK_CHARS);

  if (!installId || installId.length > 128) {
    return new Response(JSON.stringify({ error: "invalid_install_id" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  if (!feedback) {
    return new Response(JSON.stringify({ error: "empty_feedback" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
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

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceKey) {
    return new Response(JSON.stringify({ error: "server_misconfigured" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const supabase = createClient(supabaseUrl, serviceKey);

  const restriction = await activeInstallRestriction(supabase, installId);
  if (restriction) {
    return installRestrictedResponse(restriction.restricted_until);
  }

  const { count, error: countError } = await supabase
    .from("app_feedback")
    .select("id", { count: "exact", head: true })
    .eq("install_id", installId);

  if (countError) {
    console.error("feedback count failed", countError);
    return new Response(JSON.stringify({ error: "db_error" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  if ((count ?? 0) >= MAX_FEEDBACK_PER_INSTALL) {
    return jsonOk();
  }

  const ins = await supabase.from("app_feedback").insert({
    install_id: installId,
    feedback,
  });

  if (ins.error) {
    console.error("feedback insert failed", ins.error);
    return new Response(JSON.stringify({ error: "db_error" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  return jsonOk();
});
