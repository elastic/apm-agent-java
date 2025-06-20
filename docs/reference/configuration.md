---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/configuration.html
---

# Configuration [configuration]

To adapt the Elastic APM Java agent to your needs, you can configure it using one of the methods below, listed in descending order of precedence:

1) [Central configuration](docs-content://solutions/observability/apm/apm-agent-central-configuration.md)
:   Configure the Agent in the Kibana APM app. [![dynamic config](images/dynamic-config.svg "") ](#configuration-dynamic)

2) Properties file
:   The `elasticapm.properties` file is located in the same folder as the agent jar, or provided through the [`config_file`](/reference/config-core.md#config-config-file) option. ![dynamic config](images/dynamic-config.svg "")

3) Java system properties
:   All configuration keys are prefixed with `elastic.apm.`<br> ![dynamic config](images/dynamic-config.svg "")

4) Environment variables
:   All configuration keys are in uppercase and prefixed with `ELASTIC_APM_`.

5) Runtime attach parameters
:   1. `--config` parameter.<br> See [Automatic setup with `apm-agent-attach-cli.jar`](/reference/setup-attach-cli.md).
2. Arguments of `ElasticApmAttacher.attach(...)`.<br> See [Programmatic API setup to self-attach](/reference/setup-attach-api.md).
3. `elasticapm.properties` in classpath root with `ElasticApmAttacher.attach()`.<br> See [Programmatic API setup to self-attach](/reference/setup-attach-api.md).


6) Default values
:   Defined for each configuration.


## Dynamic configuration ![dynamic config](images/dynamic-config.svg "") [configuration-dynamic]

Configuration options marked with Dynamic true can be changed at runtime when set from supported sources:

