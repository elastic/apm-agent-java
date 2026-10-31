This file contains all changes which are not released yet.
<!--
 Note that the content between the marker comment lines (e.g. FIXES-START/END) will be automatically
 moved into the docs/release-notes markdown files on release (via the .ci/ReleaseChangelog.java script).
 Simply add the changes as bullet points into those sections, empty lines will be ignored. Example:

* Description of the change - [#1234](https://github.com/elastic/apm-agent-java/pull/1234)
-->

# Fixes
<!--FIXES-START-->
- Fix JVM tool bootstrap check when program arguments contain slashes - [#4269](https://github.com/elastic/apm-agent-java/issues/4269)
- Fix `NoSuchMethodError` in Log4j 2.7 log correlation, which called `ThreadContext.removeAll` that is only available since Log4j 2.8 - [#4593](https://github.com/elastic/apm-agent-java/issues/4593)
<!--FIXES-END-->
# Features and enhancements
<!--ENHANCEMENTS-START-->

<!--ENHANCEMENTS-END-->
# Deprecations
<!--DEPRECATIONS-START-->

<!--DEPRECATIONS-END-->

# Breaking Changes
<!--BREAKING-CHANGES-START-->

<!--BREAKING-CHANGES-END-->
