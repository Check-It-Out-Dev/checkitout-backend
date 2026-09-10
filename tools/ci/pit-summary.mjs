#!/usr/bin/env node
/**
 * What PIT found, in a form somebody will act on.
 *
 * A mutation report is thousands of lines of XML and the number at the top of it is the least
 * useful part: it moves by a point and nobody knows which test to write. What is useful is the two
 * lists underneath — the classes no test executes at all, and the mutants that ran and lived.
 *
 * Two scores, because they answer different questions and averaging them flatters nobody:
 *
 *   mutation score          killed / (killed + survived + no-coverage)
 *                           what the unit suite is worth over the whole scoped surface.
 *   score on covered code   killed / (killed + survived)
 *                           how good the tests that DO exist are at catching a defect.
 *
 * The gap between them is a coverage gap, not a test-quality gap, and it has a different fix:
 * "write a test" versus "make an existing test assert something". On this backend the gap is large
 * and honest — several security-relevant classes are exercised only by the Testcontainers
 * integration tier, so under the unit tier their mutants can only ever read NO_COVERAGE.
 *
 *   node tools/ci/pit-summary.mjs [--report target/pit-reports/mutations.xml]
 *                                 [--json pit-summary.json] [--break 40] [--top 20]
 */
import { existsSync, readFileSync, writeFileSync } from 'node:fs';

const argv = process.argv.slice(2);
const flag = (n, d) => {
  const i = argv.indexOf(`--${n}`);
  return i >= 0 ? argv[i + 1] : d;
};

const reportPath = flag('report', 'target/pit-reports/mutations.xml');
const outPath = flag('json', 'pit-summary.json');
const top = Number(flag('top', '20'));
const breakAt = flag('break') === undefined ? null : Number(flag('break'));

if (!existsSync(reportPath)) {
  const msg =
    `### Mutation testing (PIT)\n\n**No report at \`${reportPath}\`.** PIT did not finish, so this ` +
    `tier has no verdict. That is reported as silence rather than as success.\n`;
  console.log(msg);
  writeFileSync(outPath, JSON.stringify({ mutationScore: null, ran: false }, null, 1));
  process.exit(1);
}

const xml = readFileSync(reportPath, 'utf8');

