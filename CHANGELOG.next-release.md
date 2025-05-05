This file contains all changes which are not released yet.
<!--
 Note that the content between the marker comment lines (e.g. FIXES-START/END) will be automatically
 moved into the docs/release-notes markdown files on release (via the .ci/ReleaseChangelog.java script).
 Simply add the changes as bullet points into those sections, empty lines will be ignored. Example:

* Description of the change - [#1234](https://github.com/elastic/apm-agent-java/pull/1234)
-->

# Fixes
<!--FIXES-START-->

<!--FIXES-END-->
# Features and enhancements
<!--ENHANCEMENTS-START-->
* Remove 1000 character limit for HTTP client body capturing  - [#1234](https://github.com/elastic/apm-agent-java/pull/4058)

<!--ENHANCEMENTS-END-->
# Deprecations
<!--DEPRECATIONS-START-->

<!--DEPRECATIONS-END-->

# Breaking Changes
<!--BREAKING-CHANGES-START-->
* Switched from using a label for HTTP client body storage to using the `http.request.body.orginal` span field. This requires APM-server 8.18+, the old behaviour can be restored via the `capture_http_client_request_body_as_label` config option - [#1234](https://github.com/elastic/apm-agent-java/pull/4058)

<!--BREAKING-CHANGES-END-->
