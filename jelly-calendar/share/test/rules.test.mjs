// Security rules for shared jellies, run against the Firestore emulator:
//   npm run test:rules
// The rules cannot read sealed values, so the tests stand them in with strings of a sealed length.
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
/** Stands in for a sealed value (base64url of IV, ciphertext and tag). */
const S = (length = 64) => 'Q'.repeat(length);
/** Invite ids are 43 characters: base64url of 32 bytes derived from the code. */
const INVITE = 'i'.repeat(43);
const days = (n) => Timestamp.fromMillis(Date.now() + n * 86400000);

/** Alice makes a calendar the way the app does: the space and her member doc in one batch. */
async function aliceMakesSpace(spaceId = 's1') {
  const store = db('alice');
  const batch = writeBatch(store);
  batch.set(doc(store, `spaces/${spaceId}`), { v: 2, owner: 'alice', createdAt: serverTimestamp(), meta: S() });
  batch.set(doc(store, `spaces/${spaceId}/members/alice`), { joinedAt: serverTimestamp(), profile: S() });
  await assertSucceeds(batch.commit());
}

async function aliceInvites(id = INVITE, spaceId = 's1', expiresAt = days(3)) {
  await assertSucceeds(setDoc(doc(db('alice'), `invites/${id}`), { spaceId, by: 'alice', expiresAt, wrapped: S(200) }));
}

async function join(uid, invite = INVITE, spaceId = 's1') {
  return setDoc(doc(db(uid), `spaces/${spaceId}/members/${uid}`), { joinedAt: serverTimestamp(), profile: S(), invite });
}

async function bobJoins() {
  await aliceInvites();
  await assertSucceeds(join('bob'));
}

function jelly(by, extra = {}) {
  return {
    title: S(),
    date: S(),
    start: S(),
    duration: S(),
    flavor: S(),
    done: S(),
    note: S(),
    byName: S(),
    updatedByName: S(),
    by,
    createdAt: serverTimestamp(),
    updatedBy: by,
    updatedAt: serverTimestamp(),
    memoCount: 0,
    ...extra,
  };
}

describe('calendars', () => {
  test('a calendar is created together with its owner as first member', () => aliceMakesSpace());

  test('a calendar needs its owner\'s member doc in the same batch', async () => {
    await assertFails(setDoc(doc(db('alice'), 'spaces/s2'), { v: 2, owner: 'alice', createdAt: serverTimestamp(), meta: S() }));
  });

  test('a calendar cannot be made for someone else, or with a made-up time', async () => {
    const store = db('mallory');
    const batch = writeBatch(store);
    batch.set(doc(store, 'spaces/s2'), { v: 2, owner: 'alice', createdAt: serverTimestamp(), meta: S() });
    batch.set(doc(store, 'spaces/s2/members/mallory'), { joinedAt: serverTimestamp(), profile: S() });
    await assertFails(batch.commit());
    const alice = db('alice');
    const late = writeBatch(alice);
    late.set(doc(alice, 'spaces/s3'), { v: 2, owner: 'alice', createdAt: Timestamp.fromMillis(0), meta: S() });
    late.set(doc(alice, 'spaces/s3/members/alice'), { joinedAt: serverTimestamp(), profile: S() });
    await assertFails(late.commit());
  });

  test('a plain, unsealed name is refused', async () => {
    const alice = db('alice');
    const batch = writeBatch(alice);
    batch.set(doc(alice, 'spaces/s2'), { v: 2, owner: 'alice', createdAt: serverTimestamp(), meta: '우리 일정' });
    batch.set(doc(alice, 'spaces/s2/members/alice'), { joinedAt: serverTimestamp(), profile: S() });
    await assertFails(batch.commit());
  });

  test('only members read a calendar and its members; nobody lists calendars', async () => {
    await aliceMakesSpace();
    await assertFails(getDoc(doc(db('mallory'), 'spaces/s1')));
    await assertFails(getDocs(collection(db('mallory'), 'spaces/s1/members')));
    await assertFails(getDoc(doc(db(null), 'spaces/s1')));
    await assertSucceeds(getDoc(doc(db('alice'), 'spaces/s1')));
    await assertFails(getDocs(collection(db('alice'), 'spaces')));
  });

  test('members rename the calendar; only the owner hands it on, and only to a member', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/s1'), { meta: S(80) }));
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1'), { owner: 'bob' }));
    await assertFails(updateDoc(doc(db('alice'), 'spaces/s1'), { owner: 'mallory' }));
    await assertFails(updateDoc(doc(db('alice'), 'spaces/s1'), { v: 3 }));
    await assertSucceeds(updateDoc(doc(db('alice'), 'spaces/s1'), { owner: 'bob' }));
  });

  test('only the owner deletes a calendar', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertFails(deleteDoc(doc(db('bob'), 'spaces/s1')));
    await assertSucceeds(deleteDoc(doc(db('alice'), 'spaces/s1')));
  });

  test('the creator of a calendar from before encryption can clear it away', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const store = ctx.firestore();
      await setDoc(doc(store, 'spaces/old'), { name: '공유 젤리 달력', secret: 'x'.repeat(28), createdBy: 'alice' });
      await setDoc(doc(store, 'spaces/old/members/alice'), { name: '창현', secret: 'x'.repeat(28) });
      await setDoc(doc(store, 'spaces/old/members/bob'), { name: '지은', secret: 'x'.repeat(28) });
      await setDoc(doc(store, 'spaces/old/jellies/j1'), { title: '예전 젤리', by: 'alice' });
    });
    await assertFails(deleteDoc(doc(db('bob'), 'spaces/old')));
    await assertSucceeds(deleteDoc(doc(db('alice'), 'spaces/old/jellies/j1')));
    await assertSucceeds(deleteDoc(doc(db('alice'), 'spaces/old/members/bob')));
    const alice = db('alice');
    const batch = writeBatch(alice);
    batch.delete(doc(alice, 'spaces/old/members/alice'));
    batch.delete(doc(alice, 'spaces/old'));
    await assertSucceeds(batch.commit());
  });
});

