// Firestore security-rules tests for Tether.
//
// "app:" tests replay the exact reads/writes the Android code performs (same
// paths, same batches), so deploying the rules can't break the app.
// "blocks:" tests are abuse cases the rules must reject, including one test per
// loophole found in the Oct 2026 audit (marked [audit #n]).
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
  writeBatch, runTransaction, increment, arrayUnion, arrayRemove, deleteField,
  serverTimestamp, Bytes, Timestamp,
} = require('firebase/firestore');

// Rules compare dates with the server clock, so tests use the real "today" (UTC).
const DAY_MS = 24 * 60 * 60 * 1000;
const iso = (t) => new Date(t).toISOString().slice(0, 10);
const TODAY = iso(Date.now());
const YESTERDAY = iso(Date.now() - DAY_MS);
const LONG_AGO = iso(Date.now() - 10 * DAY_MS);
const WEEK = '2026-W40';

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
    const user = (uid, name) => setDoc(doc(db, 'users', uid), { uid, name, totalHours: 0 });
    await user('alice', 'Alice');
    await user('bob', 'Bob');
    await user('carol', 'Carol');
    // A user document from an older app version, still carrying private fields.
    await setDoc(doc(db, 'users', 'legacy'), { uid: 'legacy', name: 'Old', email: 'old@x.com', groupIds: ['g1'], totalHours: 0 });
    await setDoc(doc(db, 'groups', 'g1'), {
      id: 'g1', name: 'Study Squad', goalType: 'Study', members: ['alice', 'bob'],
      inviteCode: 'ABC123', createdBy: 'alice', solo: false, createdAt: 1, metric: 'hours',
    });
    await setDoc(doc(db, 'inviteCodes', 'ABC123'), { groupId: 'g1', createdBy: 'alice' });
    await setDoc(doc(db, 'groups', 'full'), {
      id: 'full', name: 'Full', goalType: 'Gym', members: ['u1', 'u2', 'u3', 'u4', 'u5', 'u6'],
      inviteCode: 'FULL66', createdBy: 'u1', solo: false, createdAt: 1,
    });
    await setDoc(doc(db, 'groups', 'solo'), {
      id: 'solo', name: 'Me', goalType: 'Other', members: ['alice'],
      inviteCode: '', createdBy: 'alice', solo: true, createdAt: 1,
    });
    await setDoc(doc(db, 'groupStats', 'g1', 'daily', TODAY), { alice: 1.5, bob: 2 });
    await setDoc(doc(db, 'groupStats', 'g1', 'streaks', 'bob'), { currentStreak: 4, longestStreak: 9, lastLogDate: YESTERDAY });
    await setDoc(doc(db, 'logs', 'seed-log'), {
      id: 'seed-log', userId: 'alice', groupId: 'g1', userName: 'Alice', date: TODAY, value: 1.5, note: 'private note', createdAt: 1,
    });
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

let logCounter = 0;

const photoBytes = (size = 60 * 1024) => Bytes.fromUint8Array(new Uint8Array(size).fill(7));

// Mirrors LogRepository.writeLog(): one batch — log (+ its proof photo), daily,
// weekly (each carrying the log id), total hours, personal heatmap and streak.
function logBatch(db, uid, groupId, hours, opts = {}) {
  const logId = opts.logId ?? `log-${uid}-${++logCounter}`;
  const date = opts.date ?? TODAY;
  const b = writeBatch(db);
  b.set(doc(db, 'logs', logId), {
    id: logId, userId: uid, groupId, userName: uid, userInitials: 'X',
    avatarColorHex: '#3B82F6', date, value: hours, note: opts.note ?? 'Solved two graph problems',
    createdAt: Date.now(), source: opts.source ?? 'manual', hasPhoto: opts.hasPhoto ?? !!opts.photo,
  });
  if (opts.photo) {
    b.set(doc(db, 'groupStats', groupId, 'proofs', logId), {
      uid, date, createdAt: serverTimestamp(), image: opts.photo,
    });
  }
  const statsHours = opts.statsHours ?? hours;
  b.set(doc(db, 'groupStats', groupId, 'daily', date), { [uid]: increment(statsHours), [`log_${uid}`]: logId }, { merge: true });
  b.set(doc(db, 'groupStats', groupId, 'weekly', WEEK), { [uid]: increment(statsHours), [`log_${uid}`]: logId }, { merge: true });
  b.set(doc(db, 'users', uid), { totalHours: increment(hours) }, { merge: true });
  b.set(doc(db, 'users', uid, 'heatmap', date.slice(0, 4)), { [date]: increment(hours) }, { merge: true });
  if (opts.streak !== null) {
    b.set(doc(db, 'groupStats', groupId, 'streaks', uid),
      opts.streak ?? { lastLogDate: date, currentStreak: 1, longestStreak: 1 }, { merge: true });
  }
  return b.commit();
}

