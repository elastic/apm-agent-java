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

If you find a bug,
please [report an issue](https://github.com/elastic/apm-agent-java/issues/new).
For any other assistance,
please open or add to a topic on the [APM discuss forum](https://discuss.elastic.co/c/apm).

If you need help or hit an issue,
please start by opening a topic on our discuss forums.
Please note that we reserve GitHub tickets for confirmed bugs and enhancement requests.

## Contributing

See the [contributing documentation](CONTRIBUTING.md)

## Snapshots

Download the latest snapshot from master
[here](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=co.elastic.apm&a=elastic-apm-agent&v=LATEST).

## Build form source

Execute `./mvnw clean install -DskipTests=true -Dmaven.javadoc.skip=true` to build the artifacts and to install them to your local maven repository. The build process requires JDK 9.
The agent jar is in the folder `elastic-apm-agent/target`.

## License

Elastic APM Java Agent is licensed under [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).