describe('joining and members', () => {
  test('joining needs a live invite to that very calendar', async () => {
    await aliceMakesSpace();
    await aliceMakesSpace('s2');
    await assertFails(join('mallory'));
    await assertFails(setDoc(doc(db('mallory'), 'spaces/s1/members/mallory'), { joinedAt: serverTimestamp(), profile: S() }));
    await aliceInvites('j'.repeat(43), 's2');
    await assertFails(join('mallory', 'j'.repeat(43), 's1'));
    await aliceInvites();
    await assertSucceeds(join('bob'));
    await assertSucceeds(getDocs(collection(db('bob'), 'spaces/s1/jellies')));
  });

  test('an expired invite lets nobody in', async () => {
    await aliceMakesSpace();
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `invites/${INVITE}`), { spaceId: 's1', by: 'alice', expiresAt: days(-1), wrapped: S(200) });
    });
    await assertFails(join('bob'));
  });

  test('an invite to a deleted calendar lets nobody in', async () => {
    await aliceMakesSpace();
    await aliceInvites();
    const alice = db('alice');
    const batch = writeBatch(alice);
    batch.delete(doc(alice, 'spaces/s1/members/alice'));
    batch.delete(doc(alice, 'spaces/s1'));
    await assertSucceeds(batch.commit());
    await assertFails(join('bob'));
  });

  test('nobody can add another person', async () => {
    await aliceMakesSpace();
    await aliceInvites();
    await assertFails(setDoc(doc(db('alice'), 'spaces/s1/members/bob'), { joinedAt: serverTimestamp(), profile: S(), invite: INVITE }));
  });

  test('members change their own name and colour only', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/s1/members/bob'), { profile: S(90) }));
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1/members/alice'), { profile: S(90) }));
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1/members/bob'), { joinedAt: Timestamp.fromMillis(0) }));
  });

  test('the owner lets people go; others can only leave themselves', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(join('carol'));
    await assertFails(deleteDoc(doc(db('bob'), 'spaces/s1/members/carol')));
    await assertSucceeds(deleteDoc(doc(db('alice'), 'spaces/s1/members/carol')));
    await assertFails(getDocs(collection(db('carol'), 'spaces/s1/jellies')));
    await assertSucceeds(deleteDoc(doc(db('bob'), 'spaces/s1/members/bob')));
    await assertFails(getDoc(doc(db('bob'), 'spaces/s1')));
  });
});

