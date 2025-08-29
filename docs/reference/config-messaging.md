---
navigation_title: "Messaging"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-messaging.html
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

# Messaging configuration options [config-messaging]



## `ignore_message_queues` [config-ignore-message-queues]

Used to filter out specific messaging queues/topics from being traced.

This property should be set to an array containing one or more strings. When set, sends-to and receives-from the specified queues/topic will be ignored.

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.ignore_message_queues` | `ignore_message_queues` | `ELASTIC_APM_IGNORE_MESSAGE_QUEUES` |


## `jms_listener_packages` (performance) [config-jms-listener-packages]

```{applies_to}
apm_agent_java: ga 1.36.0
```

Defines which packages contain JMS MessageListener implementations for instrumentation. When empty (default), all inner-classes or any classes that have *Listener* or *Message* in their names are considered.

This configuration option helps to make MessageListener type matching faster and improve application startup performance.

Starting from version 1.43.0, the classes that are part of the *application_packages* option are also included in the list of classes considered.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | Collection | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.jms_listener_packages` | `jms_listener_packages` | `ELASTIC_APM_JMS_LISTENER_PACKAGES` |


## `rabbitmq_naming_mode` [config-rabbitmq-naming-mode]

```{applies_to}
apm_agent_java: ga 1.46.0
```

Defines whether the agent should use the exchanges, the routing key or the queue for the naming of RabbitMQ Transactions. Valid options are `QUEUE`, `ROUTING_KEY` and `EXCHANGE`. Note that `QUEUE` only works when using RabbitMQ via spring-amqp and `ROUTING_KEY` only works for the non spring-client.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Valid options: `EXCHANGE`, `QUEUE`, `ROUTING_KEY`

| Default | Type | Dynamic |
| --- | --- | --- |
| `EXCHANGE` | RabbitMQNamingMode | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.rabbitmq_naming_mode` | `rabbitmq_naming_mode` | `ELASTIC_APM_RABBITMQ_NAMING_MODE` |

