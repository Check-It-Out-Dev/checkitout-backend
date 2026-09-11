#!/usr/bin/env node
/**
 * Measure what the README publishes, and check that it still says it.
 *
 * The README's table of figures had drifted: 8,908 test methods against 9,089 actual, 256 test
 * classes against 292. Nobody wrote a wrong number -- the numbers were right when they were typed
 * and the repository kept growing. A figure in a README with no way to re-derive it is a claim
 * with an expiry date nobody can see, and this estate publishes these five rows on a public page.
 *
 * So each figure has one home. This file defines how it is counted, `docs/testing/measured-counts.json`
 * records what that came to and when, and `--check` fails the build when the README disagrees.
 * The frontend has had the same thing (`check:published-numbers`) since the estate arc; this is
 * that, for a Maven repository.
 *
 *   node tools/ci/measure-counts.mjs            # print the measurements
 *   node tools/ci/measure-counts.mjs --write    # and record them
 *   node tools/ci/measure-counts.mjs --check    # fail if README.md disagrees with the code
 */
import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join, extname } from 'node:path';

const COUNTS = 'docs/testing/measured-counts.json';
const README = 'README.md';

const walk = (dir, ext, out = []) => {
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const entry of entries) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) walk(path, ext, out);
    else if (extname(path) === ext) out.push(path);
  }
  return out;
};

const read = (path) => readFileSync(path, 'utf8');
const count = (text, pattern) => (text.match(pattern) || []).length;
const lines = (files) => files.reduce((n, f) => n + read(f).split('\n').length, 0);

// ---- the Java tiers -------------------------------------------------------------------------

const testFiles = walk('src/test/java', '.java');
const mainFiles = walk('src/main/java', '.java');

const TEST_METHOD = /^[ \t]*@Test\b/gm;
const PARAMETERIZED = /^[ \t]*@ParameterizedTest\b/gm;
const NESTED = /^[ \t]*@Nested\b/gm;
const DISABLED = /^[ \t]*@Disabled\b/gm;

let testAnnotations = 0;
let parameterized = 0;
let nestedGroups = 0;
// A test class is a FILE that declares at least one test method. Counting declarations instead
// would count the @Nested groups twice, and counting *Test.java by name would count the base
// classes and fixtures that declare none.
let testClasses = 0;
// The README's strongest claim is that this is zero. A number nobody can re-derive is a claim
// with an invisible expiry date, and that one more than any other should not be allowed to rot.
let disabled = 0;
for (const file of testFiles) {
  const text = read(file);
  const here = count(text, TEST_METHOD) + count(text, PARAMETERIZED);
  testAnnotations += count(text, TEST_METHOD);
  parameterized += count(text, PARAMETERIZED);
  nestedGroups += count(text, NESTED);
  disabled += count(text, DISABLED);
  if (here > 0) testClasses++;
}

// ---- the Cucumber corpus --------------------------------------------------------------------

const featureFiles = walk('src/test/resources/features', '.feature');
let scenarios = 0;
let outlines = 0;
let expanded = 0;
for (const file of featureFiles) {
  const text = read(file);
  scenarios += count(text, /^\s*Scenario:/gm);
  outlines += count(text, /^\s*Scenario Outline:/gm);
  // One run per Examples row, header excluded; a commented or blank row is not a run.
  let inExamples = false;
  let header = false;
  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (/^Examples:/.test(trimmed)) {
      inExamples = true;
      header = false;
      continue;
    }
    if (inExamples && trimmed.startsWith('|')) {
      if (!header) header = true;
      else expanded++;
      continue;
    }
    if (trimmed && !trimmed.startsWith('|') && !trimmed.startsWith('#')) inExamples = false;
  }
}

// ---- the domain and the contract --------------------------------------------------------------

let entities = 0;
let controllers = 0;
for (const file of mainFiles) {
  const text = read(file);
  if (/^\s*@Entity\b/m.test(text)) entities++;
  if (/^\s*@RestController\b/m.test(text)) controllers++;
}

const spec = JSON.parse(read('docs/openapi/openapi.json'));
const paths = Object.keys(spec.paths ?? {}).length;
const METHODS = ['get', 'put', 'post', 'delete', 'patch', 'head', 'options', 'trace'];
const operations = Object.values(spec.paths ?? {}).reduce(
  (n, item) => n + METHODS.filter((m) => item[m]).length,
  0
);
const schemas = Object.keys(spec.components?.schemas ?? {}).length;

const measured = {
  testFiles: testFiles.length,
  disabledAnnotations: disabled,
  testMethods: testAnnotations + parameterized,
  testAnnotations,
  parameterizedTests: parameterized,
  testClasses,
  nestedGroups,
  testJavaLines: lines(testFiles),
  mainJavaLines: lines(mainFiles),
  featureFiles: featureFiles.length,
  scenarios,
  scenarioOutlines: outlines,
  scenariosAfterExamples: scenarios + expanded,
  entities,
  restControllers: controllers,
  openApiPaths: paths,
  openApiOperations: operations,
  openApiSchemas: schemas,
  openApiVersion: spec.openapi,
};
// Published rounded, and gated rounded. An exact line count is not the same kind of number as a
// count of tests: it moves when anyone adds a comment, and a gate that fails on every commit that
// touches a test file is a gate people learn to ignore. The exact figures stay in this file for
// anyone who wants them; what the README publishes is the ratio and the thousands.
measured.testToMainRatio = (measured.testJavaLines / measured.mainJavaLines).toFixed(1);
measured.testJavaLinesK = Math.round(measured.testJavaLines / 1000);
measured.mainJavaLinesK = Math.round(measured.mainJavaLines / 1000);

