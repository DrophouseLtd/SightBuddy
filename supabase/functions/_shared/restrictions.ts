import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.49.8";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

export const RESTRICTION_DAYS = 2;

export function restrictionUntilIso(fromMs = Date.now()): string {
  return new Date(fromMs + RESTRICTION_DAYS * 24 * 60 * 60 * 1000).toISOString();
}

/** Active restriction row with restricted_until in the future, if any. */
export async function activeInstallRestriction(
  supabase: SupabaseClient,
  installId: string,
): Promise<{ restricted_until: string } | null> {
  const { data, error } = await supabase
    .from("install_restrictions")
    .select("restricted_until")
    .eq("install_id", installId)
    .maybeSingle();
  if (error) throw error;
  if (!data?.restricted_until) return null;
  if (new Date(data.restricted_until) <= new Date()) return null;
  return { restricted_until: data.restricted_until };
}

export function installRestrictedResponse(restrictedUntil: string): Response {
  return new Response(
    JSON.stringify({
      error: "install_restricted",
      restricted_until: restrictedUntil,
    }),
    {
      status: 403,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    },
  );
}
