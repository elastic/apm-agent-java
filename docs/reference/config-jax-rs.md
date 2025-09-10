---
navigation_title: "JAX-RS"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-jax-rs.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# JAX-RS configuration options [config-jax-rs]



## `enable_jaxrs_annotation_inheritance` (performance) [config-enable-jaxrs-annotation-inheritance]

By default, the agent will scan for @Path annotations on the whole class hierarchy, recognizing a class as a JAX-RS resource if the class or any of its superclasses/interfaces has a class level @Path annotation. If your application does not use @Path annotation inheritance, set this property to *false* to only scan for direct @Path annotations. This can improve the startup time of the agent.

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.enable_jaxrs_annotation_inheritance` | `enable_jaxrs_annotation_inheritance` | `ELASTIC_APM_ENABLE_JAXRS_ANNOTATION_INHERITANCE` |


## `use_jaxrs_path_as_transaction_name` [config-use-jaxrs-path-as-transaction-name]

```{applies_to}
apm_agent_java: ga 1.8.0
```

By default, the agent will use `ClassName#methodName` for the transaction name of JAX-RS requests. If you want to use the URI template from the `@Path` annotation, set the value to `true`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.use_jaxrs_path_as_transaction_name` | `use_jaxrs_path_as_transaction_name` | `ELASTIC_APM_USE_JAXRS_PATH_AS_TRANSACTION_NAME` |