describe('users', () => {
  test('app: signup creates a public profile (no email, no group list)', async () => {
    await assertSucceeds(setDoc(doc(as('dave'), 'users', 'dave'), { uid: 'dave', name: 'Dave', totalHours: 0 }));
  });

  test('blocks: storing an email on the public profile [audit #2]', async () => {
    await assertFails(setDoc(doc(as('dave'), 'users', 'dave'), { uid: 'dave', name: 'Dave', email: 'd@x.com', totalHours: 0 }));
    await assertFails(updateDoc(doc(as('bob'), 'users', 'bob'), { email: 'bob@x.com' }));
  });

  test('blocks: re-adding a group list to the public profile', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'users', 'bob'), { groupIds: ['g1'] }));
  });

  test('app: an old profile strips its email and group list (migration)', async () => {
    await assertSucceeds(updateDoc(doc(as('legacy'), 'users', 'legacy'), { email: deleteField(), groupIds: deleteField() }));
  });

  test('app: saving focus areas from onboarding', async () => {
    await assertSucceeds(updateDoc(doc(as('bob'), 'users', 'bob'), { interests: ['coding', 'fitness'], stage: 'student' }));
  });

  test('app: anyone signed in can read names (leaderboards)', async () => {
    await assertSucceeds(getDoc(doc(as('carol'), 'users', 'alice')));
  });

  test('blocks: reading without signing in', async () => {
    await assertFails(getDoc(doc(anon(), 'users', 'alice')));
  });

  test('blocks: editing someone else\'s profile', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'users', 'alice'), { totalHours: 999 }));
  });

  test('blocks: adding arbitrary fields', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'users', 'alice'), { isAdmin: true }));
  });

  test('app: deleting your own account data', async () => {
    await assertSucceeds(deleteDoc(doc(as('bob'), 'users', 'bob')));
    await assertFails(deleteDoc(doc(as('bob'), 'users', 'alice')));
  });
});