// A hand parse rather than a dependency: the file is one flat list of <mutation status="..."> and
// adding an XML library to a Java repository to read one attribute is not a trade worth making.
const counts = {};
const perClass = new Map();
const survivors = [];
// PIT writes `<mutation detected='false' status='SURVIVED' ...>` with single-quoted attributes and
// no fixed attribute order. Split on the tag and read each record as a chunk: a regex spanning the
// whole element is easy to get subtly wrong against 1.9 MB of one-line XML, and did.
for (const chunk of xml.split('<mutation ').slice(1)) {
  const close = chunk.indexOf('</mutation>');
  const record = close === -1 ? chunk : chunk.slice(0, close);
  const status = (record.match(/status=['"]?([A-Z_]+)/) || [, ''])[1];
  if (!status) continue;
  const pick = (tag) => (record.match(new RegExp(`<${tag}>([^<]*)</${tag}>`)) || [, ''])[1];
  const cls = pick('mutatedClass');
  counts[status] = (counts[status] || 0) + 1;
  if (!perClass.has(cls)) perClass.set(cls, { killed: 0, survived: 0, noCoverage: 0 });
  const c = perClass.get(cls);
  if (status === 'KILLED' || status === 'TIMED_OUT') c.killed++;
  else if (status === 'SURVIVED') c.survived++;
  else if (status === 'NO_COVERAGE') c.noCoverage++;
  if (status === 'SURVIVED') {
    survivors.push({
      cls,
      method: pick('mutatedMethod'),
      line: pick('lineNumber'),
      mutator: (pick('mutator').split('.').pop() || '').replace(/Mutator$/, ''),
      desc: pick('description'),
    });
  }
}

const detected = (counts.KILLED || 0) + (counts.TIMED_OUT || 0);
const covered = detected + (counts.SURVIVED || 0);
const total = covered + (counts.NO_COVERAGE || 0);
const pct = (n, d) => (d ? Math.round((n / d) * 10000) / 100 : null);
const mutationScore = pct(detected, total);
const coveredScore = pct(detected, covered);

const short = (c) => c.replace(/^com\.sm\.instagram\.platform\./, '');
const rows = [...perClass.entries()].map(([cls, c]) => ({
  cls,
  ...c,
  n: c.killed + c.survived + c.noCoverage,
  score: pct(c.killed, c.killed + c.survived + c.noCoverage) ?? 0,
}));

// The classes nothing executes are the headline, not a footnote: a mutation report for a class with
// no test is not a weak score, it is an absence.
const untested = rows.filter((r) => r.killed === 0 && r.survived === 0 && r.noCoverage > 0);
const exercised = rows.filter((r) => !untested.includes(r)).sort((a, b) => a.score - b.score);

const out = [];
out.push('### Mutation testing (PIT)');
out.push('');
out.push(
  `**Mutation score ${mutationScore}%** across ${total} mutants — ${detected} caught, ` +
    `${counts.SURVIVED || 0} survived, ${counts.NO_COVERAGE || 0} never executed by any unit test.`
);
out.push('');
out.push(`On the code the unit suite actually reaches, the score is **${coveredScore}%**.`);
out.push('');

if (untested.length) {
  out.push(
    `${untested.length} classes have **no unit test at all** — every mutant in them is NO_COVERAGE. ` +
      `Several are exercised by the Testcontainers integration tier instead, which this run does not ` +
      `execute; that is a coverage gap to decide about, not a mutation result.`
  );
  out.push('');
  out.push('| class | mutants with no test |');
  out.push('| --- | ---: |');
  for (const r of untested.sort((a, b) => b.n - a.n).slice(0, top)) {
    out.push(`| \`${short(r.cls)}\` | ${r.n} |`);
  }
  out.push('');
}

if (exercised.length) {
  out.push('<details><summary>Classes the unit suite does reach, weakest first</summary>');
  out.push('');
  out.push('| class | score | caught | survived | no test |');
  out.push('| --- | ---: | ---: | ---: | ---: |');
  for (const r of exercised) {
    out.push(`| \`${short(r.cls)}\` | ${r.score}% | ${r.killed} | ${r.survived} | ${r.noCoverage} |`);
  }
  out.push('');
  out.push('</details>');
  out.push('');
}

if (survivors.length) {
  out.push(`<details><summary>${survivors.length} mutants that ran and were not noticed</summary>`);
  out.push('');
  out.push('| class:line | method | mutation |');
  out.push('| --- | --- | --- |');
  for (const s of survivors.slice(0, top)) {
    out.push(`| \`${short(s.cls)}:${s.line}\` | ${s.method} | ${s.desc || s.mutator} |`);
  }
  if (survivors.length > top) out.push(`| … | ${survivors.length - top} more in the artifact | |`);
  out.push('');
  out.push('</details>');
  out.push('');
}

const failed = breakAt !== null && mutationScore !== null && mutationScore < breakAt;
if (breakAt !== null) {
  out.push(
    failed
      ? `**Below the floor of ${breakAt}%.** The floor is a ratchet: it rises when the score does, and ` +
          'is never lowered to make a red run green.'
      : `_Floor: ${breakAt}%._`
  );
  out.push('');
}

console.log(out.join('\n'));

writeFileSync(
  outPath,
  JSON.stringify(
    {
      // The nightly verdict harvests `mutationScore` by name; keep the key stable.
      mutationScore,
      coveredScore,
      mutants: { total, ...counts },
      classesWithNoUnitTest: untested.length,
      floor: breakAt,
      ran: true,
      classes: rows.map((r) => ({ class: short(r.cls), score: r.score, ...r, cls: undefined })),
    },
    null,
    1
  )
);

process.exit(failed ? 1 : 0);
