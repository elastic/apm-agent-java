This file contains all changes which are not released yet.
<!--
 Note that the content between the marker comment lines (e.g. FIXES-START/END) will be automatically
 moved into the docs/release-notes markdown files on release (via the .ci/ReleaseChangelog.java script).
 Simply add the changes as bullet points into those sections, empty lines will be ignored. Example:

* Description of the change - [#1234](https://github.com/elastic/apm-agent-java/pull/1234)
-->

# Fixes
<!--FIXES-START-->
* Exclude `XmlLayout` class from log4j dependency - [#4459](https://github.com/elastic/apm-agent-java/pull/4459)
* Fix unsupported-aggregation warning in OpenTelemetry metric SDK exporter to use SLF4J-style `{}` placeholders instead of `%s`, so the metric name and aggregation type are rendered in the log message - [#4466](https://github.com/elastic/apm-agent-java/pull/4466)
* Stop OTel metrics exporter from throwing `IndexOutOfBoundsException` when a histogram has no explicit bucket boundaries - [#4465](https://github.com/elastic/apm-agent-java/pull/4465)
* Cast to parent buffer for Java 8 buffer method compatibility - [#4498](https://github.com/elastic/apm-agent-java/pull/4498)
<!--FIXES-END-->
# Features and enhancements
<!--ENHANCEMENTS-START-->
* Add experimental support for Spring Boot 4 / Spring Framework 7 in WebFlux instrumentation - [#4489](https://github.com/elastic/apm-agent-java/pull/4489)
<!--ENHANCEMENTS-END-->
# Deprecations
<!--DEPRECATIONS-START-->

<!--DEPRECATIONS-END-->

# Breaking Changes
<!--BREAKING-CHANGES-START-->

<!--BREAKING-CHANGES-END-->
