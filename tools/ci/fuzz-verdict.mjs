#!/usr/bin/env node
/**
 * What Schemathesis found, sorted into "the server is broken" and "the document is incomplete".
 *
 * The tier used to take the fuzzer's exit code as its verdict, which made it red on every run and
 * therefore useless: 565 findings in one number, of which the overwhelming majority were not
 * defects at all. Two categories dominated and neither means what an exit code implies:
 *
 *   Unsupported methods           Schemathesis sends TRACE at a documented path and expects 405.
 *                                 Spring Security's filter chain answers 401 before the dispatcher
 *                                 ever decides the method is unsupported. Every secured path fails
 *                                 this check by construction, and the only way to pass it would be
 *                                 to move authentication after routing, which is the wrong trade.
 *   Undocumented HTTP status code The running server returns a status the document does not
 *                                 declare for that operation. Real, and worth closing, but it is
 *                                 closed by teaching springdoc about the error envelope, not by
 *                                 changing a line of runtime behaviour.
 *
 * So the verdict is computed here instead. Gating categories are the ones where the SERVER is at
 * fault and a caller would see it. Everything else is tracked against a committed baseline: a new
 * occurrence fails the build, the existing debt is visible in the summary, and the baseline is a
 * ratchet, lowered when the count drops and never raised to make a red run green.
 *
 *   node tools/ci/fuzz-verdict.mjs [--report reports/junit.xml]
 *                                  [--baseline tools/ci/fuzz-baseline.json]
 *                                  [--json fuzz-summary.json] [--top 8]
 */
import { existsSync, readFileSync, writeFileSync } from 'node:fs';

const argv = process.argv.slice(2);
const flag = (n, d) => {
  const i = argv.indexOf(`--${n}`);
  return i >= 0 ? argv[i + 1] : d;
};

const reportPath = flag('report', 'reports/junit.xml');
const baselinePath = flag('baseline', 'tools/ci/fuzz-baseline.json');
const outPath = flag('json', 'fuzz-summary.json');
const top = Number(flag('top', '8'));
// Rewrite the baseline from this run's counts. Never in CI: it is how a person records a number
// they have looked at, in the same commit as the reason it moved.
const writeBaseline = argv.includes('--write-baseline');

// The key is the title Schemathesis prints for the check. Matching on the printed title rather than
// on the --checks flag name is deliberate: the titles are what a reader sees in the report, and the
// flag names have already been renamed once between majors.
const GATING = {
  'Server error':
    'A 5xx. Whatever the generated input was, answering with a stack trace is a defect.',
  'Response violates schema':
    'The response does not match the schema the server itself published for it, so a generated client cannot parse it.',
  'Missing Content-Type header':
    'A body with no declared type. Every generated client has to guess, and they do not all guess the same.',
  'JSON deserialization error': 'The body was not valid JSON where JSON was promised.',
};

const TRACKED = {
  'Unsupported methods':
    'Spring Security answers 401 before the dispatcher can return 405. Architectural, not a defect; see the header of this file.',
  'Undocumented HTTP status code':
    'The OpenAPI document under-declares error responses. Closed by extending the springdoc customizer, not by changing the server.',
  'API accepted schema-violating request':
    'Validation gap: input the schema forbids was accepted. Triage pending, and these are the ones worth reading first.',
  'API rejected schema-compliant request':
    'Usually a business rule the schema cannot express, such as a referenced row having to exist. Triage pending.',
  'Invalid Allow header':
    'The `Allow` header on a 405 lists what the Spring handler mapping accepts at that path; the document lists what is published. Same family as unsupported methods, and closed the same way if at all.',
};

if (!existsSync(reportPath)) {
  console.log(
    `### Schemathesis\n\n**No report at \`${reportPath}\`.** The fuzzer did not finish, so this tier ` +
      `has no verdict. That is reported as silence rather than as success.\n`
  );
  writeFileSync(outPath, JSON.stringify({ ran: false, gating: {}, tracked: {} }, null, 1));
  process.exit(1);
}

