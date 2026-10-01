// End-to-end encryption for shared jellies. Every shared calendar has its own random key that only
// its members' devices hold; the server (and whoever runs it) only ever sees ciphertext.
//
// - Values are sealed one field at a time with AES-GCM (256-bit key, random 96-bit IV). The
//   document path and field name are bound in as additional data, so a sealed value cannot be
//   moved to another field or document without failing to open.
// - An invite code is the only thing a new member types. PBKDF2 turns it into an id to look the
//   invite up by and a key that unwraps the calendar's key; the code itself never leaves the device.
// - An account password is stretched the same way into two unrelated values: one to sign in with,
//   and one that locks the account's key vault (the calendar keys, for a new phone). The password
//   and the vault key never leave the device, so neither the sign-in service nor the database can
//   open the vault.

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const INVITE_SALT = encoder.encode('jelly-share/invite/v2');
const INVITE_ITERATIONS = 300000;
const ACCOUNT_ITERATIONS = 300000;

export function randomBytes(length) {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytes;
}

/** URL-safe base64 without padding. */
export function toBase64Url(bytes) {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function fromBase64Url(text) {
  const b64 = text.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(b64 + '='.repeat((4 - (b64.length % 4)) % 4));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/** A fresh calendar key, as the raw bytes that members keep and pass on through invites. */
export function newSpaceKey() {
  return randomBytes(32);
}

export function importKey(raw) {
  return crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt', 'decrypt']);
}

/** Encrypts any JSON value for one field of one document: base64url(iv ‖ ciphertext ‖ tag). */
export async function seal(key, value, where) {
  const iv = randomBytes(12);
  const data = encoder.encode(JSON.stringify(value));
  const sealed = new Uint8Array(
    await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: encoder.encode(where) }, key, data),
  );
  const out = new Uint8Array(iv.length + sealed.length);
  out.set(iv);
  out.set(sealed, iv.length);
  return toBase64Url(out);
}

/** Decrypts what seal() made for the same place; throws when the key or place is wrong. */
export async function open(key, text, where) {
  const bytes = fromBase64Url(text);
  const plain = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: bytes.subarray(0, 12), additionalData: encoder.encode(where) },
    key,
    bytes.subarray(12),
  );
  return JSON.parse(decoder.decode(plain));
}

/**
 * What an invite code stands for: the id of its invite document and the key that wraps the
 * calendar's key. Slow on purpose, so codes cannot be guessed from a copy of the database.
 */
export async function inviteSecrets(code) {
  const base = await crypto.subtle.importKey('raw', encoder.encode(code), 'PBKDF2', false, ['deriveBits']);
  const bits = new Uint8Array(
    await crypto.subtle.deriveBits(
      { name: 'PBKDF2', hash: 'SHA-256', salt: INVITE_SALT, iterations: INVITE_ITERATIONS },
      base,
      512,
    ),
  );
  return { id: toBase64Url(bits.subarray(0, 32)), key: await importKey(bits.subarray(32)) };
}

async function stretch(secret, salt, iterations) {
  const base = await crypto.subtle.importKey('raw', encoder.encode(secret), 'PBKDF2', false, ['deriveBits']);
  return new Uint8Array(
    await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: encoder.encode(salt), iterations }, base, 512),
  );
}

/**
 * What an ID and password stand for: the password to give the sign-in service, and the raw key of
 * the account's vault. The ID is the salt, so the same password makes different values for
 * different people.
 */
export async function accountSecrets(id, password) {
  const bits = await stretch(password, `jelly-account/v1/${id}`, ACCOUNT_ITERATIONS);
  return { signIn: toBase64Url(bits.subarray(0, 32)), vault: toBase64Url(bits.subarray(32)) };
}

/** The raw vault key from a separate vault password (for accounts that sign in with Google). */
export async function vaultSecret(uid, passphrase) {
  const bits = await stretch(passphrase, `jelly-vault/v1/${uid}`, ACCOUNT_ITERATIONS);
  return toBase64Url(bits.subarray(32));
}