* [Central configuration](docs-content://solutions/observability/apm/apm-agent-central-configuration.md)
* `elasticapm.properties` file
* Java system properties, but only when set from within the application

::::{note}
There are two distinct ways to use `elasticapm.properties`: as an external configuration file, and as a classpath resource.<br> Only the external file can be used for dynamic configuration.
::::



## Minimal configuration [configuration-minimal]

In order to get started with Elastic APM, the most important configuration options are [`service_name`](/reference/config-core.md#config-service-name), [`server_url`](/reference/config-reporter.md#config-server-url) and [`application_packages`](/reference/config-stacktrace.md#config-application-packages). Note that even these settings are optional. Click on their name to see how the default values are determined.

An example configuration looks like this:

```bash
-Delastic.apm.service_name=my-cool-service
-Delastic.apm.application_packages=org.example,org.another.example
-Delastic.apm.server_url=http://127.0.0.1:8200
```

```properties
service_name=my-cool-service
application_packages=org.example,org.another.example
server_url=http://127.0.0.1:8200
```

```bash
ELASTIC_APM_SERVICE_NAME=my-cool-service
ELASTIC_APM_APPLICATION_PACKAGES=org.example,org.another.example
ELASTIC_APM_SERVER_URL=http://127.0.0.1:8200
```


## Option reference [_option_reference]

This is a list of all configuration options grouped by their category. Click on a key to get more information.

* [Circuit-Breaker](/reference/config-circuit-breaker.md)

    * [`circuit_breaker_enabled` ([1.14.0] performance)](/reference/config-circuit-breaker.md#config-circuit-breaker-enabled)
    * [`stress_monitoring_interval` (performance)](/reference/config-circuit-breaker.md#config-stress-monitoring-interval)
    * [`stress_monitor_gc_stress_threshold` (performance)](/reference/config-circuit-breaker.md#config-stress-monitor-gc-stress-threshold)
    * [`stress_monitor_gc_relief_threshold` (performance)](/reference/config-circuit-breaker.md#config-stress-monitor-gc-relief-threshold)
    * [`stress_monitor_cpu_duration_threshold` (performance)](/reference/config-circuit-breaker.md#config-stress-monitor-cpu-duration-threshold)
    * [`stress_monitor_system_cpu_stress_threshold` (performance)](/reference/config-circuit-breaker.md#config-stress-monitor-system-cpu-stress-threshold)
    * [`stress_monitor_system_cpu_relief_threshold` (performance)](/reference/config-circuit-breaker.md#config-stress-monitor-system-cpu-relief-threshold)

* [Core](/reference/config-core.md)

    * [`recording` ([1.15.0])](/reference/config-core.md#config-recording)
    * [`enabled` ([1.18.0])](/reference/config-core.md#config-enabled)
    * [`instrument` ([1.0.0])](/reference/config-core.md#config-instrument)
    * [`service_name`](/reference/config-core.md#config-service-name)
    * [`service_node_name` ([1.11.0])](/reference/config-core.md#config-service-node-name)
    * [`service_version`](/reference/config-core.md#config-service-version)
    * [`hostname` ([1.10.0])](/reference/config-core.md#config-hostname)
    * [`environment`](/reference/config-core.md#config-environment)
    * [`transaction_sample_rate` (performance)](/reference/config-core.md#config-transaction-sample-rate)
    * [`transaction_max_spans` (performance)](/reference/config-core.md#config-transaction-max-spans)
    * [`long_field_max_length` (performance [1.37.0])](/reference/config-core.md#config-long-field-max-length)
    * [`sanitize_field_names` (security)](/reference/config-core.md#config-sanitize-field-names)
    * [`enable_instrumentations` ([1.28.0])](/reference/config-core.md#config-enable-instrumentations)
    * [`disable_instrumentations` ([1.0.0])](/reference/config-core.md#config-disable-instrumentations)
    * [`enable_experimental_instrumentations` ([1.25.0])](/reference/config-core.md#config-enable-experimental-instrumentations)
    * [`unnest_exceptions`](/reference/config-core.md#config-unnest-exceptions)
    * [`ignore_exceptions` ([1.11.0])](/reference/config-core.md#config-ignore-exceptions)
    * [`capture_body` (performance)](/reference/config-core.md#config-capture-body)
    * [`capture_headers` (performance)](/reference/config-core.md#config-capture-headers)
    * [`global_labels` ([1.7.0])](/reference/config-core.md#config-global-labels)
    * [`instrument_ancient_bytecode` ([1.35.0])](/reference/config-core.md#config-instrument-ancient-bytecode)
    * [`context_propagation_only` ([1.44.0])](/reference/config-core.md#config-context-propagation-only)
    * [`classes_excluded_from_instrumentation`](/reference/config-core.md#config-classes-excluded-from-instrumentation)
    * [`trace_methods` ([1.0.0])](/reference/config-core.md#config-trace-methods)
    * [`trace_methods_duration_threshold` ([1.7.0])](/reference/config-core.md#config-trace-methods-duration-threshold)
    * [`central_config` ([1.8.0])](/reference/config-core.md#config-central-config)
    * [`breakdown_metrics` ([1.8.0])](/reference/config-core.md#config-breakdown-metrics)
    * [`config_file` ([1.8.0])](/reference/config-core.md#config-config-file)
    * [`plugins_dir` (experimental)](/reference/config-core.md#config-plugins-dir)
    * [`use_elastic_traceparent_header` ([1.14.0])](/reference/config-core.md#config-use-elastic-traceparent-header)
    * [`disable_outgoing_tracecontext_headers` ([1.37.0])](/reference/config-core.md#config-disable-outgoing-tracecontext-headers)
    * [`span_min_duration` ([1.16.0])](/reference/config-core.md#config-span-min-duration)
    * [`cloud_provider` ([1.21.0])](/reference/config-core.md#config-cloud-provider)
    * [`enable_public_api_annotation_inheritance` (performance)](/reference/config-core.md#config-enable-public-api-annotation-inheritance)
    * [`transaction_name_groups` ([1.33.0])](/reference/config-core.md#config-transaction-name-groups)
    * [`trace_continuation_strategy` ([1.34.0])](/reference/config-core.md#config-trace-continuation-strategy)
    * [`baggage_to_attach` ([1.43.0])](/reference/config-core.md#config-baggage-to-attach)

* [Datastore](/reference/config-datastore.md)

    * [`elasticsearch_capture_body_urls` ([1.37.0])](/reference/config-datastore.md#config-elasticsearch-capture-body-urls)
    * [`mongodb_capture_statement_commands`](/reference/config-datastore.md#config-mongodb-capture-statement-commands)

* [HTTP](/reference/config-http.md)

    * [`capture_body_content_types` ([1.5.0] performance)](/reference/config-http.md#config-capture-body-content-types)
    * [`transaction_ignore_urls`](/reference/config-http.md#config-transaction-ignore-urls)
    * [`transaction_ignore_user_agents` ([1.22.0])](/reference/config-http.md#config-transaction-ignore-user-agents)
    * [`use_path_as_transaction_name` ([1.0.0])](/reference/config-http.md#config-use-path-as-transaction-name)
    * [`url_groups` (deprecated)](/reference/config-http.md#config-url-groups)
    * [`capture_http_client_request_body_size` ([1.52.0] experimental)](/reference/config-http.md#config-capture-http-client-request-body-size)
    * [`capture_http_client_request_body_as_label` ([1.54.0] experimental)](/reference/config-http.md#config-capture-http-client-request-body-as-label)

* [Huge Traces](/reference/config-huge-traces.md)

    * [`span_compression_enabled` ([1.30.0])](/reference/config-huge-traces.md#config-span-compression-enabled)
    * [`span_compression_exact_match_max_duration` ([1.30.0])](/reference/config-huge-traces.md#config-span-compression-exact-match-max-duration)
    * [`span_compression_same_kind_max_duration` ([1.30.0])](/reference/config-huge-traces.md#config-span-compression-same-kind-max-duration)
    * [`exit_span_min_duration` ([1.30.0])](/reference/config-huge-traces.md#config-exit-span-min-duration)

* [JAX-RS](/reference/config-jax-rs.md)

    * [`enable_jaxrs_annotation_inheritance` (performance)](/reference/config-jax-rs.md#config-enable-jaxrs-annotation-inheritance)
    * [`use_jaxrs_path_as_transaction_name` ([1.8.0])](/reference/config-jax-rs.md#config-use-jaxrs-path-as-transaction-name)

* [JMX](/reference/config-jmx.md)

    * [`capture_jmx_metrics` ([1.11.0])](/reference/config-jmx.md#config-capture-jmx-metrics)

* [Logging](/reference/config-logging.md)

    * [`log_level`](/reference/config-logging.md#config-log-level)
    * [`log_file`](/reference/config-logging.md#config-log-file)
    * [`log_ecs_reformatting` ([1.22.0] experimental)](/reference/config-logging.md#config-log-ecs-reformatting)
    * [`log_ecs_reformatting_additional_fields` ([1.26.0])](/reference/config-logging.md#config-log-ecs-reformatting-additional-fields)
    * [`log_ecs_formatter_allow_list`](/reference/config-logging.md#config-log-ecs-formatter-allow-list)
    * [`log_ecs_reformatting_dir`](/reference/config-logging.md#config-log-ecs-reformatting-dir)
    * [`log_file_size` ([1.17.0])](/reference/config-logging.md#config-log-file-size)
    * [`log_format_sout` ([1.17.0])](/reference/config-logging.md#config-log-format-sout)
    * [`log_format_file` ([1.17.0])](/reference/config-logging.md#config-log-format-file)
    * [`log_sending` ([1.36.0] experimental)](/reference/config-logging.md#config-log-sending)

* [Messaging](/reference/config-messaging.md)

    * [`ignore_message_queues`](/reference/config-messaging.md#config-ignore-message-queues)
    * [`jms_listener_packages` (performance [1.36.0])](/reference/config-messaging.md#config-jms-listener-packages)
    * [`rabbitmq_naming_mode` ([1.46.0])](/reference/config-messaging.md#config-rabbitmq-naming-mode)

* [Metrics](/reference/config-metrics.md)

    * [`dedot_custom_metrics` ([1.22.0])](/reference/config-metrics.md#config-dedot-custom-metrics)
    * [`custom_metrics_histogram_boundaries` ([1.37.0] experimental)](/reference/config-metrics.md#config-custom-metrics-histogram-boundaries)
    * [`metric_set_limit` ([1.33.0])](/reference/config-metrics.md#config-metric-set-limit)
    * [`agent_reporter_health_metrics` ([1.35.0])](/reference/config-metrics.md#config-agent-reporter-health-metrics)
    * [`agent_background_overhead_metrics` ([1.35.0])](/reference/config-metrics.md#config-agent-background-overhead-metrics)

* [Profiling](/reference/config-profiling.md)

    * [`universal_profiling_integration_enabled` ([1.50.0])](/reference/config-profiling.md#config-universal-profiling-integration-enabled)
    * [`universal_profiling_integration_buffer_size` ([1.50.0])](/reference/config-profiling.md#config-universal-profiling-integration-buffer-size)
    * [`universal_profiling_integration_socket_dir` ([1.50.0])](/reference/config-profiling.md#config-universal-profiling-integration-socket-dir)
    * [`profiling_inferred_spans_enabled` ([1.15.0] experimental)](/reference/config-profiling.md#config-profiling-inferred-spans-enabled)
    * [`profiling_inferred_spans_logging_enabled` ([1.37.0])](/reference/config-profiling.md#config-profiling-inferred-spans-logging-enabled)
    * [`profiling_inferred_spans_sampling_interval` ([1.15.0])](/reference/config-profiling.md#config-profiling-inferred-spans-sampling-interval)
    * [`profiling_inferred_spans_min_duration` ([1.15.0])](/reference/config-profiling.md#config-profiling-inferred-spans-min-duration)
    * [`profiling_inferred_spans_included_classes` ([1.15.0])](/reference/config-profiling.md#config-profiling-inferred-spans-included-classes)
    * [`profiling_inferred_spans_excluded_classes` ([1.15.0])](/reference/config-profiling.md#config-profiling-inferred-spans-excluded-classes)
    * [`profiling_inferred_spans_lib_directory` ([1.18.0])](/reference/config-profiling.md#config-profiling-inferred-spans-lib-directory)

* [Reporter](/reference/config-reporter.md)

    * [`secret_token`](/reference/config-reporter.md#config-secret-token)
    * [`api_key`](/reference/config-reporter.md#config-api-key)
    * [`server_url`](/reference/config-reporter.md#config-server-url)
    * [`server_urls`](/reference/config-reporter.md#config-server-urls)
    * [`disable_send`](/reference/config-reporter.md#config-disable-send)
    * [`server_timeout`](/reference/config-reporter.md#config-server-timeout)
    * [`verify_server_cert`](/reference/config-reporter.md#config-verify-server-cert)
    * [`max_queue_size`](/reference/config-reporter.md#config-max-queue-size)
    * [`include_process_args`](/reference/config-reporter.md#config-include-process-args)
    * [`api_request_time`](/reference/config-reporter.md#config-api-request-time)
    * [`api_request_size`](/reference/config-reporter.md#config-api-request-size)
    * [`metrics_interval` ([1.3.0])](/reference/config-reporter.md#config-metrics-interval)
    * [`disable_metrics` ([1.3.0])](/reference/config-reporter.md#config-disable-metrics)

* [Serverless](/reference/config-serverless.md)

    * [`aws_lambda_handler` ([1.28.0])](/reference/config-serverless.md#config-aws-lambda-handler)
    * [`data_flush_timeout` ([1.28.0])](/reference/config-serverless.md#config-data-flush-timeout)

* [Stacktrace](/reference/config-stacktrace.md)

    * [`application_packages`](/reference/config-stacktrace.md#config-application-packages)
    * [`stack_trace_limit` (performance)](/reference/config-stacktrace.md#config-stack-trace-limit)
    * [`span_stack_trace_min_duration` (performance)](/reference/config-stacktrace.md#config-span-stack-trace-min-duration)

















