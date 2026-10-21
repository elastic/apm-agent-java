# Copilot instructions

This repository contains the Elastic APM Java agent.
These instructions apply when reviewing pull requests.

## Review priorities

- Prioritize correctness, compatibility, and operational safety over style.
- Focus findings on problems introduced or exposed by the pull request.
- Explain the concrete failure scenario and user impact of each finding.
- Prefer localized fixes over unrelated refactoring.

## Compatibility and runtime behavior

- Check compatibility of public APIs, configuration, and emitted telemetry.
- Preserve supported Java versions and library or framework compatibility.
- Check concurrent access, asynchronous context propagation, and lifecycle handling.
- Check hot-path changes for unnecessary allocations, blocking, or contention.
- Check resource cleanup and unbounded retention, including class-loader leaks.
- Check error handling against existing failure-isolation and logging conventions.
- Check that logs and telemetry do not expose credentials or sensitive data.

## Tests and documentation

- Look for focused regression tests for changed behavior and relevant edge cases.
- Check that tests are deterministic and exercise observable behavior.
- Check documentation when public behavior or configuration changes.
- Follow existing project conventions without requesting unrelated formatting changes.

## APM Java agent

- Check that instrumentation failures do not escape into application code or change application behavior.
- Check class-loading boundaries between agent and application code.
- Check that instrumentation matches supported library versions without accidentally instrumenting unsupported versions.
