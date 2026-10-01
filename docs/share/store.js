// Data for shared jellies, kept in Cloud Firestore and encrypted end to end (see crypto.js).
//
// What the server can see: user ids (and an account's login ID or Google address), who is in which
// calendar and who owns it, when things were written, how many memos a jelly has, and sealed
// values. Names, colours, titles, dates, times, lengths, notes and memos are sealed on the members'
// devices with the calendar's key, which never leaves them except wrapped inside an invite or
// inside the owner's own key vault, locked with a key only their password makes. Access rules:
// jelly-calendar/share/firestore.rules.
import {
  EmailAuthProvider,
  GoogleAuthProvider,
  Timestamp,
  browserLocalPersistence,
  browserPopupRedirectResolver,
  createUserWithEmailAndPassword,
  deleteUser,
  linkWithCredential,
  linkWithPopup,
  reauthenticateWithCredential,
  signInWithCredential,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
  collection,
  connectAuthEmulator,
  connectFirestoreEmulator,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  increment,
  indexedDBLocalPersistence,
  initializeApp,
  initializeAuth,
  initializeFirestore,
  memoryLocalCache,
  onAuthStateChanged,
  onSnapshot,
  orderBy,
  persistentLocalCache,
  persistentMultipleTabManager,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from './vendor/firebase.js';
import { accountSecrets, fromBase64Url, importKey, inviteSecrets, newSpaceKey, open, seal, toBase64Url, vaultSecret } from './crypto.js';

const INVITE_DAYS = 3;
// Crockford's base32 without the letters that look like digits.
const CODE_ALPHABET = '23456789ABCDEFGHJKMNPQRSTVWXYZ';
export const CODE_LENGTH = 10;
/** The sealed fields of a jelly; everything else on it is bookkeeping the rules need. */
const JELLY_FIELDS = ['title', 'date', 'start', 'duration', 'flavor', 'done', 'note', 'pinned', 'byName', 'updatedByName'];

let db = null;
let auth = null;
/** spaceId → { raw: the key as base64url, key: CryptoKey } for the calendars this device is in. */
const keys = new Map();
/** Already opened values, so that every new snapshot does not decrypt everything again. */
const opened = new Map();

/** Unbiased random string: bytes that would favour some letters are thrown away. */
function randomString(length, alphabet) {
  const limit = 256 - (256 % alphabet.length);
  let out = '';
  while (out.length < length) {
    const bytes = new Uint8Array(length * 2);
    crypto.getRandomValues(bytes);
    for (const b of bytes) {
      if (b < limit && out.length < length) out += alphabet[b % alphabet.length];
    }
  }
  return out;
}

/** Normalises what people type as an invite code: "abcde-23456" → "ABCDE23456". */
export function cleanCode(text) {
  return (text || '').toUpperCase().replace(/[^0-9A-Z]/g, '');
}

export function prettyCode(code) {
  return code.length === CODE_LENGTH ? `${code.slice(0, 5)}-${code.slice(5)}` : code;
}

/** Starts Firebase and signs in anonymously. Resolves with the user id. */
/** Connects to Firebase. Resolves with the signed-in user's id, or null when nobody is signed in. */
export async function start(config, { emulator = false } = {}) {
  const app = initializeApp(config);
  let cache;
  try {
    cache = persistentLocalCache({ tabManager: persistentMultipleTabManager() });
  } catch {
    cache = memoryLocalCache();
  }
  db = initializeFirestore(app, { localCache: cache });
  auth = initializeAuth(app, {
    persistence: [indexedDBLocalPersistence, browserLocalPersistence],
    popupRedirectResolver: browserPopupRedirectResolver,
  });
  if (emulator) {
    connectAuthEmulator(auth, `http://${location.hostname}:9099`, { disableWarnings: true });
    connectFirestoreEmulator(db, location.hostname, 8080);
  }
  const user = await new Promise((resolve, reject) => {
    const stop = onAuthStateChanged(auth, (u) => {
      stop();
      resolve(u);
    }, reject);
  });
  return user?.uid ?? null;
}

export function uid() {
  return auth?.currentUser?.uid ?? null;
}

// ---------------------------------------------------------------- accounts

// A login ID becomes the address of an email account that never receives mail.
const ID_DOMAIN = 'id.jelly-calendar.invalid';
export const ID_PATTERN = /^[a-z0-9][a-z0-9._-]{3,19}$/;
export const MIN_PASSWORD = 8;

export function cleanId(text) {
  return String(text || '').trim().toLowerCase();
}

/**
 * Who is signed in: null; { kind: 'guest' } from before accounts; { kind: 'id', id };
 * or { kind: 'google', email }.
 */
export function account() {
  const u = auth?.currentUser;
  if (!u) return null;
  if (u.isAnonymous) return { kind: 'guest' };
  const google = u.providerData.find((p) => p.providerId === 'google.com');
  if (google) return { kind: 'google', email: google.email || u.email || '' };
  const email = u.email || '';
  if (email.endsWith(`@${ID_DOMAIN}`)) return { kind: 'id', id: email.slice(0, -(ID_DOMAIN.length + 1)) };
  return { kind: 'other' };
}

/**
 * Makes an account with a login ID. Someone already in calendars from before accounts keeps them:
 * the account is added to the same user. Resolves with the raw vault key.
 */
export async function signUpWithId(id, password) {
  const secrets = await accountSecrets(id, password);
  const email = `${id}@${ID_DOMAIN}`;
  const current = auth.currentUser;
  if (current?.isAnonymous) await linkWithCredential(current, EmailAuthProvider.credential(email, secrets.signIn));
  else await createUserWithEmailAndPassword(auth, email, secrets.signIn);
  return secrets.vault;
}

/** Signs in with a login ID. Resolves with the raw vault key. */
export async function signInWithId(id, password) {
  const secrets = await accountSecrets(id, password);
  await signInWithEmailAndPassword(auth, `${id}@${ID_DOMAIN}`, secrets.signIn);
  return secrets.vault;
}

/**
 * Google: with an ID token from the Android app, or a pop-up in a browser. Someone from before
 * accounts keeps their calendars, as with a login ID.
 */
export async function signInWithGoogle(idToken) {
  const current = auth.currentUser;
  if (idToken) {
    const credential = GoogleAuthProvider.credential(idToken);
    if (current?.isAnonymous) await linkWithCredential(current, credential);
    else await signInWithCredential(auth, credential);
  } else {
    const provider = new GoogleAuthProvider();
    provider.setCustomParameters({ prompt: 'select_account' });
    if (current?.isAnonymous) await linkWithPopup(current, provider);
    else await signInWithPopup(auth, provider);
  }
}

/** The vault key of a Google account, from its separate vault password. */
export function googleVaultSecret(passphrase) {
  return vaultSecret(uid(), passphrase);
}

/** Signs out and drops the calendar keys from memory (the page clears what it saved). */
export async function signOutAccount() {
  await signOut(auth);
  keys.clear();
  opened.clear();
}

/** Asks for the password again before something that cannot be undone (ID accounts). */
export async function confirmPassword(password) {
  const user = auth.currentUser;
  const who = account();
  if (who?.kind !== 'id') return;
  const secrets = await accountSecrets(who.id, password);
  await reauthenticateWithCredential(user, EmailAuthProvider.credential(user.email, secrets.signIn));
}

/** Deletes the account itself and its vault (after the page has left every calendar). */
export async function deleteAccount() {
  const user = auth.currentUser;
  await deleteDoc(doc(db, 'users', user.uid)).catch(() => {});
  await deleteUser(user);
  keys.clear();
  opened.clear();
}

// ---------------------------------------------------------------- the key vault

const vaultAt = (id) => `users/${id}#vault`;

/**
 * Locks [contents] (the calendar keys of this phone) into the account's vault with [vaultRaw].
 * [lock] says where the key comes from: the login 'password', or a separate 'passphrase'.
 */
export async function saveVault(vaultRaw, contents, lock = 'password') {
  const me = uid();
  const key = await importKey(fromBase64Url(vaultRaw));
  await setDoc(doc(db, 'users', me), {
    v: 1,
    lock,
    vault: await seal(key, contents, vaultAt(me)),
    updatedAt: serverTimestamp(),
  });
}

/** How the account's vault is locked ('password' or 'passphrase'), or null when it has none. */
export async function vaultLock() {
  const snap = await getDoc(doc(db, 'users', uid()));
  return snap.exists() ? snap.data().lock ?? null : null;
}

/** Opens the account's vault; null when there is none. Throws when [vaultRaw] is the wrong key. */
export async function readVault(vaultRaw) {
  const me = uid();
  const snap = await getDoc(doc(db, 'users', me));
  if (!snap.exists() || typeof snap.data().vault !== 'string') return null;
  const key = await importKey(fromBase64Url(vaultRaw));
  return open(key, snap.data().vault, vaultAt(me));
}

// ---------------------------------------------------------------- keys and sealing

/** Makes a calendar's key (as kept on this device) usable. */
export async function useSpaceKey(spaceId, raw) {
  keys.set(spaceId, { raw, key: await importKey(fromBase64Url(raw)) });
}

export function forgetSpaceKey(spaceId) {
  keys.delete(spaceId);
}

function keyOf(spaceId) {
  const entry = keys.get(spaceId);
  if (!entry) throw new Error('no-key');
  return entry.key;
}

const spaceAt = (spaceId) => `spaces/${spaceId}`;
const memberAt = (spaceId, member) => `spaces/${spaceId}/members/${member}`;
const jellyAt = (spaceId, jellyId) => `spaces/${spaceId}/jellies/${jellyId}`;
const memoAt = (spaceId, jellyId, memoId) => `spaces/${spaceId}/jellies/${jellyId}/memos/${memoId}`;

async function openOnce(key, text, where) {
  const id = `${where}\n${text}`;
  if (opened.has(id)) return opened.get(id);
  const value = await open(key, text, where);
  if (opened.size > 5000) opened.clear();
  opened.set(id, value);
  return value;
}

async function sealAll(key, where, fields) {
  const out = {};
  for (const [name, value] of Object.entries(fields)) out[name] = await seal(key, value, `${where}#${name}`);
  return out;
}

async function openAll(key, where, data, names) {
  const out = {};
  for (const name of names) {
    if (typeof data[name] === 'string') out[name] = await openOnce(key, data[name], `${where}#${name}`);
  }
  return out;
}

/** onSnapshot, with each snapshot turned into opened values before it is handed on, in order. */
function watchOpened(target, openSnapshot, onData, onError) {
  let latest = 0;
  return onSnapshot(target, (snapshot) => {
    const mine = ++latest;
    openSnapshot(snapshot).then(
      (value) => mine === latest && onData(value),
      (e) => mine === latest && onError?.(e),
    );
  }, onError);
}

// ---------------------------------------------------------------- calendars and invites

/** A new shared calendar with me as its owner and first member. Resolves with its id and key. */
export async function createSpace(spaceName, profile) {
  const me = uid();
  const spaceRef = doc(collection(db, 'spaces'));
  const raw = toBase64Url(newSpaceKey());
  await useSpaceKey(spaceRef.id, raw);
  const key = keyOf(spaceRef.id);
  const batch = writeBatch(db);
  batch.set(spaceRef, {
    v: 2,
    owner: me,
    createdAt: serverTimestamp(),
    meta: await seal(key, { name: spaceName }, spaceAt(spaceRef.id)),
  });
  batch.set(doc(spaceRef, 'members', me), {
    joinedAt: serverTimestamp(),
    profile: await seal(key, profile, memberAt(spaceRef.id, me)),
  });
  await batch.commit();
  return { spaceId: spaceRef.id, key: raw };
}

/** Joins the calendar an invite code points to. Resolves with its id and key. */
export async function joinWithCode(code, profile) {
  const { id, key: wrapKey } = await inviteSecrets(cleanCode(code));
  const snap = await getDoc(doc(db, 'invites', id));
  if (!snap.exists()) throw new Error('no-invite');
  const invite = snap.data();
  if (invite.expiresAt?.toMillis() < Date.now()) throw new Error('expired-invite');
  let inside;
  try {
    inside = await open(wrapKey, invite.wrapped, `invites/${id}`);
  } catch {
    throw new Error('no-invite');
  }
  if (inside.spaceId !== invite.spaceId) throw new Error('no-invite');
  const spaceId = invite.spaceId;
  await useSpaceKey(spaceId, inside.key);
  const me = uid();
  const memberRef = doc(db, 'spaces', spaceId, 'members', me);
  const sealedProfile = await seal(keyOf(spaceId), profile, memberAt(spaceId, me));
  // Already in (for example after this browser forgot which calendar it was in): just come back.
  const existing = await getDoc(memberRef).catch(() => null);
  if (existing?.exists()) await updateDoc(memberRef, { profile: sealedProfile });
  else await setDoc(memberRef, { joinedAt: serverTimestamp(), profile: sealedProfile, invite: id });
  return { spaceId, key: inside.key };
}

/** Gives the calendar a new name (sealed like everything else); any member may. */
export async function renameSpace(spaceId, spaceName) {
  return updateDoc(doc(db, 'spaces', spaceId), { meta: await seal(keyOf(spaceId), { name: spaceName }, spaceAt(spaceId)) });
}

/** Makes an invite code for the calendar, valid for a few days and usable by several people. */
export async function makeInvite(spaceId) {
  const code = randomString(CODE_LENGTH, CODE_ALPHABET);
  const { id, key: wrapKey } = await inviteSecrets(code);
  const expiresAt = Timestamp.fromMillis(Date.now() + INVITE_DAYS * 86400000);
  await setDoc(doc(db, 'invites', id), {
    spaceId,
    by: uid(),
    expiresAt,
    wrapped: await seal(wrapKey, { spaceId, key: keys.get(spaceId).raw }, `invites/${id}`),
  });
  return { code, id, expiresAt: expiresAt.toDate() };
}

/** Withdraws an invite before it runs out. */
export function cancelInvite(id) {
  return deleteDoc(doc(db, 'invites', id));
}

/**
 * The calendar: { id, owner, name }, { id, legacy: true } for one made before encryption, or
 * { id, owner, locked: true } when this device does not have its key.
 */
export function watchSpace(spaceId, onData, onError) {
  return watchOpened(doc(db, 'spaces', spaceId), async (s) => {
    if (!s.exists()) return null;
    const data = s.data();
    if (data.v !== 2) return { id: s.id, legacy: true, owner: data.createdBy };
    const entry = keys.get(spaceId);
    if (!entry) return { id: s.id, owner: data.owner, locked: true };
    try {
      const meta = await openOnce(entry.key, data.meta, spaceAt(spaceId));
      return { id: s.id, owner: data.owner, name: meta.name };
    } catch {
      return { id: s.id, owner: data.owner, locked: true };
    }
  }, onData, onError);
}

/** Members in the order they joined: { uid, name, color }. */
export function watchMembers(spaceId, onData, onError) {
  return watchOpened(query(collection(db, 'spaces', spaceId, 'members'), orderBy('joinedAt')), (s) => {
    const key = keys.get(spaceId)?.key;
    return Promise.all(s.docs.map(async (d) => {
      let profile = {};
      try {
        if (key) profile = await openOnce(key, d.data().profile, memberAt(spaceId, d.id));
      } catch {
        profile = {};
      }
      return {
        uid: d.id,
        name: typeof profile.name === 'string' ? profile.name : '',
        color: Number.isInteger(profile.color) ? profile.color : null,
        joinedAt: d.data().joinedAt,
      };
    }));
  }, onData, onError);
}

// ---------------------------------------------------------------- jellies and memos

async function openJelly(spaceId, d) {
  const key = keys.get(spaceId)?.key;
  if (!key || !d.exists()) return null;
  const data = d.data();
  try {
    const fields = await openAll(key, jellyAt(spaceId, d.id), data, JELLY_FIELDS);
    return {
      id: d.id,
      ...fields,
      by: data.by,
      updatedBy: data.updatedBy,
      createdAt: data.createdAt,
      updatedAt: data.updatedAt,
      memoCount: data.memoCount ?? 0,
      lastMemoAt: data.lastMemoAt ?? null,
      pending: d.metadata.hasPendingWrites,
    };
  } catch (e) {
    console.warn('could not open a jelly', d.id, e);
    return null;
  }
}

/**
 * Every jelly of the calendar. Dates are sealed, so the server cannot pick a month: the page
 * filters after opening. Shared calendars hold few jellies, and the local cache keeps re-reads small.
 */
export function watchJellies(spaceId, onData, onError) {
  return watchOpened(
    collection(db, 'spaces', spaceId, 'jellies'),
    async (s) => (await Promise.all(s.docs.map((d) => openJelly(spaceId, d)))).filter(Boolean),
    onData,
    onError,
  );
}

/** One jelly; onData receives null once it is deleted. */
export function watchJelly(spaceId, jellyId, onData, onError) {
  return watchOpened(doc(db, 'spaces', spaceId, 'jellies', jellyId), (d) => openJelly(spaceId, d), onData, onError);
}

export function watchMemos(spaceId, jellyId, onData, onError) {
  return watchOpened(
    query(collection(db, 'spaces', spaceId, 'jellies', jellyId, 'memos'), orderBy('at')),
    async (s) => {
      const key = keys.get(spaceId)?.key;
      const list = await Promise.all(s.docs.map(async (d) => {
        const data = d.data();
        try {
          const fields = await openAll(key, memoAt(spaceId, jellyId, d.id), data, ['text', 'byName']);
          return { id: d.id, by: data.by, at: data.at, ...fields };
        } catch {
          return null;
        }
      }));
      return list.filter(Boolean);
    },
    onData,
    onError,
  );
}

/**
 * Puts a new jelly up. Returns its id at once; sealing and writing finish in the background
 * (the write waits for the connection when offline), reported through onDone.
 */
export function addJelly(spaceId, fields, myName, onDone) {
  const me = uid();
  const ref = doc(collection(db, 'spaces', spaceId, 'jellies'));
  const write = sealAll(keyOf(spaceId), jellyAt(spaceId, ref.id), {
    title: fields.title,
    date: fields.date,
    start: fields.start ?? null,
    duration: fields.duration,
    flavor: fields.flavor,
    done: false,
    note: fields.note ?? '',
    pinned: !!fields.pinned,
    byName: myName,
    updatedByName: myName,
  }).then((sealed) => setDoc(ref, {
    ...sealed,
    by: me,
    createdAt: serverTimestamp(),
    updatedBy: me,
    updatedAt: serverTimestamp(),
    memoCount: 0,
  }));
  write.then(() => onDone?.(null), (e) => onDone?.(e));
  return ref.id;
}

/** Changes some fields of a shared jelly and records who changed it. */
export async function updateJelly(spaceId, jellyId, patch, myName) {
  const sealed = await sealAll(keyOf(spaceId), jellyAt(spaceId, jellyId), { ...patch, updatedByName: myName });
  return updateDoc(doc(db, 'spaces', spaceId, 'jellies', jellyId), {
    ...sealed,
    updatedBy: uid(),
    updatedAt: serverTimestamp(),
  });
}

/** Deletes a jelly together with all of its memos. */
export async function deleteJelly(spaceId, jellyId) {
  const ref = doc(db, 'spaces', spaceId, 'jellies', jellyId);
  const memos = await getDocs(collection(ref, 'memos'));
  const batch = writeBatch(db);
  memos.forEach((m) => batch.delete(m.ref));
  batch.delete(ref);
  await batch.commit();
}

export async function addMemo(spaceId, jellyId, text, myName) {
  const jellyRef = doc(db, 'spaces', spaceId, 'jellies', jellyId);
  const memoRef = doc(collection(jellyRef, 'memos'));
  const sealed = await sealAll(keyOf(spaceId), memoAt(spaceId, jellyId, memoRef.id), { text, byName: myName });
  const batch = writeBatch(db);
  batch.set(memoRef, { ...sealed, by: uid(), at: serverTimestamp() });
  batch.update(jellyRef, { memoCount: increment(1), lastMemoAt: serverTimestamp() });
  await batch.commit();
}

export async function deleteMemo(spaceId, jellyId, memoId) {
  const jellyRef = doc(db, 'spaces', spaceId, 'jellies', jellyId);
  const batch = writeBatch(db);
  batch.delete(doc(jellyRef, 'memos', memoId));
  batch.update(jellyRef, { memoCount: increment(-1) });
  await batch.commit();
}

// ---------------------------------------------------------------- members

/** My name and colour, as the others see them. */
export async function updateProfile(spaceId, profile) {
  const me = uid();
  return updateDoc(doc(db, 'spaces', spaceId, 'members', me), {
    profile: await seal(keyOf(spaceId), profile, memberAt(spaceId, me)),
  });
}

/** The owner lets someone go. */
export function removeMember(spaceId, member) {
  return deleteDoc(doc(db, 'spaces', spaceId, 'members', member));
}

/**
 * Leaves a calendar. The owner first hands it to whoever has been in it longest; the last one out
 * deletes it with everything in it, so nothing is left behind on the server.
 */
export async function leave(spaceId) {
  const me = uid();
  const spaceRef = doc(db, 'spaces', spaceId);
  const [space, members] = await Promise.all([getDoc(spaceRef), getDocs(query(collection(spaceRef, 'members'), orderBy('joinedAt')))]);
  const others = members.docs.filter((m) => m.id !== me);
  if (others.length === 0) return deleteSpace(spaceId);
  const batch = writeBatch(db);
  if (space.data()?.owner === me) batch.update(spaceRef, { owner: others[0].id });
  batch.delete(doc(spaceRef, 'members', me));
  await batch.commit();
  forgetSpaceKey(spaceId);
}

/**
 * Deletes a calendar and everything in it (the owner only). Works for calendars from before
 * encryption too, whose creator counts as the owner.
 */
export async function deleteSpace(spaceId) {
  const me = uid();
  const spaceRef = doc(db, 'spaces', spaceId);
  const jellies = await getDocs(collection(spaceRef, 'jellies'));
  for (const jelly of jellies.docs) {
    // A jelly goes in the same batch as its first memos, so the rules let others' memos go with it.
    const memos = (await getDocs(collection(jelly.ref, 'memos'))).docs.map((m) => m.ref);
    let batch = writeBatch(db);
    batch.delete(jelly.ref);
    let count = 1;
    for (const memo of memos) {
      if (count === 450) {
        await batch.commit();
        batch = writeBatch(db);
        count = 0;
      }
      batch.delete(memo);
      count++;
    }
    await batch.commit();
  }
  const members = await getDocs(collection(spaceRef, 'members'));
  const others = members.docs.filter((m) => m.id !== me);
  for (let i = 0; i < others.length; i += 450) {
    const batch = writeBatch(db);
    others.slice(i, i + 450).forEach((m) => batch.delete(m.ref));
    await batch.commit();
  }
  const last = writeBatch(db);
  last.delete(doc(spaceRef, 'members', me));
  last.delete(spaceRef);
  await last.commit();
  forgetSpaceKey(spaceId);
}