describe('key vaults', () => {
  const vault = (extra = {}) => ({ v: 1, lock: 'password', vault: S(400), updatedAt: serverTimestamp(), ...extra });
  const account = (uid) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'password' } }).firestore();
  const guest = (uid) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'anonymous' } }).firestore();

  test('an account keeps its own vault, and nobody else reads it', async () => {
    await assertSucceeds(setDoc(doc(account('alice'), 'users/alice'), vault()));
    await assertSucceeds(getDoc(doc(account('alice'), 'users/alice')));
    await assertSucceeds(setDoc(doc(account('alice'), 'users/alice'), vault({ lock: 'passphrase', vault: S(900) })));
    await assertFails(getDoc(doc(account('bob'), 'users/alice')));
    await assertFails(setDoc(doc(account('bob'), 'users/alice'), vault()));
    await assertFails(getDocs(collection(account('bob'), 'users')));
    await assertSucceeds(deleteDoc(doc(account('alice'), 'users/alice')));
  });

  test('a vault is sealed, small, and only for accounts', async () => {
    await assertFails(setDoc(doc(account('alice'), 'users/alice'), vault({ vault: '{"spaces":[]}' })));
    await assertFails(setDoc(doc(account('alice'), 'users/alice'), vault({ vault: S(20001) })));
    await assertFails(setDoc(doc(account('alice'), 'users/alice'), vault({ lock: 'none' })));
    await assertFails(setDoc(doc(account('alice'), 'users/alice'), vault({ extra: 1 })));
    await assertFails(setDoc(doc(guest('carol'), 'users/carol'), vault()));
  });
});

