---
navigation_title: "Elastic APM Java Agent"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/release-notes-1.x.html
  - https://www.elastic.co/guide/en/apm/agent/java/current/release-notes.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Elastic APM Java Agent release notes [elastic-apm-java-agent-release-notes]

Review the changes, fixes, and more in each version of Elastic APM Java Agent.

To check for security updates, go to [Security announcements for the Elastic stack](https://discuss.elastic.co/c/announcements/security-announcements/31).

% Release notes includes only features, enhancements, and fixes. Add breaking changes, deprecations, and known issues to the applicable release notes sections.

% ## version.next [elastic-apm-java-agent-versionext-release-notes]
% **Release date:** Month day, year

% ### Features and enhancements [elastic-apm-java-agent-versionext-features-enhancements]

% ### Fixes [elastic-apm-java-agent-versionext-fixes]

## 1.55.1 [elastic-apm-java-agent-1-55-1-release-notes]
**Release date:** September 16, 2025

### Fixes [elastic-apm-java-agent-1-55-1-fixes]
* Fix async httpclient 5.x instrumentation - [#4185](https://github.com/elastic/apm-agent-java/pull/4185)
* Prevent `FileSystemAlreadyExistsException` on single-jar application startup - [#4204](https://github.com/elastic/apm-agent-java/pull/4204)
* Improve SQS async transaction handling to be better with flux flow - [#4205](https://github.com/elastic/apm-agent-java/pull/4205)

## 1.55.0 [elastic-apm-java-agent-1-55-0-release-notes]
**Release date:** July 15, 2025

### Features and enhancements [elastic-apm-java-agent-1-55-0-features-enhancements]
* Add support for Mongodb 5.x instrumentation - [#4139](https://github.com/elastic/apm-agent-java/pull/4139)

### Fixes [elastic-apm-java-agent-1-55-0-fixes]
* Prevent potential memory pressure by limiting OpenTelemetry metrics bridge attribute cache sizes - [#4123](https://github.com/elastic/apm-agent-java/pull/4123)
* Fix `NoSuchMethodError` for Kafka clients - [#4136](https://github.com/elastic/apm-agent-java/pull/4136)

## 1.54.0 [elastic-apm-java-agent-1-54-0-release-notes]
**Release date:** May 27, 2025

### Features and enhancements [elastic-apm-java-agent-1-54-0-features-enhancements]
* Remove 1000 character limit for HTTP client body capturing  - [#1234](https://github.com/elastic/apm-agent-java/pull/4058)

### Fixes [elastic-apm-java-agent-1-54-0-fixes]
* Added missing java 17 and 21 compatible runtimes for published lambda layers - [#4088](https://github.com/elastic/apm-agent-java/pull/4088)

## 1.53.0 [elastic-apm-java-agent-1-53-0-release-notes]
**Release date:** April 2, 2025

### Features and enhancements [elastic-apm-java-agent-1-53-0-features-enhancements]
* Add internal option to capture thread id/name as labels - [#4014](https://github.com/elastic/apm-agent-java/pull/4014)

## 1.52.2 [elastic-apm-java-agent-1-52-2-release-notes]
**Release date:** February 27, 2025

### Fixes [elastic-apm-java-agent-1-52-2-fixes]
* Prevent NPE in OpenTelemetry metrics bridge in case of asynchronous agent start - [#3880](https://github.com/elastic/apm-agent-java/pull/3880)
* Fix random Weblogic ClassNotFoundException related to thread context classloader - [#3870](https://github.com/elastic/apm-agent-java/pull/3870)
* Skips using NOFOLLOW_LINKS file open option when running on OS/400 as it’s unsupported there - [#3905](https://github.com/elastic/apm-agent-java/pull/3905)
* Add framework name and version for Spring Webflux transactions - [#3936](https://github.com/elastic/apm-agent-java/pull/3936)

## 1.52.1 [elastic-apm-java-agent-1-52-1-release-notes]
**Release date:** November 18, 2024

### Fixes [elastic-apm-java-agent-1-52-1-fixes]
* Fix JMX metric warning message about unsupported composite value types - [#3849](https://github.com/elastic/apm-agent-java/pull/3849)
* Fix JAX-WS transaction naming for @WebMethod annotated methods - [#3850](https://github.com/elastic/apm-agent-java/pull/3850)

## 1.52.0 [elastic-apm-java-agent-1-52-0-release-notes]
**Release date:** September 23, 2024

### Features and enhancements [elastic-apm-java-agent-1-52-0-features-enhancements]
* Added experimental option to capture HTTP client request bodies for Apache Http Client v4 and v5, HttpUrlConnection and Spring WebClient - [#3776](https://github.com/elastic/apm-agent-java/pull/3776), [#3962](https://github.com/elastic/apm-agent-java/pull/3962), [#3724](https://github.com/elastic/apm-agent-java/pull/3724), [#3754](https://github.com/elastic/apm-agent-java/pull/3754), [#3767](https://github.com/elastic/apm-agent-java/pull/3767)
* Agent health metrics now GA - [#3802](https://github.com/elastic/apm-agent-java/pull/3802)

### Fixes [elastic-apm-java-agent-1-52-0-fixes]
* Fix log4j2 log correlation with shaded application jar - [#3764](https://github.com/elastic/apm-agent-java/pull/3764)
* Improve automatic span class name detection for Scala and nested/anonymous classes - [#3746](https://github.com/elastic/apm-agent-java/pull/3746)

## 1.51.0 [elastic-apm-java-agent-1-51-0-release-notes]
**Release date:** July 24, 2024

### Features and enhancements [elastic-apm-java-agent-1-51-0-features-enhancements]
* Added option to make routing-key part of RabbitMQ transaction/span names - [#3636](https://github.com/elastic/apm-agent-java/pull/3636)
* Added internal option for capturing request bodies for apache httpclient v4 - [#3692](https://github.com/elastic/apm-agent-java/pull/3692)
* Added automatic module name to apm-agent-attach - [#3743](https://github.com/elastic/apm-agent-java/pull/3743)

### Fixes [elastic-apm-java-agent-1-51-0-fixes]
* Restore compatibility with Java 7 - [#3657](https://github.com/elastic/apm-agent-java/pull/3657)
* Avoid `ClassCastException` and issue warning when trying to use otel span links - [#3672](https://github.com/elastic/apm-agent-java/pull/3672)
* Avoid `NullPointerException` with runtime attach API and invalid map entries - [#3712](https://github.com/elastic/apm-agent-java/pull/3712)
* Enhance invalid state JMX metrics handling - [#3713](https://github.com/elastic/apm-agent-java/pull/3713)
* Skips using NOFOLLOW_LINKS file open option when running on z/OS as it’s unsupported there - [#3722](https://github.com/elastic/apm-agent-java/pull/3722)

## 1.50.0 [elastic-apm-java-agent-1-50-0-release-notes]
**Release date:** May 28, 2024

### Features and enhancements [elastic-apm-java-agent-1-50-0-features-enhancements]
* Added support for correlating APM data with elastic universal profiling data - [#3615](https://github.com/elastic/apm-agent-java/pull/3615), [#3602](https://github.com/elastic/apm-agent-java/pull/3602), [#3607](https://github.com/elastic/apm-agent-java/pull/3607), [#3598](https://github.com/elastic/apm-agent-java/pull/3598)
* Excluded latest AppDynamics packages from instrumentation (`com.cisco.mtagent.*`) - [#3632](https://github.com/elastic/apm-agent-java/pull/3632)

### Fixes [elastic-apm-java-agent-1-50-0-fixes]
* Fixed edge case where inferred spans could cause cycles in the trace parent-child relationships, subsequently resulting in the UI crashing - [#3588](https://github.com/elastic/apm-agent-java/pull/3588)
* Fix NPE in dropped spans statistics - [#3590](https://github.com/elastic/apm-agent-java/pull/3590)
* Fix too small activation stack size for small `transaction_max_spans` values - [#3643](https://github.com/elastic/apm-agent-java/pull/3643)

## 1.49.0 [elastic-apm-java-agent-1-49-0-release-notes]
**Release date:** April 2, 2024

### Features and enhancements [elastic-apm-java-agent-1-49-0-features-enhancements]
* Differentiate Lambda URLs from API Gateway in AWS Lambda integration - [#3417](https://github.com/elastic/apm-agent-java/pull/3417)
* Added lambda support for ELB triggers [#3411](https://github.com/elastic/apm-agent-java/pull/#3411)
* Add exclusion list option for calling DatabaseMetaData.getUserName - [#3568](https://github.com/elastic/apm-agent-java/pull/#3568)

### Fixes [elastic-apm-java-agent-1-49-0-fixes]
* Fixed problems with public API annotation inheritance - [#3551](https://github.com/elastic/apm-agent-java/pull/3551)

## 1.48.1 [elastic-apm-java-agent-1-48-1-release-notes]
**Release date:** March 6, 2024

### Fixes [elastic-apm-java-agent-1-48-1-fixes]
* Avoid another case where we might touch application exceptions for `safe_exceptions` - [#3553](https://github.com/elastic/apm-agent-java/pull/3553)
* More robust hostname detection on Windows - [#3556](https://github.com/elastic/apm-agent-java/pull/3556)

## 1.48.0 [elastic-apm-java-agent-1-48-0-release-notes]
**Release date:** March 5, 2024

### Features and enhancements [elastic-apm-java-agent-1-48-0-features-enhancements]
* Bumped base alpine docker image version - [#3524](https://github.com/elastic/apm-agent-java/pull/3524)
* Replace statement parser cache with an LRU cache to improve efficiency in certain cases [#3492](https://github.com/elastic/apm-agent-java/pull/3492)

### Fixes [elastic-apm-java-agent-1-48-0-fixes]
* Added missing support for TracerBuilder in OpenTelemetry bridge - [#3535](https://github.com/elastic/apm-agent-java/pull/3535)
* Fixed some locations to not touch exceptions when `safe_exception` is configured - [#3543](https://github.com/elastic/apm-agent-java/pull/3543)

## 1.47.1 [elastic-apm-java-agent-1-47-1-release-notes]
**Release date:** February 15, 2024

### Features and enhancements [elastic-apm-java-agent-1-47-1-features-enhancements]
* Added internal `safe_exceptions` config option to workaround JVM bugs related to touching exceptions - [#3528](https://github.com/elastic/apm-agent-java/pull/3528)

### Fixes [elastic-apm-java-agent-1-47-1-fixes]
* Cleanup extra servlet request attribute used for Spring exception handler - [#3527](https://github.com/elastic/apm-agent-java/pull/3527)

## 1.47.0 [elastic-apm-java-agent-1-47-0-release-notes]
**Release date:** February 13, 2024

### Features and enhancements [elastic-apm-java-agent-1-47-0-features-enhancements]
* Added a configuration option to use queues in names of spring-rabbit transactions - [#3424](https://github.com/elastic/apm-agent-java/pull/3424)
* Add option to retry JMX metrics capture in case of exception - [#3511](https://github.com/elastic/apm-agent-java/pull/3511)

### Fixes [elastic-apm-java-agent-1-47-0-fixes]
* Add support to CLI attach download for new agent signature for 1.46.0+ - [#3513](https://github.com/elastic/apm-agent-java/pull/3513)

## 1.46.0 [elastic-apm-java-agent-1-46-0-release-notes]
**Release date:** January 29, 2024

### Features and enhancements [elastic-apm-java-agent-1-46-0-features-enhancements]
* Added support for OpenTelementry Attributes db.statement and db.user - [#3475](https://github.com/elastic/apm-agent-java/pull/3475)

### Fixes [elastic-apm-java-agent-1-46-0-fixes]
* Fixed NPE in ApacheHttpClientApiAdapter#getHostName - [#3479](https://github.com/elastic/apm-agent-java/pull/3479)
* Fix span stack trace when combined with span compression - [#3474](https://github.com/elastic/apm-agent-java/pull/3474)
* Fix `UnsupportedClassVersionError` java 7 compatibility for jctools - [#3483](https://github.com/elastic/apm-agent-java/pull/3483)

## 1.45.0 [elastic-apm-java-agent-1-45-0-release-notes]
**Release date:** December 7, 2023

### Features and enhancements [elastic-apm-java-agent-1-45-0-features-enhancements]
* Added support for OpenTelemetry annotations - `WithSpan` and `SpanAttribute` - [#3406](https://github.com/elastic/apm-agent-java/pull/3406)
* Only automatically apply redacted exceptions for Corretto JVM 17-20. Outside that, user should use capture_exception_details=false to workaround the JVM race-condition bug if it gets triggered: [#3438](https://github.com/elastic/apm-agent-java/pull/3438)
* Added support for Spring 6.1 / Spring-Boot 3.2 - [#3440](https://github.com/elastic/apm-agent-java/pull/3440)
* Add support for Apache HTTP client 5.x - [#3419](https://github.com/elastic/apm-agent-java/pull/3419)

## 1.44.0 [elastic-apm-java-agent-1-44-0-release-notes]
**Release date:** November 21, 2023

### Features and enhancements [elastic-apm-java-agent-1-44-0-features-enhancements]
* Added protection against invalid timestamps provided by manual instrumentation - [#3363](https://github.com/elastic/apm-agent-java/pull/3363)
* Added support for AWS SDK 2.21 - [#3373](https://github.com/elastic/apm-agent-java/pull/3373)
* Capture bucket and object key to Lambda transaction as OTel attributes - `aws.s3.bucket`, `aws.s3.key` - [#3364](https://github.com/elastic/apm-agent-java/pull/3364)
* Added `context_propagation_only` configuration option - [#3358](https://github.com/elastic/apm-agent-java/pull/3358)
* Added attribute[*] for JMX pattern metrics (all metrics can now be generated with `object_name[*:type=*,name=*] attribute[*]`) - [#3376](https://github.com/elastic/apm-agent-java/pull/3376)

### Fixes [elastic-apm-java-agent-1-44-0-fixes]
* Fixed too many spans being created for `HTTPUrlConnection` requests with method `HEAD` - [#3353](https://github.com/elastic/apm-agent-java/pull/3353)
* Enhance k8s container/pod and host name detection heuristics - [#3418](https://github.com/elastic/apm-agent-java/pull/3418)

## 1.43.0 [elastic-apm-java-agent-1-43-0-release-notes]
**Release date:** September 26, 2023

### Features and enhancements [elastic-apm-java-agent-1-43-0-features-enhancements]
* Add support for Elasticsearch client 8.9 - [#3283](https://github.com/elastic/apm-agent-java/pull/3283)
* Added `baggage_to_attach` config option to allow automatic lifting of baggage into transaction, span and error attributes - [#3288](https://github.com/elastic/apm-agent-java/pull/3288), [#3289](https://github.com/elastic/apm-agent-java/pull/3289)
* Exclude elasticsearch 8.10 and newer clients from instrumentation because they natively support OpenTelemetry  - [#3303](https://github.com/elastic/apm-agent-java/pull/3303)
* Switched to OpenTelemetry compatible context propagation for Kafka - [#3300](https://github.com/elastic/apm-agent-java/pull/3300)
* Changed `cloud.project.id` collected in Google Cloud (GCP) to be the `project-id` - [#3311](https://github.com/elastic/apm-agent-java/pull/3311)
* Allow running the IntelliJ debug agent in parallel - [#3315](https://github.com/elastic/apm-agent-java/pull/3315)
* Capture `span.sync` = `false` for ES restclient async spans plugins

### Fixes [elastic-apm-java-agent-1-43-0-fixes]
* Prevent bad serialization in edge cases for span compression - [#3293](https://github.com/elastic/apm-agent-java/pull/3293)
* Allow overriding of transaction type for Servlet-API transactions - [#3226](https://github.com/elastic/apm-agent-java/pull/3226)
* Fix micrometer histogram serialization - [#3290](https://github.com/elastic/apm-agent-java/pull/3290), [#3304](https://github.com/elastic/apm-agent-java/pull/3304)
* Fix transactions not being correctly handled in certain edge cases - [#3294](https://github.com/elastic/apm-agent-java/pull/3294)
* Fixed JDBC instrumentation for DB2 - [#3313](https://github.com/elastic/apm-agent-java/pull/3313)
* Fixed OpenTelemetry metrics export breaking when `instrument=false` is configured - [#3326](https://github.com/elastic/apm-agent-java/pull/3326)

## 1.42.0 [elastic-apm-java-agent-1-42-0-release-notes]
**Release date:** August 11, 2023

### Features and enhancements [elastic-apm-java-agent-1-42-0-features-enhancements]
* Virtual thread support - [#3244](https://github.com/elastic/apm-agent-java/pull/3244), [#3286](https://github.com/elastic/apm-agent-java/pull/3286)
* Include `application_packages` in JMS listener naming heuristic - [#3299](https://github.com/elastic/apm-agent-java/pull/3299)

### Fixes [elastic-apm-java-agent-1-42-0-fixes]
* Fix JVM memory usage capture - [#3279](https://github.com/elastic/apm-agent-java/pull/3279)

## 1.41.1 [elastic-apm-java-agent-1-41-1-release-notes]
**Release date:** August 7, 2023

### Features and enhancements [elastic-apm-java-agent-1-41-1-features-enhancements]
* Replaced thread-local IO buffers with pooled ones for virtual thread friendliness - [#3239](https://github.com/elastic/apm-agent-java/pull/3239)

### Fixes [elastic-apm-java-agent-1-41-1-fixes]
* Fixed Micrometer histograms to be correctly exported with non-cumulative bucket counts - [#3264](https://github.com/elastic/apm-agent-java/pull/3264)
* Fixed SQS NoClassDefFoundError in AWS SDK instrumentation for async clients - [#3266](https://github.com/elastic/apm-agent-java/pull/3266)

## 1.41.0 [elastic-apm-java-agent-1-41-0-release-notes]
**Release date:** July 31, 2023

### Features and enhancements [elastic-apm-java-agent-1-41-0-features-enhancements]
* Added W3C baggage propagation - [#3236](https://github.com/elastic/apm-agent-java/pull/3236), [#3248](https://github.com/elastic/apm-agent-java/pull/3248)
* Added support for baggage in OpenTelemetry bridge - [#3249](https://github.com/elastic/apm-agent-java/pull/3249)
* Improved span naming and attribute collection for 7.16+ elasticsearch clients - [#3157](https://github.com/elastic/apm-agent-java/pull/3157)

### Fixes [elastic-apm-java-agent-1-41-0-fixes]
* Fixed SQS NoClassDefFoundError in AWS SDK instrumentation - [#3254](https://github.com/elastic/apm-agent-java/pull/3254)
* Fixed reference counting issues in elasticsearch instrumentation - [#3256](https://github.com/elastic/apm-agent-java/pull/3256)

## 1.40.0 [elastic-apm-java-agent-1-40-0-release-notes]
**Release date:** July 19, 2023

### Features and enhancements [elastic-apm-java-agent-1-40-0-features-enhancements]
* Capture `container.id` for cgroups v2 - [#3199](https://github.com/elastic/apm-agent-java/pull/3199)

### Fixes [elastic-apm-java-agent-1-40-0-fixes]
* fix jakarta.jms support (wasn’t fully implemented) - [#3198](https://github.com/elastic/apm-agent-java/pull/3198)
* Fixed agent programmatic attach with immutable config - [#3170](https://github.com/elastic/apm-agent-java/pull/3170)
* Prevent overriding `ELASTIC_APM_AWS_LAMBDA_HANDLER` in AWS lambda execution when explicitly set - [#3205](https://github.com/elastic/apm-agent-java/pull/3205)
* Ignore gc allocation metrics when unsupported - [#3225](https://github.com/elastic/apm-agent-java/pull/3225)
* Avoid warning log message when grpc transactions are cancelled before end - [#3223](https://github.com/elastic/apm-agent-java/pull/3223)
* Align agent hostname capture to FQDN - [#3188](https://github.com/elastic/apm-agent-java/pull/3188)

## 1.39.0 [elastic-apm-java-agent-1-39-0-release-notes]
**Release date:** June 13, 2023

### Features and enhancements [elastic-apm-java-agent-1-39-0-features-enhancements]
* Capture S3 operation details as OTel attributes - [#3136](https://github.com/elastic/apm-agent-java/pull/3136)
* Added support for recording AWS lambda transactions even if the JVM crashes or runs into a timeout - [#3134](https://github.com/elastic/apm-agent-java/pull/3134)
* Add extra built-in metrics: `jvm.fd.*` and `jvm.memory.pool.non_heap.*` - [#3147](https://github.com/elastic/apm-agent-java/pull/3147)
* Capture `span.sync` = `false` for some async spans plugins - [#3164](https://github.com/elastic/apm-agent-java/pull/3164)

### Fixes [elastic-apm-java-agent-1-39-0-fixes]
* Fixed classloading for OpenTelemetry dependencies in external plugins - [#3154](https://github.com/elastic/apm-agent-java/pull/3154)
* Handled an edge case where exceptions thrown by instrumentation code could escape into the application - [#3159](https://github.com/elastic/apm-agent-java/pull/3159)
* Added guard to gracefully handle the presence of pre 3.0 Servlet API versions in the spring service name discovery mechanism - [#3172](https://github.com/elastic/apm-agent-java/pull/3172)

## 1.38.0 [elastic-apm-java-agent-1-38-0-release-notes]
**Release date:** May 4, 2023

### Features and enhancements [elastic-apm-java-agent-1-38-0-features-enhancements]
* Added tests for Quarkus / RestEasy, adjusted vert.x router transaction name priority - [#1765](https://github.com/elastic/apm-agent-java/pull/1765)
* Added support for Spring WebMVC 6.x and Spring Boot 3.x - [#3094](https://github.com/elastic/apm-agent-java/pull/3094)
* Added `service.environment` to logs for service correlation - [#3115](https://github.com/elastic/apm-agent-java/pull/3115)
* Optimize agent overhead when an excessive number of spans is created with `trace_methods`, `@Traced` or `@CaptureSpan` - [#3151](https://github.com/elastic/apm-agent-java/pull/3151)

### Fixes [elastic-apm-java-agent-1-38-0-fixes]
* Do not use proxy to retrieve cloud metadata - [#3108](https://github.com/elastic/apm-agent-java/pull/3108)

## 1.37.0 [elastic-apm-java-agent-1-37-0-release-notes]
**Release date:** April 11, 2023

### Features and enhancements [elastic-apm-java-agent-1-37-0-features-enhancements]
* Add the [`disable_outgoing_tracecontext_headers` ([1.37.0])](/reference/config-core.md#config-disable-outgoing-tracecontext-headers) config option to disable injection of `tracecontext` on outgoing communication - [#2996](https://github.com/elastic/apm-agent-java/pull/2996)
* Add the [`profiling_inferred_spans_logging_enabled` ([1.37.0])]/apm-agent-java/docs/reference/config-profiling.md#config-profiling-inferred-spans-logging-enabled) config option to suppress async profiler warning messages - [#3002](https://github.com/elastic/apm-agent-java/pull/3002)
* Added support for OpenTelemetry metrics - [#2968](https://github.com/elastic/apm-agent-java/pull/2968), [#3014](https://github.com/elastic/apm-agent-java/pull/3014)
* Added agent.activation_method telemetry - [#2926](https://github.com/elastic/apm-agent-java/pull/2926)
* Allow creation of exit spans with `@CaptureSpan` and `@Traced` annotations - [#3046](https://github.com/elastic/apm-agent-java/pull/3046)
* Add the [`long_field_max_length` (performance [1.37.0])](/reference/config-core.md#config-long-field-max-length) config to enable capturing larger values for specific fields - [#3027](https://github.com/elastic/apm-agent-java/pull/3027)
* Provide fallback correlation when `ecs-logging-java` is used - [#3064](https://github.com/elastic/apm-agent-java/pull/3064)
* Added separate Java 8 build with updated log4j2 - [#3076](https://github.com/elastic/apm-agent-java/pull/3076)
* Add [elasticsearch_capture_body_urls]/apm-agent-java/docs/reference/config-datastore.md#config-elasticsearch-capture-body-urls) option to customize which Elasticsearch request bodies are captured - [#3091](https://github.com/elastic/apm-agent-java/pull/3091)

### Fixes [elastic-apm-java-agent-1-37-0-fixes]
* Fixed used instrumentations printed on shutdown [#3001](https://github.com/elastic/apm-agent-java/pull/3001)
* Prevent potential connection leak on network failure - [#2869](https://github.com/elastic/apm-agent-java/pull/2869)
* Fix for inferred spans where the parent id was also a child id - [#2686](https://github.com/elastic/apm-agent-java/pull/2686)
* Fix context propagation for async 7.x and 8.x Elasticsearch clients - [#3015](https://github.com/elastic/apm-agent-java/pull/3015)
* Fix exceptions filtering based on [`ignore_exceptions` ([1.11.0])](/reference/config-core.md#config-ignore-exceptions) when those are [nested](/reference/config-core.md#config-unnest-exceptions) - [#3025](https://github.com/elastic/apm-agent-java/pull/3025)
* Fix usage of `HttpUrlConnection.getResponseCode()` causing an error event due to exception capturing, even when it is internally handled - [#3024](https://github.com/elastic/apm-agent-java/pull/3024)
* Fix source code jar to contain apm-agent sources - [#3063](https://github.com/elastic/apm-agent-java/pull/3063)
* Fix security exception when security manager is used with `log_level=debug` - [#3077](https://github.com/elastic/apm-agent-java/pull/3077)
* Fix slim attacher when downloading agent version - [#3096](https://github.com/elastic/apm-agent-java/pull/3096)

## 1.36.0 [elastic-apm-java-agent-1-36-0-release-notes]
**Release date:** January 27, 2023

### Features and enhancements [elastic-apm-java-agent-1-36-0-features-enhancements]
* Add experimental log sending from the agent with `log_sending` - [#2694](https://github.com/elastic/apm-agent-java/pull/2694)
* Add bootstrap checks that enable [JVM Filtering]/apm-agent-java/docs/reference/set-up-apm-java-agent.md#jvm-filtering) on startup - [#2951](https://github.com/elastic/apm-agent-java/pull/2951)
* Added support for LDAP - [#2977](https://github.com/elastic/apm-agent-java/pull/2977)

### Fixes [elastic-apm-java-agent-1-36-0-fixes]
* Use `127.0.0.1` as default for `server_url` to prevent ipv6 ambiguity - [#2927](https://github.com/elastic/apm-agent-java/pull/2927)
* Fix some span-compression concurrency issues - [#2865](https://github.com/elastic/apm-agent-java/pull/2865)
* Add warning when agent is accidentally started on a JVM/JDK command-line tool - [#2924](https://github.com/elastic/apm-agent-java/pull/2924)
* Fix `NullPointerException` caused by the Elasticsearch REST client instrumentation when collecting dropped span metrics - [#2959](https://github.com/elastic/apm-agent-java/pull/2959)
* Fix SQS Instrumentation for Non-MessageReceive actions to avoid NoSuchElementException - [#2979](https://github.com/elastic/apm-agent-java/pull/2979)
* Fix `java.lang.NoSuchMethodError` when using the agent with WebFlux and Spring 6.x/Spring Boot 3.x - [#2935](https://github.com/elastic/apm-agent-java/pull/2935)
* Optimize JMS listener matcher - [#2930](https://github.com/elastic/apm-agent-java/pull/2930)
* Handle Corretto causing a sigsegv when accessing Throwables inside some instrumentation with AWS - [#2958](https://github.com/elastic/apm-agent-java/pull/2958)
* Change Micrometer logs to DEBUG from INFO - [#2914](https://github.com/elastic/apm-agent-java/pull/2914)

## 1.35.0 [elastic-apm-java-agent-1-35-0-release-notes]
**Release date:** December 6, 2022

### Features and enhancements [elastic-apm-java-agent-1-35-0-features-enhancements]
* Add support for log correlation for `java.util.logging` (JUL) - [#2724](https://github.com/elastic/apm-agent-java/pull/2724)
* Add support for spring-kafka batch listeners - [#2815](https://github.com/elastic/apm-agent-java/pull/2815)
* Improved instrumentation for legacy Apache HttpClient (when not using an `HttpUriRequest`, such as `BasicHttpRequest`)
* Prevented exclusion of agent-packages via `classes_excluded_from_instrumentation` to avoid unintended side effects
* Add Tomcat support for log reformatting - [#2839](https://github.com/elastic/apm-agent-java/pull/2839)
* Capture Elastic cluster name on Elastic Cloud - [#2796](https://github.com/elastic/apm-agent-java/pull/2796)
* Attacher CLI: added a `--no-fork` config to opt out from executing a forked process as different user.If `--no-fork` is used alongside discovery rules that contain only `--include-pid` rules, the attacher will not execute JVM discovery - [#2863](https://github.com/elastic/apm-agent-java/pull/2863)
* Add the option to instrument very old bytecode through the [`instrument_ancient_bytecode`](/reference/config-core.md#config-instrument-ancient-bytecode) config option - [#2866](https://github.com/elastic/apm-agent-java/pull/2866)
* Capture MongoDB statements - [#2806](https://github.com/elastic/apm-agent-java/pull/2806)
* Added agent health and background overhead metrics (experimental) - [#2864](https://github.com/elastic/apm-agent-java/pull/2864), [#2888](https://github.com/elastic/apm-agent-java/pull/2888)
* Added support for Finagle Http Client - [#2795](https://github.com/elastic/apm-agent-java/pull/2795)
* Add support for Apache HTTP client 3.x - [#2853](https://github.com/elastic/apm-agent-java/pull/2853)
* Made `api_key` and `secret_token` configuration options dynamic - [#2889](https://github.com/elastic/apm-agent-java/pull/2889)
* Misaligned micrometer interval vs Elastic agent metrics reporting interval now handled - [#2801](https://github.com/elastic/apm-agent-java/pull/2801)
* Histograms now reported from micrometer metrics - [#2895](https://github.com/elastic/apm-agent-java/pull/2895)

### Fixes [elastic-apm-java-agent-1-35-0-fixes]
* Remove `context.db` fields from S3 instrumentation - [#2821](https://github.com/elastic/apm-agent-java/pull/2821)
* Allowed OpenTelemetry `Span.updateName` to update names provided by elastic - [#2838](https://github.com/elastic/apm-agent-java/pull/2838)
* Prevent random `NullPointerException` when span compression is not possible - [#2859](https://github.com/elastic/apm-agent-java/pull/2859)
* Fix security manager issues on OpenJDK 17, with errors like: `java.lang.UnsupportedOperationException: Could not access Unsafe class` - [#2874](https://github.com/elastic/apm-agent-java/pull/2874)
* Fix security manager compatibility with Tomcat - [#2871](https://github.com/elastic/apm-agent-java/pull/2871) and [#2883](https://github.com/elastic/apm-agent-java/pull/2883)
* Fix NPE with Spring MVC - [#2896](https://github.com/elastic/apm-agent-java/pull/2896)

## 1.34.1 [elastic-apm-java-agent-1-34-1-release-notes]
**Release date:** September 29, 2022

### Features and enhancements [elastic-apm-java-agent-1-34-1-features-enhancements]
* Redact `*principal*` headers by default - [#2798](https://github.com/elastic/apm-agent-java/pull/2798)
* Activation stack was extracted from `ElasticApmTracer` into a separate class, where it also enforces a stack depth to eliminate activation leaks - [#2783](https://github.com/elastic/apm-agent-java/pull/2783)

### Fixes [elastic-apm-java-agent-1-34-1-fixes]
* Fix imports (leading to `NoClassDefFoundError`) in the AWS SDK instrumentation - [#2800](https://github.com/elastic/apm-agent-java/pull/2800)

## 1.34.0 [elastic-apm-java-agent-1-34-0-release-notes]
**Release date:** September 14, 2022

### Features and enhancements [elastic-apm-java-agent-1-34-0-features-enhancements]
* Changed the main agent class loader to work in a child-first delegation model, thus making it more isolated by preferring self packaged version of classes that are available also in the parent (bootstrap) class loader - [#2728](https://github.com/elastic/apm-agent-java/pull/2728)
* Capture Oracle SID in connection string - [#2709](https://github.com/elastic/apm-agent-java/pull/2709)
* Implemented span links in the OTel bridge - [#2685](https://github.com/elastic/apm-agent-java/pull/2685)
* Added support for MongoDB 4.x Sync Driver - [#2241](https://github.com/elastic/apm-agent-java/pull/2241)
* Capture keyspace in `db.instance` for Cassandra database - [#2684](https://github.com/elastic/apm-agent-java/pull/2684)
* Added support for AWS SQS - [#2637](https://github.com/elastic/apm-agent-java/pull/2637)
* Add [`trace_continuation_strategy`](/reference/config-core.md#config-trace-continuation-strategy) configuration option - [#2760](https://github.com/elastic/apm-agent-java/pull/2760)
* Capture user from Azure SSO with Servlet-based app containers - [#2767](https://github.com/elastic/apm-agent-java/pull/2767)
* Promote WebFlux & Reactor to GA and enable it by default - [#2782](https://github.com/elastic/apm-agent-java/pull/2782)

### Fixes [elastic-apm-java-agent-1-34-0-fixes]
* Fix unexpected side effects of `toString` calls within reactor instrumentation - [#2708](https://github.com/elastic/apm-agent-java/pull/2708)
* Fix Vert.x instrumentation for 4.3.2 - [#2700](https://github.com/elastic/apm-agent-java/pull/2700)
* Fix `NullPointerException` in `AmazonHttpClientInstrumentation` - [#2740](https://github.com/elastic/apm-agent-java/pull/2740)
* Fix stack frame exclusion patterns - [#2758](https://github.com/elastic/apm-agent-java/pull/2758)
* Fix `NullPointerException` with compressed spans - [#2755](https://github.com/elastic/apm-agent-java/pull/2755)
* Fix empty Servlet path with proper fallback - [#2748](https://github.com/elastic/apm-agent-java/pull/2748)
* Fix `ClosedByInterruptException` during indy bootstrap method resolution - [#2752](https://github.com/elastic/apm-agent-java/pull/2752)
* Enhance exclusion of other APM agents - [#2766](https://github.com/elastic/apm-agent-java/pull/2766)
* Avoid warning when log correlation option is provided in remote config - [#2765](https://github.com/elastic/apm-agent-java/pull/2765)
* Update `async-profiler` to 1.8.8 to avoid missing symbol log spam - [#2775](https://github.com/elastic/apm-agent-java/pull/2775)
* Fix container ID discovery for containers managed through AWS Fargate - [#2772](https://github.com/elastic/apm-agent-java/pull/2772)
* Make `traceparent` header computation thread-safe - [#2747](https://github.com/elastic/apm-agent-java/pull/2747)
* Fix OTel bridge with multiple OTel APIs or external plugins - [#2735](https://github.com/elastic/apm-agent-java/pull/2735)

## 1.33.0 [elastic-apm-java-agent-1-33-0-release-notes]
**Release date:** July 8, 2022

### Features and enhancements [elastic-apm-java-agent-1-33-0-features-enhancements]
* Add support for Spring WebClient - [#2229](https://github.com/elastic/apm-agent-java/pull/2229)
* Added undocumented and unsupported configuration `metric_set_limit` to increase the metric set limit - [#2148](https://github.com/elastic/apm-agent-java/pull/2148)
* Added [`transaction_name_groups`](/reference/config-core.md#config-transaction-name-groups) configuration option  - [#2676](https://github.com/elastic/apm-agent-java/pull/2676)

### Fixes [elastic-apm-java-agent-1-33-0-fixes]
* Fix for JAX-WS (SOAP) transaction names. The agent now properly names transaction for web service methods that are not annotated with `@WebMethod`. - [#2667](https://github.com/elastic/apm-agent-java/pull/2667)
* Fix public API backward compatibility that was broken in 1.32.0. With this version you can use any version of the public API once again - [#2682](https://github.com/elastic/apm-agent-java/pull/2682)
* Fix flaky transaction name with Webflux+Servlet - [#2695](https://github.com/elastic/apm-agent-java/pull/2695)

## 1.32.0 [elastic-apm-java-agent-1-32-0-release-notes]
**Release date:** June 13, 2022

### Features and enhancements [elastic-apm-java-agent-1-32-0-features-enhancements]
* Promote mature agent features as Generaly Available (GA) - [#2632](https://github.com/elastic/apm-agent-java/pull/2632)
    * [OpenTelemetry bridge]/apm-agent-java/docs/reference/opentelemetry-bridge.md) is now enabled by default
    * [Circuit breaker]/apm-agent-java/docs/reference/config-circuit-breaker.md) marked as GA (still disabled by default)
    * [API Attach]/apm-agent-java/docs/reference/setup-attach-api.md) and [CLI Attach]/apm-agent-java/docs/reference/setup-attach-cli.md) marked as GA
    * [`use_path_as_transaction_name`]/apm-agent-java/docs/reference/config-http.md#config-use-path-as-transaction-name) configuration option marked as GA
    * Dubbo instrumentation is now enabled by default
    * `com.sun.net.httpserver.HttpServer` instrumentation marked as GA
* Struts action invocations via an action chain result start a new span - [#2513](https://github.com/elastic/apm-agent-java/pull/2513)
* Added official support for Elasticsearch Java API client - [#2211](https://github.com/elastic/apm-agent-java/pull/2211)
* Added the ability to make spans non-discardable through the public API and the OpenTelemetry bridge - [#2632](https://github.com/elastic/apm-agent-java/pull/2632)
* Added support for the new service target fields - [#2578](https://github.com/elastic/apm-agent-java/pull/2578)
* Capture the database name from JDBC connection string - [#2642](https://github.com/elastic/apm-agent-java/pull/2642)
* Added an additional span around Javalin template renderers - [#2381](https://github.com/elastic/apm-agent-java/pull/2381)
* Added support for downloading the latest agent version through the attach CLI by setting `--download-agent-version latest`. In addition, when using the `apm-agent-attach-cli-slim.jar`, which does not contain a bundled agent, the latest version will be downloaded from maven at runtime unless configured otherwise through  `--download-agent-version` - [#2659](https://github.com/elastic/apm-agent-java/pull/2659)
* Added span-links to messaging systems instrumentation (supported by APM Server 8.3+ only) - [#2610](https://github.com/elastic/apm-agent-java/pull/2610)

### Fixes [elastic-apm-java-agent-1-32-0-fixes]
* Fix missing attributes in bridged OTel transactions - [#2657](https://github.com/elastic/apm-agent-java/pull/2657)
* Fix `transaction.result` with bridged OTel transactions - [#2660](https://github.com/elastic/apm-agent-java/pull/2660)

## 1.31.0 [elastic-apm-java-agent-1-31-0-release-notes]
**Release date:** May 17, 2022

### Features and enhancements [elastic-apm-java-agent-1-31-0-features-enhancements]
* Vert.x 3.x instrumentation was refactored to remove constructor instrumentation as well as wrapping of response handler. In addition, in HTTP 2 request handling, transactions are ended when the request end event occurs and are kept alive until response end, when they are allowed to recycle. This allows for spans representing asynchronous handling of requests for which the corresponding transaction has ended - [#2564](https://github.com/elastic/apm-agent-java/pull/2564)
* Jedis clients instrumentation was changed
* Set the service version when using the ECS reformatting of the application logs: [#2603](https://github.com/elastic/apm-agent-java/pull/2603)
* Add ECS-reformatting support for `java.util.logging` - [#2591](https://github.com/elastic/apm-agent-java/pull/2591)
* Added support for setting the service version on Log4j2’s EcsLayout - [#2604](https://github.com/elastic/apm-agent-java/pull/2604)
* Added support for AWS S3 and DynamoDB - [#2606](https://github.com/elastic/apm-agent-java/pull/2606)
* Added support for Jedis 4.x clients - [#2626](https://github.com/elastic/apm-agent-java/pull/2626)

### Fixes [elastic-apm-java-agent-1-31-0-fixes]
* Fixed multiple dropped stats types in a transaction producing invalid JSON: [#2589](https://github.com/elastic/apm-agent-java/pull/2589)
* Fixed NoClassDefFoundError when using OTel bridge and span.*current() : [#2596](https://github.com/elastic/apm-agent-java/pull/2596)
* Fallback to standard output when Security Manager prevents writing to log file - [#2581](https://github.com/elastic/apm-agent-java/pull/2581)
* Fix missing transactions when using Vert.x 3.x with HTTP 1 - [#2564](https://github.com/elastic/apm-agent-java/pull/2564)
* Fix Vert.x `GET null` transactions to be named `GET unknown route`, according to spec - [#2564](https://github.com/elastic/apm-agent-java/pull/2564)
* Fix OpenTelemetry bridge span end with explicit timestamp - [#2615](https://github.com/elastic/apm-agent-java/pull/2615)
* Fix improper naming for `scheduled` transactions created by `java.util.TimerTask` instrumentation - [#2620](https://github.com/elastic/apm-agent-java/pull/2620)
* Properly handle `java.lang.IllegalStateException` related to premature invocation of `ServletConfig#getServletContext()` in `Servlet#init()` instrumentations - [#2627](https://github.com/elastic/apm-agent-java/pull/2627)

## 1.30.1 [elastic-apm-java-agent-1-30-1-release-notes]
**Release date:** April 12, 2022

### Fixes [elastic-apm-java-agent-1-30-1-fixes]
* Fixed AWS Lambda instrumentation for AWS handler classes with input object types that are not AWS Events classes  - [#2551](https://github.com/elastic/apm-agent-java/pull/2551)
* Fixed service name discovery based on MANIFEST.MF file through `ServletContainerInitializer#onStartup` on Jakarta Servlet containers - [#2546](https://github.com/elastic/apm-agent-java/pull/2546)
* Fix shaded classloader package definition - [#2566](https://github.com/elastic/apm-agent-java/pull/2566)
* Fix logging initialization with Security Manager - [#2568](https://github.com/elastic/apm-agent-java/pull/2568)
* normalize empty `transaction.type` and `span.type` - [#2525](https://github.com/elastic/apm-agent-java/pull/2525)
* Allowing square brackets within the [`capture_jmx_metrics` ([1.11.0])]/apm-agent-java/docs/reference/config-jmx.md#config-capture-jmx-metrics) config value - [#2547](https://github.com/elastic/apm-agent-java/pull/2547)
* Fixed duplicated ending of `HttpUrlConnection` spans - [#2530](https://github.com/elastic/apm-agent-java/pull/2530)
* Compressed span fixes - [#2576](https://github.com/elastic/apm-agent-java/pull/2576), [#2552](https://github.com/elastic/apm-agent-java/pull/2552), [#2558](https://github.com/elastic/apm-agent-java/pull/2558)

## 1.30.0 [elastic-apm-java-agent-1-30-0-release-notes]
**Release date:** March 22, 2022

### Features and enhancements [elastic-apm-java-agent-1-30-0-features-enhancements]
* Logging frameworks instrumentations - [#2428](https://github.com/elastic/apm-agent-java/pull/2428). This refactoring includes:
* Log correlation now works based on bytecode instrumentation rather than `ActivationListener` that directly updates the MDC
* Merging the different instrumentations (log-correlation, error-capturing and ECS-reformatting) into a single plugin
* Module structure and package naming changes
* Added support for setting service name and version for a transaction via the public api - [#2451](https://github.com/elastic/apm-agent-java/pull/2451)
* Added support for en-/disabling each public annotation on each own - [#2472](https://github.com/elastic/apm-agent-java/pull/2472)
* Added support for compressing spans - [#2477](https://github.com/elastic/apm-agent-java/pull/2477)
* Added microsecond durations with `us` as unit - [#2496](https://github.com/elastic/apm-agent-java/pull/2496)
* Added support for dropping fast exit spans - [#2491](https://github.com/elastic/apm-agent-java/pull/2491)
* Added support for collecting statistics about dropped exit spans - [#2505](https://github.com/elastic/apm-agent-java/pull/2505)
* Making AWS Lambda instrumentation GA - includes some changes in Lambda transaction metadata fields and a dedicated flush HTTP request to the AWS Lambda extension - [#2424](https://github.com/elastic/apm-agent-java/pull/2424)
* Changed logging correlation to be on by default. This change includes the removal of the now redundant `enable_log_correlation` config option. If there’s a need to disable the log correlation mechanism, this can be done now through the `disable_instrumentations` config - [#2428](https://github.com/elastic/apm-agent-java/pull/2428)
* Added automatic error event capturing for log4j1 and JBoss LogManager - [#2428](https://github.com/elastic/apm-agent-java/pull/2428)
* Issue a warning when security manager is mis-configured - [#2510](https://github.com/elastic/apm-agent-java/pull/2510)
* Add experimental OpenTelemetry API bridge - [#1631](https://github.com/elastic/apm-agent-java/pull/1631)
* Proxy classes are excluded from instrumentation in more cases - [#2474](https://github.com/elastic/apm-agent-java/pull/2474)
* Only time type/method matching if the debug logging is enabled as the results are only used when debug logging is enabled - [#2471](https://github.com/elastic/apm-agent-java/pull/2471)

### Fixes [elastic-apm-java-agent-1-30-0-fixes]
* Fix cross-plugin dependencies triggering NoClassDefFound - [#2509](https://github.com/elastic/apm-agent-java/pull/2509)
* Fix status code setting in AWS Lambda transactions triggered by API Gateway V1 - [#2346](https://github.com/elastic/apm-agent-java/pull/2346)
* Fix classloading OSGi bundles with partial dependency on Servlet API + avoid SecurityException with Apache Sling - [2418](https://github.com/elastic/apm-agent-java/pull/2418)
* Respect `transaction_ignore_urls` and `transaction_ignore_user_agents` when creating transactions in the spring webflux instrumentation - [#2515](https://github.com/elastic/apm-agent-java/pull/2515)

## 1.29.0 [elastic-apm-java-agent-1-30-0-release-notes]
**Release date:** February 9, 2022

### Features and enhancements [elastic-apm-java-agent-1-29-0-features-enhancements]
* Exceptions that are logged using the fatal log level are now captured (log4j2 only) - [#2377](https://github.com/elastic/apm-agent-java/pull/2377)
* Replaced `authorization` in the default value of `sanitize_field_names` with `*auth*` - [#2326](https://github.com/elastic/apm-agent-java/pull/2326)
* Unsampled transactions are dropped and not sent to the APM-Server if the APM-Server version is 8.0+ - [#2329](https://github.com/elastic/apm-agent-java/pull/2329)
* Adding agent logging capabilities to our SDK, making it available for external plugins - [#2390](https://github.com/elastic/apm-agent-java/pull/2390)
* Service name auto-discovery improvements
* For applications deployed to application servers (`war` files) and standalone jars that are started with `java -jar`, the agent now discovers the `META-INF/MANIFEST.MF` file.
* If the manifest contains the `Implementation-Title` attribute, it is used as the default service name - [#1921](https://github.com/elastic/apm-agent-java/pull/1921), [#2434](https://github.com/elastic/apm-agent-java/pull/2434)<br> **Note**: this may change your service names if you relied on the auto-discovery that uses the name of the jar file. If that jar file also contains an `Implementation-Title` attribute in the `MANIFEST.MF` file, the latter will take precedence.
* When the manifest contains the `Implementation-Version` attribute, it is used as the default service version - [#1726](https://github.com/elastic/apm-agent-java/pull/1726), [#1922](https://github.com/elastic/apm-agent-java/pull/1922), [#2434](https://github.com/elastic/apm-agent-java/pull/2434)
* Added support for instrumenting Struts 2 static resource requests - [#1949](https://github.com/elastic/apm-agent-java/pull/1949)
* Added support for Java/Jakarta WebSocket ServerEndpoint - [#2281](https://github.com/elastic/apm-agent-java/pull/2281)
* Added support for setting the service name on Log4j2’s EcsLayout - [#2296](https://github.com/elastic/apm-agent-java/pull/2296)
* Print the used instrumentation groups when the application stops - [#2448](https://github.com/elastic/apm-agent-java/pull/2448)
* Add `elastic.apm.start_async` property that makes the agent start on a non-premain/main thread - [#2454](https://github.com/elastic/apm-agent-java/pull/2454)

### Fixes [elastic-apm-java-agent-1-29-0-fixes]
* Fix runtime attach with some docker images - [#2385](https://github.com/elastic/apm-agent-java/pull/2385)
* Restore dynamic capability to `log_level` config for plugin loggers - [#2384](https://github.com/elastic/apm-agent-java/pull/2384)
* Fix slf4j-related `LinkageError` - [#2390](https://github.com/elastic/apm-agent-java/pull/2390) and [#2376](https://github.com/elastic/apm-agent-java/pull/2376)
* Fix possible deadlock occurring when Byte Buddy reads System properties by warming up bytecode instrumentation code paths. The BCI warmup is on by default and may be disabled through the internal `warmup_byte_buddy` config option - [#2368](https://github.com/elastic/apm-agent-java/pull/2368)
* Fixed few dubbo plugin issues - [#2149](https://github.com/elastic/apm-agent-java/pull/2149)
* Dubbo transaction will should be created at the provider side
* APM headers conversion issue within dubbo transaction
* Fix External plugins automatic setting of span outcome - [#2376](https://github.com/elastic/apm-agent-java/pull/2376)
* Avoid early initialization of JMX on Weblogic - [#2420](https://github.com/elastic/apm-agent-java/pull/2420)
* Automatically disable class sharing on AWS lambda layer - [#2438](https://github.com/elastic/apm-agent-java/pull/2438)
* Avoid standalone spring applications to have two different service names, one based on the jar name, the other based on `spring.application.name`.

## 1.28.4 [elastic-apm-java-agent-1-28-4-release-notes]
**Release date:** December 30, 2021

### Features and enhancements [elastic-apm-java-agent-1-28-4-features-enhancements]
* Update Log4j to 2.12.4 and log4j2-ecs-layout to 1.3.2 - [#2378](https://github.com/elastic/apm-agent-java/pull/2378)

### Fixes [elastic-apm-java-agent-1-28-4-fixes]
* Fix `@Traced` annotation to return proper outcome instead of `failed` - [#2370](https://github.com/elastic/apm-agent-java/pull/2370)

## 1.28.3 [elastic-apm-java-agent-1-28-3-release-notes]
**Release date:** December 22, 2021

### Features and enhancements [elastic-apm-java-agent-1-28-3-features-enhancements]
* Update Log4j to 2.12.3
* Update ecs-logging-java to 1.3.0

### Fixes [elastic-apm-java-agent-1-28-3-fixes]
* Gracefully handle JDBC drivers which don’t support `Connection#getCatalog` - [#2340](https://github.com/elastic/apm-agent-java/pull/2340)
* Fix using JVM keystore options for communication with APM Server - [#2362](https://github.com/elastic/apm-agent-java/pull/2362)

## 1.28.2 [elastic-apm-java-agent-1-28-2-release-notes]
**Release date:** December 16, 2021

### Features and enhancements [elastic-apm-java-agent-1-28-2-features-enhancements]
* Update Log4j to 2.12.2

### Fixes [elastic-apm-java-agent-1-28-2-fixes]
* Fix module loading errors on J9 JVM - [#2341](https://github.com/elastic/apm-agent-java/pull/2341)
* Fixing log4j configuration error - [#2343](https://github.com/elastic/apm-agent-java/pull/2343)

## 1.28.1 [elastic-apm-java-agent-1-28-1-release-notes]
**Release date:** December 10, 2021

### Features and enhancements [elastic-apm-java-agent-1-28-1-features-enhancements]
* Added support to selectively enable instrumentations - [#2292](https://github.com/elastic/apm-agent-java/pull/2292)

### Fixes [elastic-apm-java-agent-1-28-1-fixes]
* Fix for "Log4Shell" RCE 0-day exploit in log4j [CVE-2-02-1-44228](https://nvd.nist.gov/vuln/detail/CVE-2-02-1-44228) - [#2332](https://github.com/elastic/apm-agent-java/pull/2332)
* Preferring controller names for Spring MVC transactions, `use_path_as_transaction_name` only as a fallback - [#2320](https://github.com/elastic/apm-agent-java/pull/2320)

## 1.28.0 [elastic-apm-java-agent-1-28-0-release-notes]
**Release date:** December 7, 2021

### Features and enhancements [elastic-apm-java-agent-1-28-0-features-enhancements]
* Adding experimental support for [AWS Lambda]/apm-agent-java/docs/reference/aws-lambda.md) - [#1951](https://github.com/elastic/apm-agent-java/pull/1951)
* Now supporting tomcat 10 - [#2229](https://github.com/elastic/apm-agent-java/pull/2229)

### Fixes [elastic-apm-java-agent-1-28-0-fixes]
* Fix error with parsing APM Server version for 7.16+ - [#2313](https://github.com/elastic/apm-agent-java/pull/2313)

## 1.27.1 [elastic-apm-java-agent-1-27-1-release-notes]
**Release date:** November 30, 2021

### Features and enhancements [elastic-apm-java-agent-1-27-1-features-enhancements]
* Add support to Jakarta EE for JSF - [#2254](https://github.com/elastic/apm-agent-java/pull/2254)

### Fixes [elastic-apm-java-agent-1-27-1-fixes]
* Resolves Local Privilege Escalation issue [ESA-2-02-1-30](https://discuss.elastic.co/t/apm-java-agent-security-update/291355) [CVE-2-02-1-37942](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2-02-1-37942)
* Fixing missing Micrometer metrics in Spring boot due to premature initialization - [#2255](https://github.com/elastic/apm-agent-java/pull/2255)
* Fixing hostname trimming of FQDN too aggressive - [#2286](https://github.com/elastic/apm-agent-java/pull/2286)
* Fixing agent `unknown` version - [#2289](https://github.com/elastic/apm-agent-java/pull/2289)
* Improve runtime attach configuration reliability - [#2283](https://github.com/elastic/apm-agent-java/pull/2283)

## 1.27.0 [elastic-apm-java-agent-1-27-0-release-notes]
**Release date:** November 15, 2021

### Features and enhancements [elastic-apm-java-agent-1-27-0-features-enhancements]
* Improved capturing of logged exceptions when using Log4j2 - [#2139](https://github.com/elastic/apm-agent-java/pull/2139)
* Update to async-profiler 1.8.7 and set configured `safemode` at load time though a new system property - [#2165](https://github.com/elastic/apm-agent-java/pull/2165)
* Added support to capture `context.message.routing-key` in rabbitmq, spring amqp instrumentations - [#1767](https://github.com/elastic/apm-agent-java/pull/1767)
* Breakdown metrics are now tracked per service (when using APM Server 8.0) - [#2208](https://github.com/elastic/apm-agent-java/pull/2208)
* Add support for Spring AMQP batch API - [#1716](https://github.com/elastic/apm-agent-java/pull/1716)
* Add the (current) transaction name to the error (when using APM Server 8.0) - [#2235](https://github.com/elastic/apm-agent-java/pull/2235)
* The JVM/JMX metrics are reported for each service name individually (when using APM Server 8.0) - [#2233](https://github.com/elastic/apm-agent-java/pull/2233)
* Added [`span_stack_trace_min_duration`]/apm-agent-java/docs/reference/config-stacktrace.md#config-span-stack-trace-min-duration) option. This replaces the now deprecated `span_frames_min_duration` option. The difference is that the new option has more intuitive semantics for negative values (never collect stack trace) and zero (always collect stack trace). - [#2220](https://github.com/elastic/apm-agent-java/pull/2220)
* Add support to Jakarta EE for JAX-WS - [#2247](https://github.com/elastic/apm-agent-java/pull/2247)
* Add support to Jakarta EE for JAX-RS - [#2248](https://github.com/elastic/apm-agent-java/pull/2248)
* Add support for Jakarta EE EJB annotations `@Schedule`, `@Schedules` - [#2250](https://github.com/elastic/apm-agent-java/pull/2250)
* Add support to Jakarta EE for Servlets - [#1912](https://github.com/elastic/apm-agent-java/pull/1912)
* Added support to Quartz 1.x - [#2219](https://github.com/elastic/apm-agent-java/pull/2219)
* Disable compression when sending data to a local APM Server
* Reducing startup contention related to instrumentation through `ensureInstrumented` - [#2150](https://github.com/elastic/apm-agent-java/pull/2150)
* Loading the agent from an isolated class loader - [#2109](https://github.com/elastic/apm-agent-java/pull/2109)
* Refactorings in the `apm-agent-plugin-sdk` that may imply breaking changes for beta users of the external plugin mechanism
* `WeakMapSupplier.createMap()` is now `WeakConcurrent.buildMap()` and contains more builders - [#2136](https://github.com/elastic/apm-agent-java/pull/2136)
* `GlobalThreadLocal` has been removed in favor of `DetachedThreadLocal`. To make it global, use `GlobalVariables` - [#2136](https://github.com/elastic/apm-agent-java/pull/2136)
* `DynamicTransformer.Accessor.get().ensureInstrumented` is now `DynamicTransformer.ensureInstrumented` - [#2164](https://github.com/elastic/apm-agent-java/pull/2164)
* The `@AssignTo.*` annotations have been removed. Use the `@Advice.AssignReturned.*` annotations that come with the latest version of Byte Buddy. If your plugin uses the old annotations, it will be skipped. [#2171](https://github.com/elastic/apm-agent-java/pull/2171)
* Switching last instrumentations (`trace_methods`, sparkjava, JDK `HttpServer` and Struts 2) to `TracerAwareInstrumentation` - [#2170](https://github.com/elastic/apm-agent-java/pull/2170)
* Replace concurrency plugin maps to `SpanConcurrentHashMap` ones - [#2173](https://github.com/elastic/apm-agent-java/pull/2173)
* Align User-Agent HTTP header with other APM agents - [#2177](https://github.com/elastic/apm-agent-java/pull/2177)

### Fixes [elastic-apm-java-agent-1-27-0-fixes]
* Resolves Local Privilege Escalation issue [ESA-2-02-1-29](https://discuss.elastic.co/t/apm-java-agent-security-update/289627) [CVE-2-02-1-37941](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2-02-1-37941)
* Fix k8s metadata discovery for containerd-cri envs - [#2126](https://github.com/elastic/apm-agent-java/pull/2126)
* Fixing/reducing startup delays related to `ensureInstrumented` - [#2150](https://github.com/elastic/apm-agent-java/pull/2150)
* Fix runtime attach when bytebuddy is in application classpath - [#2116](https://github.com/elastic/apm-agent-java/pull/2116)
* Fix failed integration between agent traces and host metrics coming from Beats/Elastic-Agent due to incorrect hostname discovery - [#2205](https://github.com/elastic/apm-agent-java/pull/2205)
* Fix infinitely kept-alive transactions in Hikari connection pool - [#2210](https://github.com/elastic/apm-agent-java/pull/2210)
* Fix few Webflux exceptions and missing reactor module - [#2207](https://github.com/elastic/apm-agent-java/pull/2207)

## 1.26.2 [elastic-apm-java-agent-1-26-2-release-notes]
**Release date:** December 30, 2021

### Features and enhancements [elastic-apm-java-agent-1-26-2-features-enhancements]
* Update Log4j to 2.12.4 and log4j2-ecs-layout to 1.3.2 - [#2378](https://github.com/elastic/apm-agent-java/pull/2378)

## 1.26.1 [elastic-apm-java-agent-1-26-1-release-notes]
**Release date:** December 22, 2021

### Features and enhancements [elastic-apm-java-agent-1-26-1-features-enhancements]
* Update Log4j to 2.12.3
* Update ecs-logging-java to 1.3.0

## 1.26.0 [elastic-apm-java-agent-1-26-0-release-notes]
**Release date:** September 14, 2021

### Features and enhancements [elastic-apm-java-agent-1-26-0-features-enhancements]
* Improved naming for Spring controllers - [#1906](https://github.com/elastic/apm-agent-java/pull/1906)
* ECS log reformatting improvements - [#1910](https://github.com/elastic/apm-agent-java/pull/1910)
* Automatically sets `service.node.name` in all log events if set through agent configuration
* Add `log_ecs_reformatting_additional_fields` option to support arbitrary fields in logs
* Automatically serialize markers as tags where relevant (log4j2 and logback)
* gRPC spans (client and server) can detect errors or cancellation through custom listeners - [#2067](https://github.com/elastic/apm-agent-java/pull/2067)
* Add `-download-agent-version` to the agent [attach CLI tool options]/apm-agent-java/docs/reference/setup-attach-cli.md#setup-attach-cli-usage-options), allowing the user to configure an arbitrary agent version that will be downloaded from maven and attached - [#1959](https://github.com/elastic/apm-agent-java/pull/1959)
* Add extra check to detect improper agent setup - [#2076](https://github.com/elastic/apm-agent-java/pull/2076)
* In redis tests - embedded RedisServer is replaced by testcontainers - [#2221](https://github.com/elastic/apm-agent-java/pull/2221)
* Reduce GC time overhead caused by WeakReferences - [#2086](https://github.com/elastic/apm-agent-java/pull/2086), [#2081](https://github.com/elastic/apm-agent-java/pull/2081)
* Reduced memory overhead by a smarter type pool caching strategy - [#2102](https://github.com/elastic/apm-agent-java/pull/2102).<br> The type pool cache improves the startup times by speeding up type matching (determining whether a class that’s about to be loaded should be instrumented). Generally, the more types that are cached, the faster the startup.<br> The old strategy did not impose a limit to the cache but cleared it after it hasn’t been accessed in a while. However, load test have discovered that the cache may never be cleared and leave a permanent overhead of 23mb. The actual size of the cache highly depends on the application and loosely correlates with the number of loaded classes.<br> The new caching strategy targets to allocate 1% of the committed heap, at least 0.5mb and max 10mb. If a particular entry hasn’t been accessed within 20s, it will be removed from the cache.<br> The results based on load testing are very positive:
* Equivalent startup times (within the margins of error of the previous strategy)
* Equivalent allocation rate (within the margins of error of the previous strategy)
* Reduced avg heap utilization from 10%/15mb (previous strategy) to within margins of error without the agent
* Reduced GC time due to the additional headroom that the application can utilize.
* Based on heap dump analysis, after warmup, the cache size is now around 59kb (down from 23mb with the previous strategy).
* Migrate several plugins to indy dispatcher [#2087](https://github.com/elastic/apm-agent-java/pull/2087), [#2088](https://github.com/elastic/apm-agent-java/pull/2088), [#2090](https://github.com/elastic/apm-agent-java/pull/2090), [#2094](https://github.com/elastic/apm-agent-java/pull/2094), [#2095](https://github.com/elastic/apm-agent-java/pull/2095)

### Fixes [elastic-apm-java-agent-1-26-0-fixes]
* Fix failure to parse some forms of the `Implementation-Version` property from jar manifest files - [#1931](https://github.com/elastic/apm-agent-java/pull/1931)
* Ensure single value for context-propagation header - [#1937](https://github.com/elastic/apm-agent-java/pull/1937)
* Fix gRPC non-terminated (therefore non-reported) client spans - [#2067](https://github.com/elastic/apm-agent-java/pull/2067)
* Fix Webflux response status code - [#1948](https://github.com/elastic/apm-agent-java/pull/1948)
* Ensure path filtering is applied when Servlet path is not available - [#2099](https://github.com/elastic/apm-agent-java/pull/2099)
* Align span subtype for MS SqlServer - [#2112](https://github.com/elastic/apm-agent-java/pull/2112)
* Fix potential destination host name corruption in OkHttp client spans - [#2118](https://github.com/elastic/apm-agent-java/pull/2118)

## 1.25.0 [elastic-apm-java-agent-1-25-0-release-notes]
**Release date:** July 22, 2022

### Features and enhancements [elastic-apm-java-agent-1-25-0-features-enhancements]
* Support for inheritance of public API annotations - [#1805](https://github.com/elastic/apm-agent-java/pull/1805)
* JDBC instrumentation sets `context.db.instance` - [#1820](https://github.com/elastic/apm-agent-java/pull/1820)
* Add support for Vert.x web client- [#1824](https://github.com/elastic/apm-agent-java/pull/1824)
* Avoid recycling of spans and transactions that are using through the public API, so to avoid reference-counting-related errors - [#1859](https://github.com/elastic/apm-agent-java/pull/1859)
* Add [`enable_experimental_instrumentations` ([1.25.0])](/reference/config-core.md#config-enable-experimental-instrumentations) configuration option to enable experimental features - [#1863](https://github.com/elastic/apm-agent-java/pull/1863)
* Previously, when adding an instrumentation group to `disable_instrumentations`, we had to make sure to not forget the default `experimental` value, for example when disabling `jdbc` instrumentation we had to set `disable_instrumentations=experimental,jdbc` otherwise setting `disable_instrumentations=jdbc` would disable jdbc and also enable experimental features, which would not be the desired effect.
* Previously, by default `disable_instrumentations` contained `experimental`
* Now by default `disable_instrumentations` is empty and `enable_experimental_instrumentations=false`
* Set `enable_experimental_instrumentations=true` to enable experimental instrumentations
* Eliminating concerns related to log4j2 vulnerability - [https://nvd.nist.gov/vuln/detail/CVE-2-02-0-9488#vulnCurrentDescriptionTitle](https://nvd.nist.gov/vuln/detail/CVE-2-02-0-9488#vulnCurrentDescriptionTitle). We cannot upgrade to version above 2.12.1 because this is the last version of log4j that is compatible with Java 7. Instead, we exclude the SMTP appender (which is the vulnerable one) from our artifacts. Note that older versions of our agent are not vulnerable as well, as the SMTP appender was never used, this is only to further reduce our users' concerns.
* Adding public APIs for setting `destination.service.resource`, `destination.address` and `destination.port` fields for exit spans - [#1788](https://github.com/elastic/apm-agent-java/pull/1788)
* Only use emulated runtime attachment as fallback, remove the `--without-emulated-attach` option - [#1865](https://github.com/elastic/apm-agent-java/pull/1865)
* Instrument `javax.servlet.Filter` the same way as `javax.servlet.FilterChain` - [#1858](https://github.com/elastic/apm-agent-java/pull/1858)
* Propagate trace context headers in HTTP calls occurring from within traced exit points, for example - when using Elasticsearch’s REST client - [#1883](https://github.com/elastic/apm-agent-java/pull/1883)
* Added support for naming sparkjava (not Apache Spark) transactions [#1894](https://github.com/elastic/apm-agent-java/pull/1894)
* Added the ability to manually create exit spans, which will result with the auto creation of service nodes in the service map and downstream service in the dependencies table - [#1898](https://github.com/elastic/apm-agent-java/pull/1898)
* Basic support for `com.sun.net.httpserver.HttpServer` - [#1854](https://github.com/elastic/apm-agent-java/pull/1854)
* Update to async-profiler 1.8.6 [#1907](https://github.com/elastic/apm-agent-java/pull/1907)
* Added support for setting the framework using the public api (#1908) - [#1909](https://github.com/elastic/apm-agent-java/pull/1909)

### Fixes [elastic-apm-java-agent-1-25-0-fixes]
* Fix NPE with `null` binary header values + properly serialize them - [#1842](https://github.com/elastic/apm-agent-java/pull/1842)
* Fix `ListenerExecutionFailedException` when using Spring AMQP’s ReplyTo container - [#1872](https://github.com/elastic/apm-agent-java/pull/1872)
* Enabling log ECS reformatting when using Logback configured with `LayoutWrappingEncoder` and a pattern layout - [#1879](https://github.com/elastic/apm-agent-java/pull/1879)
* Fix NPE with Webflux + context propagation headers - [#1871](https://github.com/elastic/apm-agent-java/pull/1871)
* Fix `ClassCastException` with `ConnnectionMetaData` and multiple classloaders - [#1864](https://github.com/elastic/apm-agent-java/pull/1864)
* Fix NPE in `co.elastic.apm.agent.servlet.helper.ServletTransactionCreationHelper.getClassloader` - [#1861](https://github.com/elastic/apm-agent-java/pull/1861)
* Fix for Jboss JMX unexpected notifications - [#1895](https://github.com/elastic/apm-agent-java/pull/1895)

## 1.24.0 [elastic-apm-java-agent-1-24-0-release-notes]
**Release date:** May 31, 2021

### Features and enhancements [elastic-apm-java-agent-1-24-0-features-enhancements]
* Basic support for Apache Struts 2 [#1763](https://github.com/elastic/apm-agent-java/pull/1763)
* Extending the [`log_ecs_reformatting` ([1.22.0] experimental)]/apm-agent-java/docs/reference/config-logging.md#config-log-ecs-reformatting) config option to enable the overriding of logs with ECS-reformatted events. With the new `OVERRIDE` option, non-file logs can be ECS-reformatted automatically as well - [#1793](https://github.com/elastic/apm-agent-java/pull/1793)
* Instrumentation for Vert.x Web [#1697](https://github.com/elastic/apm-agent-java/pull/1697)
* Changed log level of vm arguments to debug
* Giving precedence for the W3C `tracecontext` header over the `elastic-apm-traceparent` header - [#1821](https://github.com/elastic/apm-agent-java/pull/1821)
* Add instrumentation for Webflux - [#1305](https://github.com/elastic/apm-agent-java/pull/1305)
* Add instrumentation for Javalin [#1822](https://github.com/elastic/apm-agent-java/pull/1822)
* Remove single-package limitation for embedded plugins - [#1780](https://github.com/elastic/apm-agent-java/pull/1780)

### Fixes [elastic-apm-java-agent-1-24-0-fixes]
* Fix another error related to instrumentation plugins loading on Windows - [#1785](https://github.com/elastic/apm-agent-java/pull/1785)
* Load Spring AMQP plugin- [#1784](https://github.com/elastic/apm-agent-java/pull/1784)
* Avoid `IllegalStateException` when multiple `tracestate` headers are used - [#1808](https://github.com/elastic/apm-agent-java/pull/1808)
* Ensure CLI attach avoids `sudo` only when required and avoid blocking - [#1819](https://github.com/elastic/apm-agent-java/pull/1819)
* Avoid sending metric-sets without samples, so to adhere to the intake API - [#1826](https://github.com/elastic/apm-agent-java/pull/1826)
* Fixing our type-pool cache, so that it can’t cause OOM (softly-referenced), and it gets cleared when not used for a while - [#1828](https://github.com/elastic/apm-agent-java/pull/1828)

## 1.23.0 [elastic-apm-java-agent-1-23-0-release-notes]
**Release date:** April 22, 2021

### Features and enhancements [elastic-apm-java-agent-1-23-0-features-enhancements]
* Overhaul of the [attacher cli]/apm-agent-java/docs/reference/setup-attach-cli.md) application that allows to attach the agent to running JVMs - [#1667](https://github.com/elastic/apm-agent-java/pull/1667)
    * The artifact of the standalone cli application is now called `apm-agent-attach-cli`. The attacher API is still called `apm-agent-attach`.
    * There is also a slim version of the cli application that does not bundle the Java agent. It requires the `--agent-jar` option to be set.
    * Improved logging<br> The application uses [Java ECS logging](ecs-logging-java://reference/index.md) to emit JSON logs. The log level can be configured with the `--log-level` option. By default, the program is logging to the console but using the `--log-file` option, it can also log to a file.
    * Attach to JVMs running under a different user (unix only)<br> The JVM requires the attacher to be running under the same user as the target VM (the attachee). The `apm-agent-attach-standalone.jar` can now be run with a user that has permissions to switch to the user that runs the target VM. On Windows, the attacher can still only attach to JVMs that are running with under the same user.
    * New include/exclude discovery rules<br>

        * `--include-all`: Attach to all discovered JVMs. If no matchers are provided, it will not attach to any JVMs.
        * `--include-user`/`--exclude-user`: Attach to all JVMs of a given operating system user.
        * `--include-main`/`--exclude-main`: Attach to all JVMs that whose main class/jar name, or system properties match the provided regex.
        * `--include-vmargs`/`--exclude-vmargs`: Attach to all JVMs that whose main class/jar name, or system properties match the provided regex.

    * Removal of options<br>

        * The deprecated `--arg` option has been removed.
        * The `-i`/`--include`, `-e`/`exclude` options have been removed in favor of the `--<include|exclude>-<main|vmargs>` options.
        * The `-p`/`--pid` options have been removed in favor of the `--include-pid` option.

    * Changed behavior of  the `-l`/`--list` option<br> The option now only lists JVMs that match the include/exclude discovery rules. Thus, it can be used to do a dry-run of the matchers without actually performing an attachment. It even works in combination with `--continuous` now. By default, the VM arguments are not printed, but only when the `-a`/`--list-vmargs` option is set.
    * Remove dependency on `jps`<br> Even when matching on the main class name or on system properties,
    * Checks the Java version before attaching to avoid attachment on unsupported JVMs.
* Cassandra instrumentation - [#1712](https://github.com/elastic/apm-agent-java/pull/1712)
* Log correlation supports JBoss Logging - [#1737](https://github.com/elastic/apm-agent-java/pull/1737)
* Update Byte-buddy to `1.11.0` - [#1769](https://github.com/elastic/apm-agent-java/pull/1769)
* Support for user.domain [#1756](https://github.com/elastic/apm-agent-java/pull/1756)
* JAX-RS supports javax.ws.rs.PATCH
* Enabling build and unit tests on Windows - [#1671](https://github.com/elastic/apm-agent-java/pull/1671)
* Migrate some plugins to indy dispatcher [#1369](https://github.com/elastic/apm-agent-java/pull/1369) [#1410](https://github.com/elastic/apm-agent-java/pull/1410) [#1374](https://github.com/elastic/apm-agent-java/pull/1374)

### Fixes [elastic-apm-java-agent-1-23-0-fixes]
* Fixed log correlation for log4j2 - [#1720](https://github.com/elastic/apm-agent-java/pull/1720)
* Fix apm-log4j1-plugin and apm-log4j2-plugin dependency on slf4j - [#1723](https://github.com/elastic/apm-agent-java/pull/1723)
* Avoid systematic `MessageNotWriteableException` error logging, now only visible in `debug` - [#1715](https://github.com/elastic/apm-agent-java/pull/1715) and [#1730](https://github.com/elastic/apm-agent-java/pull/1730)
* Fix rounded number format for non-english locales - [#1728](https://github.com/elastic/apm-agent-java/pull/1728)
* Fix `NullPointerException` on legacy Apache client instrumentation when host is `null` - [#1746](https://github.com/elastic/apm-agent-java/pull/1746)
* Apply consistent proxy class exclusion heuristic - [#1738](https://github.com/elastic/apm-agent-java/pull/1738)
* Fix micrometer serialization error - [#1741](https://github.com/elastic/apm-agent-java/pull/1741)
* Optimize & avoid `ensureInstrumented` deadlock by skipping stack-frame computation for Java7+ bytecode - [#1758](https://github.com/elastic/apm-agent-java/pull/1758)
* Fix instrumentation plugins loading on Windows - [#1671](https://github.com/elastic/apm-agent-java/pull/1671)

## 1.22.0 [elastic-apm-java-agent-1-22-0-release-notes]
**Release date:** March 24, 2021
* Introducing a new mechanism to ease the development of community instrumentation plugins. See [`plugins_dir` (experimental)](/reference/config-core.md#config-plugins-dir) for more details. This configuration was already added in 1.18.0, but more extensive and continuous integration testing allows us to expose it now. It is still marked as "experimental" though, meaning that future changes in the mechanism may break early contributed plugins. However, we highly encourage our community to try it out and we will do our best to assist with such efforts.
* Deprecating `ignore_user_agents` in favour of `transaction_ignore_user_agents`, maintaining the same functionality - [#1644](https://github.com/elastic/apm-agent-java/pull/1644)
* Update existing Hibernate Search 6 instrumentation to the final relase
* The [`use_path_as_transaction_name`]/apm-agent-java/docs/reference/config-http.md#config-use-path-as-transaction-name) option is now dynamic
* Flushing internal and micrometer metrics before the agent shuts down - [#1658](https://github.com/elastic/apm-agent-java/pull/1658)
* Support for OkHttp 4.4+ -  [#1672](https://github.com/elastic/apm-agent-java/pull/1672)
* Adding capability to automatically create ECS-JSON-formatted version of the original application log files, through the [`log_ecs_reformatting` ([1.22.0] experimental)]/apm-agent-java/docs/reference/config-logging.md#config-log-ecs-reformatting) config option. This allows effortless ingestion of logs to Elasticsearch without any further configuration. Supports log4j1, log4j2 and Logback. [#1261](https://github.com/elastic/apm-agent-java/pull/1261)
* Add support to Spring AMQP - [#1657](https://github.com/elastic/apm-agent-java/pull/1657)
* Adds the ability to automatically configure usage of the OpenTracing bridge in systems using ServiceLoader - [#1708](https://github.com/elastic/apm-agent-java/pull/1708)
* Update to async-profiler 1.8.5 - includes a fix to a Java 7 crash and enhanced safe mode to better deal with corrupted stack frames.
* Add a warning on startup when `-Xverify:none` or `-noverify` flags are set as this can lead to crashes that are very difficult to debug - [#1593](https://github.com/elastic/apm-agent-java/pull/1593). In an upcoming version, the agent will not start when these flags are set, unless the system property `elastic.apm.disable_bootstrap_checks` is set to true.

### Fixes [elastic-apm-java-agent-1-22-0-fixes]
* fix sample rate rounded to zero when lower than precision - [#1655](https://github.com/elastic/apm-agent-java/pull/1655)
* fixed a couple of bugs with the external plugin mechanism (not documented until now) - [#1660](https://github.com/elastic/apm-agent-java/pull/1660)
* Fix runtime attach conflict with multiple users - [#1704](https://github.com/elastic/apm-agent-java/pull/1704)

## 1.21.0 [elastic-apm-java-agent-1-21-0-release-notes]
**Release date:** February 9, 2021

### Features and enhancements [elastic-apm-java-agent-1-21-0-features-enhancements]
* Add cloud provider metadata to reported events, see [spec](https://github.com/elastic/apm/blob/master/specs/agents/metadata.md#cloud-provider-metadata) for details. By default, the agent will try to automatically detect the cloud provider on startup, but this can be configured through the [`cloud_provider`](/reference/config-core.md#config-cloud-provider) config option - [#1599](https://github.com/elastic/apm-agent-java/pull/1599)
* Add span & transaction `outcome` field to improve error rate calculations - [#1613](https://github.com/elastic/apm-agent-java/pull/1613)

### Fixes [elastic-apm-java-agent-1-21-0-fixes]
* Fixing crashes observed in Java 7 at sporadic timing by applying a few seconds delay on bootstrap - [#1594](https://github.com/elastic/apm-agent-java/pull/1594)
* Fallback to using "TLS" `SSLContext` when "SSL" is not available - [#1633](https://github.com/elastic/apm-agent-java/pull/1633)
* Fixing agent startup failure with `NullPointerException` thrown by Byte-buddy’s `MultipleParentClassLoader` - [#1647](https://github.com/elastic/apm-agent-java/pull/1647)
* Fix cached type resolution triggering `ClassCastException` - [#1649](https://github.com/elastic/apm-agent-java/pull/1649)

## 1.20.0 [elastic-apm-java-agent-1-20-0-release-notes]
**Release date:** January 7, 2021

### Features and enhancements [elastic-apm-java-agent-1-20-0-features-enhancements]
* Add support for RabbitMQ clients - [#1328](https://github.com/elastic/apm-agent-java/pull/1328)
* Migrate some plugins to indy dispatcher [#1405](https://github.com/elastic/apm-agent-java/pull/1405) [#1394](https://github.com/elastic/apm-agent-java/pull/1394)

### Fixes [elastic-apm-java-agent-1-20-0-fixes]
* Fix small memory allocation regression introduced with tracestate header [#1508](https://github.com/elastic/apm-agent-java/pull/1508)
* Fix `NullPointerException` from `WeakConcurrentMap.put` through the Elasticsearch client instrumentation - [#1531](https://github.com/elastic/apm-agent-java/pull/1531)
* Sending `transaction_id` and `parent_id` only for events that contain a valid `trace_id` as well - [#1537](https://github.com/elastic/apm-agent-java/pull/1537)
* Fix `ClassNotFoundError` with old versions of Spring resttemplate [#1524](https://github.com/elastic/apm-agent-java/pull/1524)
* Fix Micrometer-driven metrics validation errors by the APM Server when sending with illegal values - [#1559](https://github.com/elastic/apm-agent-java/pull/1559)
* Serialize all stack trace frames when setting `stack_trace_limit=-1` instead of none - [#1571](https://github.com/elastic/apm-agent-java/pull/1571)
* Fix `UnsupportedOperationException` when calling `ServletContext.getClassLoader()` - [#1576](https://github.com/elastic/apm-agent-java/pull/1576)
* Fix improper request body capturing - [#1579](https://github.com/elastic/apm-agent-java/pull/1579)
* Avoid `NullPointerException` due to null return values instrumentation advices - [#1601](https://github.com/elastic/apm-agent-java/pull/1601)
* Update async-profiler to 1.8.3 [1602](https://github.com/elastic/apm-agent-java/pull/1602)
* Use null-safe data structures to avoid `NullPointerException` [1597](https://github.com/elastic/apm-agent-java/pull/1597)
* Fix memory leak in sampling profiler mechanism - [#1592](https://github.com/elastic/apm-agent-java/pull/1592)

## 1.19.0 [elastic-apm-java-agent-1-19-0-release-notes]
**Release date:** November 10, 2020

### Features and enhancements [elastic-apm-java-agent-1-19-0-features-enhancements]
* The agent version now includes a git hash if it’s a snapshot version. This makes it easier to differ distinct snapshot builds of the same version. Example: `1.18.1-SNAPSHOT.4655910`
* Add support for sampling weight with propagation in `tracestate` W3C header [#1384](https://github.com/elastic/apm-agent-java/pull/1384)
* Adding two more valid options to the `log_level` config: `WARNING` (equivalent to `WARN`) and `CRITICAL` (will be treated as `ERROR`) - [1431](https://github.com/elastic/apm-agent-java/pull/1431)
* Add the ability to disable Servlet-related spans for `INCLUDE`, `FORWARD` and `ERROR` dispatches (without affecting basic Servlet capturing) by adding `servlet-api-dispatch` to [`disable_instrumentations` ([1.0.0])](/reference/config-core.md#config-disable-instrumentations) - [1448](https://github.com/elastic/apm-agent-java/pull/1448)
* Add Sampling Profiler support for AArch64 architectures - [1443](https://github.com/elastic/apm-agent-java/pull/1443)
* Support proper transaction naming when using Spring’s `ServletWrappingController` - [#1461](https://github.com/elastic/apm-agent-java/pull/1461)
* Update async-profiler to 1.8.2 [1471](https://github.com/elastic/apm-agent-java/pull/1471)
* Update existing Hibernate Search 6 instrumentation to work with the latest CR1 release
* Deprecating the `addLabel` public API in favor of `setLabel` (still supporting `addLabel`) - [#1449](https://github.com/elastic/apm-agent-java/pull/1449)
* Migrate some plugins to indy dispatcher [1404](https://github.com/elastic/apm-agent-java/pull/1404) [1411](https://github.com/elastic/apm-agent-java/pull/1411)
* Replace System Rules with System Lambda [#1434](https://github.com/elastic/apm-agent-java/pull/1434)

### Fixes [elastic-apm-java-agent-1-19-0-fixes]
* Fix `HttpUrlConnection` instrumentation issue (affecting distributed tracing as well) when using HTTPS without using `java.net.HttpURLConnection#disconnect` - [1447](https://github.com/elastic/apm-agent-java/pull/1447)
* Fixes class loading issue that can occur when deploying multiple applications to the same application server - [#1458](https://github.com/elastic/apm-agent-java/pull/1458)
* Fix ability to disable agent on startup wasn’t working for runtime attach [1444](https://github.com/elastic/apm-agent-java/pull/1444)
* Avoid `UnsupportedOperationException` on some spring application startup [1464](https://github.com/elastic/apm-agent-java/pull/1464)
* Fix ignored runtime attach `config_file` [1469](https://github.com/elastic/apm-agent-java/pull/1469)
* Fix `IllegalAccessError: Module 'java.base' no access to: package 'java.lang'...` in J9 VMs of Java version >= 9 - [#1468](https://github.com/elastic/apm-agent-java/pull/1468)
* Fix JVM version parsing on HP-UX [#1477](https://github.com/elastic/apm-agent-java/pull/1477)
* Fix Spring-JMS transactions lifecycle management when using multiple concurrent consumers - [#1496](https://github.com/elastic/apm-agent-java/pull/1496)

## 1.18.1 [elastic-apm-java-agent-1-18-1-release-notes]
**Release date:** October 6, 2020

### Features and enhancements [elastic-apm-java-agent-1-18-1-features-enhancements]
* Migrate some plugins to indy dispatcher [1362](https://github.com/elastic/apm-agent-java/pull/1362) [1366](https://github.com/elastic/apm-agent-java/pull/1366) [1363](https://github.com/elastic/apm-agent-java/pull/1363) [1383](https://github.com/elastic/apm-agent-java/pull/1383) [1368](https://github.com/elastic/apm-agent-java/pull/1368) [1364](https://github.com/elastic/apm-agent-java/pull/1364) [1365](https://github.com/elastic/apm-agent-java/pull/1365) [1367](https://github.com/elastic/apm-agent-java/pull/1367) [1371](https://github.com/elastic/apm-agent-java/pull/1371)

### Fixes [elastic-apm-java-agent-1-18-1-fixes]
* Fix instrumentation error for HttpClient - [#1402](https://github.com/elastic/apm-agent-java/pull/1402)
* Eliminate `unsupported class version error` messages related to loading the Java 11 HttpClient plugin in pre-Java-11 JVMs [1397](https://github.com/elastic/apm-agent-java/pull/1397)
* Fix rejected metric events by APM Server with response code 400 due to data validation error - sanitizing Micrometer metricset tag keys - [1413](https://github.com/elastic/apm-agent-java/pull/1413)
* Fix invalid micrometer metrics with non-numeric values [1419](https://github.com/elastic/apm-agent-java/pull/1419)
* Fix `NoClassDefFoundError` with JDBC instrumentation plugin [1409](https://github.com/elastic/apm-agent-java/pull/1409)
* Apply `disable_metrics` config to Micrometer metrics - [1421](https://github.com/elastic/apm-agent-java/pull/1421)
* Remove cgroup `inactive_file.bytes` metric according to spec [1422](https://github.com/elastic/apm-agent-java/pull/1422)

## 1.18.0 [elastic-apm-java-agent-1-18-0-release-notes]
**Release date:** September 8, 2020

### Features and enhancements [elastic-apm-java-agent-1-18-0-features-enhancements]
* Enabling instrumentation of classes compiled with Java 1.4. This is reverting the restriction of instrumenting only bytecode of Java 1.5 or higher ([#320](https://github.com/elastic/apm-agent-java/pull/320)), which was added due to potential `VerifyError`. Such errors should be avoided now by the usage of `TypeConstantAdjustment` - [#1317](https://github.com/elastic/apm-agent-java/pull/1317)
* Enabling agent to work without attempting any communication with APM server, by allowing setting `server_urls` with an empty string - [#1295](https://github.com/elastic/apm-agent-java/pull/1295)
* Add [micrometer support]/apm-agent-java/docs/reference/metrics.md#metrics-micrometer) - [#1303](https://github.com/elastic/apm-agent-java/pull/1303)
* Add `profiling_inferred_spans_lib_directory` option to override the default temp directory used for exporting the async-profiler library. This is useful for server-hardened environments where `/tmp` is often configured with `noexec`, leading to `java.lang.UnsatisfiedLinkError` errors - [#1350](https://github.com/elastic/apm-agent-java/pull/1350)
* Create spans for Servlet dispatches to FORWARD, INCLUDE and ERROR - [#1212](https://github.com/elastic/apm-agent-java/pull/1212)
* Support JDK 11 HTTPClient - [#1307](https://github.com/elastic/apm-agent-java/pull/1307)
* Lazily create profiler temporary files [#1360](https://github.com/elastic/apm-agent-java/pull/1360)
* Convert the followings to Indy Plugins (see details in [1.18.0-rc1 relase notes](/release-notes/index.md#elastic-apm-java-agent-1-18-0-release-notes)): gRPC, AsyncHttpClient, Apache HttpClient
* The agent now collects cgroup memory metrics (see details in [Metrics page](/reference/metrics.md#metrics-cgroup))
* Update async-profiler to 1.8.1 [#1382](https://github.com/elastic/apm-agent-java/pull/1382)
* Runtime attach install option is promoted to *beta* status (was experimental).
* Experimental support for runtime attachment now also for OSGi containers, JBoss, and WildFly
* New mitigation of OSGi bootdelegation errors (`NoClassDefFoundError`). You can remove any `org.osgi.framework.bootdelegation` related configuration. This release also removes the configuration option `boot_delegation_packages`.
* Overhaul of the `ExecutorService` instrumentation that avoids `ClassCastException` issues - [#1206](https://github.com/elastic/apm-agent-java/pull/1206)
* Support for `ForkJoinPool` and `ScheduledExecutorService` (see [Asynchronous frameworks]/apm-agent-java/docs/reference/supported-technologies.md#supported-async-frameworks))
* Support for `ExecutorService#invokeAny` and `ExecutorService#invokeAll`
* Added support for `java.util.TimerTask` - [#1235](https://github.com/elastic/apm-agent-java/pull/1235)
* Add capturing of request body in Elasticsearch queries: `_msearch`, `_count`, `_msearch/template`, `_search/template`, `_rollup_search` - [#1222](https://github.com/elastic/apm-agent-java/pull/1222)
* Add [`enabled`](/reference/config-core.md#config-enabled) flag
* Add experimental support for Scala Futures
* The agent now collects heap memory pools metrics - [#1228](https://github.com/elastic/apm-agent-java/pull/1228)

### Fixes [elastic-apm-java-agent-1-18-0-fixes]
* Fixes a `NoClassDefFoundError` in the JMS instrumentation of `MessageListener` - [#1287](https://github.com/elastic/apm-agent-java/pull/1287)
* Fix `/ by zero` error message when setting `server_urls` with an empty string - [#1295](https://github.com/elastic/apm-agent-java/pull/1295)
* Fix `ClassNotFoundException` or `ClassCastException` in some cases where special log4j configurations are used - [#1322](https://github.com/elastic/apm-agent-java/pull/1322)
* Fix `NumberFormatException` when using early access Java version - [#1325](https://github.com/elastic/apm-agent-java/pull/1325)
* Fix `service_name` config being ignored when set to the same auto-discovered default value - [#1324](https://github.com/elastic/apm-agent-java/pull/1324)
* Fix service name error when updating a web app on a Servlet container - [#1326](https://github.com/elastic/apm-agent-java/pull/1326)
* Fix remote attach *jps* executable not found when *java* binary is symlinked ot a JRE - [#1352](https://github.com/elastic/apm-agent-java/pull/1352)
* Fixes error capturing for log4j2 loggers. Version 1.17.0 introduced a regression.
* Fixes `NullPointerException` related to JAX-RS and Quartz instrumentation - [#1249](https://github.com/elastic/apm-agent-java/pull/1249)
* Expanding k8s pod ID discovery to some formerly non-supported environments
* When `recording` is set to `false`, the agent will not send captured errors anymore.
* Fixes NPE in Dubbo instrumentation that occurs when the application is acting both as a provider and as a consumer - [#1260](https://github.com/elastic/apm-agent-java/pull/1260)
* Adding a delay by default what attaching the agent to Tomcat using the premain route to work around the JUL deadlock issue - [#1262](https://github.com/elastic/apm-agent-java/pull/1262)
* Fixes missing `jboss.as:*` MBeans on JBoss - [#1257](https://github.com/elastic/apm-agent-java/pull/1257)

## 1.17.0 [elastic-apm-java-agent-1-17-0-release-notes]
**Release date:** June 17, 2020

### Features and enhancements [elastic-apm-java-agent-1-17-0-features-enhancements]
* Log files are now rotated after they reach [`log_file_size` ([1.17.0])]/apm-agent-java/docs/reference/config-logging.md#config-log-file-size). There will always be one history file `${log_file}.1`.
* Add [`log_format_sout` ([1.17.0])]/apm-agent-java/docs/reference/config-logging.md#config-log-format-sout) and [`log_format_file` ([1.17.0])]/apm-agent-java/docs/reference/config-logging.md#config-log-format-file) with the options `PLAIN_TEXT` and `JSON`. The latter uses [ecs-logging-java](https://github.com/elastic/ecs-logging-java) to format the logs.
* Exposing [`classes_excluded_from_instrumentation`](/reference/config-core.md#config-classes-excluded-from-instrumentation) config - [#1187](https://github.com/elastic/apm-agent-java/pull/1187)
* Add support for naming transactions based on Grails controllers. Supports Grails 3+ - [#1171](https://github.com/elastic/apm-agent-java/pull/1171)
* Add support for the Apache/Alibaba Dubbo RPC framework
* Async Profiler version upgraded to 1.7.1, with a new debugging flag for the stack frame recovery mechanism - [#1173](https://github.com/elastic/apm-agent-java/pull/1173)

### Fixes [elastic-apm-java-agent-1-17-0-fixes]
* Fixes `IndexOutOfBoundsException` that can occur when profiler-inferred spans are enabled. This also makes the profiler more resilient by just removing the call tree related to the exception (which might be in an invalid state) as opposed to stopping the profiler when an exception occurs.
* Fix `NumberFormatException` when parsing Ingres/Actian JDBC connection strings - [#1198](https://github.com/elastic/apm-agent-java/pull/1198)
* Prevent agent from overriding JVM configured truststore when not using HTTPS for communication with APM server - [#1203](https://github.com/elastic/apm-agent-java/pull/1203)
* Fix `java.lang.IllegalStateException` with `jps` JVM when using continuous runtime attach - [1205](https://github.com/elastic/apm-agent-java/pull/1205)
* Fix agent trying to load log4j2 plugins from application - [1214](https://github.com/elastic/apm-agent-java/pull/1214)
* Fix memory leak in gRPC instrumentation plugin - [1196](https://github.com/elastic/apm-agent-java/pull/1196)
* Fix HTTPS connection failures when agent is configured to use HTTPS to communicate with APM server [1209](https://github.com/elastic/apm-agent-java/pull/1209)

## 1.16.0 [elastic-apm-java-agent-1-16-0-release-notes]
**Release date:** May 13, 2020

### Features and enhancements [elastic-apm-java-agent-1-16-0-features-enhancements]
* The log correlation feature now adds `error.id` to the MDC. See [Logging frameworks](/reference/supported-technologies.md#supported-logging-frameworks) for details. - [#1050](https://github.com/elastic/apm-agent-java/pull/1050)
* Deprecating the `incubating` tag in favour of the `experimental` tag. This is not a breaking change, so former [`disable_instrumentation`](/reference/config-core.md#config-disable-instrumentations) configuration containing the `incubating` tag will still be respected - [#1123](https://github.com/elastic/apm-agent-java/pull/1123)
* Add a `--without-emulated-attach` option for runtime attachment to allow disabling this feature as a workaround.
* Add workaround for JDK bug JDK-8236039 with TLS 1.3 [#1149](https://github.com/elastic/apm-agent-java/pull/1149)
* Add log level `OFF` to silence agent logging
* Adds [`span_min_duration`](/reference/config-core.md#config-span-min-duration) option to exclude fast executing spans. When set together with one of the more specific thresholds - `trace_methods_duration_threshold` or `profiling_inferred_spans_min_duration`, the higher threshold will determine which spans will be discarded.
* Automatically instrument quartz jobs from the quartz-jobs artifact [#1170](https://github.com/elastic/apm-agent-java/pull/1170)
* Perform re-parenting of regular spans to be a child of profiler-inferred spans. Requires APM Server and Kibana 7.8.0. [#1117](https://github.com/elastic/apm-agent-java/pull/1117)
* Upgrade Async Profiler version to 1.7.0

### Fixes [elastic-apm-java-agent-1-16-0-fixes]
* When Servlet-related Exceptions are handled through exception handlers that return a 200 status code, agent shouldn’t override with 500 - [#1103](https://github.com/elastic/apm-agent-java/pull/1103)
* Exclude Quartz 1 from instrumentation to avoid `IncompatibleClassChangeError: Found class org.quartz.JobExecutionContext, but interface was expected` - [#1108](https://github.com/elastic/apm-agent-java/pull/1108)
* Fix breakdown metrics span sub-types [#1113](https://github.com/elastic/apm-agent-java/pull/1113)
* Fix flaky gRPC server instrumentation [#1122](https://github.com/elastic/apm-agent-java/pull/1122)
* Fix side effect of calling `Statement.getUpdateCount` more than once [#1139](https://github.com/elastic/apm-agent-java/pull/1139)
* Stop capturing JDBC affected rows count using `Statement.getUpdateCount` to prevent unreliable side-effects [#1147](https://github.com/elastic/apm-agent-java/pull/1147)
* Fix OpenTracing error tag handling (set transaction error result when tag value is `true`) [#1159](https://github.com/elastic/apm-agent-java/pull/1159)
* Due to a bug in the build we didn’t include the gRPC plugin in the build so far
* `java.lang.ClassNotFoundException: Unable to load class 'jdk.internal...'` is thrown when tracing specific versions of Atlassian systems [#1168](https://github.com/elastic/apm-agent-java/pull/1168)
* Make sure spans are kept active during `AsyncHandler` methods in the `AsyncHttpClient`
* CPU and memory metrics are sometimes not reported properly when using IBM J9 [#1148](https://github.com/elastic/apm-agent-java/pull/1148)
* `NullPointerException` thrown by the agent on WebLogic [#1142](https://github.com/elastic/apm-agent-java/pull/1142)

## 1.15.0 [elastic-apm-java-agent-1-15-0-release-notes]
**Release date:** March 27, 2020

### Features and enhancements [elastic-apm-java-agent-1-15-0-features-enhancements]
* Gracefully abort agent init when running on a known Java 8 buggy JVM [#1075](https://github.com/elastic/apm-agent-java/pull/1075).
* Add support for [Redis Redisson client]/apm-agent-java/docs/reference/supported-technologies.md#supported-databases)
* Makes [`instrument` ([1.0.0])](/reference/config-core.md#config-instrument), [`trace_methods` ([1.0.0])](/reference/config-core.md#config-trace-methods), and [`disable_instrumentations` ([1.0.0])](/reference/config-core.md#config-disable-instrumentations) dynamic. Note that changing these values at runtime can slow down the application temporarily.
* Do not instrument Servlet API before 3.0 [#1077](https://github.com/elastic/apm-agent-java/pull/1077)
* Add support for API keys for apm backend authentication [#1083](https://github.com/elastic/apm-agent-java/pull/1083)
* Add support for [gRPC]/apm-agent-java/docs/reference/supported-technologies.md#supported-rpc-frameworks) client & server instrumentation [#1019](https://github.com/elastic/apm-agent-java/pull/1019)
* Deprecating `active` configuration option in favor of `recording`. Setting `active` still works as it’s now an alias for `recording`.

### Fixes [elastic-apm-java-agent-1-15-0-fixes]
* When JAX-RS-annotated method delegates to another JAX-RS-annotated method, transaction name should include method A - [#1062](https://github.com/elastic/apm-agent-java/pull/1062)
* Fixed bug that prevented an APM Error from being created when calling `org.slf4j.Logger#error` - [#1049](https://github.com/elastic/apm-agent-java/pull/1049)
* Wrong address in JDBC spans for Oracle, MySQL and MariaDB when multiple hosts are configured - [#1082](https://github.com/elastic/apm-agent-java/pull/1082)
* Document and re-order configuration priorities [#1087](https://github.com/elastic/apm-agent-java/pull/1087)
* Improve heuristic for `service_name` when not set through config [#1097](https://github.com/elastic/apm-agent-java/pull/1097)

## 1.14.0 [elastic-apm-java-agent-1-14-0-release-notes]
**Release date:** March 4, 2020

### Features and enhancements [elastic-apm-java-agent-1-14-0-features-enhancements]
* Support for the official [W3C](https://www.w3.org/TR/trace-context) `traceparent` and `tracestate` headers.<br> The agent now accepts both the `elastic-apm-traceparent` and the official `traceparent` header. By default, it sends both headers on outgoing requests, unless [`use_elastic_traceparent_header`](/reference/config-core.md#config-use-elastic-traceparent-header) is set to false.
* Creating spans for slow methods with the help of the sampling profiler [async-profiler](https://github.com/jvm-profiling-tools/async-profiler). This is a low-overhead way of seeing which methods make your transactions slow and a replacement for the `trace_methods` configuration option. See [Java method monitoring]/apm-agent-java/docs/reference/supported-technologies.md#supported-java-methods) for more details
* Adding a Circuit Breaker to pause the agent when stress is detected on the system and resume when the stress is relieved. See [Circuit Breaker]/apm-agent-java/docs/reference/overhead-performance-tuning.md#circuit-breaker) and [#1040](https://github.com/elastic/apm-agent-java/pull/1040) for more info.
* `Span#captureException` and `Transaction#captureException` in public API return reported error id - [#1015](https://github.com/elastic/apm-agent-java/pull/1015)

### Fixes [elastic-apm-java-agent-1-14-0-fixes]
* java.lang.IllegalStateException: Cannot resolve type description for <com.another.commercial.apm.agent.Class> - [#1037](https://github.com/elastic/apm-agent-java/pull/1037)
* properly handle `java.sql.SQLException` for unsupported JDBC features [#1035](https://github.com/elastic/apm-agent-java/pull/) [#1025](https://github.com/elastic/apm-agent-java/issues/1025)

## 1.13.0 [elastic-apm-java-agent-1-13-0-release-notes]
**Release date:** February 11, 2020

### Features and enhancements [elastic-apm-java-agent-1-13-0-features-enhancements]
* Add support for [Redis Lettuce client](/reference/supported-technologies.md#supported-databases)
* Add `context.message.age.ms` field for JMS message receiving spans and transactions - [#970](https://github.com/elastic/apm-agent-java/pull/970)
* Instrument log4j2 Logger#error(String, Throwable) ([#919](https://github.com/elastic/apm-agent-java/pull/919)) Automatically captures exceptions when calling `logger.error("message", exception)`
* Add instrumentation for external process execution through `java.lang.Process` and Apache `commons-exec` - [#903](https://github.com/elastic/apm-agent-java/pull/903)
* Add `destination` fields to exit span contexts - [#976](https://github.com/elastic/apm-agent-java/pull/976)
* Removed `context.message.topic.name` field - [#993](https://github.com/elastic/apm-agent-java/pull/993)
* Add support for Kafka clients - [#981](https://github.com/elastic/apm-agent-java/pull/981)
* Add support for binary `traceparent` header format (see the [spec](https://github.com/elastic/apm/blob/master/docs/agent-development.md#Binary-Fields) for more details) - [#1009](https://github.com/elastic/apm-agent-java/pull/1009)
* Add support for log correlation for log4j and log4j2, even when not used in combination with slf4j. See [Logging frameworks]/apm-agent-java/docs/reference/supported-technologies.md#supported-logging-frameworks) for details.

### Fixes [elastic-apm-java-agent-1-13-0-fixes]
* Fix parsing value of `trace_methods` configuration property [#930](https://github.com/elastic/apm-agent-java/pull/930)
* Workaround for `java.util.logging` deadlock [#965](https://github.com/elastic/apm-agent-java/pull/965)
* JMS should propagate traceparent header when transactions are not sampled [#999](https://github.com/elastic/apm-agent-java/pull/999)
* Spans are not closed if JDBC implementation does not support `getUpdateCount` [#1008](https://github.com/elastic/apm-agent-java/pull/1008)

## 1.12.0 [elastic-apm-java-agent-1-12-0-release-notes]
**Release date:** November 21, 2019

### Features and enhancements [elastic-apm-java-agent-1-12-0-features-enhancements]
* JMS Enhancements [#911](https://github.com/elastic/apm-agent-java/pull/911):

    * Add special handling for temporary queues/topics
    * Capture message bodies of text Messages

        * Rely on the existing `ELASTIC_APM_CAPTURE_BODY` agent config option (off by default).
        * Send as `context.message.body`
        * Limit size to 10000 characters. If longer than this size, trim to 9999 and append with ellipsis

    * Introduce the `ignore_message_queues` configuration to disable instrumentation (message tagging) for specific queues/topics as suggested in [#710](https://github.com/elastic/apm-agent-java/pull/710)
    * Capture predefined message headers and all properties

        * Rely on the existing `ELASTIC_APM_CAPTURE_HEADERS` agent config option.
        * Send as `context.message.headers`
        * Sanitize sensitive headers/properties based on the `sanitize_field_names` config option

* Added support for the MongoDB sync driver. See [supported data stores](/reference/supported-technologies.md#supported-databases).

### Fixes [elastic-apm-java-agent-1-12-0-fixes]
* JDBC regression- `PreparedStatement#executeUpdate()` and `PreparedStatement#executeLargeUpdate()` are not traced [#918](https://github.com/elastic/apm-agent-java/pull/918)
* When systemd cgroup driver is used, the discovered Kubernetes pod UID contains "_" instead of "-" [#920](https://github.com/elastic/apm-agent-java/pull/920)
* DB2 jcc4 driver is not traced properly [#926](https://github.com/elastic/apm-agent-java/pull/926)

## 1.11.0 [elastic-apm-java-agent-1-11-0-release-notes]
**Release date:** October 31, 2019

### Features and enhancements [elastic-apm-java-agent-1-11-0-features-enhancements]
* Add the ability to configure a unique name for a JVM within a service through the [`service_node_name`](/reference/config-core.md#config-service-node-name) config option.
* Add ability to ignore some exceptions to be reported as errors <<config-ignore-exceptions[ignore_exceptions]
* Applying new logic for JMS `javax.jms.MessageConsumer#receive` so that, instead of the transaction created for the polling method itself (ie from `receive` start to end), the agent will create a transaction attempting to capture the code executed during actual message handling. This logic is suitable for environments where polling APIs are invoked within dedicated polling threads. This polling transaction creation strategy can be reversed through a configuration option (`message_polling_transaction_strategy`) that is not exposed in the properties file by default.
* Send IP obtained through `javax.servlet.ServletRequest#getRemoteAddr()` in `context.request.socket.remote_address` instead of parsing from headers [#889](https://github.com/elastic/apm-agent-java/pull/889)
* Added `ElasticApmAttacher.attach(String propertiesLocation)` to specify a custom properties location
* Logs message when `transaction_max_spans` has been exceeded [#849](https://github.com/elastic/apm-agent-java/pull/849)
* Report the number of affected rows by a SQL statement (UPDATE,DELETE,INSERT) in *affected_rows* span attribute [#707](https://github.com/elastic/apm-agent-java/pull/707)
* Add [`@Traced`](/reference/public-api.md) annotation which either creates a span or a transaction, depending on the context
* Report JMS destination as a span/transaction context field [#906](https://github.com/elastic/apm-agent-java/pull/906)
* Added [`capture_jmx_metrics`](/reference/config-jmx.md#config-capture-jmx-metrics) configuration option

### Fixes [elastic-apm-java-agent-1-11-0-fixes]
* JMS creates polling transactions even when the API invocations return without a message
* Support registering MBeans which are added after agent startup

## 1.10.0 [elastic-apm-java-agent-1-10-0-release-notes]
**Release date:** September 30, 2019

### Features and enhancements [elastic-apm-java-agent-1-10-0-features-enhancements]
* Add ability to manually specify reported [hostname](/reference/config-core.md#config-hostname)
* Add support for [Redis Jedis client]/apm-agent-java/docs/reference/supported-technologies.md#supported-databases).
* Add support for identifying target JVM to attach apm agent to using JVM property. See also the documentation of the [`--include` and `--exclude` flags]/apm-agent-java/docs/reference/setup-attach-cli.md#setup-attach-cli-usage-options)
* Added [`capture_jmx_metrics`]/apm-agent-java/docs/reference/config-jmx.md#config-capture-jmx-metrics) configuration option
* Improve servlet error capture [#812](https://github.com/elastic/apm-agent-java/pull/812) Among others, now also takes Spring MVC `@ExceptionHandler`s into account
* Instrument Logger#error(String, Throwable) [#821](https://github.com/elastic/apm-agent-java/pull/821) Automatically captures exceptions when calling `logger.error("message", exception)`
* Easier log correlation with [https://github.com/elastic/java-ecs-logging](https://github.com/elastic/java-ecs-logging). See [docs]https://www.elastic.co/guide/en/apm/agent/java/current/log-correlation.html).
* Avoid creating a temp agent file for each attachment [#859](https://github.com/elastic/apm-agent-java/pull/859)
* Instrument `View#render` instead of `DispatcherServlet#render` [#829](https://github.com/elastic/apm-agent-java/pull/829) This makes the transaction breakdown graph more useful. Instead of `dispatcher-servlet`, the graph now shows a type which is based on the view name, for example, `FreeMarker` or `Thymeleaf`.

### Fixes [elastic-apm-java-agent-1-10-0-fixes]
* Error in log when setting [server_urls](/reference/config-reporter.md#config-server-urls) to an empty string - `co.elastic.apm.agent.configuration.ApmServerConfigurationSource - Expected previousException not to be null`
* Avoid terminating the TCP connection to APM Server when polling for configuration updates [#823](https://github.com/elastic/apm-agent-java/pull/823)

## 1.9.0 [elastic-apm-java-agent-1-9-0-release-notes]
**Release date:** August 22, 2019

### Features and enhancements [elastic-apm-java-agent-1-9-0-features-enhancements]
* Upgrading supported OpenTracing version from 0.31 to 0.33
* Added annotation and meta-annotation matching support for `trace_methods`, for example:

    * `public @java.inject.* org.example.*` (for annotation)
    * `public @@javax.enterprise.context.NormalScope org.example.*` (for meta-annotation)

* The runtime attachment now also works when the `tools.jar` or the `jdk.attach` module is not available. This means you don’t need a full JDK installation - the JRE is sufficient. This makes the runtime attachment work in more environments such as minimal Docker containers. Note that the runtime attachment currently does not work for OSGi containers like those used in many application servers such as JBoss and WildFly. See the [documentation]/apm-agent-java/docs/reference/setup-attach-cli.md) for more information.
* Support for Hibernate Search

### Fixes [elastic-apm-java-agent-1-9-0-fixes]
* A warning in logs saying APM server is not available when using 1.8 with APM server 6.x. Due to that, agent 1.8.0 will silently ignore non-string labels, even if used with APM server of versions 6.7.x or 6.8.x that support such. If APM server version is <6.7 or 7.0+, this should have no effect. Otherwise, upgrade the Java agent to 1.9.0+.
* `ApacheHttpAsyncClientInstrumentation` matching increases startup time considerably
* Log correlation feature is active when `active==false`
* Tomcat’s memory leak prevention mechanism is causing a…  memory leak. JDBC statement map is leaking in Tomcat if the application that first used it is undeployed/redeployed. See [this related discussion](https://discuss.elastic.co/t/elastic-apm-agent-jdbchelper-seems-to-use-a-lot-of-memory/195295).

## 1.8.0 [elastic-apm-java-agent-1-8-0-release-notes]
**Release date:** July 30, 2019

### Features and enhancements [elastic-apm-java-agent-1-8-0-features-enhancements]
* Added support for tracking [time spent by span type](https://www.elastic.co/guide/en/kibana/7.3/transactions.md). Can be disabled by setting [`breakdown_metrics`](/reference/config-core.md#config-breakdown-metrics) to `false`.
* Added support for [central configuration](https://www.elastic.co/guide/en/kibana/7.3/agent-configuration.md). Can be disabled by setting [`central_config`](/reference/config-core.md#config-central-config) to `false`.
* Added support for Spring’s JMS flavor - instrumenting `org.springframework.jms.listener.SessionAwareMessageListener`
* Added support to legacy ApacheHttpClient APIs (which adds support to Axis2 configured to use ApacheHttpClient)
* Added support for setting [`server_urls`](/reference/config-reporter.md#config-server-urls) dynamically via properties file [#723](https://github.com/elastic/apm-agent-java/pull/723)
* Added [`config_file`](/reference/config-core.md#config-config-file) option
* Added option to use `@javax.ws.rs.Path` value as transaction name [`use_jaxrs_path_as_transaction_name`]/apm-agent-java/docs/reference/config-jax-rs.md#config-use-jaxrs-path-as-transaction-name)
* Instrument quartz jobs [docs]/apm-agent-java/docs/reference/supported-technologies.md#supported-scheduling-frameworks)
* SQL parsing improvements [#696](https://github.com/elastic/apm-agent-java/pull/696)
* Introduce priorities for transaction name [#748](https://github.com/elastic/apm-agent-java/pull/748). Now uses the path as transaction name if [`use_path_as_transaction_name`]/apm-agent-java/docs/reference/config-http.md#config-use-path-as-transaction-name) is set to `true` rather than `ServletClass#doGet`. But if a name can be determined from a high level framework, like Spring MVC, that takes precedence. User-supplied names from the API always take precedence over any others.
* Use JSP path name as transaction name as opposed to the generated servlet class name [#751](https://github.com/elastic/apm-agent-java/pull/751)

### Fixes [elastic-apm-java-agent-1-8-0-fixes]
* Some JMS Consumers and Producers are filtered due to class name filtering in instrumentation matching
* Jetty: When no display name is set and context path is "/" transaction service names will now correctly fall back to configured values
* JDBC’s `executeBatch` is not traced
* Drops non-String labels when connected to APM Server < 6.7 to avoid validation errors [#687](https://github.com/elastic/apm-agent-java/pull/687)
* Parsing container ID in cloud foundry garden [#695](https://github.com/elastic/apm-agent-java/pull/695)
* Automatic instrumentation should not override manual results [#752](https://github.com/elastic/apm-agent-java/pull/752)

## 1.7.0 [elastic-apm-java-agent-1-7-0-release-notes]
**Release date:** June 13, 2019

### Features and enhancements [elastic-apm-java-agent-1-7-0-features-enhancements]
* Added the `trace_methods_duration_threshold` config option. When using the `trace_methods` config option with wild cards, this enables considerable reduction of overhead by limiting the number of spans captured and reported (see more details in config documentation). NOTE: Using wildcards is still not the recommended approach for the `trace_methods` feature.
* Add `Transaction#addCustomContext(String key, String|Number|boolean value)` to public API
* Added support for AsyncHttpClient 2.x
* Added [`global_labels`](/reference/config-core.md#config-global-labels) configuration option. This requires APM Server 7.2+.
* Added basic support for JMS- distributed tracing for basic scenarios of `send`, `receive`, `receiveNoWait` and `onMessage`. Both Queues and Topics are supported. Async `send` APIs are not supported in this version. NOTE: This feature is currently marked as "experimental" and is disabled by default. In order to enable, it is required to set the [`disable_instrumentations`](/reference/config-core.md#config-disable-instrumentations) configuration property to an empty string.
* Improved OSGi support: added a configuration option for `bootdelegation` packages [#641](https://github.com/elastic/apm-agent-java/pull/641)
* Better span names for SQL spans. For example, `SELECT FROM user` instead of just `SELECT` [#633](https://github.com/elastic/apm-agent-java/pull/633)

### Fixes [elastic-apm-java-agent-1-7-0-fixes]
* ClassCastException related to async instrumentation of Pilotfish Executor causing thread hang (applied workaround)
* NullPointerException when computing Servlet transaction name with null HTTP method name
* FileNotFoundException when trying to find implementation version of jar with encoded URL
* NullPointerException when closing Apache AsyncHttpClient request producer
* Fixes loading of `elasticapm.properties` for Spring Boot applications
* Fix startup error on WebLogic 12.2.1.2.0 [#649](https://github.com/elastic/apm-agent-java/pull/649)
* Disable metrics reporting and APM Server health check when active=false [#653](https://github.com/elastic/apm-agent-java/pull/653)

## 1.6.1 [elastic-apm-java-agent-1-6-1-release-notes]
**Release date:** April 26, 2019

### Fixes [elastic-apm-java-agent-1-6-1-fixes]
* Fixes transaction name for non-sampled transactions [#581](https://github.com/elastic/apm-agent-java/issues/581)
* Makes log_file option work again [#594](https://github.com/elastic/apm-agent-java/issues/594)
* Async context propagation fixes

    * Fixing some async mechanisms lifecycle issues [#605](https://github.com/elastic/apm-agent-java/issues/605)
    * Fixes exceptions when using WildFly managed executor services [#589](https://github.com/elastic/apm-agent-java/issues/589)
    * Exclude glassfish Executor which does not permit wrapped runnables [#596](https://github.com/elastic/apm-agent-java/issues/596)
    * Exclude DumbExecutor [#598](https://github.com/elastic/apm-agent-java/issues/598)

* Fixes Manifest version reading error to support `jar:file` protocol [#601](https://github.com/elastic/apm-agent-java/issues/601)
* Fixes transaction name for non-sampled transactions [#597](https://github.com/elastic/apm-agent-java/issues/597)
* Fixes potential classloader deadlock by preloading `FileSystems.getDefault()` [#603](https://github.com/elastic/apm-agent-java/issues/603)

## 1.6.0 [elastic-apm-java-agent-1-6-0-release-notes]
**Release date:** April 16, 2019

### Features and enhancements [elastic-apm-java-agent-1-6-0-features-enhancements]
* Java APM Agent became part of the Cloud Foundry Java Buildpack as of [Release v4.19](https://github.com/cloudfoundry/java-buildpack/releases/tag/v4.19)
* Support Apache HttpAsyncClient - span creation and cross-service trace context propagation
* Added the `jvm.thread.count` metric, indicating the number of live threads in the JVM (daemon and non-daemon)
* Added support for WebLogic
* Added support for Spring `@Scheduled` and EJB `@Schedule` annotations - [#569](https://github.com/elastic/apm-agent-java/pull/569)

### Fixes [elastic-apm-java-agent-1-6-0-fixes]
* Avoid that the agent blocks server shutdown in case the APM Server is not available - [#554](https://github.com/elastic/apm-agent-java/pull/554)
* Public API annotations improper retention prevents it from being used with Groovy - [#567](https://github.com/elastic/apm-agent-java/pull/567)
* Eliminate side effects of class loading related to Instrumentation matching mechanism

## 1.5.0 [elastic-apm-java-agent-1-5-0-release-notes]
**Release date:** March 26, 2019

### Features and enhancements [elastic-apm-java-agent-1-5-0-features-enhancements]
* Added property `"allow_path_on_hierarchy"` to JAX-RS plugin, to lookup inherited usage of `@path`
* Support for number and boolean labels in the public API [497](https://github.com/elastic/apm-agent-java/pull/497). This change also renames `tag` to `label` on the API level to be compliant with the [Elastic Common Schema (ECS)](https://github.com/elastic/ecs#-base-fields). The `addTag(String, String)` method is still supported but deprecated in favor of `addLabel(String, String)`. As of version 7.x of the stack, labels will be stored under `labels` in Elasticsearch. Previously, they were stored under `context.tags`.
* Support async queries made by Elasticsearch REST client
* Added `setStartTimestamp(long epochMicros)` and `end(long epochMicros)` API methods to `Span` and `Transaction`, allowing to set custom start and end timestamps.
* Auto-detection of the `service_name` based on the `<display-name>` element of the `web.xml` with a fallback to the servlet context path. If you are using a spring-based application, the agent will use the setting for `spring.application.name` for its `service_name`. See the documentation for [`service_name`](/reference/config-core.md#config-service-name) for more information. Note: this requires APM Server 7.0+. If using previous versions, nothing will change.
* Previously, enabling [`capture_body`](/reference/config-core.md#config-capture-body) could only capture form parameters. Now it supports all UTF-8 encoded plain-text content types. The option [`capture_body_content_types`]/apm-agent-java/docs/reference/config-http.md#config-capture-body-content-types) controls which `Content-Type`s should be captured.
* Support async calls made by OkHttp client (`Call#enqueue`)
* Added support for providing config options on agent attach.

    * CLI example: `--config server_urls=http://localhost:8200,http://localhost:8201`
    * API example: `ElasticApmAttacher.attach(Map.of("server_urls", "http://localhost:8200,http://localhost:8201"));`

### Fixes [elastic-apm-java-agent-1-5-0-fixes]
* Logging integration through MDC is not working properly - [#499](https://github.com/elastic/apm-agent-java/issues/499)
* ClassCastException with adoptopenjdk/openjdk11-openj9 - [#505](https://github.com/elastic/apm-agent-java/issues/505)
* Span count limitation is not working properly - reported [in our forum](https://discuss.elastic.co/t/kibana-apm-not-showing-spans-which-are-visible-in-discover-too-many-spans/171690)
* Java agent causes Exceptions in Alfresco cluster environment due to failure in the instrumentation of Hazelcast `Executor`s - reported [in our forum](https://discuss.elastic.co/t/cant-run-apm-java-agent-in-alfresco-cluster-environment/172962)

## 1.4.0 [elastic-apm-java-agent-1-4-0-release-notes]
**Release date:** February 14, 2019

### Features and enhancements [elastic-apm-java-agent-1-4-0-features-enhancements]
* Added support for sync calls of OkHttp client
* Added support for context propagation for `java.util.concurrent.ExecutorService`s
* The `trace_methods` configuration now allows to omit the method matcher. Example: `com.example.*` traces all classes and methods within the `com.example` package and sub-packages.
* Added support for JSF. Tested on WildFly, WebSphere Liberty and Payara with embedded JSF implementation and on Tomcat and Jetty with MyFaces 2.2 and 2.3
* Introduces a new configuration option `disable_metrics` which disables the collection of metrics via a wildcard expression.
* Support for HttpUrlConnection
* Adds `subtype` and `action` to spans. This replaces former typing mechanism where type, subtype and action were all set through the type in an hierarchical dotted-syntax. In order to support existing API usages, dotted types are parsed into subtype and action, however `Span.createSpan` and `Span.setType` are deprecated starting this version. Instead, type-less spans can be created using the new `Span.startSpan` API and typed spans can be created using the new `Span.startSpan(String type, String subtype, String action)` API
* Support for JBoss EAP 6.4, 7.0, 7.1 and 7.2
* Improved startup times
* Support for SOAP (JAX-WS). SOAP client create spans and propagate context. Transactions are created for `@WebService` classes and `@WebMethod` methods.

### Fixes [elastic-apm-java-agent-1-4-0-fixes]
* Fixes a failure in BitBucket when agent deployed [#349](https://github.com/elastic/apm-agent-java/issues/349)
* Fixes increased CPU consumption [#453](https://github.com/elastic/apm-agent-java/issues/453) and [#443](https://github.com/elastic/apm-agent-java/issues/443)
* Fixed some OpenTracing bridge functionalities that were not working when auto-instrumentation is disabled
* Fixed an error occurring when ending an OpenTracing span before deactivating
* Sending proper `null` for metrics that have a NaN value
* Fixes JVM crash with Java 7 [#458](https://github.com/elastic/apm-agent-java/issues/458)
* Fixes an application deployment failure when using EclipseLink and `trace_methods` configuration [#474](https://github.com/elastic/apm-agent-java/issues/474)

## 1.3.0 [elastic-apm-java-agent-1-3-0-release-notes]
**Release date:** January 10, 2019

### Features and enhancements [elastic-apm-java-agent-1-3-0-features-enhancements]
* The agent now collects system and JVM metrics [#360](https://github.com/elastic/apm-agent-java/pull/360)
* Add API methods `ElasticApm#startTransactionWithRemoteParent` and `Span#injectTraceHeaders` to allow for manual context propagation [#396](https://github.com/elastic/apm-agent-java/pull/396).
* Added `trace_methods` configuration option which lets you define which methods in your project or 3rd party libraries should be traced. To create spans for all `public` methods of classes whose name ends in `Service` which are in a sub-package of `org.example.services` use this matcher: `public org.example.services.*.*Service#*` [#398](https://github.com/elastic/apm-agent-java/pull/398)
* Added span for `DispatcherServlet#render` [#409](https://github.com/elastic/apm-agent-java/pull/409).
* Flush reporter on shutdown to make sure all recorded Spans are sent to the server before the program exits [#397](https://github.com/elastic/apm-agent-java/pull/397)
* Adds Kubernetes [#383](https://github.com/elastic/apm-agent-java/issues/383) and Docker metadata to, enabling correlation with the Kibana Infra UI.
* Improved error handling of the Servlet Async API [#399](https://github.com/elastic/apm-agent-java/issues/399)
* Support async API’s used with AsyncContext.start [#388](https://github.com/elastic/apm-agent-java/issues/388)

### Fixes [elastic-apm-java-agent-1-3-0-fixes]
* Fixing a potential memory leak when there is no connection with APM server
* Fixes NoSuchMethodError CharBuffer.flip() which occurs when using the Elasticsearch RestClient and Java 7 or 8 [#401](https://github.com/elastic/apm-agent-java/pull/401)

## 1.2.0 [elastic-apm-java-agent-1-2-0-release-notes]
**Release date:** December 19, 2018

### Features and enhancements [elastic-apm-java-agent-1-2-0-features-enhancements]
* Added `capture_headers` configuration option. Set to `false` to disable capturing request and response headers. This will reduce the allocation rate of the agent and can save you network bandwidth and disk space.
* Makes the API methods `addTag`, `setName`, `setType`, `setUser` and `setResult` fluent, so that calls can be chained.

### Fixes [elastic-apm-java-agent-1-2-0-fixes]
* Catch all errors thrown within agent injected code
* Enable public APIs and OpenTracing bridge to work properly in OSGi systems, fixes [this WildFly issue](https://github.com/elastic/apm-agent-java/issues/362)
* Remove module-info.java to enable agent working on early Tomcat 8.5 versions
* Fix [async Servlet API issue](https://github.com/elastic/apm-agent-java/issues/371)

## 1.1.0 [elastic-apm-java-agent-1-1-0-release-notes]
**Release date:** November 28, 2018

### Features and enhancements [elastic-apm-java-agent-1-1-0-features-enhancements]
* Some memory allocation improvements
* Enabling bootdelegation for agent classes in Atlassian OSGI systems

### Fixes [elastic-apm-java-agent-1-1-0-fixes]
* Update dsl-json which fixes a memory leak. See [ngs-doo/dsl-json#102](https://github.com/ngs-doo/dsl-json/pull/102) for details.
* Avoid `VerifyError`s by non instrumenting classes compiled for Java 4 or earlier
* Enable APM Server URL configuration with path (fixes #339)
* Reverse `system.hostname` and `system.platform` order sent to APM server

## 1.0.1 [elastic-apm-java-agent-1-0-1-release-notes]
**Release date:** November 15, 2018

### Fixes [elastic-apm-java-agent-1-0-1-fixes]
* Fixes NoSuchMethodError CharBuffer.flip() which occurs when using the Elasticsearch RestClient and Java 7 or 8 [#313](https://github.com/elastic/apm-agent-java/pull/313)

## 1.0.0 [elastic-apm-java-agent-1-0-0-release-notes]
**Release date:** November 14, 2018

### Features and enhancements [elastic-apm-java-agent-1-0-0-features-enhancements]
* Adds `@CaptureTransaction` and `@CaptureSpan` annotations which let you declaratively add custom transactions and spans. Note that it is required to configure the `application_packages` for this to work. See the [documentation]/apm-agent-java/docs/reference/public-api.md#api-annotation) for more information.
* The public API now supports to activate a span on the current thread. This makes the span available via `ElasticApm#currentSpan()` Refer to the [documentation]/apm-agent-java/docs/reference/public-api.md#api-span-activate) for more details.
* Capturing of Elasticsearch RestClient 5.0.2+ calls. Currently, the `*Async` methods are not supported, only their synchronous counterparts.
* Added API methods to enable correlating the spans created from the JavaScrip Real User Monitoring agent with the Java agent transaction. More information can be found in the [documentation]/apm-agent-java/docs/reference/public-api.md#api-ensure-parent-id).
* Added `Transaction.isSampled()` and `Span.isSampled()` methods to the public API
* Added `Transaction#setResult` to the public API [#293](https://github.com/elastic/apm-agent-java/pull/293)
* Support for Distributed Tracing
* Adds `@CaptureTransaction` and `@CaptureSpan` annotations which let you declaratively add custom transactions and spans. Note that it is required to configure the `application_packages` for this to work. See the [documentation]/apm-agent-java/docs/reference/public-api.md#api-annotation) for more information.
* The public API now supports to activate a span on the current thread. This makes the span available via `ElasticApm#currentSpan()` Refer to the [documentation]/apm-agent-java/docs/reference/public-api.md#api-span-activate) for more details.
* Capturing of Elasticsearch RestClient 5.0.2+ calls. Currently, the `*Async` methods are not supported, only their synchronous counterparts.
* Added API methods to enable correlating the spans created from the JavaScrip Real User Monitoring agent with the Java agent transaction. More information can be found in the [documentation]/apm-agent-java/docs/reference/public-api.md#api-ensure-parent-id).
* Microsecond accurate timestamps [#261](https://github.com/elastic/apm-agent-java/pull/261)
* Support for JAX-RS annotations. Transactions are named based on your resources (`ResourceClass#resourceMethod`).

### Fixes [elastic-apm-java-agent-1-0-0-fixes]
* Fix for situations where status code is reported as `200`, even though it actually was `500` [#225](https://github.com/elastic/apm-agent-java/pull/225)
* Capturing the username now properly works when using Spring security [#183](https://github.com/elastic/apm-agent-java/pull/183)
* Fix for situations where status code is reported as `200`, even though it actually was `500` [#225](https://github.com/elastic/apm-agent-java/pull/225)




