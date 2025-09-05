---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/java-method-monitoring.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# How to find slow methods [java-method-monitoring]

Identifying a problematic service is only half of the battle when diagnosing application slowdowns. Luckily, the Elastic APM Java Agent provides multiple ways to get method-level insights into your code. This can help you diagnose slow requests due to heavy computations, inefficient algorithms, or similar problems not related to interactions between services.


## *If you don’t know which methods you want to monitor… * [_if_you_dont_know_which_methods_you_want_to_monitor]


### Sampling-based profiler [_sampling_based_profiler]

Find out which part of your code is making your application slow by periodically recording running methods with a sampling-based profiler.

![green check](images/green-check.svg "") Very low overhead.<br> ![green check](images/green-check.svg "") No code changes required.<br> ![red x](images/red-x.svg "") Does not work on Windows and on OpenJ9.<br> ![red x](images/red-x.svg "") The duration of profiler-inferred spans are not exact measurements, only estimates.

[Learn more](/reference/method-sampling-based.md)


## *If you know which methods you want to monitor… * [_if_you_know_which_methods_you_want_to_monitor]


### API/Code [_apicode]

Use the API or OpenTracing bridge to manually create spans for methods of interest.

![green check](images/green-check.svg "") Most flexible.<br> ![red x](images/red-x.svg "") Incorrect API usage may lead to invalid traces (scope leaks).

[Learn more](/reference/method-api.md)


### Annotations [_annotations]

Annotations can be placed on top of methods to automatically create spans for them.

![green check](images/green-check.svg "") Easier and more robust than the API.<br> ![red x](images/red-x.svg "") Less flexible on its own, but can be combined with the API.

[Learn more](/reference/method-annotations.md)


### Configuration-based [_configuration_based]

Use a configuration option to specify additional methods to instrument.

![green check](images/green-check.svg "") No need to modify source code.<br> ![green check](images/green-check.svg "") Possible to monitor code in third-party libraries.<br> ![green check](images/green-check.svg "") Match methods via wildcards.<br> ![red x](images/red-x.svg "") Easy to overuse which hurts runtime and startup performance.

[Learn more](/reference/method-config-based.md)





