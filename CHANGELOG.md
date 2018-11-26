# next

## Features

## Bug Fixes
 * Update dsl-json which fixes a memory leak.
 See [ngs-doo/dsl-json#102](https://github.com/ngs-doo/dsl-json/pull/102) for details. 
 * Avoid `VerifyError`s by non instrumenting classes compiled for Java 4 or earlier

# 1.0.1

## Bug Fixes
 * Fixes NoSuchMethodError CharBuffer.flip() which occurs when using the Elasticsearch RestClient and Java 7 or 8 (#313)

# 1.0.0

## Breaking changes
 * Remove intake v1 support. This version requires APM Server 6.5.0+ which supports the intake api v2.
   Until the time the APM Server 6.5.0 is officially released,
   you can test with docker by pulling the APM Server image via
   `docker pull docker.elastic.co/apm/apm-server:6.5.0-SNAPSHOT`. 

## Features
 * Adds `@CaptureTransaction` and `@CaptureSpan` annotations which let you declaratively add custom transactions and spans.
   Note that it is required to configure the `application_packages` for this to work.
   See the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-annotation) for more information.
 * The public API now supports to activate a span on the current thread.
   This makes the span available via `ElasticApm#currentSpan()`
   Refer to the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-span-activate) for more details.
 * Capturing of Elasticsearch RestClient 5.0.2+ calls.
   Currently, the `*Async` methods are not supported, only their synchronous counterparts.
 * Added API methods to enable correlating the spans created from the JavaScrip Real User Monitoring agent with the Java agent transaction.
   More information can be found in the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-ensure-parent-id).
 * Added `Transaction.isSampled()` and `Span.isSampled()` methods to the public API
 * Added `Transaction#setResult` to the public API (#293)

## Bug Fixes
 * Fix for situations where status code is reported as `200`, even though it actually was `500` (#225)
 * Capturing the username now properly works when using Spring security (#183)

# 1.0.0.RC1

## Breaking changes
 * Remove intake v1 support. This version requires APM Server 6.5.0+ which supports the intake api v2.
   Until the time the APM Server 6.5.0 is officially released,
   you can test with docker by pulling the APM Server image via
   `docker pull docker.elastic.co/apm/apm-server:6.5.0-SNAPSHOT`. 

## Features
 * Adds `@CaptureTransaction` and `@CaptureSpan` annotations which let you declaratively add custom transactions and spans.
   Note that it is required to configure the `application_packages` for this to work.
   See the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-annotation) for more information.
 * The public API now supports to activate a span on the current thread.
   This makes the span available via `ElasticApm#currentSpan()`
   Refer to the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-span-activate) for more details.
 * Capturing of Elasticsearch RestClient 5.0.2+ calls.
   Currently, the `*Async` methods are not supported, only their synchronous counterparts.
 * Added API methods to enable correlating the spans created from the JavaScrip Real User Monitoring agent with the Java agent transaction.
   More information can be found in the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-ensure-parent-id).
 * Microsecond accurate timestamps (#261)

## Bug Fixes
 * Fix for situations where status code is reported as `200`, even though it actually was `500` (#225)

# 0.8.0

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
