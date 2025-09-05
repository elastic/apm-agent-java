---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/public-api.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Public API [public-api]

The public API of the Elastic APM Java agent lets you customize and manually create spans and transactions, as well as track errors.

The first step in getting started with the API is to declare a dependency to the API:

```xml
<dependency>
    <groupId>co.elastic.apm</groupId>
    <artifactId>apm-agent-api</artifactId>
    <version>${elastic-apm.version}</version>
</dependency>
```

```groovy
compile "co.elastic.apm:apm-agent-api:$elasticApmVersion"
```

Replace the version placeholders with the [ latest version from maven central](https://mvnrepository.com/artifact/co.elastic.apm/apm-agent-api/latest): ![Maven Central](https://img.shields.io/maven-central/v/co.elastic.apm/apm-agent-api.svg "")

* [Tracer API](#api-tracer-api) - Access the currently active transaction and span
* [Annotation API](#api-annotation) - Annotations that make easier to create custom spans and transactions
* [Transaction API](#api-transaction) - Transaction methods
* [Span API](#api-span) - Span methods


## Tracer API [api-tracer-api]

The tracer gives you access to the currently active transaction and span. It can also be used to track an exception.

To use the API, you can just invoke the static methods on the class `co.elastic.apm.api.ElasticApm`.


### `Transaction currentTransaction()` [api-current-transaction]

Returns the currently active transaction. See [Transaction API](#api-transaction) on how to customize the current transaction.

If there is no current transaction, this method will return a noop transaction, which means that you never have to check for `null` values.

```java
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Transaction;

Transaction transaction = ElasticApm.currentTransaction();
```

::::{note}
Transactions created via [`ElasticApm.startTransaction()`](#api-start-transaction) can not be retrieved by calling this method. See [`span.activate()`](#api-span-activate) on how to achieve that.
::::



### `Span currentSpan()` [api-current-span]

Returns the currently active span or transaction. See [Span API](#api-span) on how to customize the current span.

If there is no current span, this method will return a noop span, which means that you never have to check for `null` values.

Note that even if this method is returning a noop span, you can still [capture exceptions](#api-span-capture-exception) on it. These exceptions will not have a link to a Span or a Transaction.

```java
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;

Span span = ElasticApm.currentSpan();
```

::::{note}
Spans created via [`startSpan()`](#api-span-start-span) or [`startSpan(String, String, String)`](#api-span-start-span-with-type) can not be retrieved by calling this method. See [`span.activate()`](#api-span-activate) on how to achieve that.
::::



### `Transaction startTransaction()` [api-start-transaction]

Use this method to create a custom transaction.

Note that the agent will do this for you automatically when ever your application receives an incoming HTTP request. You only need to use this method to create custom transactions.

It is important to call [`void end()`](#api-transaction-end) when the transaction has ended. A best practice is to use the transaction in a try-catch-finally block. Example:

```java
Transaction transaction = ElasticApm.startTransaction();
try {
    transaction.setName("MyController#myAction");
    transaction.setType(Transaction.TYPE_REQUEST);
    // do your thing...
} catch (Exception e) {
    transaction.captureException(e);
    throw e;
} finally {
    transaction.end();
}
```

::::{note}
Transactions created via this method can not be retrieved by calling [`ElasticApm.currentSpan()`](#api-current-span) or [`ElasticApm.currentTransaction()`](#api-current-transaction). See [`transaction.activate()`](#api-transaction-activate) on how to achieve that.
::::



### `Transaction startTransactionWithRemoteParent(HeaderExtractor)` [1.3.0] [api-start-transaction-with-remote-parent-header]

Similar to [`Transaction startTransaction()`](#api-start-transaction) but creates this transaction as the child of a remote parent.

* `headerExtractor`: a functional interface which receives a header name and returns the first header with that name

Example:

```java
// Hook into a callback provided by the framework that is called on incoming requests
public Response onIncomingRequest(Request request) throws Exception {
    // creates a transaction representing the server-side handling of the request
    Transaction transaction = ElasticApm.startTransactionWithRemoteParent(key -> request.getHeader(key));
    try (final Scope scope = transaction.activate()) {
        String name = "a useful name like ClassName#methodName where the request is handled";
        transaction.setName(name);
        transaction.setType(Transaction.TYPE_REQUEST);
        return request.handle();
    } catch (Exception e) {
        transaction.captureException(e);
        throw e;
    } finally {
        transaction.end();
    }
}
```

::::{note}
If the protocol supports multi-value headers, use [`Transaction startTransactionWithRemoteParent(HeaderExtractor, HeadersExtractor)` [1.3.0]](#api-start-transaction-with-remote-parent-headers)
::::



### `Transaction startTransactionWithRemoteParent(HeaderExtractor, HeadersExtractor)` [1.3.0] [api-start-transaction-with-remote-parent-headers]

Similar to [`Transaction startTransaction()`](#api-start-transaction) but creates this transaction as the child of a remote parent.

* `headerExtractor`:  a functional interface which receives a header name and returns the first header with that name
* `headersExtractor`: a functional interface which receives a header name and returns all headers with that name

Example:

```java
// Hook into a callback provided by the framework that is called on incoming requests
public Response onIncomingRequest(Request request) throws Exception {
    // creates a transaction representing the server-side handling of the request
    Transaction transaction = ElasticApm.startTransactionWithRemoteParent(request::getHeader, request::getHeaders);
    try (final Scope scope = transaction.activate()) {
        String name = "a useful name like ClassName#methodName where the request is handled";
        transaction.setName(name);
        transaction.setType(Transaction.TYPE_REQUEST);
        return request.handle();
    } catch (Exception e) {
        transaction.captureException(e);
        throw e;
    } finally {
        transaction.end();
    }
}
```

::::{note}
If the protocol does not support multi-value headers, use [`Transaction startTransactionWithRemoteParent(HeaderExtractor)` [1.3.0]](#api-start-transaction-with-remote-parent-header)
::::



### `void setServiceInfoForClassLoader(ClassLoader, String, String)` [1.30.0] [api-set-service-info-for-class-loader]

Associates a class loader with a service name and version.

The association is used to overwrite the autodetected service name and version when a transaction is started.

::::{note}
If the class loader already is associated with a service name and version, the existing information will not be overwritten.
::::


* `classLoader`: the class loader which should be associated with the given service name and version
* `serviceName`: the service name
* `serviceVersion`: the service version


## Annotation API [api-annotation]

The API comes with two annotations which make it easier to create custom spans and transactions. Just put the annotations on top of your methods and the agent will take care of creating and reporting the corresponding transaction and spans. It will also make sure to capture any uncaught exceptions.

::::{note}
It is required to configure the [`application_packages`](/reference/config-stacktrace.md#config-application-packages), otherwise these annotations will be ignored.
::::



### `@CaptureTransaction` [api-capture-transaction]

Annotating a method with `@CaptureTransaction` creates a transaction for that method.

Note that this only works when there is no active transaction on the same thread.

* `value`: The name of the transaction. Defaults to `ClassName#methodName`
* `type`: The type of the transaction. Defaults to `request`

::::{note}
Using this annotation implicitly creates a Transaction and activates it when entering the annotated method. It also implicitly ends it and deactivates it before exiting the annotated method. See [`ElasticApm.startTransaction()`](#api-start-transaction), [`transaction.activate()`](#api-transaction-activate) and [`transaction.end()`](#api-transaction-end)
::::



### `@CaptureSpan` [api-capture-span]

Annotating a method with `@CaptureSpan` creates a span as the child of the currently active span or transaction ([`Span currentSpan()`](#api-current-span)).

When there is no current span or transaction, no span will be created.

* `value`: The name of the span. Defaults to `ClassName#methodName`
* `type`: The type of the span, e.g. `db` for DB span. Defaults to `app`
* `subtype`: The subtype of the span, e.g. `mysql` for DB span. Defaults to empty string
* `action`: The action related to the span, e.g. `query` for DB spans. Defaults to empty string
* `discardable`: By default, spans may be discarded in certain scenarios. Set this attribute to `false` to make this span non-discardable.
* `exit`: By default, spans are internal spans, making it an exit span prevents the creation of nested spans and is intended to represent calls to an external system like a database or third-party service.

::::{note}
Using this annotation implicitly creates a Span and activates it when entering the annotated method. It also implicitly ends it and deactivates it before exiting the annotated method. See [`startSpan()`](#api-span-start-span), [`startSpan(String, String, String)`](#api-span-start-span-with-type), [`span.activate()`](#api-transaction-activate) and [`span.end()`](#api-span-end)
::::



### `@Traced`  [1.11.0] [api-traced]

Annotating a method with `@Traced` creates a span as the child of the currently active span or transaction.

When there is no current span, a transaction will be created instead.

Use this annotation over [`@CaptureSpan`](#api-capture-span) or [`@CaptureTransaction`](#api-capture-transaction) if a method can both be an entry point (a transaction) or a unit of work within a transaction (a span).

* `value`: The name of the span or transaction. Defaults to `ClassName#methodName`
* `type`: The type of the span or transaction. Defaults to `request` for transactions and `app` for spans
* `subtype`: The subtype of the span, e.g. `mysql` for DB span. Defaults to empty string. Has no effect when a transaction is created.
* `action`: The action related to the span, e.g. `query` for DB spans. Defaults to empty string. Has no effect when a transaction is created.
* `discardable`: By default, spans may be discarded in certain scenarios. Set this attribute to `false` to make this span non-discardable. This attribute has no effect if the created event is a Transaction.

::::{note}
Using this annotation implicitly creates a span or transaction and activates it when entering the annotated method. It also implicitly ends it and deactivates it before exiting the annotated method. See [`startSpan()`](#api-span-start-span), [`startSpan(String, String, String)`](#api-span-start-span-with-type), [`span.activate()`](#api-transaction-activate) and [`span.end()`](#api-span-end)
::::



## Transaction API [api-transaction]

A transaction is the data captured by an agent representing an event occurring in a monitored service and groups multiple spans in a logical group. A transaction is the first [`Span`](#api-span) of a service, and is also known under the term entry span.

See [`Transaction currentTransaction()`](#api-current-transaction) on how to get a reference of the current transaction.

`Transaction` is a sub-type of `Span`. So it has all the methods a [`Span`](#api-span) offers plus additional ones.

::::{note}
Calling any of the transaction’s methods after [`void end()`](#api-transaction-end) has been called is illegal. You may only interact with transaction when you have control over its lifecycle. For example, if a span is ended in another thread you must not add labels if there is a chance for a race between the [`void end()`](#api-transaction-end) and the [`Transaction setLabel(String key, value)` [1.5.0 as `addLabel`]](#api-transaction-add-tag) method.
::::



### `Transaction setName(String name)` [api-set-name]

Override the name of the current transaction. For supported frameworks, the transaction name is determined automatically, and can be overridden using this method.

Example:

```java
transaction.setName("My Transaction");
```

* `name`: (required) A string describing name of the transaction


### `Transaction setType(String type)` [api-transaction-set-type]

Sets the type of the transaction. There’s a special type called `request`, which is used by the agent for the transactions automatically created when an incoming HTTP request is detected.

Example:

```java
transaction.setType(Transaction.TYPE_REQUEST);
```

* `type`: The type of the transaction


### `Transaction setFrameworkName(String frameworkName)` [1.25.0] [api-transaction-set-framework-name]

Provides a way to manually set the `service.framework.name` field. For supported frameworks, the framework name is determined automatically, and can be overridden using this function. `null` or the empty string will make the agent omit this field.

Example:

```java
transaction.setFrameworkName("My Framework");
```

* `frameworkName`: The name of the framework


### `Transaction setServiceInfo(String serviceName, String serviceVersion)` [1.30.0] [api-transaction-set-service-info]

Sets the service name and version for this transaction and its child spans.

::::{note}
If this method is called after child spans are already created, they may have the wrong service name and version.
::::


* `serviceName`: the service name
* `serviceVersion`: the service version


### `Transaction useServiceInfoForClassLoader(ClassLoader classLoader)` [1.30.0] [api-transaction-use-service-info-for-class-loader]

Sets the service name and version, that are associated with the given class loader (see: [`ElasticApm#setServiceInfoForClassLoader(ClassLoader, String, String)`](#api-set-service-info-for-class-loader)), for this transaction and its child spans.

::::{note}
If this method is called after child spans are already created, they may have the wrong service name and version.
::::


* `classLoader`: the class loader that should be used to set the service name and version


### `Transaction setLabel(String key, value)` [1.5.0 as `addLabel`] [api-transaction-add-tag]

Labels are used to add **indexed** information to transactions, spans, and errors. Indexed means the data is searchable and aggregatable in Elasticsearch. Multiple labels can be defined with different key-value pairs.

* Indexed: Yes
* Elasticsearch type: [object](elasticsearch://reference/elasticsearch/mapping-reference/object.md)
* Elasticsearch field: `labels` (previously `context.tags` in <v.7.0)

Label values can be a string, boolean, or number. Because labels for a given key are stored in the same place in Elasticsearch, all label values of a given key must have the same data type. Multiple data types per key will throw an exception, e.g. `{foo: bar}` and `{foo: 42}`

::::{note}
Number and boolean labels were only introduced in APM Server 6.7+. Using this API in combination with an older APM Server versions leads to validation errors.
::::


::::{important}
Avoid defining too many user-specified labels. Defining too many unique fields in an index is a condition that can lead to a [mapping explosion](docs-content://manage-data/data-store/mapping.md#mapping-limit-settings).
::::


```java
transaction.setLabel("foo", "bar");
```

* `String key`:   The tag key
* `String|Number|boolean value`: The tag value


### `Transaction addCustomContext(String key, value)` [1.7.0] [api-transaction-add-custom-context]

Custom context is used to add non-indexed, custom contextual information to transactions. Non-indexed means the data is not searchable or aggregatable in Elasticsearch, and you cannot build dashboards on top of the data. However, non-indexed information is useful for other reasons, like providing contextual information to help you quickly debug performance issues or errors.

The value can be a `String`, `Number` or `boolean`.

```java
transaction.addCustomContext("foo", "bar");
```

* `String key`:   The tag key
* `String|Number|boolean value`: The tag value


### `Transaction setUser(String id, String email, String username)` [api-transaction-set-user]

Call this to enrich collected performance data and errors with information about the user/client. This method can be called at any point during the request/response life cycle (i.e. while a transaction is active). The given context will be added to the active transaction.

If an error is captured, the context from the active transaction is used as context for the captured error.

```java
transaction.setUser(user.getId(), user.getEmail(), user.getUsername());
```

* `id`:       The user’s id or `null`, if not applicable.
* `email`:    The user’s email address or `null`, if not applicable.
* `username`: The user’s name or `null`, if not applicable.


### `Transaction setUser(String id, String email, String username, String domain)` [1.23.0] [api-transaction-set-user2]

Call this to enrich collected performance data and errors with information about the user/client. This method can be called at any point during the request/response life cycle (i.e. while a transaction is active). The given context will be added to the active transaction.

If an error is captured, the context from the active transaction is used as context for the captured error.

```java
transaction.setUser(user.getId(), user.getEmail(), user.getUsername(), user.getDomain());
```

* `id`:       The user’s id or `null`, if not applicable.
* `email`:    The user’s email address or `null`, if not applicable.
* `username`: The user’s name or `null`, if not applicable.
* `domain`:   The user’s domain or `null`, if not applicable.


### `String captureException(Exception e)` [api-transaction-capture-exception]

Captures an exception and reports it to the APM server. Since version 1.14.0 - returns the id of reported error.


### `String getId()` [api-transaction-get-id]

Returns the id of this transaction (never `null`)

If this transaction represents a noop, this method returns an empty string.


### `String getTraceId()` [api-transaction-get-trace-id]

Returns the trace-id of this transaction.

The trace-id is consistent across all transactions and spans which belong to the same logical trace, even for transactions and spans which happened in another service (given this service is also monitored by Elastic APM).

If this span represents a noop, this method returns an empty string.


### `String ensureParentId()` [api-ensure-parent-id]

If the transaction does not have a parent-ID yet, calling this method generates a new ID, sets it as the parent-ID of this transaction, and returns it as a `String`.

This enables the correlation of the spans the JavaScript Real User Monitoring (RUM) agent creates for the initial page load with the transaction of the backend service. If your backend service generates the HTML page dynamically, initializing the JavaScript RUM agent with the value of this method allows analyzing the time spent in the browser vs in the backend services.

To enable the JavaScript RUM agent when using an HTML templating language like Freemarker, add `ElasticApm.currentTransaction()` with the key `"transaction"` to the model.

Also, add a snippet similar to this to the body of your HTML page, preferably before other JS libraries:

```html
<script src="elastic-apm-js-base/dist/bundles/elastic-apm-js-base.umd.min.js"></script>
<script>
  elasticApm.init({
    serviceName: "service-name",
    serverUrl: "http://127.0.0.1:8200",
    pageLoadTraceId: "${transaction.traceId}",
    pageLoadSpanId: "${transaction.ensureParentId()}",
    pageLoadSampled: ${transaction.sampled}
  })
</script>
```

See the [JavaScript RUM agent documentation](apm-agent-rum-js://reference/index.md) for more information.


### `Span startSpan(String type, String subtype, String action)` [api-transaction-start-span-with-type]

Start and return a new span with a type, a subtype and an action, as a child of this transaction.

The type, subtype and action strings are used to group similar spans together, with different resolution. For instance, all DB spans are given the type `db`; all spans of MySQL queries are given the subtype `mysql` and all spans describing queries are given the action `query`. In this example `db` is considered the general type. Though there are no naming restrictions for the general types, the following are standardized across all Elastic APM agents: `app`, `db`, `cache`, `template`, and `ext`.

::::{note}
*.* (dot) character is not allowed within type, subtype and action. Any such character will be replaced with a *_* (underscore) character.
::::


It is important to call [`void end()`](#api-span-end) when the span has ended. A best practice is to use the span in a try-catch-finally block. Example:

```java
Span span = parent.startSpan("db", "mysql", "query");
try {
    span.setName("SELECT FROM customer");
    // do your thing...
} catch (Exception e) {
    span.captureException(e);
    throw e;
} finally {
    span.end();
}
```

::::{note}
Spans created via this method can not be retrieved by calling [`ElasticApm.currentSpan()`](#api-current-span). See [`span.activate()`](#api-span-activate) on how to achieve that.
::::



### `Span startExitSpan(String type, String subtype, String action)` [api-transaction-start-exit-span-with-type]

Start and return a new exit span with a type, a subtype and an action, as a child of this transaction.

Similar to [`startSpan(String, String, String)`](#api-span-start-span-with-type), but the created span will be used to create a node in the Service Map and a downstream service in the Dependencies Table. The provided subtype will be used as the downstream service name, unless the `service.target.type` and `service.target.name` fields are explicitly set through [`setServiceTarget(String type, String name)`](#api-span-set-service-target).


### `Span startSpan()` [api-transaction-start-span]

Start and return a new custom span with no type as a child of this transaction.

It is important to call [`void end()`](#api-span-end) when the span has ended. A best practice is to use the span in a try-catch-finally block. Example:

```java
Span span = parent.startSpan();
try {
    span.setName("SELECT FROM customer");
    // do your thing...
} catch (Exception e) {
    span.captureException(e);
    throw e;
} finally {
    span.end();
}
```

::::{note}
Spans created via this method can not be retrieved by calling [`ElasticApm.currentSpan()`](#api-current-span). See [`span.activate()`](#api-span-activate) on how to achieve that.
::::



### `Transaction setResult(String result)` [api-transaction-set-result]

A string describing the result of the transaction. This is typically the HTTP status code, or e.g. "success" for a background task

* `result`: a string describing the result of the transaction

The result value set through API will have priority over the value that might be set by auto-instrumentation.


### `Span setOutcome(Outcome outcome)` [1.21.0] [api-transaction-set-outcome]

Sets the transaction or span outcome. Use either `FAILURE` or `SUCCESS` to indicate success or failure, use `UNKNOWN` when the outcome can’t be properly known.

* `outcome`: transaction or span outcome

Outcome is used to compute error rates between services, using `UNKNOWN` will not alter those rates. The value set through API will have higher priority over the value that might be set by auto-instrumentation.


### `Transaction setStartTimestamp(long epochMicros)` [1.5.0] [api-transaction-set-start-timestamp]

Sets the start timestamp of this event.

* `epochMicros`: the timestamp of when this event started, in microseconds (µs) since epoch


### `void end()` [api-transaction-end]

Ends the transaction and schedules it to be reported to the APM Server. It is illegal to call any methods on a transaction instance which has already ended. This also includes this method and [`Span startSpan()`](#api-transaction-start-span). Example:

```java
transaction.end();
```


### `void end(long epochMicros)` [1.5.0] [api-transaction-end-timestamp]

Ends the transaction and schedules it to be reported to the APM Server. It is illegal to call any methods on a transaction instance which has already ended. This also includes this method and [`Span startSpan()`](#api-transaction-start-span).

* `epochMicros`: the timestamp of when this event ended, in microseconds (µs) since epoch

Example:

```java
transaction.end(System.currentTimeMillis() * 1000);
```


### `Scope activate()` [api-transaction-activate]

Makes this span the active span on the current thread until `Scope#close()` has been called. Scopes should only be used in try-with-resource statements in order to make sure the `Scope#close()` method is called in all circumstances. Failing to close a scope can lead to memory leaks and corrupts the parent-child relationships.

This method should always be used within a try-with-resources statement:

```java
Transaction transaction = ElasticApm.startTransaction();
// Within the try block the transaction is available
// on the current thread via ElasticApm.currentTransaction().
// This is also true for methods called within the try block.
try (final Scope scope = transaction.activate()) {
    transaction.setName("MyController#myAction");
    transaction.setType(Transaction.TYPE_REQUEST);
    // do your thing...
} catch (Exception e) {
    transaction.captureException(e);
    throw e;
} finally {
    transaction.end();
}
```

::::{note}
[`Scope activate()`](#api-transaction-activate) and `Scope#close()` have to be called on the same thread.
::::



### `boolean isSampled()` [api-transaction-is-sampled]

Returns true if this transaction is recorded and sent to the APM Server


### `void injectTraceHeaders(HeaderInjector headerInjector)` [1.3.0] [api-transaction-inject-trace-headers]

* `headerInjector`: tells the agent how to inject a header into the request object

Allows for manual propagation of the tracing headers.

If you want to manually instrument an RPC framework which is not already supported by the auto-instrumentation capabilities of the agent, you can use this method to inject the required tracing headers into the header section of that framework’s request object.

Example:

```java
// Hook into a callback provided by the RPC framework that is called on outgoing requests
public Response onOutgoingRequest(Request request) throws Exception {
    // creates a span representing the external call
    Span span = ElasticApm.currentSpan()
            .startSpan("external", "http", null)
            .setName(request.getMethod() + " " + request.getHost());
    try (final Scope scope = transaction.activate()) {
        span.injectTraceHeaders((name, value) -> request.addHeader(name, value));
        return request.execute();
    } catch (Exception e) {
        span.captureException(e);
        throw e;
    } finally {
        span.end();
    }
}
```


## Span API [api-span]

A span contains information about a specific code path, executed as part of a transaction.

If for example a database query happens within a recorded transaction, a span representing this database query may be created. In such a case the name of the span will contain information about the query itself, and the type will hold information about the database type.

See [`Span currentSpan()`](#api-current-span) on how to get a reference of the current span.


### `Span setName(String name)` [api-span-set-name]

Override the name of the current span.

Example:

```java
span.setName("SELECT FROM customer");
```

* `name`: the name of the span


### `Span setLabel(String key, value)` [1.5.0 as `addLabel`] [api-span-add-tag]

A flat mapping of user-defined labels with string, number or boolean values.

::::{note}
In version 6.x, labels are stored under `context.tags` in Elasticsearch. As of version 7.x, they are stored as `labels` to comply with the [Elastic Common Schema (ECS)](https://github.com/elastic/ecs).
::::


::::{note}
The labels are indexed in Elasticsearch so that they are searchable and aggregatable. By all means, you should avoid that user specified data, like URL parameters, is used as a tag key as it can lead to mapping explosions.
::::


```java
span.setLabel("foo", "bar");
```

* `String key`:   The tag key
* `String|Number|boolean value`: The tag value


### `String captureException(Exception e)` [api-span-capture-exception]

Captures an exception and reports it to the APM server. Since version 1.14.0 - returns the id of reported error.


### `String getId()` [api-span-get-id]

Returns the id of this span (never `null`)

If this span represents a noop, this method returns an empty string.


### `String getTraceId()` [api-span-get-trace-id]

Returns the trace-ID of this span.

The trace-ID is consistent across all transactions and spans which belong to the same logical trace, even for transactions and spans which happened in another service (given this service is also monitored by Elastic APM).

If this span represents a noop, this method returns an empty string.


### `Span setStartTimestamp(long epochMicros)` [1.5.0] [api-span-set-start-timestamp]

Sets the start timestamp of this event.

* `epochMicros`: the timestamp of when this event started, in microseconds (µs) since epoch


### `Span setDestinationService(String resource)` [1.25.0] [api-span-set-destination-resource]

Provides a way to manually set the span’s `destination.service.resource` field, which is used for the construction of service maps and the identification of downstream services. Any value set through this method will take precedence over the automatically inferred one. Using `null` or empty resource string will result in the omission of this field from the span context.


### `Span setNonDiscardable()` [1.32.0] [api-span-set-non-discardable]

Makes this span non-discardable. In some cases, spans may be discarded, for example if [`span_min_duration` ([1.16.0])](/reference/config-core.md#config-span-min-duration) is set and the span does not exceed the configured threshold. Use this method to make sure the current span is not discarded.

::::{note}
making a span non-discardable implicitly makes the entire stack of active spans non-discardable as well. Child spans can still be discarded.
::::



### `Span setDestinationAddress(String address, int port)` [1.25.0] [api-span-set-destination-address]

Provides a way to manually set the span’s `destination.address` and `destination.port` fields. Values set through this method will take precedence over the automatically discovered ones. Using `null` or empty address or non-positive port will result in the omission of the corresponding field from the span context.


### `Span setServiceTarget(String type, String name)` [1.32.0] [api-span-set-service-target]

Provides a way to manually set the span `service.target.type` and `service.target.name` fields. Values set through this method will take precedence over the automatically discovered ones. Using `null` or empty string values will result in the omission of those fields from the span context.


### `void end()` [api-span-end]

Ends the span and schedules it to be reported to the APM Server. It is illegal to call any methods on a span instance which has already ended. This also includes this method and [`Span startSpan()`](#api-span-start-span).


### `void end(long epochMicros)` [1.5.0] [api-span-end-timestamp]

Ends the span and schedules it to be reported to the APM Server. It is illegal to call any methods on a span instance which has already ended. This also includes this method and [`Span startSpan()`](#api-span-start-span).

* `epochMicros`: the timestamp of when this event ended, in microseconds (µs) since epoch

Example:

```java
span.end(System.currentTimeMillis() * 1000);
```


### `Span startSpan(String type, String subtype, String action)` [api-span-start-span-with-type]

Start and return a new span with a type, a subtype and an action, as a child of this span.

The type, subtype and action strings are used to group similar spans together, with different resolution. For instance, all DB spans are given the type `db`; all spans of MySQL queries are given the subtype `mysql` and all spans describing queries are give the action `query`. In this example `db` is considered the general type. Though there are no naming restrictions for the general types, the following are standardized across all Elastic APM agents: `app`, `db`, `cache`, `template`, and `ext`.

::::{note}
*.* (dot) character is not allowed within type, subtype and action. Any such character will be replaced with a *_* (underscore) character.
::::


It is important to call [`void end()`](#api-span-end) when the span has ended. A best practice is to use the span in a try-catch-finally block. Example:

```java
Span span = parent.startSpan("db", "mysql", "query");
try {
    span.setName("SELECT FROM customer");
    // do your thing...
} catch (Exception e) {
    span.captureException(e);
    throw e;
} finally {
    span.end();
}
```

::::{note}
Spans created via this method can not be retrieved by calling [`ElasticApm.currentSpan()`](#api-current-span). See [`span.activate()`](#api-span-activate) on how to achieve that.
::::



### `Span startExitSpan(String type, String subtype, String action)` [api-span-start-exit-span-with-type]

Start and return a new exit span with a type, a subtype and an action, as a child of this span.

Similar to [`startSpan(String, String, String)`](#api-span-start-span-with-type), but the created span will be used to create a node in the Service Map and a downstream service in the Dependencies Table. The provided subtype will be used as the downstream service name, unless the `service.target.type` and `service.target.name` fields are explicitly set through [`setServiceTarget(String type, String name)`](#api-span-set-service-target).

If invoked on a span which is already an exit span, this method will return a noop span.


### `Span startSpan()` [api-span-start-span]

Start and return a new custom span with no type as a child of this span.

It is important to call [`void end()`](#api-span-end) when the span has ended. A best practice is to use the span in a try-catch-finally block. Example:

```java
Span span = parent.startSpan();
try {
    span.setName("SELECT FROM customer");
    // do your thing...
} catch (Exception e) {
    span.captureException(e);
    throw e;
} finally {
    span.end();
}
```

::::{note}
Spans created via this method can not be retrieved by calling [`ElasticApm.currentSpan()`](#api-current-span). See [`span.activate()`](#api-span-activate) on how to achieve that.
::::



### `Scope activate()` [api-span-activate]

Makes this span the active span on the current thread until `Scope#close()` has been called. Scopes should only be used in try-with-resource statements in order to make sure the `Scope#close()` method is called in all circumstances. Failing to close a scope can lead to memory leaks and corrupts the parent-child relationships.

This method should always be used within a try-with-resources statement:

```java
Span span = parent.startSpan("db", "mysql", "query");
// Within the try block the span is available
// on the current thread via ElasticApm.currentSpan().
// This is also true for methods called within the try block.
try (final Scope scope = span.activate()) {
    span.setName("SELECT FROM customer");
    // do your thing...
} catch (Exception e) {
    span.captureException(e);
    throw e;
} finally {
    span.end();
}
```

::::{note}
Calling any of the span’s methods after [`void end()`](#api-span-end) has been called is illegal. You may only interact with span when you have control over its lifecycle. For example, if a span is ended in another thread you must not add labels if there is a chance for a race between the [`void end()`](#api-span-end) and the [`Span setLabel(String key, value)` [1.5.0 as `addLabel`]](#api-span-add-tag) method.
::::



### `boolean isSampled()` [api-span-is-sampled]

Returns true if this span is recorded and sent to the APM Server


### `void injectTraceHeaders(HeaderInjector headerInjector)` [1.3.0] [api-span-inject-trace-headers]

* `headerInjector`: tells the agent how to inject a header into the request object

Allows for manual propagation of the tracing headers.

If you want to manually instrument an RPC framework which is not already supported by the auto-instrumentation capabilities of the agent, you can use this method to inject the required tracing headers into the header section of that framework’s request object.

Example:

```java
// Hook into a callback provided by the RPC framework that is called on outgoing requests
public Response onOutgoingRequest(Request request) throws Exception {
    // creates a span representing the external call
    Span span = ElasticApm.currentSpan()
            .startSpan("external", "http", null)
            .setName(request.getMethod() + " " + request.getHost());
    try (final Scope scope = transaction.activate()) {
        span.injectTraceHeaders((name, value) -> request.addHeader(name, value));
        return request.execute();
    } catch (Exception e) {
        span.captureException(e);
        throw e;
    } finally {
        span.end();
    }
}
```

