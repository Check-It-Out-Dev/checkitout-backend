# `java/log-injection` — 500 alerts, one control

_Written 2026-09-10, after the structured-encoder change. This is the document the dismissal comment
on each alert points at._

## What CodeQL is reporting

`java/log-injection` is a dataflow query. It says, correctly: *this log entry depends on a
user-provided value.* On this codebase it finds 500 of them — every place a request-derived string
reaches a logger, which in a web application handling forms, uploads, OAuth callbacks and support
tickets is most places that log anything useful.

It is worth being precise about the vulnerability, because the name suggests something it is not.
The risk is **not** that a user can execute code by logging. It is **forgery**: a value containing a
newline ends the record early and starts a new one, and the invented line sits in the same file, in
the same shape, with nothing marking it as invented. An attacker who can get a string into a log can
write log entries. That is an integrity problem for anything downstream that reads those logs —
an audit trail, an alert rule, an incident timeline.

## Why the alerts are still open after the fix

CodeQL reasons about the flow from source to sink. It cannot see what the *appender* does with the
value once it arrives, so the alert persists whatever the encoder is. Both halves of that sentence
matter: the alerts staying open is not evidence the problem is unfixed, and the alerts closing would
not have been evidence that it was.

## The control

Every deployed environment writes **structured ECS** rather than a `%msg%n` pattern:

| profile | appender | encoder |
| --- | --- | --- |
| `prod` | `UNIFIED_PROD_FILE` | structured |
| `tst` | `UNIFIED_TEST_FILE` | structured |
| `sandbox` | `CONSOLE_JSON` | structured |
| `dev`, `local` | console and file | pattern, deliberately |

JSON cannot contain a raw newline inside a value — the encoder escapes it — so a record stays one
record no matter what the message contains. Forgery stops being *possible* rather than becoming
unlikely, which is the difference between a control and a mitigation.

`dev` and `local` keep the human-readable pattern on purpose. That output goes to a developer's own
terminal; there is no audit trail there to forge, and JSON in a terminal is worse for the person
reading it.

## Why not sanitise the 500 call sites

It is the obvious answer and it is weaker in every dimension that matters. One missed site is the
entire hole. Every log call written afterwards is a new chance to miss one. And the reviewer of a
future pull request has to know the rule and apply it by eye, where an encoder applies it by
construction. Neutralising at the sink is the control CodeQL's own guidance lists first, and it is
the one that does not decay.

## How the control is held in place

`src/test/java/com/sm/instagram/platform/unit/logging/LogForgeryUnitTest.java` encodes through the
same encoder the appender uses and asserts:

- a newline in a logged value cannot start a second record,
- a carriage return cannot either,
- a value shaped like JSON cannot break out of its field and overwrite `log.level`.

Their discriminating power is the line count: a pattern encoder emits two lines for the first
message and never produces an escaped `\n`. If anyone puts the pattern back, these fail.

## Disposition

The 500 alerts are dismissed as **won't fix**, with a comment pointing at this document. The dataflow
they describe is real and will stay real; the impact is closed at the sink and guarded by tests.
Leaving them open would not make the codebase safer — it would bury the findings that *are*
actionable under 500 that are not, which is its own kind of insecurity.

If the encoder is ever reverted, reopen them: the tests will have failed first.
