// Firestore security-rules tests for Tether.
//
// Each "app" test replays the exact reads/writes the Android code performs
// (same paths, same batches), so deploying the rules can't break the app.
// Each "blocks" test is an abuse case the rules must reject.
//
// Run:  cd firestore-tests && npm install && npm test

const { test, before, beforeEach, after, describe } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const {
  doc, setDoc, getDoc, getDocs, updateDoc, deleteDoc, collection, query, where,
  writeBatch, runTransaction, increment, arrayUnion, arrayRemove,
} = require('firebase/firestore');

const TODAY = '2026-05-10';
const WEEK = '2026-W19';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-tether',
    firestore: {
      rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8'),
    },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  // Seed: group g1 (alice creator, bob member), carol outside, full group, solo group.
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const user = (uid, name, groupIds) =>
      setDoc(doc(db, 'users', uid), { uid, name, email: `${uid}@x.com`, groupIds, totalHours: 0 });
    await user('alice', 'Alice', ['g1']);
    await user('bob', 'Bob', ['g1']);
    await user('carol', 'Carol', []);
    await setDoc(doc(db, 'groups', 'g1'), {
      id: 'g1', name: 'Study Squad', goalType: 'Study', members: ['alice', 'bob'],
      inviteCode: 'ABC123', createdBy: 'alice', solo: false, createdAt: 1,
    });
    await setDoc(doc(db, 'groups', 'full'), {
      id: 'full', name: 'Full', goalType: 'Gym', members: ['u1', 'u2', 'u3', 'u4', 'u5', 'u6'],
      inviteCode: 'FULL66', createdBy: 'u1', solo: false, createdAt: 1,
    });
    await setDoc(doc(db, 'groups', 'solo'), {
      id: 'solo', name: 'Me', goalType: 'Other', members: ['alice'],
      inviteCode: '', createdBy: 'alice', solo: true, createdAt: 1,
    });
    await setDoc(doc(db, 'groupStats', 'g1', 'daily', TODAY), { alice: 1.5, bob: 2 });
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

// Mirrors LogRepository.writeLog(): one batch, five writes.
function logBatch(db, uid, groupId, hours, streak = { currentStreak: 1, longestStreak: 1 }) {
  const b = writeBatch(db);
  b.set(doc(db, 'logs', `log-${uid}-${hours}`), {
    id: `log-${uid}-${hours}`, userId: uid, groupId, userName: uid, userInitials: 'X',
    avatarColorHex: '#3B82F6', date: TODAY, value: hours, note: '', createdAt: Date.now(),
  });
  b.set(doc(db, 'groupStats', groupId, 'daily', TODAY), { [uid]: increment(hours) }, { merge: true });
  b.set(doc(db, 'groupStats', groupId, 'weekly', WEEK), { [uid]: increment(hours) }, { merge: true });
  b.set(doc(db, 'users', uid), { totalHours: increment(hours) }, { merge: true });
  b.set(doc(db, 'groupStats', groupId, 'streaks', uid), { lastLogDate: TODAY, ...streak }, { merge: true });
  return b.commit();
}

describe('users', () => {
  test('app: signup creates your own user doc', async () => {
    await assertSucceeds(setDoc(doc(as('dave'), 'users', 'dave'),
      { uid: 'dave', name: 'Dave', email: 'd@x.com', totalHours: 0, groupIds: [] }));
  });

  test('app: anyone signed in can read names (leaderboard)', async () => {
    await assertSucceeds(getDoc(doc(as('carol'), 'users', 'alice')));
  });

  test('blocks: reading without signing in', async () => {
    await assertFails(getDoc(doc(anon(), 'users', 'alice')));
  });

  test('blocks: editing someone else\'s user doc', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'users', 'alice'), { totalHours: 999 }));
  });

  test('blocks: adding arbitrary fields to your own doc', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'users', 'alice'), { isAdmin: true }));
  });
});

