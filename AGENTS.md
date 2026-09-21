# DayBricks — Agent Guide

Keep changes focused, preserve existing project conventions, and avoid unnecessary context, tool calls, builds, and model-driven waiting.

## Working principles

* Make the smallest coherent change that fully solves the requested task.
* Preserve the existing architecture and conventions unless the task requires changing them.
* Do not perform unrelated refactors, cleanup, formatting sweeps, dependency upgrades, or generated-file edits.
* Preserve unrelated user changes in the working tree.
* Do not commit, push, publish, deploy, or alter external systems unless explicitly requested.
* When a minor ambiguity has a safe conventional default, use it rather than stopping unnecessarily.

## Context discipline

Context is limited and should be treated as a resource.

* Read the files directly relevant to the task first.
* Prefer targeted search over broad repository exploration.
* Prefer `rg` / `rg --files` when available.
* Batch independent searches and file reads when practical.
* Do not repeatedly reread unchanged files or logs.
* Do not inspect generated directories such as `build/`, `.gradle/`, or `.idea/` unless specifically needed.
* Stop gathering context once enough evidence exists to make the change safely.
* Read deeper documentation only when it is relevant to the current task.

## Toolset

See docs in `docs/local-toolchain.md` for the recommended local toolchain.

## Verification

Use the narrowest verification that gives meaningful confidence.

Prefer, in order:

1. the affected test or test class;
2. the affected module's test/compile/check task;
3. the relevant application build;
4. broader project checks only when the change genuinely requires them.

Do not automatically run the entire test suite or a full project build after a small change.

Do not start an emulator or run expensive instrumentation tests unless they provide evidence that narrower checks cannot.

If a check fails for an unrelated pre-existing reason, report it rather than expanding the task automatically.

## Long-running commands

Long-running deterministic commands are waiting problems, not reasoning problems.

Examples: Gradle builds/tests, dependency downloads, emulator startup, code generation, packaging, and installation.

While such a command is running:

* DO NOT busy-poll it.
* DO NOT repeatedly check its status every few seconds or every minute.
* DO NOT spend model turns merely reporting that it is still running.
* Prefer one blocking operation, event-driven completion, or the longest reasonable supported wait.
* If nothing requires intervention, remain idle until the process finishes or produces meaningful new information.
* Never turn a long-running command into a model-driven polling loop.

## Command output

Keep noisy output out of model context whenever practical.

* For verbose commands, preserve full output in a log when useful.
* Inspect the exit status first.
* On success, avoid reading the complete log.
* On failure, find the first meaningful error and inspect only the relevant surrounding output.
* Do not dump entire Gradle, compiler, test, `adb`, or `logcat` logs into context.
* Prefer filtered Android logs over unrestricted `adb logcat`.

## Failure handling

When a build or test fails:

1. identify the first meaningful failure;
2. inspect only enough evidence to understand it;
3. make a relevant change;
4. rerun the narrowest verification that can confirm the fix.

Do not repeatedly rerun an unchanged failing command.

If tool calls are repeating without producing new information, stop and reassess instead of continuing the loop.

### Known Windows sandbox failure

On Windows, always run Gradle with sandbox escalation. Sandboxed Gradle can fail
with `AccessDeniedException` on generated `R.jar`. Do not attempt a sandboxed run
first, and do not clean the build or delete the JAR in response.

## Completion

Before finishing:

* review the relevant diff;
* ensure no unrelated files were changed;
* run the narrowest sufficient verification.

Report concisely:

* what changed;
* what verification was run;
* whether it passed;
* any material unresolved issue.
