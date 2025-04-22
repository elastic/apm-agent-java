---
navigation_title: "JMX"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-jmx.html
---

# JMX configuration options [config-jmx]



## `capture_jmx_metrics` ([1.11.0]) [config-capture-jmx-metrics]

Report metrics from JMX to the APM Server

Can contain multiple comma separated JMX metric definitions:

```
object_name[<JMX object name pattern>] attribute[<JMX attribute>:metric_name=<optional metric name>]
```

* `object_name`:

    For more information about the JMX object name pattern syntax, see the [`ObjectName` Javadocs](https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.md).

* `attribute`:

    The name of the JMX attribute. The JMX value has to be either a `Number` or a composite where the composite items are numbers. This element can be defined multiple times. An attribute can contain optional properties. The syntax for that is the same as for [`ObjectName`](https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.md).

    * `metric_name`:

        A property within `attribute`. This is the name under which the metric will be stored. Setting this is optional and will be the same as the `attribute` if not set. Note that all JMX metric names will be prefixed with `jvm.jmx.` by the agent.


The agent creates `labels` for each [JMX key property](https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.md#getKeyPropertyList()) such as `type` and `name`.

The [JMX object name pattern](https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.md) supports wildcards. The attribute definition does NOT support wildcards, but a special definition `attribute[*]` is accepted (from 1.44.0) to mean match all possible (numeric) attributes for the associated object name pattern The definition `object_name[*:type=*,name=*] attribute[*]` would match all possible JMX metrics In the following example, the agent will create a metricset for each memory pool `name` (such as `G1 Old Generation` and `G1 Young Generation`)

```
object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count] attribute[CollectionTime]
```

The resulting documents in Elasticsearch look similar to these (metadata omitted for brevity):

```json
{
  "@timestamp": "2019-08-20T16:51:07.512Z",
  "jvm": {
    "jmx": {
      "collection_count": 0,
      "CollectionTime":   0
    }
  },
  "labels": {
    "type": "GarbageCollector",
    "name": "G1 Old Generation"
  }
}
```

```json
{
  "@timestamp": "2019-08-20T16:51:07.512Z",
  "jvm": {
    "jmx": {
      "collection_count": 2,
      "CollectionTime":  11
    }
  },
  "labels": {
    "type": "GarbageCollector",
    "name": "G1 Young Generation"
  }
}
```

The agent also supports composite values for the attribute value. In this example, `HeapMemoryUsage` is a composite value, consisting of `committed`, `init`, `used` and `max`.

```
object_name[java.lang:type=Memory] attribute[HeapMemoryUsage:metric_name=heap]
```

The resulting documents in Elasticsearch look similar to this:

```json
{
  "@timestamp": "2019-08-20T16:51:07.512Z",
  "jvm": {
    "jmx": {
      "heap": {
        "max":      4294967296,
        "init":      268435456,
        "committed": 268435456,
        "used":       22404496
      }
    }
  },
  "labels": {
    "type": "Memory"
  }
}
```

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.capture_jmx_metrics` | `capture_jmx_metrics` | `ELASTIC_APM_CAPTURE_JMX_METRICS` |

