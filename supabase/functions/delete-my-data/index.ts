/**
 * Sight Buddy — delete server-held data for an install_id + 2-day AI restriction.
 *
 * Deploy: npx supabase functions deploy delete-my-data
 */
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.8";
import { assertIntegrityAllowed } from "../_shared/integrity.ts";
import {
  activeInstallRestriction,
  restrictionUntilIso,
} from "../_shared/restrictions.ts";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

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

  const restrictedUntil = restrictionUntilIso();

  const delQuota = await supabase.from("llm_quota").delete().eq(
    "install_id",
    installId,
  );
  if (delQuota.error) {
    console.error("delete llm_quota failed", delQuota.error);
    return new Response(JSON.stringify({ error: "db_error" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const delFeedback = await supabase.from("app_feedback").delete().eq(
    "install_id",
    installId,
  );
  if (delFeedback.error) {
    console.error("delete app_feedback failed", delFeedback.error);
    return new Response(JSON.stringify({ error: "db_error" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const upsert = await supabase.from("install_restrictions").upsert({
    install_id: installId,
    restricted_until: restrictedUntil,
  });
  if (upsert.error) {
    console.error("upsert install_restrictions failed", upsert.error);
    return new Response(JSON.stringify({ error: "db_error" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  // Idempotent success even if already restricted (data still cleared above).
  const active = await activeInstallRestriction(supabase, installId);
  const until = active?.restricted_until ?? restrictedUntil;

  return new Response(
    JSON.stringify({ ok: true, restricted_until: until }),
    {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    },
  );
});