describe('groups', () => {
  test('app: create a group (batch with your groupIds)', async () => {
    const db = as('carol');
    const b = writeBatch(db);
    b.set(doc(db, 'groups', 'g2'), {
      id: 'g2', name: 'Gym Bros', goalType: 'Gym', members: ['carol'],
      inviteCode: 'ZZZ999', createdBy: 'carol', solo: false, createdAt: 2,
    });
    b.set(doc(db, 'users', 'carol'), { groupIds: arrayUnion('g2') }, { merge: true });
    await assertSucceeds(b.commit());
  });

  test('blocks: creating a group that already contains other people', async () => {
    await assertFails(setDoc(doc(as('carol'), 'groups', 'g3'), {
      id: 'g3', name: 'X', goalType: 'Gym', members: ['carol', 'bob'],
      inviteCode: 'QQQ111', createdBy: 'carol', solo: false, createdAt: 2,
    }));
  });

  test('blocks: creating a group in someone else\'s name', async () => {
    await assertFails(setDoc(doc(as('carol'), 'groups', 'g3'), {
      id: 'g3', name: 'X', goalType: 'Gym', members: ['carol'],
      inviteCode: 'QQQ111', createdBy: 'alice', solo: false, createdAt: 2,
    }));
  });

  test('app: join by invite code (query + transaction)', async () => {
    const db = as('carol');
    const found = await assertSucceeds(
      getDocs(query(collection(db, 'groups'), where('inviteCode', '==', 'ABC123'))));
    const ref = found.docs[0].ref;
    await assertSucceeds(runTransaction(db, async (tx) => {
      await tx.get(ref);
      tx.update(ref, { members: arrayUnion('carol') });
      tx.set(doc(db, 'users', 'carol'), { groupIds: arrayUnion('g1') }, { merge: true });
    }));
  });

  test('blocks: joining a full group (6 members)', async () => {
    await assertFails(updateDoc(doc(as('carol'), 'groups', 'full'), { members: arrayUnion('carol') }));
  });

  test('blocks: joining a solo group', async () => {
    await assertFails(updateDoc(doc(as('carol'), 'groups', 'solo'), { members: arrayUnion('carol') }));
  });

  test('blocks: adding someone else to a group', async () => {
    await assertFails(updateDoc(doc(as('carol'), 'groups', 'g1'), { members: arrayUnion('dave') }));
  });

  test('blocks: renaming a group via update', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'groups', 'g1'), { name: 'Hacked' }));
  });

  test('app: leave a group (batch)', async () => {
    const db = as('bob');
    const b = writeBatch(db);
    b.update(doc(db, 'groups', 'g1'), { members: arrayRemove('bob') });
    b.update(doc(db, 'users', 'bob'), { groupIds: arrayRemove('g1') });
    await assertSucceeds(b.commit());
  });

  test('blocks: kicking another member', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'groups', 'g1'), { members: arrayRemove('alice') }));
  });

  test('app: creator deletes the group (batch)', async () => {
    const db = as('alice');
    const b = writeBatch(db);
    b.delete(doc(db, 'groups', 'g1'));
    b.update(doc(db, 'users', 'alice'), { groupIds: arrayRemove('g1') });
    await assertSucceeds(b.commit());
  });

  test('blocks: a non-creator deleting the group', async () => {
    await assertFails(deleteDoc(doc(as('bob'), 'groups', 'g1')));
  });
});

describe('logging hours', () => {
  test('app: member logs hours (log + stats + total + streak in one batch)', async () => {
    await assertSucceeds(logBatch(as('bob'), 'bob', 'g1', 1.25));
  });

  test('app: system log (0h) after joining', async () => {
    await assertSucceeds(setDoc(doc(as('bob'), 'logs', 'sys1'), {
      id: 'sys1', userId: 'bob', groupId: 'g1', userName: 'Bob', userInitials: 'B',
      avatarColorHex: '#22C55E', date: TODAY, value: 0, note: 'Joined the group! 👋', createdAt: 1,
    }));
  });

  test('blocks: logging in a group you are not in', async () => {
    await assertFails(logBatch(as('carol'), 'carol', 'g1', 1));
  });

  test('blocks: logging more than 24 hours at once', async () => {
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 30));
  });

  test('blocks: writing a log as someone else', async () => {
    await assertFails(setDoc(doc(as('bob'), 'logs', 'fake'), {
      id: 'fake', userId: 'alice', groupId: 'g1', date: TODAY, value: 2, note: '', createdAt: 1,
    }));
  });

  test('blocks: raising another member\'s hours', async () => {
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'daily', TODAY),
      { alice: increment(5) }, { merge: true }));
  });

  test('blocks: lowering your own hours on the leaderboard', async () => {
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'daily', TODAY),
      { bob: increment(-1) }, { merge: true }));
  });

  test('blocks: editing someone else\'s streak', async () => {
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'streaks', 'alice'),
      { currentStreak: 0, longestStreak: 0, lastLogDate: TODAY }));
  });

  test('app: delete your own log', async () => {
    await logBatch(as('bob'), 'bob', 'g1', 1);
    await assertSucceeds(deleteDoc(doc(as('bob'), 'logs', 'log-bob-1')));
  });

  test('blocks: deleting someone else\'s log', async () => {
    await logBatch(as('bob'), 'bob', 'g1', 1);
    await assertFails(deleteDoc(doc(as('alice'), 'logs', 'log-bob-1')));
  });
});

