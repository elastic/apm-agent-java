# next version


## Breaking changes
 * Removed `ElasticApm.startSpan`. Spans can now only be created from their transactions via `Transaction#createSpan`.
 * `ElasticApm.startTransaction` and `Transaction#createSpan` don't activate the transaction and spans
   and are thus not available via `ElasticApm.activeTransaction` and `ElasticApm.activeSpan`.

## Features
 * Public API
    * Add `Span#captureException` and `Transaction#captureException` to public API.
      `ElasticApm.captureException` is deprecated now. Use `ElasticApm.currentSpan().captureException(exception)` instead.
    * Added `Transaction.getId` and `Span.getId` methods 
 * Added support for async servlet requests
 * Added support for Payara/Glassfish
 * Incubating support for Apache HttpClient
 * Support for Spring RestTemplate
 * Added configuration options `use_path_as_transaction_name` and `url_groups`,
   which allow to use the URL path as the transaction name.
   As that could contain path parameters, like `/user/$userId` however,
   You can set the `url_groups` option to define a wildcard pattern, like `/user/*`,
   to group those paths together.
   This is especially helpful when using an unsupported Servlet API-based framework. 
 * Support duration suffixes (`ms`, `s` and `m`) for duration configuration options.
   Not using the duration suffix logs out a deprecation warning and will not be supported in future versions.

## Bug Fixes
