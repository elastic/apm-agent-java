[![Build Status](https://apm-ci.elastic.co/buildStatus/icon?job=apm-agent-java%2Fapm-agent-java-mbp%2Fmaster)](https://apm-ci.elastic.co/job/apm-agent-java/job/apm-agent-java-mbp/job/master/)
[![codecov](https://codecov.io/gh/elastic/apm-agent-java/branch/master/graph/badge.svg)](https://codecov.io/gh/elastic/apm-agent-java)
[![Maven Central](https://img.shields.io/maven-central/v/co.elastic.apm/apm-agent-api.svg)](https://search.maven.org/search?q=g:co.elastic.apm%20AND%20a:elastic-apm-agent)

# apm-agent-java

Please fill out this survey to help us prioritizing framework support: https://docs.google.com/forms/d/e/1FAIpQLScd0RYiwZGrEuxykYkv9z8Hl3exx_LKCtjsqEo1OWx8BkLrOQ/viewform?usp=sf_link

## Release announcements

To get notified about new releases, watch this repository for `Releases only`.

## Documentation

Docs are located [here](https://www.elastic.co/guide/en/apm/agent/java/current/index.html).

## Getting Help

If you find a bug or an issue, please
1. open a new topic on the [APM discuss forum](https://discuss.elastic.co/tags/c/apm/java) (or add to an existing one)
1. [report an issue](https://github.com/elastic/apm-agent-java/issues/new) on the java agent repository

Please note that we reserve GitHub tickets for actionable things we can work on, thus confirmed bugs and enhancement requests only.

Help requests are better served in [APM discuss forum](https://discuss.elastic.co/tags/c/observability/apm/58/java).

## Contributing

See the [contributing documentation](CONTRIBUTING.md)

## Snapshots

Snapshots are built from `master` branch and are available here:

* [elastic-apm-agent.jar](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=co.elastic.apm&a=elastic-apm-agent&v=LATEST)
* [apm-agent-attach-cli.jar](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=co.elastic.apm&a=apm-agent-attach-cli&v=LATEST)

Those snapshots include features & bugfixes for the next release, see [releases notes](https://www.elastic.co/guide/en/apm/agent/java/master/_unreleased.html) details.

## Build form source

Execute `./mvnw clean install -DskipTests=true -Dmaven.javadoc.skip=true` to build the artifacts and to install them to your local maven repository. The build process requires JDK 9.
The agent jar is in the folder `elastic-apm-agent/target`.

## License

Elastic APM Java Agent is licensed under [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).
