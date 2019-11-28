# Contributing to the Elastic APM Java agent

The APM Agent is open source and we love to receive contributions from our community — you!

There are many ways to contribute,
from writing tutorials or blog posts,
improving the documentation,
submitting bug reports and feature requests or writing code.

You can get in touch with us through [Discuss](https://discuss.elastic.co/c/apm),
feedback and ideas are always welcome.

## Code contributions

If you have a bugfix or new feature that you would like to contribute,
please find or open an issue about it first.
Talk about what you would like to do.
It may be that somebody is already working on it,
or that there are particular issues that you should know about before implementing the change.

Once you are all set to go, [this "cookbook recipe" blog post](https://www.elastic.co/blog/a-cookbook-for-contributing-a-plugin-to-the-elastic-apm-java-agent) can guide you through. 

### Submitting your changes

Generally, we require that you test any code you are adding or modifying.
Once your changes are ready to submit for review:

1. Sign the Contributor License Agreement

    Please make sure you have signed our [Contributor License Agreement](https://www.elastic.co/contributor-agreement/).
    We are not asking you to assign copyright to us,
    but to give us the right to distribute your code without restriction.
    We ask this of all contributors in order to assure our users of the origin and continuing existence of the code.
    You only need to sign the CLA once.

2. Test your changes

    Run the test suite to make sure that nothing is broken.
    See [testing](#testing) for details.

3. Rebase your changes

    Update your local repository with the most recent code from the main repo,
    and rebase your branch on top of the latest master branch.
    We prefer your initial changes to be squashed into a single commit.
    Later,
    if we ask you to make changes,
    add them as separate commits.
    This makes them easier to review.
    As a final step before merging we will either ask you to squash all commits yourself or we'll do it for you.

4. Submit a pull request

    Push your local changes to your forked copy of the repository and [submit a pull request](https://help.github.com/articles/using-pull-requests).
    In the pull request,
    choose a title which sums up the changes that you have made,
    and in the body provide more details about what your changes do.
    Also mention the number of the issue where discussion has taken place,
    eg "Closes #123".

5. Be patient

    We might not be able to review your code as fast as we would like to,
    but we'll do our best to dedicate it the attention it deserves.
    Your effort is much appreciated!

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
<template name="enter" value="@Advice.OnMethodEnter(suppress = Throwable.class)&#10;private static void onEnter() {&#10;    $END$&#10;}" description="Adds @OnMethodEnter advice" toReformat="false" toShortenFQNames="true">
  <context>
    <option name="JAVA_DECLARATION" value="true" />
  </context>
</template>
```

**`exit`**

```xml
<template name="exit" value="@Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)&#10;private static void onExit(@Advice.Thrown Throwable thrown) {&#10;    $END$&#10;}" description="Adds @OnMethodExit advice" toReformat="false" toShortenFQNames="true">
  <context>
    <option name="JAVA_DECLARATION" value="true" />
  </context>
</template>
```


**`logger`**
```xml
<template name="logger" value="private static final Logger logger = LoggerFactory.getLogger($CLASS_NAME$.class);" description="" toReformat="false" toShortenFQNames="true">
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
<template name="at" value="assertThat($EXPR$)$END$;" description="assertJ assert expression" toReformat="false" toShortenFQNames="true">
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

All feature development and most bug fixes hit the master branch first.
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
  * Performance testing for every commit to master
  * Releases.
    Daily snapshots from the master branch.
    Automated release process.
* Feature toggles over long-running feature branches
  Even if a feature is not quite ready for production use,
  merge it into master as soon as possible.
  Create a configuration option for this new feature and disable it by default.
  One advantage is that less time will be spent rebasing long lasting feature branches.
  Another advantage is that users can try out cutting-edge features by activating a configuration option.
  Instrumentations can be tagged with `incubating` which makes them being disabled by default.

### Architecture overview

See [`apm-agent-core/README.md`](apm-agent-core/README.md)

### Adding support for instrumenting new libraries/frameworks/APIs

See [`apm-agent-plugins/README.md`](apm-agent-plugins/README.md)

### Documenting

HTML Documentation is generated from text files stored in `docs` folder using [AsciiDoc](http://asciidoc.org/) format.
The ``configuration.asciidoc`` file is generated from running `co.elastic.apm.agent.configuration.ConfigurationExporter`,
all the other asciidoc text files are written manually.

A preview of the documentation is generated for each pull-request.
Click on the build `Details` of the `elasticsearch-ci/docs` job and go to the bottom of the `Console Output` to see the link.

In order to generate a local copy of agent documentation, you will need to clone [docs](https://github.com/elastic/docs) repository
and follow [those instructions](https://github.com/elastic/docs#for-a-local-repo).

### Releasing

If you have access to make releases, the process is as follows:

1. Check if sonatype is up: https://status.maven.org
1. Review project version. The release version will be `${project.version}` without the `-SNAPSHOT`. 
   1. In case you want to update the version, execute `mvn release:update-versions`
1. Execute the release Jenkins job on the internal ci server. This job is same as the snapshot-build job, but it also:
   1. Removes `-SNAPSHOT` from all `${project.version}` occurrences and makes a commit before build
   1. Tags this new commit with the version name, e.g. `v1.1.0`
   1. Advances the version for next development iteration and makes a commit
   1. Uploads artifacts to maven central
1. Login to https://oss.sonatype.org, go to Staging Repositories, close and release the staged artifacts.
1. Fetch and checkout the latest tag e.g. `git fetch origin` 
1. If this was a major release,
   create a new branch for the major
   1. For example `git checkout -b 2.x && git push -u origin`
   1. Add the new branch to the `conf.yaml` in the docs repo
1. If this was a minor release,
   reset the current major branch (`1.x`, `2.x` etc) to point to the current tag, e.g. `git branch -f 1.x v1.1.0`
   1. Update the branch on upstream with `git push origin 1.x`
1. Update [`CHANGELOG.md`](CHANGELOG.md) to reflect version release. Go over PRs or git log and add bug fixes and features.
1. Go to https://github.com/elastic/apm-agent-java/releases and draft a new release.
   Use the contents of [`CHANGELOG.md`](CHANGELOG.md) for the release description.
1. Update [`cloudfoundry/index.yml`](cloudfoundry/index.yml)
