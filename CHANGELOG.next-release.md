This file contains all changes which are not released yet.
<!--
 Note that the content between the marker comment lines (e.g. FIXES-START/END) will be automatically
 moved into the docs/release-notes markdown files on release (via the .ci/ReleaseChangelog.java script).
 Simply add the changes as bullet points into those sections, empty lines will be ignored. Example:

* Description of the change - [#1234](https://github.com/elastic/apm-agent-java/pull/1234)
-->

# Fixes
<!--FIXES-START-->

- avoid caching when reading version from jar to prevent side effects - [#4543](https://github.com/elastic/apm-agent-java/pull/4543)
- fix Spring Webflux 7 NoSuchMethodError on HttpHeaders#entrySet() - [#4556](https://github.com/elastic/apm-agent-java/pull/4556)

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
