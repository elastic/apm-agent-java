# next version


## Breaking changes
 * Removed `ElasticApm.startSpan`. Spans can now only be created from their transactions via `Transaction#createSpan`.
 * `ElasticApm.startTransaction` and `Transaction#createSpan` don't activate the transaction and spans
   and are thus not available via `ElasticApm.activeTransaction` and `ElasticApm.activeSpan`.

## Features
 * Add `Span#captureException` and `Transaction#captureException` to public API.
   `ElasticApm.captureException` is deprecated now. Use `ElasticApm.currentSpan().captureException(exception)` instead.

## Bug Fixes
