---
navigation_title: "HTTP"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-http.html
---

# HTTP configuration options [config-http]



## `capture_body_content_types` ([1.5.0] performance) [config-capture-body-content-types]

Configures which content types should be recorded.

The defaults end with a wildcard so that content types like `text/plain; charset=utf-8` are captured as well.

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `application/x-www-form-urlencoded*, text/*, application/json*, application/xml*` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.capture_body_content_types` | `capture_body_content_types` | `ELASTIC_APM_CAPTURE_BODY_CONTENT_TYPES` |


## `transaction_ignore_urls` [config-transaction-ignore-urls]

Used to restrict requests to certain URLs from being instrumented.

This property should be set to an array containing one or more strings. When an incoming HTTP request is detected, its URL will be tested against each element in this list.

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `/VAADIN/*, /heartbeat*, /favicon.ico, *.js, *.css, *.jpg, *.jpeg, *.png, *.gif, *.webp, *.svg, *.woff, *.woff2` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.transaction_ignore_urls` | `transaction_ignore_urls` | `ELASTIC_APM_TRANSACTION_IGNORE_URLS` |


## `transaction_ignore_user_agents` ([1.22.0]) [config-transaction-ignore-user-agents]

Used to restrict requests from certain User-Agents from being instrumented.

When an incoming HTTP request is detected, the User-Agent from the request headers will be tested against each element in this list. Example: `curl/*`, `*pingdom*`

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.transaction_ignore_user_agents` | `transaction_ignore_user_agents` | `ELASTIC_APM_TRANSACTION_IGNORE_USER_AGENTS` |


## `use_path_as_transaction_name` ([1.0.0]) [config-use-path-as-transaction-name]

If set to `true`, transaction names of unsupported or partially-supported frameworks will be in the form of `$method $path` instead of just `$method unknown route`.

::::{warning}
If your URLs contain path parameters like `/user/$userId`, you should be very careful when enabling this flag, as it can lead to an explosion of transaction groups. Take a look at the [`transaction_name_groups`](/reference/config-core.md#config-transaction-name-groups) option on how to mitigate this problem by grouping URLs together.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.use_path_as_transaction_name` | `use_path_as_transaction_name` | `ELASTIC_APM_USE_PATH_AS_TRANSACTION_NAME` |


## `url_groups` (deprecated) [config-url-groups]

Deprecated in favor of [`transaction_name_groups`](/reference/config-core.md#config-transaction-name-groups).

This option is only considered, when `use_path_as_transaction_name` is active.

With this option, you can group several URL paths together by using a wildcard expression like `/user/*`.

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.url_groups` | `url_groups` | `ELASTIC_APM_URL_GROUPS` |


## `capture_http_client_request_body_size` ([1.52.0] experimental) [config-capture-http-client-request-body-size]

::::{note}
This feature is currently experimental, which means it is disabled by default and it is not guaranteed to be backwards compatible in future releases.
::::

Configures that the first n bytes of http-client request bodies shall be captured.
Note that only request bodies will be captured for content types matching the [`transaction_name_groups`](/reference/config-core.md#config-transaction-name-groups) configuration.
A value of 0 disables body capturing. Note that even if this option is configured higher, the maximum amount of decoded characters will still be limited by the value of the [`long_field_max_length`](/reference/config-core.md#config-long-field-max-length) option.

Currently only support for Apache Http Client v4 and v5, HttpUrlConnection, Spring Webflux WebClient and other frameworks building on top of these (e.g. Spring RestTemplate).

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `0` | Integer | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.capture_http_client_request_body_size` | `capture_http_client_request_body_size` | `ELASTIC_APM_CAPTURE_HTTP_CLIENT_REQUEST_BODY_SIZE` |


## `capture_http_client_request_body_as_label` ([1.54.0]) [config-capture-http-client-request-body-as-label]

::::{note}
This feature is currently experimental, which means it is disabled by default and it is not guaranteed to be backwards compatible in future releases.
::::

If `capture_http_client_request_body_size` is configured, by default the request body will be stored in the `http.request.body.orginal` field.
This requires APM-server version 8.18+.
For compatibility with older APM-server versions, this option can be set to `true`, which will make the agent store the body in the `labels.http_request_body_content` field instead.
Note that in this case only a maximum of 1000 characters are supported.

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | false |

| Java System Properties | Property file | Environment                                             |
| --- | --- |---------------------------------------------------------|
| `elastic.apm.capture_http_client_request_body_as_label` | `capture_http_client_request_body_as_label` | `ELASTIC_APM_CAPTURE_HTTP_CLIENT_REQUEST_BODY_AS_LABEL` |
