---
navigation_title: "Datastore"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-datastore.html
---

# Datastore configuration options [config-datastore]



## `elasticsearch_capture_body_urls` ([1.37.0]) [config-elasticsearch-capture-body-urls]

The URL path patterns for which the APM agent will capture the request body of outgoing requests to Elasticsearch made with the `elasticsearch-restclient` instrumentation. The default setting captures the body for Elasticsearch REST APIs searches and counts.

The captured request body (if any) is stored on the `span.db.statement` field. Captured request bodies are truncated to a maximum length defined by [`long_field_max_length` (performance [1.37.0])](/reference/config-core.md#config-long-field-max-length). This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `*_search, *_msearch, *_msearch/template, *_search/template, *_count, *_sql, *_eql/search, *_async_search` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.elasticsearch_capture_body_urls` | `elasticsearch_capture_body_urls` | `ELASTIC_APM_ELASTICSEARCH_CAPTURE_BODY_URLS` |


## `mongodb_capture_statement_commands` [config-mongodb-capture-statement-commands]

MongoDB command names for which the command document will be captured, limited to common read-only operations by default. Set to ` ""` (empty) to disable capture, and `"*"` to capture all (which is discouraged as it may lead to sensitive information capture).

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `find, aggregate, count, distinct, mapReduce` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.mongodb_capture_statement_commands` | `mongodb_capture_statement_commands` | `ELASTIC_APM_MONGODB_CAPTURE_STATEMENT_COMMANDS` |