// ---- what the README says ---------------------------------------------------------------------

/**
 * Each claim names the row it lives in and the figure it must equal. Anchoring on the row rather
 * than on a bare number is what stops "38" in one sentence satisfying a check meant for another.
 */
const CLAIMS = [
  { row: /\*\*Test methods\*\*.*$/m, key: 'testMethods' },
  { row: /\*\*Test methods\*\*.*$/m, key: 'testAnnotations' },
  { row: /\*\*Test methods\*\*.*$/m, key: 'parameterizedTests' },
  { row: /\*\*Test methods\*\*.*$/m, key: 'testClasses' },
  { row: /\*\*Test methods\*\*.*$/m, key: 'nestedGroups' },
  { row: /`@Disabled` appears zero times\*\*[\s\S]{0,80}/m, key: 'testFiles' },
  { row: /\*\*Test code : main code\*\*.*$/m, key: 'testJavaLinesK' },
  { row: /\*\*Test code : main code\*\*.*$/m, key: 'mainJavaLinesK' },
  { row: /\*\*Cucumber\*\*.*$/m, key: 'featureFiles' },
  { row: /\*\*Cucumber\*\*.*$/m, key: 'scenarios' },
  { row: /\*\*Cucumber\*\*.*$/m, key: 'scenarioOutlines' },
  { row: /\*\*Cucumber\*\*.*$/m, key: 'scenariosAfterExamples' },
  { row: /\*\*Domain\*\*.*$/m, key: 'entities' },
  { row: /\*\*Domain\*\*.*$/m, key: 'restControllers' },
  { row: /\*\*Contract\*\*.*$/m, key: 'openApiPaths' },
  { row: /\*\*Contract\*\*.*$/m, key: 'openApiOperations' },
  { row: /\*\*Contract\*\*.*$/m, key: 'openApiSchemas' },
];

const group = (n) => n.toLocaleString('en-US');
const numbersIn = (text) =>
  new Set((text.match(/\d[\d,]*/g) || []).map((s) => Number(s.replace(/,/g, ''))));

const mode = process.argv.includes('--check')
  ? 'check'
  : process.argv.includes('--write')
    ? 'write'
    : 'print';

if (mode === 'print' || mode === 'write') {
  for (const [key, value] of Object.entries(measured)) {
    console.log(`${key.padEnd(24)} ${value}`);
  }
}

if (mode === 'write') {
  writeFileSync(
    COUNTS,
    JSON.stringify({ measuredAt: new Date().toISOString().slice(0, 10), ...measured }, null, 2) + '\n'
  );
  console.log(`\nwritten to ${COUNTS}`);
}

if (mode === 'check') {
  const readme = read(README);
  const missing = [];
  for (const claim of CLAIMS) {
    const row = readme.match(claim.row);
    if (!row) {
      missing.push(`the row for ${claim.key} is not in ${README} any more`);
      continue;
    }
    const expected = measured[claim.key];
    if (!numbersIn(row[0]).has(expected)) {
      missing.push(
        `${claim.key}: the code says ${group(expected)}, and the row does not: ${row[0].trim().slice(0, 120)}`
      );
    }
  }

  const ratioRow = readme.match(/\*\*Test code : main code\*\*.*$/m);
  if (ratioRow && !ratioRow[0].includes(`${measured.testToMainRatio} : 1`)) {
    missing.push(
      `the test-to-main ratio is ${measured.testToMainRatio} : 1, and the row does not say so`
    );
  }

  if (measured.disabledAnnotations !== 0) {
    missing.push(
      `the README says @Disabled appears zero times; it appears ${measured.disabledAnnotations} times`
    );
  }

  let recorded = null;
  try {
    recorded = JSON.parse(read(COUNTS));
  } catch {
    missing.push(`${COUNTS} is missing; run this with --write`);
  }
  if (recorded) {
    for (const [key, value] of Object.entries(measured)) {
      if (recorded[key] !== value) {
        missing.push(`${COUNTS} says ${key} is ${recorded[key]}, the code says ${value}`);
      }
    }
  }

  if (missing.length) {
    console.error('check:published-numbers FAILED\n');
    for (const line of missing) console.error(`  ${line}`);
    console.error(
      `\n${missing.length} figure(s) out of date. Re-measure with --write and update ${README}.`
    );
    process.exit(1);
  }
  console.log(
    `check:published-numbers OK — ${CLAIMS.length} published figures match the code, and ${COUNTS} matches it too.`
  );
}