describe('groups and invite codes', () => {
  test('app: create a group with its invite code (batch)', async () => {
    const db = as('carol');
    const b = writeBatch(db);
    b.set(doc(db, 'groups', 'g2'), {
      id: 'g2', name: 'Gym Bros', goalType: 'Gym', members: ['carol'],
      inviteCode: 'ZZZ999', createdBy: 'carol', solo: false, createdAt: 2, metric: 'hours', proof: 'optional',
    });
    b.set(doc(db, 'inviteCodes', 'ZZZ999'), { groupId: 'g2', createdBy: 'carol' });
    await assertSucceeds(b.commit());
  });

  test('blocks: an invite code pointing at someone else\'s group', async () => {
    await assertFails(setDoc(doc(as('carol'), 'inviteCodes', 'HACK01'), { groupId: 'g1', createdBy: 'alice' }));
  });

  test('blocks: overwriting an existing invite code', async () => {
    await assertFails(setDoc(doc(as('alice'), 'inviteCodes', 'ABC123'), { groupId: 'solo', createdBy: 'alice' }));
  });

  test('app: a member backfills a missing invite code (migration)', async () => {
    await env.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), 'inviteCodes', 'ABC123')));
    await assertSucceeds(setDoc(doc(as('bob'), 'inviteCodes', 'ABC123'), { groupId: 'g1', createdBy: 'alice' }));
  });

  test('blocks: listing all groups to harvest invite codes [audit #1]', async () => {
    await assertFails(getDocs(collection(as('carol'), 'groups')));
  });

  test('blocks: listing all invite codes [audit #1]', async () => {
    await assertFails(getDocs(collection(as('carol'), 'inviteCodes')));
  });

  test('app: list my groups (members array-contains me)', async () => {
    const snap = await assertSucceeds(getDocs(query(collection(as('bob'), 'groups'), where('members', 'array-contains', 'bob'))));
    if (snap.size !== 1) throw new Error(`expected 1 group, got ${snap.size}`);
  });

  test('blocks: listing groups by any other filter', async () => {
    await assertFails(getDocs(query(collection(as('carol'), 'groups'), where('goalType', '==', 'Study'))));
  });

  test('app: join with a code (lookup + transaction)', async () => {
    const db = as('carol');
    const code = await assertSucceeds(getDoc(doc(db, 'inviteCodes', 'ABC123')));
    const ref = doc(db, 'groups', code.data().groupId);
    await assertSucceeds(runTransaction(db, async (tx) => {
      await tx.get(ref);
      tx.update(ref, { members: arrayUnion('carol') });
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

  test('app: a member leaves', async () => {
    await assertSucceeds(updateDoc(doc(as('bob'), 'groups', 'g1'), { members: arrayRemove('bob') }));
  });

  test('blocks: the creator leaving and orphaning the group', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'groups', 'g1'), { members: arrayRemove('alice') }));
  });

  test('app: the creator hands the group over when deleting their account', async () => {
    await assertSucceeds(updateDoc(doc(as('alice'), 'groups', 'g1'), { members: ['bob'], createdBy: 'bob' }));
  });

  test('blocks: handing a group to a non-member', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'groups', 'g1'), { members: ['bob'], createdBy: 'carol' }));
  });

  test('blocks: kicking another member', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'groups', 'g1'), { members: arrayRemove('alice') }));
  });

  test('app: creator deletes the group and its invite code (batch)', async () => {
    const db = as('alice');
    const b = writeBatch(db);
    b.delete(doc(db, 'groups', 'g1'));
    b.delete(doc(db, 'inviteCodes', 'ABC123'));
    await assertSucceeds(b.commit());
  });

  test('blocks: a non-creator deleting the group or its code', async () => {
    await assertFails(deleteDoc(doc(as('bob'), 'groups', 'g1')));
    await assertFails(deleteDoc(doc(as('bob'), 'inviteCodes', 'ABC123')));
  });

  test('app: the creator ranks the leaderboard by solves, then back to hours', async () => {
    await assertSucceeds(updateDoc(doc(as('alice'), 'groups', 'g1'), { metric: 'solves' }));
    await assertSucceeds(updateDoc(doc(as('alice'), 'groups', 'g1'), { metric: 'hours' }));
  });

  test('blocks: a member changing the metric, or an unknown metric', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'groups', 'g1'), { metric: 'solves' }));
    await assertFails(updateDoc(doc(as('alice'), 'groups', 'g1'), { metric: 'karma' }));
  });

  test('blocks: creating a group that already ranks by solves', async () => {
    const db = as('carol');
    const b = writeBatch(db);
    b.set(doc(db, 'groups', 'g3'), {
      id: 'g3', name: 'Coders', goalType: 'Coding', members: ['carol'],
      inviteCode: 'YYY888', createdBy: 'carol', solo: false, createdAt: 2, metric: 'solves',
    });
    b.set(doc(db, 'inviteCodes', 'YYY888'), { groupId: 'g3', createdBy: 'carol' });
    await assertFails(b.commit());
  });
});

