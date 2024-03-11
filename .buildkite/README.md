# Buildkite

This README provides an overview of the Buildkite pipeline that automates the build and publishing process.

## Release pipeline

This is the Buildkite pipeline for releasing the APM Agent Java.

### Pipeline Configuration

To view the pipeline and its configuration, click [here](https://buildkite.com/elastic/apm-agent-java-release) or
go to the definition in `release.yml`.

### Credentials

The release team provides the credentials required to publish the artifacts in Maven Central and sign them
with the GPG.

If further details are needed, please go to [prepare-release.sh](hooks/prepare-release.sh).

## Snapshot pipeline

This is the Buildkite pipeline for the APM Agent Java in charge of the snapshots.

### Pipeline Configuration

To view the pipeline and its configuration, click [here](https://buildkite.com/elastic/apm-agent-java-snapshot) or
go to the definition in `snapshot.yml`.

## opentelemetry-benchmark pipeline

This is the Buildkite pipeline for the Opentelemetry Benchmark.

### Pipeline Configuration

To view the pipeline and its configuration, click [here](https://buildkite.com/elastic/apm-agent-java-opentelemetry-benchmark) or
go to the definition in `opentelemetry-benchmark.yml`.

## Buildkite VM runners

A set of Buildkite VM runners has been created for this repository. The VM runners contain
the required software:
* JDK
* GPG
* Maven dependencies

If a new version of Java is required, update the `.java-version` file with the latest major. When the changes are merged onto `main,` wait for the following day; that's when the automation will be responsible for recreating the VM with the new Java version.

If you would like to know more about how it works, please go to https://github.com/elastic/ci-agent-images/tree/main/vm-images/apm-agent-java (**NOTE**: only available for Elastic employees)
