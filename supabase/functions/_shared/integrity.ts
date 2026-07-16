/**
 * Play Integrity token verification via Google's decodeIntegrityToken API.
 *
 * Requires GOOGLE_SERVICE_ACCOUNT_KEY secret set in Supabase:
 *   npx supabase secrets set GOOGLE_SERVICE_ACCOUNT_KEY='{ ... }'
 *
 * Dev/test: set Supabase secret ALLOW_TEST_INTEGRITY_BYPASS=true on the dev
 * project so devDebug builds can send the literal "test-token".
 * Production must not set this secret.
 */

// ── Deno-native base64url (no external deps) ────────────────────────────────

function base64url(data: Uint8Array): string {
  const b64 = btoa(String.fromCharCode(...data));
  return b64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64urlStr(s: string): string {
  return base64url(new TextEncoder().encode(s));
}

// ── Service-account JWT → access-token exchange ─────────────────────────────

interface ServiceAccountKey {
  client_email: string;
  private_key: string;
  token_uri: string;
}

const TOKEN_CACHE: { token: string; expiresAt: number } = {
  token: "",
  expiresAt: 0,
};

async function getAccessToken(sa: ServiceAccountKey): Promise<string> {
  if (TOKEN_CACHE.token && Date.now() < TOKEN_CACHE.expiresAt - 30_000) {
    return TOKEN_CACHE.token;
  }

  const now = Math.floor(Date.now() / 1000);
  const header = base64urlStr(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = base64urlStr(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/playintegrity",
      aud: sa.token_uri,
      iat: now,
      exp: now + 3600,
    }),
  );
  const signingInput = `${header}.${payload}`;

  const pemBody = sa.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const keyBytes = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));

  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    new TextEncoder().encode(signingInput),
  );
  const jwt = `${signingInput}.${base64url(new Uint8Array(sig))}`;

  const res = await fetch(sa.token_uri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Token exchange failed (${res.status}): ${text}`);
  }

  const json = await res.json();
  TOKEN_CACHE.token = json.access_token;
  TOKEN_CACHE.expiresAt = Date.now() + json.expires_in * 1000;
  return json.access_token;
}

// ── Public API ──────────────────────────────────────────────────────────────

const PACKAGE_NAME = "com.drophouse.sightbuddy";

export type IntegrityResult =
  | { ok: true }
  | { ok: false; message: string };

/**
 * Verifies an integrity token.
 *
 * - `"test-token"` → passes only when ALLOW_TEST_INTEGRITY_BYPASS=true (dev project)
 * - empty string → always rejects
 * - anything else → verified against Google's Play Integrity API
 */
export async function assertIntegrityAllowed(
  token: string,
): Promise<IntegrityResult> {
  const t = token.trim();

  if (t === "test-token") {
    if (Deno.env.get("ALLOW_TEST_INTEGRITY_BYPASS") === "true") {
      return { ok: true };
    }
    return { ok: false, message: "Test integrity bypass is not enabled." };
  }
  if (!t) return { ok: false, message: "Missing integrity token." };

  const saRaw = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_KEY");
  if (!saRaw) {
    console.error("GOOGLE_SERVICE_ACCOUNT_KEY secret is not set");
    return { ok: false, message: "Server integrity configuration missing." };
  }

  let sa: ServiceAccountKey;
  try {
    sa = JSON.parse(saRaw);
  } catch {
    console.error("GOOGLE_SERVICE_ACCOUNT_KEY is not valid JSON");
    return { ok: false, message: "Server integrity configuration error." };
  }

  try {
    const accessToken = await getAccessToken(sa);

    const verifyRes = await fetch(
      `https://playintegrity.googleapis.com/v1/${PACKAGE_NAME}:decodeIntegrityToken`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ integrity_token: t }),
      },
    );

    if (!verifyRes.ok) {
      const errText = await verifyRes.text();
      console.error(
        `Play Integrity API error (${verifyRes.status}): ${errText}`,
      );
      return { ok: false, message: "Integrity verification failed." };
    }

    const verdict = await verifyRes.json();
    const payload = verdict.tokenPayloadExternal;

    if (!payload) {
      console.error("No tokenPayloadExternal in verdict:", verdict);
      return { ok: false, message: "Unexpected integrity response." };
    }

    const pkg = payload.requestDetails?.requestPackageName;
    if (pkg !== PACKAGE_NAME) {
      console.warn(`Package mismatch: expected ${PACKAGE_NAME}, got ${pkg}`);
      return { ok: false, message: "Package name mismatch." };
    }

    const deviceVerdict: string[] =
      payload.deviceIntegrity?.deviceRecognitionVerdict ?? [];
    if (!deviceVerdict.includes("MEETS_DEVICE_INTEGRITY")) {
      console.warn("Device integrity not met:", deviceVerdict);
      return { ok: false, message: "Device integrity check failed." };
    }

    return { ok: true };
  } catch (e) {
    console.error("Integrity verification error:", e);
    return { ok: false, message: "Integrity verification error." };
  }
}