describe('reading', () => {
  test('app: leaderboard listeners (group, stats, streaks, nudges, names)', async () => {
    const db = as('bob');
    await assertSucceeds(getDoc(doc(db, 'groups', 'g1')));
    await assertSucceeds(getDoc(doc(db, 'groupStats', 'g1', 'daily', TODAY)));
    await assertSucceeds(getDoc(doc(db, 'groupStats', 'g1', 'weekly', WEEK)));
    await assertSucceeds(getDocs(collection(db, 'groupStats', 'g1', 'streaks')));
    await assertSucceeds(getDocs(query(collection(db, 'groupStats', 'g1', 'nudges'), where('date', '==', TODAY))));
    await assertSucceeds(getDocs(query(collection(db, 'users'), where('uid', 'in', ['alice', 'bob']))));
  });

  test('app: activity watcher + profile queries', async () => {
    const db = as('bob');
    await assertSucceeds(getDocs(query(collection(db, 'logs'),
      where('groupId', '==', 'g1'), where('date', '==', TODAY))));
    await assertSucceeds(getDocs(query(collection(db, 'logs'), where('userId', '==', 'bob'))));
    await assertSucceeds(getDocs(query(collection(db, 'groupStats', 'g1', 'nudges'),
      where('nudgedUid', '==', 'bob'), where('date', '==', TODAY))));
  });

  test('blocks: unauthenticated reads of stats', async () => {
    await assertFails(getDoc(doc(anon(), 'groupStats', 'g1', 'daily', TODAY)));
  });
});

describe('nudges', () => {
  const nudge = (from, to, id = `${TODAY}_${from}_${to}`) => ({
    id, data: { nudgerUid: from, nudgerName: from, nudgedUid: to, groupId: 'g1', date: TODAY, timestamp: Date.now() },
  });

  test('app: nudge a group member', async () => {
    const n = nudge('bob', 'alice');
    await assertSucceeds(setDoc(doc(as('bob'), 'groupStats', 'g1', 'nudges', n.id), n.data));
  });

  test('app: re-sending the same nudge just overwrites it', async () => {
    const n = nudge('bob', 'alice');
    await setDoc(doc(as('bob'), 'groupStats', 'g1', 'nudges', n.id), n.data);
    await assertSucceeds(setDoc(doc(as('bob'), 'groupStats', 'g1', 'nudges', n.id), n.data));
  });

  test('blocks: nudging in someone else\'s name', async () => {
    const n = nudge('alice', 'bob');
    await assertFails(setDoc(doc(as('carol'), 'groupStats', 'g1', 'nudges', n.id), n.data));
  });

  test('blocks: nudging someone outside the group', async () => {
    const n = nudge('bob', 'carol');
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'nudges', n.id), n.data));
  });

  test('blocks: nudging yourself', async () => {
    const n = nudge('bob', 'bob');
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'nudges', n.id), n.data));
  });

  test('blocks: a nudge id that does not match its contents', async () => {
    const n = nudge('bob', 'alice', `${TODAY}_bob_someoneelse`);
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'nudges', n.id), n.data));
  });

  test('legacy: old app versions can still write the old nudge path', async () => {
    await assertSucceeds(setDoc(doc(as('bob'), 'nudges', 'g1', TODAY, 'bob_alice'), { nudgedUid: 'alice' }));
  });
});

describe('default deny', () => {
  test('blocks: unknown collections', async () => {
    await assertFails(setDoc(doc(as('alice'), 'admin', 'x'), { a: 1 }));
  });
});
