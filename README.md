[![Build Status](https://apm-ci.elastic.co/job/elastic+apm-agent-java+master/badge/icon)](https://apm-ci.elastic.co/job/elastic+apm-agent-java+master/)
[![codecov](https://codecov.io/gh/elastic/apm-agent-java/branch/master/graph/badge.svg)](https://codecov.io/gh/elastic/apm-agent-java)
[![Maven Central](https://img.shields.io/maven-central/v/co.elastic.apm/apm-agent-api.svg)](https://search.maven.org/search?q=g:co.elastic.apm%20AND%20a:elastic-apm-agent)

# apm-agent-java

This agent is in beta state. That means it's ready for serious testing, but not for production usage. 

Please fill out this survey to help us prioritizing framework support: https://docs.google.com/forms/d/e/1FAIpQLScd0RYiwZGrEuxykYkv9z8Hl3exx_LKCtjsqEo1OWx8BkLrOQ/viewform?usp=sf_link

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

Execute `./mvnw clean install -PskipTests=true` to build the artifacts and to install them to your local maven repository.
The agent jar is in the folder `elastic-apm-agent/target`.
