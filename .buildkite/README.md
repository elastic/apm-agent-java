# Buildkite

This README provides an overview of the Buildkite pipeline that automates the build and publishing process.

## Release pipeline

This is the Buildkite pipeline for releasing the APM Agent Java.

### Pipeline Configuration

To view the pipeline and its configuration, click [here](https://buildkite.com/elastic/apm-agent-java-release) or
go to the definition in the `elastic/ci` repository.

### Credentials

The release team provides the credentials required to publish the artifacts in Maven Central and sign them
with the GPG.

If further details are needed, please go to [prepare-release.sh](hooks/prepare-release.sh).

## Snapshot pipeline

This is the Buildkite pipeline for the APM Agent Java in charge of the snapshots.

### Pipeline Configuration

To view the pipeline and its configuration, click [here](https://buildkite.com/elastic/apm-agent-java-snapshot) or
go to the definition in the `elastic/ci` repository.

## opentelemetry-benchmark pipeline

This is the Buildkite pipeline for the Opentelemetry Benchmark.

### Pipeline Configuration

To view the pipeline and its configuration, click [here](https://buildkite.com/elastic/apm-agent-java-opentelemetry-benchmark) or
go to the definition in `opentelemetry-benchmark.yml`.
