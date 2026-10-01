// Security rules for shared jellies, run against the Firestore emulator:
//   npm run test:rules
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  increment,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';

const SECRET = 'aaaaaaaaaaaaaaaaaaaaaaaa';
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-jelly',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

const db = (uid) => (uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore());

/** Alice makes a space the way the app does: the space and her member doc in one batch. */
async function aliceMakesSpace() {
  const store = db('alice');
  const batch = writeBatch(store);
  batch.set(doc(store, 'spaces/s1'), { name: '우리 일정', secret: SECRET, createdBy: 'alice', createdAt: serverTimestamp() });
  batch.set(doc(store, 'spaces/s1/members/alice'), { name: '창현', secret: SECRET, joinedAt: serverTimestamp() });
  await assertSucceeds(batch.commit());
}

async function bobJoins() {
  await assertSucceeds(
    setDoc(doc(db('bob'), 'spaces/s1/members/bob'), { name: '지은', secret: SECRET, joinedAt: serverTimestamp() }),
  );
}

function jelly(by, extra = {}) {
  return {
    title: '저녁 약속',
    date: '2026-10-03',
    start: 19 * 60,
    duration: 90,
    flavor: 1,
    done: false,
    note: '',
    by,
    byName: by,
    createdAt: serverTimestamp(),
    updatedBy: by,
    updatedByName: by,
    updatedAt: serverTimestamp(),
    memoCount: 0,
    ...extra,
  };
}

describe('spaces and members', () => {
  test('a space is created together with its first member', aliceMakesSpace);

  test('a space cannot be created for someone else', async () => {
    await assertFails(
      setDoc(doc(db('mallory'), 'spaces/s2'), { name: 'x', secret: SECRET, createdBy: 'alice', createdAt: serverTimestamp() }),
    );
  });

  test('a short secret is refused', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'spaces/s2'), { name: 'x', secret: 'short', createdBy: 'alice', createdAt: serverTimestamp() }),
    );
  });

  test('joining needs the secret', async () => {
    await aliceMakesSpace();
    await assertFails(
      setDoc(doc(db('mallory'), 'spaces/s1/members/mallory'), { name: '몰래', secret: 'b'.repeat(24), joinedAt: serverTimestamp() }),
    );
    await bobJoins();
  });

  test('nobody can add another person', async () => {
    await aliceMakesSpace();
    await assertFails(
      setDoc(doc(db('alice'), 'spaces/s1/members/bob'), { name: '지은', secret: SECRET, joinedAt: serverTimestamp() }),
    );
  });

  test('only members can read the space and its members', async () => {
    await aliceMakesSpace();
    await assertFails(getDoc(doc(db('mallory'), 'spaces/s1')));
    await assertFails(getDocs(collection(db('mallory'), 'spaces/s1/members')));
    await assertFails(getDoc(doc(db(null), 'spaces/s1')));
    await assertSucceeds(getDoc(doc(db('alice'), 'spaces/s1')));
    await bobJoins();
    await assertSucceeds(getDocs(collection(db('bob'), 'spaces/s1/members')));
  });

  test('spaces cannot be listed', async () => {
    await aliceMakesSpace();
    await assertFails(getDocs(collection(db('alice'), 'spaces')));
  });

  test('members rename themselves only', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/s1/members/bob'), { name: '지은이' }));
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1/members/alice'), { name: '바꿈' }));
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1/members/bob'), { secret: 'c'.repeat(24) }));
  });

  test('the secret of a space cannot be changed', async () => {
    await aliceMakesSpace();
    await assertFails(updateDoc(doc(db('alice'), 'spaces/s1'), { secret: 'd'.repeat(24) }));
    await assertSucceeds(updateDoc(doc(db('alice'), 'spaces/s1'), { name: '주말 일정' }));
  });

  test('leaving removes access', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(deleteDoc(doc(db('bob'), 'spaces/s1/members/bob')));
    await assertFails(getDoc(doc(db('bob'), 'spaces/s1')));
  });
});

describe('invites', () => {
  const inv = (by, spaceId = 's1', secret = SECRET) => ({
    spaceId,
    secret,
    by,
    expiresAt: Timestamp.fromMillis(Date.now() + 7 * 86400000),
  });

  test('a member makes an invite and anyone signed in can read it by code', async () => {
    await aliceMakesSpace();
    await assertSucceeds(setDoc(doc(db('alice'), 'invites/ABCD2345'), inv('alice')));
    const got = await assertSucceeds(getDoc(doc(db('bob'), 'invites/ABCD2345')));
    if (got.data().secret !== SECRET) throw new Error('invite did not carry the secret');
    await assertFails(getDoc(doc(db(null), 'invites/ABCD2345')));
  });

  test('invites cannot be listed', async () => {
    await aliceMakesSpace();
    await assertSucceeds(setDoc(doc(db('alice'), 'invites/ABCD2345'), inv('alice')));
    await assertFails(getDocs(collection(db('bob'), 'invites')));
  });

  test('outsiders and wrong secrets cannot make invites', async () => {
    await aliceMakesSpace();
    await assertFails(setDoc(doc(db('mallory'), 'invites/ZZZZ2345'), inv('mallory')));
    await assertFails(setDoc(doc(db('alice'), 'invites/YYYY2345'), inv('alice', 's1', 'e'.repeat(24))));
  });
});