describe('logging hours', () => {
  test('app: member logs hours (log + stats + total + heatmap + streak in one batch)', async () => {
    await assertSucceeds(logBatch(as('bob'), 'bob', 'g1', 1.25, {
      streak: { lastLogDate: TODAY, currentStreak: 5, longestStreak: 9 },
    }));
  });

  test('app: logging twice in a day', async () => {
    await logBatch(as('bob'), 'bob', 'g1', 3, { streak: { lastLogDate: TODAY, currentStreak: 5, longestStreak: 9 } });
    await assertSucceeds(logBatch(as('bob'), 'bob', 'g1', 2.5, { streak: { lastLogDate: TODAY, currentStreak: 5, longestStreak: 9 } }));
  });

  test('app: system log (0h) after joining', async () => {
    await assertSucceeds(setDoc(doc(as('bob'), 'logs', 'sys1'), {
      id: 'sys1', userId: 'bob', groupId: 'g1', userName: 'Bob', userInitials: 'B',
      avatarColorHex: '#22C55E', date: TODAY, value: 0, note: 'Joined the group! 👋', createdAt: 1,
    }));
  });

  test('blocks: pushing a day past 24h with repeated writes [audit #4]', async () => {
    const streak = { lastLogDate: TODAY, currentStreak: 5, longestStreak: 9 };
    await logBatch(as('bob'), 'bob', 'g1', 12, { streak });
    await logBatch(as('bob'), 'bob', 'g1', 9, { streak });
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 6, { streak }));   // 2 + 12 + 9 + 6 > 24
  });

  test('blocks: raising leaderboard hours without a log [audit #6]', async () => {
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'daily', TODAY), { bob: increment(10) }, { merge: true }));
  });

  test('blocks: stats that don\'t match the log they cite [audit #6]', async () => {
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 1, { statsHours: 8, streak: null }));
  });

  test('blocks: reusing an old log to justify a new increase [audit #6]', async () => {
    await assertFails(setDoc(doc(as('alice'), 'groupStats', 'g1', 'daily', TODAY),
      { alice: increment(1.5), log_alice: 'seed-log' }, { merge: true }));
  });

  test('blocks: backdating a log with a wrong phone clock', async () => {
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 1, { date: LONG_AGO, streak: null }));
  });

  test('blocks: logging in a group you are not in', async () => {
    await assertFails(logBatch(as('carol'), 'carol', 'g1', 1, { streak: null }));
  });

  test('blocks: logging more than 24 hours at once', async () => {
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 30, { streak: null }));
  });

  test('blocks: writing a log as someone else', async () => {
    await assertFails(setDoc(doc(as('bob'), 'logs', 'fake'), {
      id: 'fake', userId: 'alice', groupId: 'g1', date: TODAY, value: 2, note: 'Did some work', createdAt: 1,
    }));
  });

  test('blocks: raising another member\'s hours', async () => {
    const db = as('bob');
    const b = writeBatch(db);
    b.set(doc(db, 'logs', 'x1'), { id: 'x1', userId: 'bob', groupId: 'g1', date: TODAY, value: 5, note: 'Did some work', createdAt: 1 });
    b.set(doc(db, 'groupStats', 'g1', 'daily', TODAY), { alice: increment(5), log_bob: 'x1' }, { merge: true });
    await assertFails(b.commit());
  });

  test('app: delete your own log', async () => {
    await logBatch(as('bob'), 'bob', 'g1', 1, { logId: 'mine', streak: { lastLogDate: TODAY, currentStreak: 5, longestStreak: 9 } });
    await assertSucceeds(deleteDoc(doc(as('bob'), 'logs', 'mine')));
  });

  test('blocks: deleting someone else\'s log', async () => {
    await assertFails(deleteDoc(doc(as('bob'), 'logs', 'seed-log')));
  });
});

