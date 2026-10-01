// Data for shared jellies, kept in Cloud Firestore. See jelly-calendar/share/firestore.rules for
// who may read and write what.
import {
  Timestamp,
  browserLocalPersistence,
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
  signInAnonymously,
  updateDoc,
  where,
  writeBatch,
} from './vendor/firebase.js';

const INVITE_DAYS = 7;
// Crockford's base32 without the letters that look like digits.
const CODE_ALPHABET = '23456789ABCDEFGHJKMNPQRSTVWXYZ';

let db = null;
let auth = null;

function randomString(length, alphabet) {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  let out = '';
  for (const b of bytes) out += alphabet[b % alphabet.length];
  return out;
}

const SECRET_ALPHABET = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';

/** Normalises what people type as an invite code: "abcd-2345" → "ABCD2345". */
export function cleanCode(text) {
  return (text || '').toUpperCase().replace(/[^0-9A-Z]/g, '');
}

export function prettyCode(code) {
  return code.length === 8 ? `${code.slice(0, 4)}-${code.slice(4)}` : code;
}

/** Starts Firebase and signs in anonymously. Resolves with the user id. */
export async function start(config, { emulator = false } = {}) {
  const app = initializeApp(config);
  let cache;
  try {
    cache = persistentLocalCache({ tabManager: persistentMultipleTabManager() });
  } catch {
    cache = memoryLocalCache();
  }
  db = initializeFirestore(app, { localCache: cache });
  // Anonymous sign-in only, so no pop-up or redirect helpers (they would load extra Google scripts).
  auth = initializeAuth(app, { persistence: [indexedDBLocalPersistence, browserLocalPersistence] });
  if (emulator) {
    connectAuthEmulator(auth, `http://${location.hostname}:9099`, { disableWarnings: true });
    connectFirestoreEmulator(db, location.hostname, 8080);
  }
  const user = await new Promise((resolve, reject) => {
    const stop = onAuthStateChanged(auth, (u) => {
      if (u) {
        stop();
        resolve(u);
      }
    }, reject);
    if (!auth.currentUser) signInAnonymously(auth).catch(reject);
  });
  return user.uid;
}

export function uid() {
  return auth?.currentUser?.uid ?? null;
}

/** A new shared calendar with me as its first member. */
export async function createSpace(spaceName, myName) {
  const me = uid();
  const spaceRef = doc(collection(db, 'spaces'));
  const secret = randomString(28, SECRET_ALPHABET);
  const batch = writeBatch(db);
  batch.set(spaceRef, { name: spaceName, secret, createdBy: me, createdAt: serverTimestamp() });
  batch.set(doc(spaceRef, 'members', me), { name: myName, secret, joinedAt: serverTimestamp() });
  await batch.commit();
  return spaceRef.id;
}

/** Joins the space an invite code points to. Returns its id. */
export async function joinWithCode(code, myName) {
  const snap = await getDoc(doc(db, 'invites', cleanCode(code)));
  if (!snap.exists()) throw new Error('no-invite');
  const invite = snap.data();
  if (invite.expiresAt && invite.expiresAt.toMillis() < Date.now()) throw new Error('expired-invite');
  const memberRef = doc(db, 'spaces', invite.spaceId, 'members', uid());
  // Already in (for example after this browser forgot which space it was in): just come back.
  const existing = await getDoc(memberRef).catch(() => null);
  if (existing?.exists()) {
    if (existing.data().name !== myName) await updateDoc(memberRef, { name: myName });
    return invite.spaceId;
  }
  await setDoc(memberRef, { name: myName, secret: invite.secret, joinedAt: serverTimestamp() });
  return invite.spaceId;
}

/** Makes an invite code for the space, valid for a week. */
export async function makeInvite(spaceId) {
  const space = await getDoc(doc(db, 'spaces', spaceId));
  const code = randomString(8, CODE_ALPHABET);
  const expiresAt = Timestamp.fromMillis(Date.now() + INVITE_DAYS * 86400000);
  await setDoc(doc(db, 'invites', code), { spaceId, secret: space.data().secret, by: uid(), expiresAt });
  return { code, expiresAt: expiresAt.toDate() };
}

export function watchSpace(spaceId, onData, onError) {
  return onSnapshot(doc(db, 'spaces', spaceId), (s) => onData(s.exists() ? { id: s.id, ...s.data() } : null), onError);
}

export function watchMembers(spaceId, onData, onError) {
  return onSnapshot(
    query(collection(db, 'spaces', spaceId, 'members'), orderBy('joinedAt')),
    (s) => onData(s.docs.map((d) => ({ uid: d.id, name: d.data().name, joinedAt: d.data().joinedAt }))),
    onError,
  );
}

/** Jellies dated between two YYYY-MM-DD days, both included. */
export function watchJellies(spaceId, from, to, onData, onError) {
  return onSnapshot(
    query(collection(db, 'spaces', spaceId, 'jellies'), where('date', '>=', from), where('date', '<=', to)),
    { includeMetadataChanges: false },
    (s) => onData(s.docs.map((d) => ({ id: d.id, ...d.data(), pending: d.metadata.hasPendingWrites }))),
    onError,
  );
}

/** One jelly; onData receives null once it is deleted. */
export function watchJelly(spaceId, jellyId, onData, onError) {
  return onSnapshot(
    doc(db, 'spaces', spaceId, 'jellies', jellyId),
    (s) => onData(s.exists() ? { id: s.id, ...s.data(), pending: s.metadata.hasPendingWrites } : null),
    onError,
  );
}

export function watchMemos(spaceId, jellyId, onData, onError) {
  return onSnapshot(
    query(collection(db, 'spaces', spaceId, 'jellies', jellyId, 'memos'), orderBy('at')),
    (s) => onData(s.docs.map((d) => ({ id: d.id, ...d.data() }))),
    onError,
  );
}

/**
 * Puts a new jelly up. Returns its id at once; the write itself finishes in the background
 * (and waits for the connection when offline), reported through onDone.
 */
export function addJelly(spaceId, fields, myName, onDone) {
  const me = uid();
  const ref = doc(collection(db, 'spaces', spaceId, 'jellies'));
  const write = setDoc(ref, {
    title: fields.title,
    date: fields.date,
    start: fields.start ?? null,
    duration: fields.duration,
    flavor: fields.flavor,
    done: false,
    note: fields.note ?? '',
    by: me,
    byName: myName,
    createdAt: serverTimestamp(),
    updatedBy: me,
    updatedByName: myName,
    updatedAt: serverTimestamp(),
    memoCount: 0,
  });
  write.then(() => onDone?.(null), (e) => onDone?.(e));
  return ref.id;
}

/** Changes some fields of a shared jelly and records who changed it. */
export function updateJelly(spaceId, jellyId, patch, myName) {
  return updateDoc(doc(db, 'spaces', spaceId, 'jellies', jellyId), {
    ...patch,
    updatedBy: uid(),
    updatedByName: myName,
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
  const batch = writeBatch(db);
  batch.set(doc(collection(jellyRef, 'memos')), { text, by: uid(), byName: myName, at: serverTimestamp() });
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

export function rename(spaceId, name) {
  return updateDoc(doc(db, 'spaces', spaceId, 'members', uid()), { name });
}

export function leave(spaceId) {
  return deleteDoc(doc(db, 'spaces', spaceId, 'members', uid()));
}
