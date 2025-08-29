---
navigation_title: "Reporter"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-reporter.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
products:
  - id: cloud-serverless
  - id: observability
  - id: apm
---

# Reporter configuration options [config-reporter]



## `secret_token` [config-secret-token]

This string is used to ensure that only your agents can send data to your APM server.

Both the agents and the APM server have to be configured with the same secret token. Use if APM Server requires a token.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.secret_token` | `secret_token` | `ELASTIC_APM_SECRET_TOKEN` |


## `api_key` [config-api-key]

This string is used to ensure that only your agents can send data to your APM server.

Agents can use API keys as a replacement of secret token, APM server can have multiple API keys. When both secret token and API key are used, API key has priority and secret token is ignored. Use if APM Server requires an API key.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.api_key` | `api_key` | `ELASTIC_APM_API_KEY` |


## `server_url` [config-server-url]

The URL must be fully qualified, including protocol (http or https) and port.

If SSL is enabled on the APM Server, use the `https` protocol. For more information, see [SSL/TLS communication with APM Server](/reference/ssl-configuration.md).

If outgoing HTTP traffic has to go through a proxy, you can use the Java system properties `http.proxyHost` and `http.proxyPort` to set that up. See also [Java’s proxy documentation](https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.md) for more information.

::::{note}
This configuration can only be reloaded dynamically as of 1.8.0
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `http://127.0.0.1:8200` | URL | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.server_url` | `server_url` | `ELASTIC_APM_SERVER_URL` |


## `server_urls` [config-server-urls]

The URLs must be fully qualified, including protocol (http or https) and port.

Fails over to the next APM Server URL in the event of connection errors. Achieves load-balancing by shuffling the list of configured URLs. When multiple agents are active, they’ll tend towards spreading evenly across the set of servers due to randomization.

If SSL is enabled on the APM Server, use the `https` protocol. For more information, see [SSL/TLS communication with APM Server](/reference/ssl-configuration.md).

If outgoing HTTP traffic has to go through a proxy, you can use the Java system properties `http.proxyHost` and `http.proxyPort` to set that up. See also [Java’s proxy documentation](https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.md) for more information.

::::{note}
This configuration is specific to the Java agent and does not align with any other APM agent. In order to use a cross-agent config, use [`server_url`](#config-server-url) instead, which is the recommended option regardless if you are only setting a single URL.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.server_urls` | `server_urls` | `ELASTIC_APM_SERVER_URLS` |


## `disable_send` [config-disable-send]

If set to `true`, the agent will work as usual, except from any task requiring communication with the APM server. Events will be dropped and the agent won’t be able to receive central configuration, which means that any other configuration cannot be changed in this state without restarting the service. An example use case for this would be maintaining the ability to create traces and log trace/transaction/span IDs through the log correlation feature, without setting up an APM Server.

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.disable_send` | `disable_send` | `ELASTIC_APM_DISABLE_SEND` |


## `server_timeout` [config-server-timeout]

If a request to the APM server takes longer than the configured timeout, the request is cancelled and the event (exception or transaction) is discarded. Set to 0 to disable timeouts.

::::{warning}
If timeouts are disabled or set to a high value, your app could experience memory issues if the APM server times out.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Supports the duration suffixes `ms`, `s` and `m`. Example: `5s`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `5s` | TimeDuration | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.server_timeout` | `server_timeout` | `ELASTIC_APM_SERVER_TIMEOUT` |


## `verify_server_cert` [config-verify-server-cert]

By default, the agent verifies the SSL certificate if you use an HTTPS connection to the APM server.

Verification can be disabled by changing this setting to false.

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.verify_server_cert` | `verify_server_cert` | `ELASTIC_APM_VERIFY_SERVER_CERT` |


## `max_queue_size` [config-max-queue-size]

The maximum size of buffered events.

Events like transactions and spans are buffered when the agent can’t keep up with sending them to the APM Server or if the APM server is down.

If the queue is full, events are rejected which means you will lose transactions and spans in that case. This guards the application from crashing in case the APM server is unavailable for a longer period of time.

A lower value will decrease the heap overhead of the agent, while a higher value makes it less likely to lose events in case of a temporary spike in throughput.

| Default | Type | Dynamic |
| --- | --- | --- |
| `512` | Integer | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.max_queue_size` | `max_queue_size` | `ELASTIC_APM_MAX_QUEUE_SIZE` |


## `include_process_args` [config-include-process-args]

Whether each transaction should have the process arguments attached. Disabled by default to save disk space.

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.include_process_args` | `include_process_args` | `ELASTIC_APM_INCLUDE_PROCESS_ARGS` |


## `api_request_time` [config-api-request-time]

Maximum time to keep an HTTP request to the APM Server open for.

::::{note}
This value has to be lower than the APM Server’s `read_timeout` setting.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Supports the duration suffixes `ms`, `s` and `m`. Example: `10s`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `10s` | TimeDuration | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.api_request_time` | `api_request_time` | `ELASTIC_APM_API_REQUEST_TIME` |


## `api_request_size` [config-api-request-size]

The maximum total compressed size of the request body which is sent to the APM server intake api via a chunked encoding (HTTP streaming). Note that a small overshoot is possible.

Allowed byte units are `b`, `kb` and `mb`. `1kb` is equal to `1024b`.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `768kb` | ByteValue | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.api_request_size` | `api_request_size` | `ELASTIC_APM_API_REQUEST_SIZE` |


## `metrics_interval` ([1.3.0]) [config-metrics-interval]

The interval at which the agent sends metrics to the APM Server, rounded down to the nearest second (ie 3783ms would be applied as 3000ms). If there is an interval (step) defined in the Meter, that interval (to the nearest second) will instead be used, for that Meter. If the Meter step interval is less than 1 second, the meter will not be reported. Must be at least `1s`. Set to `0s` to deactivate.

Supports the duration suffixes `ms`, `s` and `m`. Example: `30s`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `30s` | TimeDuration | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.metrics_interval` | `metrics_interval` | `ELASTIC_APM_METRICS_INTERVAL` |


## `disable_metrics` ([1.3.0]) [config-disable-metrics]

Disables the collection of certain metrics. If the name of a metric matches any of the wildcard expressions, it will not be collected. Example: `foo.*,bar.*`

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | List | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.disable_metrics` | `disable_metrics` | `ELASTIC_APM_DISABLE_METRICS` |

