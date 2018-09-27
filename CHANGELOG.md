# next version

## Breaking changes
 * Wildcard patterns are case insensitive by default. Prepend `(?-i)` to make the matching case sensitive.

## Features
 * Wildcard patterns are now not limited to only one wildcard in the middle and can be arbitrarily complex now.
   Example: `*foo*bar*baz`.
 * Support for JAX-RS annotations.
   Transactions are named based on your resources (`ResourceClass#resourceMethod`).

## Bug Fixes

# 0.7.0

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
 * Add ability to add multiple APM server URLs, which enables client-side load balancing.
   The configuration option `server_url` has been renamed to `server_urls` to reflect this change.
   However, `server_url` still works for backwards compatibility.
 * The configuration option `service_name` is now optional.
   It defaults to the main class name,
   the name of the executed jar file (removing the version number),
   or the application server name (for example `tomcat-application`).
   In a lot of cases,
   you will still want to set the `service_name` explicitly.
   But it helps getting started and seeing data easier,
   as there are no required configuration options anymore.
   In the future we will most likely determine more useful application names for Servlet API-based applications.

## Bug Fixes