describe('jellies and memos', () => {
  test('members add, edit and remove shared jellies', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(setDoc(doc(db('alice'), 'spaces/s1/jellies/j1'), jelly('alice')));
    // Bob changes Alice's jelly; it stays hers.
    await assertSucceeds(
      updateDoc(doc(db('bob'), 'spaces/s1/jellies/j1'), {
        title: '저녁 약속 (7시 반)',
        start: 19 * 60 + 30,
        updatedBy: 'bob',
        updatedByName: '지은',
        updatedAt: serverTimestamp(),
      }),
    );
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1/jellies/j1'), { by: 'bob', updatedBy: 'bob' }));
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1/jellies/j1'), { title: '이름만', updatedBy: 'alice' }));
    await assertSucceeds(deleteDoc(doc(db('bob'), 'spaces/s1/jellies/j1')));
  });

  test('outsiders see and change nothing', async () => {
    await aliceMakesSpace();
    await assertSucceeds(setDoc(doc(db('alice'), 'spaces/s1/jellies/j1'), jelly('alice')));
    await assertFails(getDoc(doc(db('mallory'), 'spaces/s1/jellies/j1')));
    await assertFails(getDocs(collection(db('mallory'), 'spaces/s1/jellies')));
    await assertFails(setDoc(doc(db('mallory'), 'spaces/s1/jellies/j2'), jelly('mallory')));
    await assertFails(updateDoc(doc(db('mallory'), 'spaces/s1/jellies/j1'), { title: 'x', updatedBy: 'mallory' }));
  });

  test('bad jellies are refused', async () => {
    await aliceMakesSpace();
    const ref = (id) => doc(db('alice'), `spaces/s1/jellies/${id}`);
    await assertFails(setDoc(ref('a'), jelly('alice', { title: '' })));
    await assertFails(setDoc(ref('b'), jelly('alice', { date: '10/3' })));
    await assertFails(setDoc(ref('c'), jelly('alice', { duration: 0 })));
    await assertFails(setDoc(ref('d'), jelly('alice', { start: 1440 })));
    await assertFails(setDoc(ref('e'), jelly('alice', { by: 'bob' })));
    await assertFails(setDoc(ref('f'), jelly('alice', { extra: true })));
    await assertSucceeds(setDoc(ref('g'), jelly('alice', { start: null })));
  });

  test('memos: anyone in the space writes, writers remove their own', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(setDoc(doc(db('alice'), 'spaces/s1/jellies/j1'), jelly('alice')));
    // Bob adds a memo and bumps the counter in one batch, as the app does.
    const bob = db('bob');
    const batch = writeBatch(bob);
    batch.set(doc(bob, 'spaces/s1/jellies/j1/memos/m1'), { text: '역 앞에서 봐요', by: 'bob', byName: '지은', at: serverTimestamp() });
    batch.update(doc(bob, 'spaces/s1/jellies/j1'), { memoCount: increment(1), lastMemoAt: serverTimestamp() });
    await assertSucceeds(batch.commit());
    await assertSucceeds(getDocs(collection(db('alice'), 'spaces/s1/jellies/j1/memos')));
    await assertFails(deleteDoc(doc(db('alice'), 'spaces/s1/jellies/j1/memos/m1')));
    await assertFails(
      setDoc(doc(db('alice'), 'spaces/s1/jellies/j1/memos/m2'), { text: '사칭', by: 'bob', byName: '지은', at: serverTimestamp() }),
    );
    const undo = writeBatch(bob);
    undo.delete(doc(bob, 'spaces/s1/jellies/j1/memos/m1'));
    undo.update(doc(bob, 'spaces/s1/jellies/j1'), { memoCount: increment(-1) });
    await assertSucceeds(undo.commit());
  });

  test('deleting a jelly takes everyone\'s memos with it', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(setDoc(doc(db('alice'), 'spaces/s1/jellies/j1'), jelly('alice')));
    await assertSucceeds(
      setDoc(doc(db('bob'), 'spaces/s1/jellies/j1/memos/m1'), { text: '좋아요', by: 'bob', byName: '지은', at: serverTimestamp() }),
    );
    const alice = db('alice');
    const batch = writeBatch(alice);
    batch.delete(doc(alice, 'spaces/s1/jellies/j1/memos/m1'));
    batch.delete(doc(alice, 'spaces/s1/jellies/j1'));
    await assertSucceeds(batch.commit());
  });
});
