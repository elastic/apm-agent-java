---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/upgrading.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Upgrading [upgrading]

Upgrades between minor versions of the agent, like from 1.1 to 1.2 are always backwards compatible. Upgrades that involve a major version bump often come with some backwards incompatible changes.

Before upgrading the agent, be sure to review the:

* [Agent release notes](/release-notes/index.md)
* [Agent and Server compatibility chart](docs-content://solutions/observability/apm/apm-agent-compatibility.md)


## Recommended upgrade steps [upgrade-steps]

1. Shut down the application.
2. Download the latest release of the agent jar file from [maven central](https://mvnrepository.com/artifact/co.elastic.apm/elastic-apm-agent/latest).
3. Optionally change JVM settings, e.g., if the path to the agent jar has changed due to a different file name.
4. Restart the application.


## End of life dates [end-of-life-dates]

We love all our products, but sometimes we must say goodbye to a release so that we can continue moving forward on future development and innovation. Our [End of life policy](https://www.elastic.co/support/eol) defines how long a given release is considered supported, as well as how long a release is considered still in active development or maintenance.