describe('invites', () => {
  test('members make invites; anyone signed in reads one by its id; nobody lists them', async () => {
    await aliceMakesSpace();
    await aliceInvites();
    await assertSucceeds(getDoc(doc(db('bob'), `invites/${INVITE}`)));
    await assertFails(getDoc(doc(db(null), `invites/${INVITE}`)));
    await assertFails(getDocs(collection(db('bob'), 'invites')));
  });

  test('outsiders, odd ids, long lives and old calendars get no invites', async () => {
    await aliceMakesSpace();
    const make = (uid, id, data) => setDoc(doc(db(uid), `invites/${id}`), { spaceId: 's1', by: uid, expiresAt: days(3), wrapped: S(200), ...data });
    await assertFails(make('mallory', 'm'.repeat(43), {}));
    await assertFails(make('alice', 'ABCDE23456', {}));
    await assertFails(make('alice', 'a'.repeat(43), { expiresAt: days(5) }));
    await assertFails(make('alice', 'b'.repeat(43), { expiresAt: days(-1) }));
    await assertFails(make('alice', 'c'.repeat(43), { by: 'bob' }));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'spaces/old'), { name: 'x', secret: 'x'.repeat(28), createdBy: 'alice' });
      await setDoc(doc(ctx.firestore(), 'spaces/old/members/alice'), { name: '창현', secret: 'x'.repeat(28) });
    });
    await assertFails(make('alice', 'd'.repeat(43), { spaceId: 'old' }));
  });

  test('the maker withdraws an invite; anyone clears it away once it has run out', async () => {
    await aliceMakesSpace();
    await aliceInvites();
    await assertFails(deleteDoc(doc(db('bob'), `invites/${INVITE}`)));
    await assertSucceeds(deleteDoc(doc(db('alice'), `invites/${INVITE}`)));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `invites/${INVITE}`), { spaceId: 's1', by: 'alice', expiresAt: days(-1), wrapped: S(200) });
    });
    await assertSucceeds(deleteDoc(doc(db('bob'), `invites/${INVITE}`)));
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
        title: S(70),
        start: S(70),
        updatedBy: 'bob',
        updatedByName: S(),
        updatedAt: serverTimestamp(),
      }),
    );
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1/jellies/j1'), { by: 'bob', updatedBy: 'bob', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1/jellies/j1'), { title: S(72), updatedBy: 'alice' }));
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1/jellies/j1'), { title: S(72), updatedBy: 'bob', updatedAt: Timestamp.fromMillis(0) }));
    await assertSucceeds(deleteDoc(doc(db('bob'), 'spaces/s1/jellies/j1')));
  });

  test('a jelly may be pinned, but only with a sealed value', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(setDoc(doc(db('alice'), 'spaces/s1/jellies/j1'), jelly('alice', { pinned: S() })));
    await assertSucceeds(setDoc(doc(db('alice'), 'spaces/s1/jellies/j2'), jelly('alice')));
    await assertSucceeds(updateDoc(doc(db('bob'), 'spaces/s1/jellies/j2'), {
      pinned: S(),
      updatedBy: 'bob',
      updatedByName: S(),
      updatedAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(db('alice'), 'spaces/s1/jellies/j3'), jelly('alice', { pinned: true })));
    await assertFails(updateDoc(doc(db('bob'), 'spaces/s1/jellies/j2'), {
      pinned: S(201),
      updatedBy: 'bob',
      updatedAt: serverTimestamp(),
    }));
  });

  test('outsiders see and change nothing', async () => {
    await aliceMakesSpace();
    await assertSucceeds(setDoc(doc(db('alice'), 'spaces/s1/jellies/j1'), jelly('alice')));
    await assertFails(getDoc(doc(db('mallory'), 'spaces/s1/jellies/j1')));
    await assertFails(getDocs(collection(db('mallory'), 'spaces/s1/jellies')));
    await assertFails(setDoc(doc(db('mallory'), 'spaces/s1/jellies/j2'), jelly('mallory')));
    await assertFails(updateDoc(doc(db('mallory'), 'spaces/s1/jellies/j1'), { title: S(70), updatedBy: 'mallory', updatedAt: serverTimestamp() }));
  });

  test('bad jellies are refused', async () => {
    await aliceMakesSpace();
    const ref = (id) => doc(db('alice'), `spaces/s1/jellies/${id}`);
    const { title, ...untitled } = jelly('alice');
    await assertFails(setDoc(ref('a'), untitled));
    await assertFails(setDoc(ref('b'), jelly('alice', { title: '저녁 약속' })));
    await assertFails(setDoc(ref('c'), jelly('alice', { note: S(6001) })));
    await assertFails(setDoc(ref('d'), jelly('alice', { by: 'bob' })));
    await assertFails(setDoc(ref('e'), jelly('alice', { extra: S() })));
    await assertFails(setDoc(ref('f'), jelly('alice', { memoCount: 3 })));
    await assertFails(setDoc(ref('g'), jelly('alice', { createdAt: Timestamp.fromMillis(0) })));
    await assertSucceeds(setDoc(ref('h'), jelly('alice')));
  });

  test('memos: anyone in the calendar writes, writers remove their own', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(setDoc(doc(db('alice'), 'spaces/s1/jellies/j1'), jelly('alice')));
    // Bob adds a memo and bumps the counter in one batch, as the app does.
    const bob = db('bob');
    const batch = writeBatch(bob);
    batch.set(doc(bob, 'spaces/s1/jellies/j1/memos/m1'), { text: S(), byName: S(), by: 'bob', at: serverTimestamp() });
    batch.update(doc(bob, 'spaces/s1/jellies/j1'), { memoCount: increment(1), lastMemoAt: serverTimestamp() });
    await assertSucceeds(batch.commit());
    await assertSucceeds(getDocs(collection(db('alice'), 'spaces/s1/jellies/j1/memos')));
    await assertFails(deleteDoc(doc(db('alice'), 'spaces/s1/jellies/j1/memos/m1')));
    await assertFails(setDoc(doc(db('alice'), 'spaces/s1/jellies/j1/memos/m2'), { text: S(), byName: S(), by: 'bob', at: serverTimestamp() }));
    await assertFails(setDoc(doc(db('alice'), 'spaces/s1/jellies/j1/memos/m3'), { text: '평문 메모', byName: S(), by: 'alice', at: serverTimestamp() }));
    const undo = writeBatch(bob);
    undo.delete(doc(bob, 'spaces/s1/jellies/j1/memos/m1'));
    undo.update(doc(bob, 'spaces/s1/jellies/j1'), { memoCount: increment(-1) });
    await assertSucceeds(undo.commit());
  });

  test('deleting a jelly takes everyone\'s memos with it', async () => {
    await aliceMakesSpace();
    await bobJoins();
    await assertSucceeds(setDoc(doc(db('alice'), 'spaces/s1/jellies/j1'), jelly('alice')));
    await assertSucceeds(setDoc(doc(db('bob'), 'spaces/s1/jellies/j1/memos/m1'), { text: S(), byName: S(), by: 'bob', at: serverTimestamp() }));
    const alice = db('alice');
    const batch = writeBatch(alice);
    batch.delete(doc(alice, 'spaces/s1/jellies/j1/memos/m1'));
    batch.delete(doc(alice, 'spaces/s1/jellies/j1'));
    await assertSucceeds(batch.commit());
  });
});
