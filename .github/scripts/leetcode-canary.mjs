// Daily canary for the LeetCode integration.
//
// Sends the app's exact GraphQL queries (app/src/main/assets/leetcode/queries.json)
// to LeetCode. GraphQL validates every field before running a query, so asking
// about a username that can't exist proves the schema still has all the fields
// the app reads, without touching anyone's data. Fails the workflow (and emails
// the repo owner) if LeetCode renamed or removed anything.
import { readFileSync } from 'node:fs';

const queries = JSON.parse(readFileSync('app/src/main/assets/leetcode/queries.json', 'utf8'));
const GHOST = 'tether-canary-nonexistent-user-7f3a';
const ALLOWED = /does not exist/i;

async function run(name, query, variables) {
  const res = await fetch('https://leetcode.com/graphql', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Referer: 'https://leetcode.com', 'User-Agent': 'Mozilla/5.0 Tether-Canary' },
    body: JSON.stringify({ query, variables }),
  });
  if (!res.ok) throw new Error(`${name}: HTTP ${res.status}`);
  const body = await res.json();
  const unexpected = (body.errors ?? []).filter((e) => !ALLOWED.test(e.message ?? ''));
  if (unexpected.length) throw new Error(`${name}: ${unexpected.map((e) => e.message).join('; ')}`);
  if (!body.data) throw new Error(`${name}: response has no data`);
  console.log(`✓ ${name}`);
  return body.data;
}

const recent = await run('recent', queries.recent, { username: GHOST, limit: 20 });
if (!Array.isArray(recent.recentAcSubmissionList)) {
  throw new Error('recent: recentAcSubmissionList is not a list');
}
await run('profile', queries.profile, { username: GHOST });
console.log('LeetCode schema matches what Tether expects.');