const xml = readFileSync(reportPath, 'utf8');
const unescape = (s) =>
  s
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, String.fromCharCode(39))
    .replace(/&#10;/g, '\n')
    .replace(/&amp;/g, '&');

// One flat list of <testcase name="OP"><failure>...</failure></testcase>; a hand parse for the same
// reason pit-summary.mjs hand-parses PIT's XML: one attribute and one text node do not justify a
// dependency in a Java repository.
const findings = new Map(); // title -> { cases, ops:Set, byOp:Map, examples:[] }
let operations = 0;
let failedOperations = 0;

for (const chunk of xml.split('<testcase ').slice(1)) {
  operations++;
  const op = unescape((chunk.match(/name="([^"]*)"/) || [, '?'])[1]);
  const f = chunk.indexOf('<failure');
  if (f === -1) continue;
  failedOperations++;
  const bodyStart = chunk.indexOf('>', f) + 1;
  const body = unescape(chunk.slice(bodyStart, chunk.indexOf('</failure>', bodyStart)));

  // Each finding inside one operation starts with `N. Test Case ID: xxxxxx`, and the check's title
  // is the first bullet under it.
  for (const block of body.split(/^\s*\d+\. Test Case ID: \S+\s*$/m).slice(1)) {
    // `Response violates schema (2 violations)` is the same check as `Response violates schema`;
    // Schemathesis appends the count when one response breaks the schema in more than one place.
    // Keyed on the bare title, or the classification table would need a row per arity.
    const title = (block.match(/^\s*-\s+(.+?)\s*$/m) || [, null])[1]?.replace(
      / \(\d+ violations?\)$/,
      ''
    );
    if (!title) continue;
    if (!findings.has(title))
      findings.set(title, { cases: 0, ops: new Set(), byOp: new Map(), examples: [] });
    const rec = findings.get(title);
    rec.cases++;
    rec.ops.add(op);
    rec.byOp.set(op, (rec.byOp.get(op) || 0) + 1);
    if (rec.examples.length < top) {
      const detail = (block.match(/^ {2,}(\S.*?)\s*$/m) || [, ''])[1];
      const repro = (block.match(/Reproduce with:[\s\S]*?(curl [^\n]+)/) || [, ''])[1];
      rec.examples.push({ op, detail, repro });
    }
  }
}

const baseline = existsSync(baselinePath) ? JSON.parse(readFileSync(baselinePath, 'utf8')) : {};
const budget = baseline.tracked || {};
// Findings that are properties of the environment this tier runs in rather than of the server.
// Keyed by check title, then by operation, with the reason as the value -- written down and
// printed, because the difference between "this cannot be tested here" and "we stopped looking"
// is whether anyone can read which one it is.
const environment = baseline.environment || {};
const exemptionsFor = (title) => environment[title] || {};

const rows = [...findings.entries()]
  .map(([title, r]) => ({
    title,
    cases: r.cases,
    ops: r.ops.size,
    // Cases the environment is known to cause, and what is left once they are set aside. Only the
    // remainder can gate; the exempt ones are reported in their own section.
    exempt: [...r.byOp].reduce((n, [op, k]) => n + (op in exemptionsFor(title) ? k : 0), 0),
    seenExemptOps: [...r.byOp.keys()].filter((op) => op in exemptionsFor(title)),
    examples: r.examples,
    gating: title in GATING,
    known: title in GATING || title in TRACKED,
    // A tracked category with no number written down is allowed nothing. Debt is only debt once
    // somebody has written the figure into the baseline; until then it gates.
    allowed: title in GATING ? 0 : (budget[title] ?? 0),
  }))
  .map((r) => ({ ...r, chargeable: r.cases - r.exempt }))
  .sort((a, b) => b.cases - a.cases);

// A category nobody has classified is treated as gating. A new check appearing in a new
// Schemathesis release must land in front of a person, not be silently absorbed into the debt.
const breaches = rows.filter(
  (r) => (r.gating && r.chargeable > 0) || !r.known || r.chargeable > r.allowed
);
const improved = rows.filter((r) => !r.gating && r.known && r.chargeable < r.allowed);

const out = [];
out.push('### Schemathesis');
out.push('');
const totalCases = rows.reduce((n, r) => n + r.cases, 0);
out.push(
  `${operations} operations fuzzed, ${failedOperations} with at least one finding, ${totalCases} ` +
    `findings in ${rows.length} categories.`
);
out.push('');
out.push('| finding | cases | operations | |');
out.push('| --- | ---: | ---: | --- |');
for (const r of rows) {
  const verdict = r.gating
    ? r.chargeable > 0
      ? r.exempt
        ? `**gates** (${r.exempt} of them the environment's, see below)`
        : '**gates**'
      : `the environment's, all ${r.exempt} of them — see below`
    : !r.known
      ? '**new, unclassified, gates until someone reads it**'
      : r.chargeable > r.allowed
        ? `**over budget** (${r.allowed} allowed)`
        : r.chargeable < r.allowed
          ? `tracked, down from ${r.allowed}`
          : `tracked (${r.allowed} allowed)`;
  out.push(`| ${r.title} | ${r.cases} | ${r.ops} | ${verdict} |`);
}
out.push('');

for (const r of rows) {
  const why = GATING[r.title] || TRACKED[r.title];
  if (why) out.push(`- **${r.title}** — ${why}`);
}
out.push('');

// A gating category whose every case is the environment's has nothing to show here; it is
// reported in full in the section below, and an empty <details> is just noise.
const interesting = rows.filter((r) => (r.gating && r.chargeable > 0) || !r.known);
for (const r of interesting) {
  if (!r.examples.some((e) => !(e.op in exemptionsFor(r.title)))) continue;
  out.push(`<details><summary>${r.title}: ${Math.min(top, r.cases)} of ${r.cases}</summary>`);
  out.push('');
  for (const e of r.examples) {
    if (e.op in exemptionsFor(r.title)) continue;
    out.push(`\`${e.op}\`${e.detail ? ` — ${e.detail}` : ''}`);
    if (e.repro) out.push(['', '```', e.repro, '```'].join('\n'));
    out.push('');
  }
  out.push('</details>');
  out.push('');
}

const declared = Object.entries(environment).flatMap(([title, ops]) =>
  Object.entries(ops).map(([op, reason]) => ({
    title,
    op,
    reason,
    seen: rows.some((r) => r.title === title && r.seenExemptOps.includes(op)),
  }))
);
if (declared.length) {
  out.push('<details><summary>' + declared.length + ' finding(s) the environment causes, not the server</summary>');
  out.push('');
  for (const d of declared) {
    out.push(`\`${d.op}\` — ${d.title}${d.seen ? '' : ' (not produced this run)'}`);
    out.push('');
    out.push(`  ${d.reason}`);
    out.push('');
  }
  out.push('</details>');
  out.push('');
}

if (improved.length) {
  out.push(
    '_Below budget this run. Schemathesis reseeds every run and these counts swing by a third, so ' +
      'ratchet to a figure above the highest of several runs rather than to this one -- see the ' +
      'note in `tools/ci/fuzz-baseline.json`. This run: ' +
      improved.map((r) => `${r.title} ${r.allowed} → ${r.chargeable}`).join(', ') +
      '._'
  );
  out.push('');
}

out.push(
  breaches.length
    ? '**Red.** ' +
        breaches
          .map((r) =>
            r.gating || !r.known
              ? `${r.chargeable} × ${r.title}`
              : `${r.title} ${r.chargeable} > ${r.allowed}`
          )
          .join('; ') +
        '.'
    : '**Green.** No server-side defect, and no tracked category above its budget.'
);
out.push('');
out.push(
  '_The budgets are a ratchet: lowered when a count drops, never raised to make a red run green._'
);

console.log(out.join('\n'));

writeFileSync(
  outPath,
  JSON.stringify(
    {
      ran: true,
      operations,
      failedOperations,
      findings: totalCases,
      // The nightly verdict and the dashboard read these by name; keep the keys stable.
      gating: Object.fromEntries(rows.filter((r) => r.gating).map((r) => [r.title, r.chargeable])),
      tracked: Object.fromEntries(rows.filter((r) => !r.gating).map((r) => [r.title, r.chargeable])),
      environment: Object.fromEntries(
        rows.filter((r) => r.exempt).map((r) => [r.title, r.exempt])
      ),
      budget,
      breaches: breaches.map((r) => r.title),
    },
    null,
    1
  )
);

if (writeBaseline) {
  const tracked = Object.fromEntries(
    rows.filter((r) => !r.gating && r.known).map((r) => [r.title, r.chargeable])
  );
  // Everything else in the file is prose a person wrote -- the note, the date, the environment
  // exemptions and their reasons. Rewriting it from the run's counts alone would delete all of it.
  writeFileSync(baselinePath, JSON.stringify({ ...baseline, tracked }, null, 2) + '\n');
  console.error(`baseline written to ${baselinePath}`);
}

process.exit(breaches.length ? 1 : 0);