describe('streaks', () => {
  const streakRef = (db, uid = 'bob') => doc(db, 'groupStats', 'g1', 'streaks', uid);

  test('app: consecutive day continues the streak', async () => {
    await assertSucceeds(setDoc(streakRef(as('bob')), { lastLogDate: TODAY, currentStreak: 5, longestStreak: 9 }));
  });

  test('app: a LeetCode solve today credits the streak with no hours logged (merge)', async () => {
    await assertSucceeds(setDoc(streakRef(as('bob')),
      { lastLogDate: TODAY, currentStreak: 5, longestStreak: 9 }, { merge: true }));
  });

  test('app: a gap resets the streak to 1', async () => {
    await env.withSecurityRulesDisabled((ctx) => setDoc(streakRef(ctx.firestore()), { currentStreak: 4, longestStreak: 9, lastLogDate: LONG_AGO }));
    await assertSucceeds(setDoc(streakRef(as('bob')), { lastLogDate: TODAY, currentStreak: 1, longestStreak: 9 }));
  });

  test('app: first ever streak', async () => {
    await assertSucceeds(setDoc(streakRef(as('alice'), 'alice'), { lastLogDate: TODAY, currentStreak: 1, longestStreak: 1 }));
  });

  test('blocks: setting any streak you like [audit #5]', async () => {
    await assertFails(setDoc(streakRef(as('bob')), { lastLogDate: TODAY, currentStreak: 365, longestStreak: 365 }));
  });

  test('blocks: continuing a streak across a gap [audit #5]', async () => {
    await env.withSecurityRulesDisabled((ctx) => setDoc(streakRef(ctx.firestore()), { currentStreak: 4, longestStreak: 9, lastLogDate: LONG_AGO }));
    await assertFails(setDoc(streakRef(as('bob')), { lastLogDate: TODAY, currentStreak: 5, longestStreak: 9 }));
  });

  test('blocks: bumping the streak twice on the same day [audit #5]', async () => {
    await setDoc(streakRef(as('bob')), { lastLogDate: TODAY, currentStreak: 5, longestStreak: 9 });
    await assertFails(setDoc(streakRef(as('bob')), { lastLogDate: TODAY, currentStreak: 6, longestStreak: 9 }));
  });

  test('blocks: inflating the longest streak', async () => {
    await assertFails(setDoc(streakRef(as('bob')), { lastLogDate: TODAY, currentStreak: 5, longestStreak: 50 }));
  });

  test('blocks: editing someone else\'s streak', async () => {
    await assertFails(setDoc(streakRef(as('alice')), { lastLogDate: TODAY, currentStreak: 5, longestStreak: 9 }));
  });
});

describe('reading group data', () => {
  test('app: members read the leaderboard (group, stats, streaks, nudges, names)', async () => {
    const db = as('bob');
    await assertSucceeds(getDoc(doc(db, 'groups', 'g1')));
    await assertSucceeds(getDoc(doc(db, 'groupStats', 'g1', 'daily', TODAY)));
    await assertSucceeds(getDoc(doc(db, 'groupStats', 'g1', 'weekly', WEEK)));
    await assertSucceeds(getDocs(collection(db, 'groupStats', 'g1', 'streaks')));
    await assertSucceeds(getDocs(query(collection(db, 'groupStats', 'g1', 'nudges'), where('date', '==', TODAY))));
    await assertSucceeds(getDocs(query(collection(db, 'users'), where('uid', 'in', ['alice', 'bob']))));
  });

  test('app: activity watcher and profile queries', async () => {
    const db = as('bob');
    await assertSucceeds(getDocs(query(collection(db, 'logs'), where('groupId', '==', 'g1'), where('date', '==', TODAY))));
    await assertSucceeds(getDocs(query(collection(db, 'logs'), where('userId', '==', 'bob'))));
    await assertSucceeds(getDocs(query(collection(db, 'groupStats', 'g1', 'nudges'),
      where('nudgedUid', '==', 'bob'), where('date', '==', TODAY))));
  });

  test('blocks: reading every log note [audit #3]', async () => {
    await assertFails(getDocs(collection(as('carol'), 'logs')));
  });

  test('blocks: reading another group\'s logs [audit #3]', async () => {
    await assertFails(getDocs(query(collection(as('carol'), 'logs'), where('groupId', '==', 'g1'))));
    await assertFails(getDoc(doc(as('carol'), 'logs', 'seed-log')));
  });

  test('blocks: non-members reading a group\'s stats and streaks', async () => {
    await assertFails(getDoc(doc(as('carol'), 'groupStats', 'g1', 'daily', TODAY)));
    await assertFails(getDocs(collection(as('carol'), 'groupStats', 'g1', 'streaks')));
  });

  test('blocks: unauthenticated reads', async () => {
    await assertFails(getDoc(doc(anon(), 'groups', 'g1')));
  });
});

