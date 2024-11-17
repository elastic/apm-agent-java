[![main](https://github.com/elastic/apm-agent-java/actions/workflows/main.yml/badge.svg)](https://github.com/elastic/apm-agent-java/actions/workflows/main.yml)
[![codecov](https://codecov.io/gh/elastic/apm-agent-java/branch/main/graph/badge.svg)](https://codecov.io/gh/elastic/apm-agent-java)
[![Maven Central](https://img.shields.io/maven-central/v/co.elastic.apm/apm-agent-api.svg)](https://mvnrepository.com/artifact/co.elastic.apm/elastic-apm-agent/latest)

# apm-agent-java

Please fill out this survey to help us prioritizing framework support: https://docs.google.com/forms/d/e/1FAIpQLScd0RYiwZGrEuxykYkv9z8Hl3exx_LKCtjsqEo1OWx8BkLrOQ/viewform?usp=sf_link

## Overview
The Elastic APM Java Agent monitors Java applications, capturing and analyzing performance metrics.

## Documentation
Documentation is located [here](https://www.elastic.co/guide/en/apm/agent/java/current/index.html).

## Prerequisites
- JDK 17
- Maven

## Installation Steps
### Option 1: Manual Installation
Snapshots are built from the `main` branch and are available here:
- [elastic-apm-agent.jar](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=co.elastic.apm&a=elastic-apm-agent&v=LATEST)
- [apm-agent-attach-cli.jar](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=co.elastic.apm&a=apm-agent-attach-cli&v=LATEST)
- [apm-agent-attach.jar](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=co.elastic.apm&a=apm-agent-attach&v=LATEST)
- [apm-agent-api.jar](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=co.elastic.apm&a=apm-agent-api&v=LATEST)

For unreleased features and bug fixes, refer to the [release notes](https://www.elastic.co/guide/en/apm/agent/java/current/_unreleased.html).

### Build from Source
Run the following to build the artifacts and install them to your local Maven repository:
```bash
./mvnw clean install -DskipTests=true -Dmaven.javadoc.skip=true
```
The agent jar will be in the `elastic-apm-agent/target` folder.

## Help and Support
If you find a bug or issue, you can:
1. Open a new topic on the [APM discuss forum](https://discuss.elastic.co/tags/c/apm/java).
2. [Report an issue](https://github.com/elastic/apm-agent-java/issues/new) on the Java agent repository.

For help requests, use the [APM discuss forum](https://discuss.elastic.co/tags/c/observability/apm/58/java).

## Contributing
See the [contributing documentation](CONTRIBUTING.md).

## Release Announcements
To get notified about new releases, watch this repository for `Releases only`.

## License
Elastic APM Java Agent is licensed under [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).

