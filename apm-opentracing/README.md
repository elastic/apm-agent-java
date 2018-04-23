# Elastic APM OpenTracing bridge

The Elastic APM OpenTracing bridge makes allows to create Elastic APM `Transactions` and `Spans`,
using the OpenTracing API.
In other words,
it translates the calls to the OpenTracing API to Elastic APM and thus allows for reusing existing instrumentation.

The first span of a service will be converted to an Elastic APM
[`Transaction`](https://www.elastic.co/guide/en/apm/server/current/transactions.html),
subsequent spans are mapped to Elastic APM
[`Span`](https://www.elastic.co/guide/en/apm/server/current/spans.html)s.
Logging an Exception on the OpenTracing span will create an Elastic Apm
[`Error`](https://www.elastic.co/guide/en/apm/server/current/errors.html). Example:

```
Exception e = ...
span.log(
    Map.of(
        "event", "error", 
        "error.object", e
    )
)
```

## Initialize tracer

```
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.opentracing.ApmTracer;
import io.opentracing.Tracer

Tracer tracer = new ApmTracer(ElasticApmTracer.get());
```

## Elastic APM specific tags

Elastic APM defines some tags which are not included in the OpenTracing API but are relevant in the context of Elastic APM.

- `type` - sets the type of the transaction,
  for example `request`, `ext` or `db`
- `user.id` - sets the user id,
  appears in the "User" tab in the transaction details in the Elastic APM UI
- `user.email` - sets the user email,
  appears in the "User" tab in the transaction details in the Elastic APM UI
- `user.username` - sets the user name,
  appears in the "User" tab in the transaction details in the Elastic APM UI
- `result` - sets the result of the transaction. Overrides the default value of `success`.
  If the `error` tag is set to `true`, the default value is `error`.