describe('nudges', () => {
  const nudge = (from, to, id = `${TODAY}_${from}_${to}`, date = TODAY) => ({
    id, data: { nudgerUid: from, nudgerName: from, nudgedUid: to, groupId: 'g1', date, timestamp: Date.now() },
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
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'nudges', n.id), n.data));
  });

  test('blocks: nudging someone outside the group or yourself', async () => {
    const out = nudge('bob', 'carol');
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'nudges', out.id), out.data));
    const self = nudge('bob', 'bob');
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'nudges', self.id), self.data));
  });

  test('blocks: nudges dated on other days', async () => {
    const n = nudge('bob', 'alice', `${LONG_AGO}_bob_alice`, LONG_AGO);
    await assertFails(setDoc(doc(as('bob'), 'groupStats', 'g1', 'nudges', n.id), n.data));
  });

  test('blocks: the retired open nudge path', async () => {
    await assertFails(setDoc(doc(as('bob'), 'nudges', 'g1', TODAY, 'bob_alice'), { nudgedUid: 'alice' }));
  });
});

describe('connected accounts and LeetCode completions', () => {
  const completion = (key, source, extra = {}) => ({
    key, title: 'LRU Cache', slug: key, source, completedAt: 1, syncedAt: 2, userName: 'Bob', ...extra,
  });

  test('app: connect and disconnect a LeetCode handle', async () => {
    await assertSucceeds(updateDoc(doc(as('bob'), 'users', 'bob'), { leetcodeUsername: 'Bob_Codes' }));
    await assertSucceeds(updateDoc(doc(as('bob'), 'users', 'bob'), { leetcodeUsername: deleteField() }));
  });

  test('blocks: the retired handle-claim collection [audit #8]', async () => {
    // Claims let anyone squat a handle; ownership is now proven by a code in the
    // LeetCode bio, checked by every viewer, so the collection is closed.
    await assertFails(setDoc(doc(as('bob'), 'leetcodeUsernames', 'hemxnth16'), { uid: 'bob', username: 'hemxnth16' }));
  });

  test('app: sync writes verified completions and the index (batch)', async () => {
    const db = as('bob');
    const b = writeBatch(db);
    b.set(doc(db, 'users', 'bob', 'completions', 'lru-cache'), completion('lru-cache', 'leetcode'));
    b.set(doc(db, 'users', 'bob', 'meta', 'completionIndex'), { items: { 'lru-cache': { s: 'leetcode', t: 1 } } }, { merge: true });
    await assertSucceeds(b.commit());
  });

  test('blocks: self-reported completions (only LeetCode sync writes them)', async () => {
    const { slug, ...manual } = completion('a2z:selection-sort', 'self');
    await assertFails(setDoc(doc(as('bob'), 'users', 'bob', 'completions', 'a2z:selection-sort'), manual));
  });

  test('app: delete your own completions (account deletion)', async () => {
    const ref = (db) => doc(db, 'users', 'bob', 'completions', 'lru-cache');
    await assertSucceeds(setDoc(ref(as('bob')), completion('lru-cache', 'leetcode')));
    await assertSucceeds(deleteDoc(ref(as('bob'))));
  });

  test('app: members read each other\'s completion index', async () => {
    await setDoc(doc(as('bob'), 'users', 'bob', 'meta', 'completionIndex'), { items: {} });
    await assertSucceeds(getDoc(doc(as('alice'), 'users', 'bob', 'meta', 'completionIndex')));
  });

  test('blocks: a "verified" completion whose slug differs from its key [audit #7]', async () => {
    await assertFails(setDoc(doc(as('bob'), 'users', 'bob', 'completions', 'two-sum'),
      completion('two-sum', 'leetcode', { slug: 'median-of-two-sorted-arrays' })));
  });

  test('blocks: writing someone else\'s completions or index', async () => {
    await assertFails(setDoc(doc(as('bob'), 'users', 'alice', 'completions', 'lru-cache'), completion('lru-cache', 'leetcode')));
    await assertFails(setDoc(doc(as('bob'), 'users', 'alice', 'meta', 'completionIndex'), { items: {} }));
  });

  test('blocks: unknown sources and mismatched keys', async () => {
    await assertFails(setDoc(doc(as('bob'), 'users', 'bob', 'completions', 'lru-cache'), completion('lru-cache', 'admin')));
    await assertFails(setDoc(doc(as('bob'), 'users', 'bob', 'completions', 'lru-cache'), completion('two-sum', 'leetcode')));
  });

  test('blocks: reading someone else\'s heatmap', async () => {
    await assertFails(getDoc(doc(as('alice'), 'users', 'bob', 'heatmap', TODAY.slice(0, 4))));
  });
});

