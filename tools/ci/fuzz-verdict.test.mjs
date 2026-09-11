#!/usr/bin/env node
/**
 * What the verdict tool must not do: turn a red run green.
 *
 * It decides whether the fuzz tier passes, so the interesting cases are the ones where it could
 * wrongly say yes — an unclassified category absorbed into the debt, an environment exemption
 * swallowing a neighbouring real finding, a baseline rewrite deleting the prose that explains
 * itself. Each of those is a plausible next edit, which is why each has a test.
 *
 *   node tools/ci/fuzz-verdict.test.mjs
 */
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

// fileURLToPath, not .pathname: on Windows the latter yields /C:/... and node cannot run it.
const TOOL = fileURLToPath(new URL('fuzz-verdict.mjs', import.meta.url));
let failures = 0;
let checks = 0;

const check = (what, ok, detail = '') => {
  checks++;
  if (!ok) {
    failures++;
    console.error(`FAIL  ${what}${detail ? `\n      ${detail}` : ''}`);
  } else {
    console.log(`ok    ${what}`);
  }
};

/** A JUnit report in the shape Schemathesis writes: one testcase per operation. */
const report = (operations) =>
  '<?xml version="1.0" encoding="utf-8"?><testsuites><testsuite>' +
  operations
    .map(([op, findings]) => {
      if (!findings.length) return `<testcase name="${op}"></testcase>`;
      const body = findings
        .map((title, i) => `${i + 1}. Test Case ID: abc${i}\n\n- ${title}\n\n    detail here\n`)
        .join('\n');
      return `<testcase name="${op}"><failure>${body}</failure></testcase>`;
    })
    .join('') +
  '</testsuite></testsuites>';

const run = (operations, baseline, extraArgs = []) => {
  const dir = mkdtempSync(join(tmpdir(), 'fuzz-verdict-'));
  const reportPath = join(dir, 'junit.xml');
  const baselinePath = join(dir, 'baseline.json');
  const jsonPath = join(dir, 'summary.json');
  writeFileSync(reportPath, report(operations));
  writeFileSync(baselinePath, JSON.stringify(baseline, null, 2));
  let status = 0;
  let stdout = '';
  try {
    stdout = execFileSync(
      process.execPath,
      [TOOL, '--report', reportPath, '--baseline', baselinePath, '--json', jsonPath, ...extraArgs],
      { encoding: 'utf8' }
    );
  } catch (e) {
    status = e.status;
    stdout = e.stdout || '';
  }
  const summary = JSON.parse(readFileSync(jsonPath, 'utf8'));
  const written = JSON.parse(readFileSync(baselinePath, 'utf8'));
  rmSync(dir, { recursive: true, force: true });
  return { status, stdout, summary, written };
};

const EMPTY = { tracked: {} };

// A clean run passes.
{
  const { status, stdout } = run([['GET /users/me', []]], EMPTY);
  check('a run with no findings is green', status === 0 && stdout.includes('**Green.**'));
}

// A gating category fails, however small.
{
  const { status, stdout } = run([['GET /users/me', ['Server error']]], EMPTY);
  check('one server error is red', status === 1 && stdout.includes('**Red.**'));
}

// A category nobody has classified must not be absorbed into the debt.
{
  const { status, stdout } = run([['GET /users/me', ['Some New Check From A New Release']]], EMPTY);
  check(
    'an unclassified category gates',
    status === 1 && stdout.includes('new, unclassified'),
    stdout
  );
}

// Tracked categories are debt up to their written budget, and not a case beyond it.
{
  const at = run(
    [
      ['GET /a', ['Unsupported methods']],
      ['GET /b', ['Unsupported methods']],
    ],
    { tracked: { 'Unsupported methods': 2 } }
  );
  check('a tracked category at its budget is green', at.status === 0);

  const over = run(
    [
      ['GET /a', ['Unsupported methods']],
      ['GET /b', ['Unsupported methods']],
    ],
    { tracked: { 'Unsupported methods': 1 } }
  );
  check('a tracked category over its budget is red', over.status === 1);

  const unwritten = run([['GET /a', ['Unsupported methods']]], EMPTY);
  check('a tracked category with no budget written down gates', unwritten.status === 1);
}

// The environment exemption: it may excuse the operation it names, and nothing else.
{
  const env = {
    tracked: {},
    environment: { 'Server error': { 'POST /auth/firebase/verify-reset-code': 'no upstream here' } },
  };

  const excused = run([['POST /auth/firebase/verify-reset-code', ['Server error']]], env);
  check(
    'the named operation does not gate',
    excused.status === 0 && excused.stdout.includes('**Green.**'),
    excused.stdout
  );
  check(
    'and it is still printed, with the reason',
    excused.stdout.includes('no upstream here') &&
      excused.stdout.includes('the environment causes, not the server')
  );

  const neighbour = run(
    [
      ['POST /auth/firebase/verify-reset-code', ['Server error']],
      ['POST /user-social-connection', ['Server error']],
    ],
    env
  );
  check(
    'a real finding beside an exempted one still gates',
    neighbour.status === 1 && neighbour.stdout.includes('1 × Server error'),
    neighbour.stdout
  );
  check(
    'and the exemption does not excuse it by category',
    neighbour.summary.gating['Server error'] === 1
  );

  const otherCheck = run(
    [['POST /auth/firebase/verify-reset-code', ['Response violates schema']]],
    env
  );
  check(
    'an exemption for one check does not cover another on the same operation',
    otherCheck.status === 1
  );

  const stale = run([['GET /users/me', []]], env);
  check(
    'an exemption that produced nothing is reported rather than silently kept',
    stale.status === 0 && stale.stdout.includes('not produced this run'),
    stale.stdout
  );
}

// --write-baseline records counts; it must not delete the prose that explains them.
{
  const { written } = run([['GET /a', ['Unsupported methods']]], {
    note: ['why these numbers are what they are'],
    measuredAt: '2026-09-11',
    tracked: { 'Unsupported methods': 99 },
    environment: { 'Server error': { 'POST /x': 'a reason worth keeping' } },
  }, ['--write-baseline']);
  check('the new count is written', written.tracked['Unsupported methods'] === 1);
  check('the note survives', Array.isArray(written.note) && written.note.length === 1);
  check('the exemptions survive', written.environment['Server error']['POST /x'] === 'a reason worth keeping');
}

// No report at all is silence, not success.
{
  const dir = mkdtempSync(join(tmpdir(), 'fuzz-verdict-'));
  let status = 0;
  try {
    // --json explicitly, or the tool writes fuzz-summary.json into whatever directory the test
    // was started from, which is the repository root.
    execFileSync(
      process.execPath,
      [TOOL, '--report', join(dir, 'does-not-exist.xml'), '--json', join(dir, 'summary.json')],
      { encoding: 'utf8' }
    );
  } catch (e) {
    status = e.status;
  }
  rmSync(dir, { recursive: true, force: true });
  check('a missing report is red', status === 1);
}

console.log(`\n${checks - failures}/${checks} checks passed`);
process.exit(failures ? 1 : 0);
