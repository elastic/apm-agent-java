# Contributing to the Elastic APM Java agent

The APM Agent is open source and we love to receive contributions from our community — you!

There are many ways to contribute,
from writing tutorials or blog posts,
improving the documentation,
submitting bug reports and feature requests or writing code.

If you want to be rewarded for your contributions, sign up for the [Elastic Contributor Program](https://www.elastic.co/community/contributor). Each time you make a valid contribution, you’ll earn points that increase your chances of winning prizes and being recognized as a top contributor.

You can get in touch with us through [Discuss](https://discuss.elastic.co/c/apm),
feedback and ideas are always welcome.

## Code contributions

If you have a bugfix or new feature that you would like to contribute, please do the following:
- Double check in open issues if there are any related issues or PRs
- Open an issue, ensure that you have properly described the use-case and possible solutions, link related issues/PRs if any
- Open a PR and link the issue created in previous step with your code changes.

Doing so allows to:
- Share knowledge and document a bug/missing feature
- Get feedback if someone is already working on it or is having a similar issue
- Benefit from the team experience by discussing it first, there are lots of implementation details that might not be
obvious at first sight.

Once you are all set to go, [this "cookbook recipe" blog post](https://www.elastic.co/blog/a-cookbook-for-contributing-a-plugin-to-the-elastic-apm-java-agent) can guide you through.

### Testing

The test suite consists of unit and integration tests.
To run the unit tests, simply execute

```bash
./mvnw clean test
```

To run all tests, including the integration tests, execute

```bash
./mvnw clean verify
```

The integration tests take some more time to execute.
For small changes you don't have to execute them locally.
When creating a pull requests,
they will be executed by a CI server.

The code requires at least Java 17 to be built.
The tests require at least Java 11 to run.
You can use a different JVM for testing than building by passing the `test_java_binary` to maven:

```bash
./mvnw clean test -Dtest_java_binary=/path/to/java_home/bin/java
```

#### Performance testing

We have some JMH Tests that allow to track the following performance metrics deltas when agent is activated.

- memory allocation rate (GC pressure)
- cpu time

In order to run them, you can use the `ElasticApmActiveContinuousBenchmark` from IDE or command line.

Metrics reported by this test are just data, in order to make good use of them, you have to
compare them against `main` branch values as a baseline to know if a given code change has any impact.

### Configuring IDEs

#### IntelliJ

1. Import the repository as a maven project
1. After importing, select the IntelliJ profile in Maven Projects sidebar

   <img width="268" alt="Maven profiles" src="https://user-images.githubusercontent.com/2163464/43443771-22e3ba3c-94a2-11e8-9bd8-5ed73b7e975a.png">
1. This project makes heavy use of the `javax.annotation.Nonnull` and `javax.annotation.Nullable` annotations.
   In order for the IDE to properly understand these annotations and to show warnings when invariants are violated,
   there are a few things which need to be configured.
   Open the preferences,
   search for nullable and enable the inspections `Constant conditions & exceptions` and `@NotNull/@Nullable problems`

   <img width="684" alt="inspections" src="https://user-images.githubusercontent.com/2163464/43443888-87e3ffd2-94a2-11e8-8cc6-263f408479e3.png">
1. In the same window,
   select `Constant conditions & exceptions`
   click on `Configure annotations`,
   and select the `javax.annotation` annotations.

   <img width="382" alt="configure annotations" src="https://user-images.githubusercontent.com/2163464/43444414-f1e5ce5a-94a3-11e8-8fa4-9f048c50ccc0.png">

##### Useful Live Templates

These live templates can be pasted in Preferences > Editor > Live Templates > other

**`enter`**
```xml
<template name="enter" value="@Advice.OnMethodEnter(suppress = Throwable.class, inline = false)&#10;public static void onEnter() {&#10;    $END$&#10;}" description="Adds @OnMethodEnter advice" toReformat="true" toShortenFQNames="true">
  <context>
    <option name="JAVA_DECLARATION" value="true" />
  </context>
</template>
```

**`exit`**

```xml
<template name="exit" value="@Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)&#10;public static void onExit(@Advice.Thrown @Nullable Throwable thrown, @Advice.Return @Nullable Object returnValue) {&#10;    $END$&#10;}" description="Adds @OnMethodExit advice" toReformat="true" toShortenFQNames="true">
    <context>
        <option name="JAVA_DECLARATION" value="true" />
    </context>
</template>
```


**`logger`**
```xml
<template name="logger" value="private static final Logger logger = LoggerFactory.getLogger($CLASS_NAME$.class);" description="" toReformat="true" toShortenFQNames="true">
  <variable name="CLASS_NAME" expression="className()" defaultValue="" alwaysStopAt="true" />
  <context>
    <option name="JAVA_DECLARATION" value="true" />
  </context>
</template>
```

**`test`**
```xml
<template name="test" value="@Test&#10;void $METHOD$(){&#10;    $END$&#10;}" description="create junit test method" toReformat="true" toShortenFQNames="true">
  <variable name="METHOD" expression="" defaultValue="" alwaysStopAt="true" />
  <context>
    <option name="JAVA_DECLARATION" value="true" />
  </context>
</template>
```

**`at`**
```xml
<template name="at" value="assertThat($EXPR$)$END$;" description="assertJ assert expression" toReformat="true" toShortenFQNames="true" useStaticImport="true">
  <variable name="EXPR" expression="" defaultValue="" alwaysStopAt="true" />
  <context>
    <option name="JAVA_STATEMENT" value="true" />
  </context>
</template>
 ```


### Java Language Formatting Guidelines

Please follow these formatting guidelines:

* Java indent is 4 spaces
* Line width is 140 characters
* The rest is left to Java coding standards
* Disable “auto-format on save” to prevent unnecessary format changes.
  This makes reviews much harder as it generates unnecessary formatting changes.
  If your IDE supports formatting only modified chunks that is fine to do.
* Wildcard imports (`import foo.bar.baz.*`) are forbidden.
  Please attempt to tame your IDE so it doesn't make them and please send a PR against this document with instructions for your IDE if it doesn't contain them.
* Eclipse: `Preferences->Java->Code Style->Organize Imports`.
  There are two boxes labeled "`Number of (static )? imports needed for .*`".
  Set their values to 99999 or some other absurdly high value.
* IntelliJ: `Preferences/Settings->Editor->Code Style->Java->Imports`.
  There are two configuration options: `Class count to use import with '*'` and `Names count to use static import with '*'`.
  Set their values to 99999 or some other absurdly high value.
* Don't worry too much about import order.
  Try not to change it but don't worry about fighting your IDE to stop it from doing so.

### License Headers

We require license headers on all Java files.
When executing the tests,
the license headers will be added automatically.

### Workflow

All feature development and most bug fixes hit the main branch first.
Pull requests should be reviewed by someone with commit access.
Once approved, the author of the pull request,
or reviewer if the author does not have commit access,
should "Squash and merge".

### Coding guidelines

There are a few guidelines or goals for the development of the agent.
Some of these goals may be contrary at times,
like performance and readability.
We should try to find the best compromise and balance for each specific case.

Not all of these guidelines are perfectly put into practice and there is always room for improvement.
But for each change,
we should think about whether they bring us closer to or further away from those goals.

* Easy to understand and to extend
  * This is an Open Source project and we embrace our community.
    That also means that the code should be as approachable as possible.
    That's not always easy as bytecode manipulation and instrumentation is a very complex domain.
    Sometimes, this also conflicts with other goals, like performance.
  * Also, a good developer documentation is critical to achieving this goal.
    As a general guideline for code documentation,
    document why you did something that way,
    rather than how you did it.
    The latter should be expressed by readable code.
    Documenting the considered alternatives can also help others a lot to understand the problem space.
* Diagnosability
  * Most issues should be resolvable when the user provides their debug logs.
    This decreases the back-and-forth to gather additional information.
    If an issue could not be resolved by looking at the debug logs,
    think about if we can add the necessary information to make diagnosing similar issues easier.
  * Try to make exception messages as descriptive as possible.
    When this particular exception is thrown,
    what is the user supposed to do to fix the problem?
  * Sometimes the best debug logs are just not enough to diagnose a problem.
    The agent code should be easily debuggable in the user's IDE.
* Performance is a feature
  * Find the right balance of low-level and high-level code
  * We are especially proud of the low allocation rate of the agent.
    This does not only make the average overhead look good,
    but also makes the performance of the agent way more predictable and stable in the higher percentiles,
    as these are heavily influenced by GC pauses.
  * Don't guess when it comes to performance.
    Improvements should be verified by the benchmarks.
    To get more insight about potential areas of improvements,
    enable Flight Recording during the benchmarks and analyze the results in Java Mission Control.
* Easy to install for users
  * Installing the agent should be as easy as possible.
    The fewer installation steps and required configuration options the user has to deal with,
    the better.
* Testability/Test Pyramid
  * Most tests should be simple and easily debuggable JUnit tests.
    In fact, the main workflow should be to write tests alongside the production code.
    You should rarely have to build the agent and attach it to a test application in order to verify your new code works as expected.
    This makes turnarounds much faster and usually also increases the test coverage.
    Speaking of which,
    use code coverage tools to check that the most important branches are covered by unit tests.
    The coverage of new code should typically not be lower than the current total coverage of the project.
  * Integration tests should also be easy to execute and debug locally,
    without the requirement of a complex testing setup.
    The integration test should be smoke/sanity tests for a specific technology like an application server or database.
    The bulk of the actual coverage should come from the unit tests,
    as they are usually easier and faster to execute and easier to debug.
* Continuous Everything
  * Unit testing for every PR
  * Integration testing for every PR
  * Performance testing for every commit to main
  * Releases.
    Daily snapshots from the main branch.
    Automated release process.
* Feature toggles over long-running feature branches
  Even if a feature is not quite ready for production use,
  merge it into main as soon as possible.
  Create a configuration option for this new feature and disable it by default.
  One advantage is that less time will be spent rebasing long lasting feature branches.
  Another advantage is that users can try out cutting-edge features by activating a configuration option.
  Instrumentations can be tagged with `experimental` which makes them being disabled by default.

### Architecture overview

See [`apm-agent-core/README.md`](apm-agent-core/README.md)

### Adding support for instrumenting new libraries/frameworks/APIs

See [`apm-agent-plugins/README.md`](apm-agent-plugins/README.md)

### Documenting

HTML Documentation is generated from text files stored in `docs` folder using [AsciiDoc](http://asciidoc.org/) format.
The `configuration.asciidoc` file is generated from running `co.elastic.apm.agent.configuration.ConfigurationExporter`
(e.g. via `./mvnw -Dsurefire.failIfNoTests=false -Dtest=ConfigurationExporterTest -pl apm-agent -am clean test`). All the other asciidoc text files
are written manually.

A preview of the documentation is generated for each pull-request.
Click on the build `Details` of the `elasticsearch-ci/docs` job and go to the bottom of the `Console Output` to see the link.

This step is part of Elasticsearch CI, and the build job is [the following](https://elasticsearch-ci.elastic.co/view/Docs/job/elastic+docs+apm-agent-java+pull-request/).

In order to generate a local copy of agent documentation, you will need to clone/update the [docs](https://github.com/elastic/docs)
and [apm-aws-lambda](https://github.com/elastic/apm-aws-lambda) repositories and run the following.
(The `--direct_html --open` options tells the doc build container to serve the docs at <http://localhost:8000>.
See [the "docs" repo README](https://github.com/elastic/docs#for-a-local-repo) for more details.)

```
../docs/build_docs --resource ../apm-aws-lambda/docs \
  --doc ./docs/index.asciidoc \
  --direct_html --open
```

### Releasing

If you have access to make releases, the process is as follows:

For illustration purpose, `1.2.3` will be the target release version, and the git remote will be `upstream`.

1. Check if sonatype is up: https://status.maven.org
1. Update [`CHANGELOG.asciidoc`](CHANGELOG.asciidoc) to reflect the new version release:
   1. Go over PRs or git log and add bug fixes and features.
   1. Move release notes from the `Unreleased` sub-heading to the correct `[[release-notes-{major}.x]]` sub-heading ([Example PR](https://github.com/elastic/apm-agent-java/pull/1027/files) for 1.13.0 release).
1. For major releases, [create an issue](https://github.com/elastic/website-requests/issues/new) to request an update of the [EOL table](https://www.elastic.co/support/eol).
1. Review Maven project version, you must have `${project.version}` equal to `1.2.3-SNAPSHOT`, `-SNAPSHOT` suffix will be removed during release process.
   1. If needed, use following command to update version - `mvn release:update-versions`, then commit and push changes.
1. Execute the release Jenkins job on the internal ci server. This job is same as the snapshot-build job, but it also:
   1. Removes `-SNAPSHOT` from all `${project.version}` occurrences and makes a commit before build
   1. Tags this new commit with the version name, e.g. `v1.2.3`.
   1. Advances the version for next development iteration e.g. `1.2.4-SNAPSHOT` and makes a commit.
   1. Uploads artifacts to maven central in a staging repository.
1. Login to https://oss.sonatype.org, go to Staging Repositories, close and release the staged artifacts.
1. Fetch and checkout the latest tag e.g. `git fetch upstream && git checkout v1.2.3`
1. If this was a major release, create a new branch for the major
   1. Create and push new major branch: `git checkout -b 2.x && git push -u upstream`
   1. Add the new branch to the `conf.yaml` in the docs repo
1. If this was a minor release, update the current minor branch (`1.x`, `2.x` etc) to the `v1.2.3` tag
   1. Update local `1.x` branch & update remote: `git branch -f 1.x v1.2.3 && git push upstream 1.x`
1. Wait for the new version contents to become available on our [release notes page](https://www.elastic.co/guide/en/apm/agent/java/current/release-notes.html)
1. Go to https://github.com/elastic/apm-agent-java/releases and draft a new release:
   1. Provide a link to release notes in documentation (from previous step) as release description.
   1. Download `elastic-apm-java-aws-lambda-layer-<VERSION>.zip` from the CI release job artifacts and upload it to the release draft
1. Wait for released package to be available in [maven central](https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/)
1. Build and push a Docker image using the instructions below
   Use `SONATYPE_FALLBACK=1 scripts/jenkins/build_docker.sh` to build image with released artifact.
   Requires credentials, thus need to delegate this manual step to someone that has them.
1. Update [`cloudfoundry/index.yml`](cloudfoundry/index.yml) on `main`.
1. Publish release on Github. This will notify users watching repository.

###  Docker images

#### Pulling images for use

Docker images are available for the APM Java agent. They can be downloaded from
docker.elastic.co and are located in the `observability` namespace.

For example, to download the v1.12.0 of the agent, use the following:

```
docker pull  docker.elastic.co/observability/apm-agent-java:1.12.0
```

#### Creating images for a Release

Images are normally created and published as a part of the release process.
Scripts which manage the Docker release process are located in [`scripts/jenkins/`](scripts/jenkins/).

##### Building images locally

Building images on a workstation requires the following:

* Docker daemon installed and running
* [cURL](https://curl.haxx.se/) installed
* A local checkout of the `apm-agent-java` repo
* The `git` command-line tool

Local image building is handled via the [`build_docker.sh script`](scripts/jenkins/build_docker.sh).

If you wish to use a locally built artifact in the built image, execute [`./mvnw package`](mvnw)`
and ensure that artifacts are present in `elastic-apm-agent/target/*.jar`.

To create a Docker image from artifacts generated by [`./mvnw package`](mvnw),
run [`scripts/jenkins/build_docker.sh`](scripts/jenkins/build_docker.sh).

Alternatively, it is also possible to use the most recent artifact from the [Sonatype
repository](https://oss.sonatype.org/#nexus-search;gav~co.elastic.apm~apm-agent-java~~~).

To do so, first clean any artifacts with [`./mvnw clean`](mvnw) and then run the Docker
build script with the `SONATYPE_FALLBACK` environment variable present. For example,

`SONATYPE_FALLBACK=1 scripts/jenkins/build_docker.sh`

After running the [`build_docker.sh`](scripts/jenkins/build_docker.sh) script, images can be seen by executing
`docker images|egrep docker.elastic.co/observability/apm-agent-java` which should
produce output similar to the following:

`docker.elastic.co/observability/apm-agent-java   1.12.0               1f45b5858d81        26 hours ago        10.6MB`

No output from the above command indicates that the image did not build correctly
and that the output of the [`build_docker.sh`](scripts/jenkins/build_docker.sh)
script should be examined to determine the cause.


#### Pushing a image to the Elastic repo

_Notice:_ You must have access to release secrets in order to push images.

Prior to pushing images, you must login to the Elastic Docker repo using the correct
credentials using the [`docker login`](https://docs.docker.com/engine/reference/commandline/login/) command.

To push an image, run the [`scripts/jenkins/push_docker.sh`](scripts/jenkins/push_docker.sh)
script. An image will be pushed.