describe('proof: notes, photos and reactions', () => {
  const setProofMode = (mode) => env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'groups', 'g1'), { proof: mode }));
  const proofRef = (db, logId) => doc(db, 'groupStats', 'g1', 'proofs', logId);
  const reactionRef = (db, id) => doc(db, 'groupStats', 'g1', 'reactions', id);

  test('blocks: logging hours without saying what you did', async () => {
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 1, { note: '', streak: null }));
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 1, { note: 'ok', streak: null }));
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 1, { note: 'x'.repeat(201), streak: null }));
  });

  test('app: log with a photo (log + proof in one batch)', async () => {
    await assertSucceeds(logBatch(as('bob'), 'bob', 'g1', 1, { photo: photoBytes(), streak: null }));
  });

  test('app: members see a photo the same day; outsiders never do', async () => {
    await logBatch(as('bob'), 'bob', 'g1', 1, { logId: 'p1', photo: photoBytes(), streak: null });
    await assertSucceeds(getDoc(proofRef(as('alice'), 'p1')));
    await assertFails(getDoc(proofRef(as('carol'), 'p1')));
  });

  test('blocks: viewing a photo more than 24 h after it was taken', async () => {
    await env.withSecurityRulesDisabled((ctx) => setDoc(proofRef(ctx.firestore(), 'old'), {
      uid: 'bob', date: YESTERDAY, createdAt: Timestamp.fromMillis(Date.now() - 25 * 3600 * 1000), image: photoBytes(10),
    }));
    await assertFails(getDoc(proofRef(as('alice'), 'old')));
  });

  test('blocks: attaching a photo to an existing log later', async () => {
    await assertFails(setDoc(proofRef(as('alice'), 'seed-log'), {
      uid: 'alice', date: TODAY, createdAt: serverTimestamp(), image: photoBytes(),
    }));
  });

  test('blocks: photos over 150 KB, or a proof the log doesn\'t declare', async () => {
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 1, { photo: photoBytes(151 * 1024), streak: null }));
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 1, { photo: photoBytes(), hasPhoto: false, streak: null }));
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 1, { hasPhoto: true, streak: null }));
  });

  test('blocks: a photo with a client-chosen timestamp', async () => {
    const db = as('bob');
    const b = writeBatch(db);
    b.set(doc(db, 'logs', 'ts1'), {
      id: 'ts1', userId: 'bob', groupId: 'g1', date: TODAY, value: 1, note: 'Did some work', createdAt: 1,
      source: 'manual', hasPhoto: true,
    });
    b.set(proofRef(db, 'ts1'), { uid: 'bob', date: TODAY, createdAt: Timestamp.fromMillis(Date.now() + DAY_MS), image: photoBytes() });
    await assertFails(b.commit());
  });

  test('required group: manual logs need a photo, timer sessions don\'t', async () => {
    await setProofMode('required');
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 1, { streak: null }));
    await assertSucceeds(logBatch(as('bob'), 'bob', 'g1', 1, { photo: photoBytes(), streak: null }));
    await assertSucceeds(logBatch(as('bob'), 'bob', 'g1', 1, { source: 'timer', streak: null }));
  });

  test('blocks: a photo in a group that turned photos off', async () => {
    await setProofMode('off');
    await assertFails(logBatch(as('bob'), 'bob', 'g1', 1, { photo: photoBytes(), streak: null }));
    await assertSucceeds(logBatch(as('bob'), 'bob', 'g1', 1, { streak: null }));
  });

  test('app: the creator sets the photo mode; members can\'t; unknown modes fail', async () => {
    await assertSucceeds(updateDoc(doc(as('alice'), 'groups', 'g1'), { proof: 'required' }));
    await assertFails(updateDoc(doc(as('bob'), 'groups', 'g1'), { proof: 'off' }));
    await assertFails(updateDoc(doc(as('alice'), 'groups', 'g1'), { proof: 'sometimes' }));
  });

  test('app: cleaning up your own older photos (list + delete)', async () => {
    await logBatch(as('bob'), 'bob', 'g1', 1, { logId: 'p2', photo: photoBytes(), streak: null });
    const db = as('bob');
    await assertSucceeds(getDocs(query(collection(db, 'groupStats', 'g1', 'proofs'), where('uid', '==', 'bob'))));
    await assertSucceeds(deleteDoc(proofRef(db, 'p2')));
  });

  test('blocks: listing everyone\'s photos or deleting someone else\'s', async () => {
    await logBatch(as('bob'), 'bob', 'g1', 1, { logId: 'p3', photo: photoBytes(), streak: null });
    await assertFails(getDocs(collection(as('alice'), 'groupStats', 'g1', 'proofs')));
    await assertFails(deleteDoc(proofRef(as('alice'), 'p3')));
  });

  test('app: react ✓ / 🤨 to a friend\'s log, change it, remove it', async () => {
    const db = as('bob');
    await assertSucceeds(setDoc(reactionRef(db, 'seed-log_bob'), { logId: 'seed-log', uid: 'bob', date: TODAY, kind: 'doubt' }));
    await assertSucceeds(setDoc(reactionRef(db, 'seed-log_bob'), { logId: 'seed-log', uid: 'bob', date: TODAY, kind: 'ok' }));
    await assertSucceeds(getDocs(query(collection(db, 'groupStats', 'g1', 'reactions'), where('date', '==', TODAY))));
    await assertSucceeds(deleteDoc(reactionRef(db, 'seed-log_bob')));
  });

  test('blocks: reacting to your own log, as someone else, or with a made-up reaction', async () => {
    await assertFails(setDoc(reactionRef(as('alice'), 'seed-log_alice'), { logId: 'seed-log', uid: 'alice', date: TODAY, kind: 'ok' }));
    await assertFails(setDoc(reactionRef(as('bob'), 'seed-log_alice'), { logId: 'seed-log', uid: 'alice', date: TODAY, kind: 'ok' }));
    await assertFails(setDoc(reactionRef(as('bob'), 'seed-log_bob'), { logId: 'seed-log', uid: 'bob', date: TODAY, kind: 'fire' }));
  });

  test('blocks: outsiders reading or adding reactions', async () => {
    await assertFails(getDocs(collection(as('carol'), 'groupStats', 'g1', 'reactions')));
    await assertFails(setDoc(reactionRef(as('carol'), 'seed-log_carol'), { logId: 'seed-log', uid: 'carol', date: TODAY, kind: 'doubt' }));
  });

  test('blocks: removing someone else\'s reaction', async () => {
    await setDoc(reactionRef(as('bob'), 'seed-log_bob'), { logId: 'seed-log', uid: 'bob', date: TODAY, kind: 'ok' });
    await assertFails(deleteDoc(reactionRef(as('alice'), 'seed-log_bob')));
  });
});

describe('said vs. did', () => {
  test('app: save focus areas with weekly targets', async () => {
    await assertSucceeds(updateDoc(doc(as('bob'), 'users', 'bob'),
      { interests: ['coding', 'fitness'], targets: { coding: 8, fitness: 4 }, stage: 'student' }));
  });

  test('blocks: targets that aren\'t a small map', async () => {
    await assertFails(updateDoc(doc(as('bob'), 'users', 'bob'), { targets: 'lots' }));
    const many = Object.fromEntries([...Array(9).keys()].map((i) => [`a${i}`, 1]));
    await assertFails(updateDoc(doc(as('bob'), 'users', 'bob'), { targets: many }));
  });
});

describe('default deny', () => {
  test('blocks: unknown collections', async () => {
    await assertFails(setDoc(doc(as('alice'), 'admin', 'x'), { a: 1 }));
  });
});
