---
name: create-java-agent-instrumentation-plugin
description: Design, implement, validate, document, and prepare a new built-in instrumentation plugin for the Elastic APM Java agent. Use when adding support for a Java framework or library, selecting Byte Buddy instrumentation points, handling synchronous or reactive lifecycles, defining spans or metrics, adding configuration, testing supported-version boundaries, assembling the agent, or preparing the new-plugin PR checklist.
---

# Create a Java agent instrumentation plugin

Build instrumentation around a stable semantic operation, not around whichever method is easiest to match. Treat compatibility, class loading, lifecycle correctness, privacy, overhead, packaging, and documentation as part of the implementation rather than follow-up work.

This skill is for built-in plugins in the `apm-agent-java` repository. For an external community plugin, also read `docs/reference/plugin-api.md`; its packaging and loading path differ from a built-in module.

## Start with repository evidence

Before editing code:

1. Read `CONTRIBUTING.md`, especially testing, Java formatting, license headers, and build-JDK requirements.
2. Read `docs/reference/plugin-api.md` for the instrumentation/advice/service-loader model.
3. Inspect two or three nearby plugins with similar behavior:
   - synchronous library calls;
   - asynchronous or reactive calls, if applicable;
   - configuration or metrics, if applicable.
4. Inspect the target library's actual bytecode/API for every supported version. Do not design only from documentation or one implementation class.
5. Write down:
   - the logical operations users should see;
   - the supported and explicitly unsupported versions;
   - synchronous, future-based, callback-based, and streaming variants;
   - expected span parent/child relationships;
   - attributes, metrics, content, and errors to capture;
   - privacy and cardinality risks;
   - existing agent instrumentation that will create nested spans.

Search with `rg`/`rg --files`, preserve unrelated working-tree changes, and do not add analysis notes or scratch files to a commit.

## Choose a good instrumentation point

A good point has all of these properties:

- It represents one user-meaningful operation with an unambiguous start and completion.
- It is stable across the supported version range.
- It exposes the inputs and outputs needed for naming, attributes, outcome, and errors.
- It is high enough in the stack to avoid provider-specific duplication, but low enough to run for all intended entry paths.
- It runs once per logical operation. Watch for overload delegation, default methods, wrappers, retries, and implementations calling other instrumented methods.
- It preserves downstream work beneath the new span when a nested HTTP, database, or messaging span should be its child.
- It can be matched narrowly and cheaply.

Prefer a stable framework abstraction or canonical implementation method over:

- controllers or application code, which miss non-web use;
- low-level network calls, which lose framework semantics and duplicate existing HTTP instrumentation;
- convenience overloads that delegate to another overload;
- constructors, getters, builders, or hot utility methods without a logical operation boundary;
- internal implementation classes that vary by provider or patch release.

When an interface declares the operation, instrument concrete implementations with `hasSuperType(...)` and exclude interfaces. Add a cheap name-based prefilter when practical. Match the exact method name, argument count, argument types, return type, and relevant modifiers so unrelated overloads are not transformed.

Test that one application call creates exactly one intended span.

Do not add call-depth suppression merely because wrappers might exist. First reproduce a wrapper or overload delegating into another matched method and assert the duplicate spans. If duplication is real, add a shared `CallDepth` guard, decrement it on every exit path including exceptions and no-span paths, and keep the regression test proving that only the outer logical call is reported.

## Define support boundaries before writing advice

State a minimum supported version and decide whether there is a maximum known-compatible version. Do not assume that compiling against one version prevents matching an incompatible runtime version.

Implement a class-loader guard that requires:

- the base API classes used by the matcher/advice; and
- a marker class or method introduced at the minimum supported version.

The marker must distinguish the supported line from older versions that contain the common API. Put shared guards in a common base instrumentation so every instrumentation in the plugin uses the same boundary.

Add isolated compatibility tests that load dependency jars with a dedicated `URLClassLoader`, preferably with a `null` parent, and assert:

- representative versions immediately below the boundary are rejected;
- the minimum supported version is accepted;
- all instrumentation classes in the plugin obey the same guard.

Do not use the application's Maven test classpath as proof of version safety; it can hide missing classes through parent delegation.

## Keep class-loader boundaries safe

Agent instrumentation runs across the agent class loader and application class loaders. A class that compiles can still fail at runtime with `NoClassDefFoundError`, `LinkageError`, or a service-loader startup failure.

Follow these rules:

- Guard every external type referenced by advice or helper code.
- Follow the repository's matcher idioms, including static imports for shared matchers such as `classLoaderCanLoadClass`; avoid repeated fully qualified matcher calls that obscure the matcher expression.
- Keep target-library dependencies `provided` unless packaging them is intentional and safe.
- Do not pass agent implementation objects into application code or application objects into long-lived global agent state unnecessarily.
- Use the repository's established non-inline advice pattern unless there is a measured reason not to.
- Suppress advice failures with `suppress = Throwable.class`; instrumentation must not break the application.
- Capture application exceptions through `onThrowable = Throwable.class` and report them on the span.
- Use `@Nullable` where advice values can legitimately be absent.
- Avoid introducing a core-agent dependency or modifying `apm-agent-core` merely for plugin convenience. Make cross-module changes only when the capability truly belongs in shared core behavior and explain that decision in the PR.

Register every built-in instrumentation in:

`META-INF/services/co.elastic.apm.agent.sdk.ElasticApmInstrumentation`

Register a configuration provider in its corresponding service descriptor when the plugin owns configuration. Add the module to the plugin reactor and the assembled-agent build. A passing module test is not proof that the plugin was packaged.

### Make nullness contracts explicit

Add a licensed `package-info.java` with the repository's non-null default to every new production package:

```java
@NonnullApi
package co.elastic.apm.agent.example;

import co.elastic.apm.agent.sdk.NonnullApi;
```

Package annotations do not apply recursively to subpackages. Add a corresponding `package-info.java` wherever production code uses a separate package.

Under `@NonnullApi`, annotate every legitimate exception with `javax.annotation.Nullable`, including method returns, parameters, fields, and advice state. A broad type such as `Object` does not imply that null is allowed.

Treat `@Advice.OnMethodEnter` and `@Advice.Enter` as one nullness contract. When entry advice may decline to create a span or return activation state, annotate the entry method's return value and the exit advice parameter as nullable:

```java
@Nullable
@Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
public static Object onEnter(...) {
    Span<?> span = startSpan(...);
    return span == null ? null : span.activate();
}

@Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
public static void onExit(@Advice.Enter @Nullable Object state, ...) {
    endSpan((Span<?>) state, ...);
}
```

No span being created is an expected path when instrumentation is disabled, there is no eligible context, or a helper declines the operation. Exit advice and helpers must accept absent state as a safe no-op. When correcting nullability, audit every instrumentation, advice, and shared helper in the plugin rather than fixing only the first compiler warning.

## Implement the span lifecycle correctly

### Synchronous operations

On method entry:

1. Create the span from the current context.
2. Return immediately if span creation is disabled or unsampled behavior requires no span. Model the absent advice state explicitly as nullable.
3. Set stable name, type, subtype, action, destination/service target, and request attributes.
4. Activate the span when downstream instrumentations should be children.

On method exit:

1. Capture response metadata if available.
2. Capture the thrown exception and set failure outcome when applicable.
3. Restore/deactivate the previous context exactly once.
4. End the span exactly once.

Test successful, exceptional, null-response, and nested-span paths. Verify that activation does not leak to later work.

### Futures, callbacks, and reactive publishers

Method return is often only assembly, not operation completion. Do not end an async span merely because the method returned a future or publisher.

For cold reactive publishers:

- Wrap the returned publisher without subscribing to it.
- Start the span at subscription time, normally with `defer`, so each subscription receives its own span and correct parent context.
- Preserve cancellation and backpressure semantics.
- Record the first emitted item at the actual first `onNext`, not when the publisher is created.
- Capture failure from the async error signal.
- End once on completion, error, or cancellation; use an atomic guard if multiple terminal hooks can race.
- Mark the span asynchronous where the agent model requires it.
- Ensure downstream client spans are parented beneath the logical span when feasible.

For streaming responses:

- Span duration is the overall response time and must extend through terminal completion.
- Time to first token/chunk is measured from subscription/operation start to the first meaningful emitted response item.
- A provider may emit text over several chunks and finish with a metadata-only chunk. Keeping only the last response loses the assistant content.
- Aggregate all content chunks for the final output while retaining response metadata and usage from terminal chunks.
- Aggregate content only when content capture is enabled; otherwise avoid retaining the full stream in memory.
- On error or cancellation, decide explicitly whether partial content is captured and test that policy.

Never assume one provider's chunk shape represents all providers.

## Follow semantic conventions deliberately

Use an agreed OpenTelemetry semantic convention when one exists. Confirm the convention version used by the repository and distinguish stable fields from experimental ones.

Treat a convention rename as a semantic migration, not a search-and-replace operation. Revalidate the new attribute's meaning, value vocabulary, requirement level, and relationship to other signals before carrying the old value forward.

Every telemetry value needs reliable provenance from a stable target-library API, request, response, configuration, or protocol field. Do not infer providers, database systems, or other semantic identities from implementation class names, package names, substring matching, wrappers, or proxies. A framework name is not a provider name. If the instrumentation point does not expose a trustworthy optional value, omit it. If the value is required for a standard signal, either choose a better instrumentation point, add narrowly scoped provider-specific instrumentation, use a non-standard signal with clear documentation, or do not emit that signal.

Apply provenance decisions consistently across spans, metrics, and events. Removing an unreliable span attribute also means removing the same unreliable metric dimension rather than leaving contradictory telemetry.

For each signal define:

- span name and operation name;
- span kind/type/subtype/action;
- request, response, usage, and error attributes;
- destination/service target fields;
- units and descriptions for metrics;
- attribute types, structured JSON schema, and cardinality limits.

Do not invent a second field for data already represented by a standard attribute without a documented compatibility reason.

Keep semantic attributes separate from the agent's internal span taxonomy. External library values may be appropriate for attributes such as `db.system` or service-target metadata but invalid as APM `span.type`, `span.subtype`, or `span.action`. Check the repository's allowed span-type schema; use a schema-defined generic value such as `unknown` when no reliable mapping exists, and preserve the raw value only in an unconstrained semantic field.

Structured messages may contain more than text. Preserve assistant tool calls and tool-response messages even when `getText()` is empty. Serialize roles and parts with valid JSON escaping, including tool-call ID, name, arguments, and response. An empty text field does not prove that a message has no content.

When an attribute is serialized JSON, make clear whether it is a normalized semantic-convention representation or an actual provider wire payload. Do not imply that a framework-level object reproduces provider wire JSON. Apply the configured size limit to the complete serialized attribute, not independently to every fragment, while preserving valid JSON and required closing structures. Test it by parsing JSON, asserting its structure, and verifying its total bounded length rather than comparing only a fragile string.

## Treat content capture as sensitive data handling

Prompts, responses, documents, queries, tool arguments, and tool results may contain credentials, personal data, or proprietary content.

Content capture must normally be:

- disabled by default;
- controlled by an explicit security/performance-tagged option;
- documented with heap, network, and storage implications;
- truncated through the repository's long-field limit;
- excluded entirely when disabled, rather than collected and discarded later;
- tested in both disabled and enabled states.

If the option is dynamic, read it at a point that gives coherent behavior for an operation. Decide what happens if the value changes during a long stream.

Agent-side dynamic support does not automatically mean Kibana central-configuration UI support. Kibana may require a separate allowlist/UI change. State this limitation in the option documentation instead of implying that central configuration is already available.

Do not assume a Java application or the APM agent reads a `.env` file. Environment variables must be exported by the shell, sourced by the launch script, or loaded by the application/framework.

## Add metrics without widening core unnecessarily

Prefer the embedded OpenTelemetry SDK already provided by the agent when a plugin needs an OTel metric. Do not add core histogram APIs solely for one instrumentation if the embedded SDK can provide the metric.

For a histogram:

- define an explicit unit and description;
- use low-cardinality dimensions;
- initialize it through an available SDK lifecycle and tolerate the SDK being unavailable;
- record one observation per logical operation at the correct event;
- test multiple observations and their attributes.

Check all required dimensions of a standard metric before using its standard name. Never invent a required dimension merely to make the signal appear compliant. If a required dimension is unavailable at the selected instrumentation point, explicitly reconsider the instrumentation point, metric name, or whether the metric should be emitted.

An exported histogram such as `{"values":[6.8],"counts":[1]}` is normal after one request. With more traffic in the same aggregation period it may contain multiple bucket/value entries. Do not diagnose a one-count histogram as broken without generating repeated traffic and checking export temporality and dimensions.

For streaming GenAI operations, keep the TTFC metric distinct from overall response latency. The logical span duration already represents total response time; the TTFC histogram measures responsiveness before the first chunk.

## Add the module completely

A built-in plugin usually requires all of the following:

- a module POM following neighboring plugin conventions;
- a licensed `package-info.java` with `@NonnullApi` for every production package;
- target-library dependencies with correct scopes;
- instrumentation and advice classes;
- shared helpers kept as small and allocation-conscious as practical;
- the instrumentation service descriptor;
- a configuration-provider descriptor when applicable;
- inclusion in `apm-agent-plugins/pom.xml`;
- inclusion in the assembled-agent build/dependency list;
- tests and compatibility fixtures;
- documentation and changelog updates.

Follow the neighboring module's Java target rather than silently raising it. The repository build requires Java 17, even when plugin bytecode targets an older Java runtime.

## Test in layers

### Matcher and advice tests

Prove:

- only intended types and methods match;
- one logical call produces one span;
- span name, type, subtype, action, parent, outcome, and expected attributes are correct;
- errors are captured and context is restored;
- content is absent by default and present when enabled;
- structured messages, documents, tool calls, and tool responses survive serialization;
- scalar and structured content obey the complete attribute-size limit, and structured content remains valid after truncation;
- synchronous and async variants have correct lifetimes.

### Streaming tests

Use a stream containing at least:

1. a first content chunk;
2. another content chunk;
3. a final metadata-only chunk.

Assert:

- no span ends before subscription termination;
- final assistant output contains all content chunks;
- final response metadata and usage are retained;
- TTFC is recorded once and is non-negative;
- the TTFC histogram receives the observation;
- error and cancellation paths end once;
- multiple subscriptions do not reuse a span.

### Version compatibility tests

Test at least:

- the minimum supported version;
- the newest version used by the module tests;
- one or more representative unsupported versions immediately below the boundary.

The unsupported-version test must prove that the class-loader matcher rejects the library before advice linkage can occur.

### Module and assembly verification

Run the narrow module suite first, then a clean assembly. Adapt the module path as needed:

```bash
JAVA_HOME=/path/to/jdk17 PATH=/path/to/jdk17/bin:$PATH \
  ./mvnw -pl apm-agent-plugins/<plugin-module> -am clean test

JAVA_HOME=/path/to/jdk17 PATH=/path/to/jdk17/bin:$PATH \
  ./mvnw -pl elastic-apm-agent -am -DskipTests clean package
```

Run the repository's applicable Error Prone/NullAway or equivalent static-analysis profile as well. A default Maven compilation or an IDE build may not exercise the nullness checks used during review or CI. Resolve warnings by documenting the real contract; do not silence them with broad suppression or fake non-null values.

Then verify that the packaged agent contains the transformed plugin classes/service entries and launch it once:

```bash
unzip -l elastic-apm-agent/target/elastic-apm-agent-*.jar | rg '<plugin-package|plugin-artifact>'

/path/to/jdk17/bin/java \
  -javaagent:/absolute/path/to/elastic-apm-agent/target/elastic-apm-agent-<version>.jar \
  -Delastic.apm.recording=false \
  -version
```

A module test can pass while the assembled agent contains stale or invalid bytecode. If startup reports `java.lang.Error: Unresolved compilation problems`, perform a clean Maven compile/install of the affected reactor dependencies, rebuild the agent, inspect the packaged class, and repeat the startup smoke test. Do not ship an agent validated only from IDE output.

### Repository integration testing

Do not assume every plugin needs a new forked application under `integration-tests/`. Choose the smallest test topology that proves the behavior at risk, following nearby instrumentation for the same kind of technology.

#### Choose the test topology deliberately

The repository uses several complementary patterns:

1. **In-process instrumentation tests.** Most library plugins extend `AbstractInstrumentationTest`, execute the real library API with Byte Buddy instrumentation active, and assert against `MockReporter`. Prefer this for matcher/advice behavior, span fields, context propagation, errors, configuration changes, and library calls that do not require packaging boundaries.
2. **Isolated dependency-version tests.** Use the repository's dependency-loading test runner, such as `JUnit4TestClassWithDependencyRunner`, when the same behavioral suite must run against multiple library versions or when the application class path could hide class-loader problems. Explicitly include split transitive artifacts required by newer versions.
3. **Local protocol fixtures.** Use an in-process server such as WireMock on dynamic ports for HTTP clients and protocol edge cases. Verify propagation headers and server-observed requests as well as the reported span.
4. **Containerized dependencies.** Use Testcontainers for databases, brokers, and application servers when a faithful service is required. Follow `TestContainersUtils` conventions, constrain container memory, use mapped ports and readiness strategies, and stop containers reliably. When the host test process connects to a container, use `container.getHost()` with `container.getMappedPort(...)`; reserve container-internal addresses, network aliases, and original ports for container-to-container communication on the same Docker network.
5. **Forked-JVM tests.** Launch a separate application process when testing premain/runtime attach, agent packaging, service-loader behavior, framework auto-configuration, Java-version boundaries, or serialized intake from the actual agent. Use `AgentFileAccessor` or the established agent/container helper instead of guessing a JAR path.
6. **Application-server matrix tests.** Extend the existing application-server infrastructure when testing WAR deployment or servlet-container-specific behavior. Reuse `AgentTestContainer`, its deployment/runtime-attach support, remote debugging, and the existing mock APM server rather than creating a parallel harness.

A new standalone integration-test module is justified when no existing topology proves an important packaging or wiring boundary. It is not a substitute for focused in-process tests, version compatibility tests, or manual validation.

#### Reuse behavioral patterns from established plugins

Apply the patterns relevant to the technology:

- **HTTP clients:** use dynamic HTTP and HTTPS ports; test success, protocol errors, connection failures, redirects, destination fields, outgoing trace/baggage headers, disabled propagation, and async completion. Assert what the server received, not only what the reporter contains.
- **Databases:** use a real compatible server when wire behavior matters; cover representative create/read/update/delete and cursor/paging operations, errors, statements, destination/service target, sync versus async flags, and cleanup. Perform fixture cleanup outside the traced operation and reset the reporter so setup/teardown spans do not pollute assertions.
- **Messaging:** exercise producer and consumer paths together, including header propagation, batch processing and span links, ignored queues/topics, sanitization, partial iteration, and async callbacks. A request/reply topic can reduce arbitrary waits while proving both directions.
- **Reactive libraries:** use `StepVerifier` or the framework's deterministic subscriber test utility; force thread switches where propagation is the subject; test completion, error, empty result, cancellation, multiple subscriptions, hook registration/cleanup, and reference-count or context release. Do not validate reactive behavior by blocking everything into a synchronous call.
- **Web frameworks:** invoke real annotated and/or functional endpoints through an HTTP client; cover route naming, status/error mappings, empty and streaming responses, path parameters, and child spans. Avoid duplicating every controller style when lower-level instrumentation tests already prove the common path.
- **Application servers:** package the deployable artifact before running, deploy through the shared container abstraction, wait on an explicit status endpoint, ignore readiness traffic, and test both premain and runtime attach only where supported.

Test operation semantics rather than incidental implementation order. Assert exact counts when duplicates or lifecycle leaks are the risk; otherwise select events by stable name/type/IDs so unrelated framework spans do not make the test brittle.

#### Follow repository test idioms

Use the repository's assertion facade in instrumentation tests:

```java
import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
```

That class extends AssertJ's `Assertions`, so the same import supports agent-specific assertions and ordinary strings, numbers, maps, collections, and JSON-derived values. Do not mix it with fully qualified `org.assertj.core.api.Assertions.assertThat(...)` calls unless a real overload conflict requires it.

Keep test bodies focused on behavior. Follow neighboring import conventions and statically import repeatedly used constants such as `TimeUnit.SECONDS` when that improves readability. Avoid fully qualified class names scattered through assertions and lifecycle calls.

Treat use of shared test abstractions as part of correctness, not only style. Before creating a helper, inspect the repository's agent-JAR accessors, container abstractions, assertion classes, dependency-version runners, mock servers, and test utilities. If a new test exposes a generally incorrect behavior in shared test infrastructure, fix the abstraction at its owning test layer and let all callers benefit. Do not add a plugin-local workaround. This does not authorize changing production agent core for plugin convenience: distinguish a justified shared-test-infrastructure correction from a new production dependency or API.

#### Make fixtures deterministic and CI-safe

- Require no cloud account, external API key, public service, or internet access at test runtime beyond dependency/container acquisition already expected by CI.
- Use dynamic host ports, unique database/topic/resource names, and per-test temporary directories. Never assume tests run alone.
- For host-to-container calls, construct endpoints from `getHost()` and the mapped port. For container-to-container calls, use a shared Docker network, a network alias, and the container port. Do not interchange these address spaces.
- Use explicit readiness conditions and bounded eventual assertions instead of sleeps. Include process or container logs in startup failures.
- Close clients, schedulers, JMX connections, processes, and containers in teardown that also runs after failures. Kill descendant processes when the fixture launches them.
- Disable span compression when exact span counts are under test.
- Clear the reporter or fake intake only after readiness/setup traffic has completed.
- Treat Failsafe's CI reruns as diagnostic tolerance, not permission for flaky timing or shared state.
- Pin images and representative dependency versions deliberately. Document why an old service image is retained when client compatibility requires it.

When using a fake APM intake server, support the endpoints and encodings the agent actually uses, parse NDJSON safely while the reporter runs concurrently, and retain the complete intake envelopes. Correlate transactions, spans, and errors by `trace_id`, `transaction_id`, and `parent_id`; assert serialized schema fields instead of relying solely on in-memory agent objects.

Metrics are asynchronous. Configure a short test metrics interval and await the named sample with a bounded timeout. For histograms, assert the presence of `values` and `counts` and the expected total observation count without depending on a particular bucket layout.

#### Test supported versions without multiplying cost blindly

Run the core behavioral suite against the minimum supported and current representative versions. Add intermediate versions where APIs, artifact layout, or behavior changed. Use the latest version for expensive variants that do not materially change across every release, such as a legacy facade, rather than taking a full Cartesian product.

Class-loader isolation matters: a dependency already loaded by the Maven/application class loader can make a version test pass for the wrong reason. Use the repository's isolated dependency runner and list required transitive artifacts explicitly. Keep the separate below-minimum class-loader guard test; a supported-version behavioral matrix does not prove that unsupported versions are rejected safely.

#### Add a standalone integration-test module completely

When a separate module under `integration-tests/<technology>-test` is warranted:

1. Build a minimal representative application and exercise its public API.
2. Add the module to `integration-tests/pom.xml`.
3. Use a Failsafe name such as `*IT` and confirm that `verify` executes it.
4. Select the correct CI profile. Ordinary forked tests belong to `ci-non-application-server-integration-tests`; shared application-server tests belong to their dedicated profile.
5. Confirm whether reactor registration already makes the existing workflow run it before editing CI configuration.
6. Add the non-published artifact to the root Central Publishing exclusion list when required.
7. Check the packaged application's dependency tree. Parent dependency management may override a newer framework transitive dependency with an incompatible version; add the narrowest explicit override and explain it.
8. Capture application output and fail readiness immediately if the child process exits.

Run the focused test with the same profile used by CI, then read the Failsafe report. Compilation and application packaging do not prove that the integration test executed:

```bash
JAVA_HOME=/absolute/path/to/jdk17 PATH=/absolute/path/to/jdk17/bin:$PATH \
  ./mvnw -pl integration-tests/<technology>-test \
  -P ci-non-application-server-integration-tests verify \
  -Dmaven.javadoc.skip=true
```

Pin the forked JVM to the required JDK and verify it from logs. On macOS, check the result of `/usr/libexec/java_home`; when several Homebrew JDKs are installed, use the exact JDK 17 path rather than trusting version discovery.

#### Spring AI test-topology example

Spring AI does not inherently require a standalone module under `integration-tests/`. Focused in-process instrumentation tests plus isolated supported/unsupported-version tests are normally sufficient for matchers, advice lifecycle, content serialization, streaming aggregation, metrics, vector stores, and tool callbacks.

Add a forked packaged-agent test only when the change depends on a boundary those tests cannot prove, such as premain packaging, service loading, framework auto-configuration, MCP child-process discovery, or serialized intake from the assembled agent. State the exact boundary before creating the module. A manually maintained application used for exploratory smoke testing is not by itself a reason to copy that application into the repository or CI.

When a forked Spring AI test is justified, keep it deterministic and local. Use fake chat and embedding models and, if required, a local stdio MCP server. Cover only the boundary that motivated the test rather than duplicating the complete in-process behavioral suite.

### Real-application smoke test

Use a minimal application with the actual agent JAR and target framework. Exercise every operation through its public API, including sync, streaming, tools, and errors where applicable.

Before blaming instrumentation, verify:

- the launch script references the newly built agent JAR;
- the script uses the expected JDK (`process.runtime.version` confirms it);
- the agent started without service-loader or linkage errors;
- experimental instrumentation is enabled if required;
- content-capture variables are truly exported, not merely present in `.env`;
- the request reached the expected framework route.

Inspect the complete trace, not only one document. Confirm the transaction name, started-span count, logical span, downstream HTTP/database spans, parent IDs, content attributes, and streaming duration. A generic transaction such as `POST unknown route` with zero spans can indicate a broader agent startup/framework instrumentation problem rather than a defect in the new plugin.

## Document the user contract

For a new plugin update all applicable locations:

- `CHANGELOG.next-release.md`;
- `docs/reference/supported-technologies.md` with exact supported versions and maturity;
- configuration reference/navigation/property-file documentation;
- enablement instructions for experimental plugins;
- security/performance warnings for captured content;
- the version introducing each API/config option;
- any Kibana central-configuration limitation.

Use one maturity term consistently. If the plugin is experimental, say so in the changelog and supported-technologies table, put it in the `experimental` instrumentation group, document that it is disabled by default, and show how to enable it. Do not describe an experimental plugin as generally available.

## Prepare the pull request

Rebase onto current `main` before opening the PR when the branch has diverged and the worktree is clean. Resolve conflicts carefully, rerun compatibility/module tests and agent assembly, then push with `--force-with-lease` if the rebase rewrote published commits. Never use destructive reset/checkout operations on unrelated user work.

In the PR description explain:

- what operations are instrumented;
- sync/async/streaming lifecycle behavior;
- spans, attributes, metrics, and content policy;
- supported versions and maturity;
- how old unsupported versions are rejected;
- configuration defaults, dynamic behavior, and UI limitations;
- tests and full-agent verification performed.

For the repository PR template, select only **This is a new plugin** and satisfy its items:

- changelog updated;
- style guidelines followed;
- documentation updated;
- effective tests added;
- new and existing module unit tests pass locally;
- supported-technologies table updated;
- API/config introduction version documented;
- unsupported versions protected by a described class-loader guard and compatibility tests.

Do not check repository-wide test claims that were not actually run. State the exact suite and assembly commands used.

## Completion checklist

Do not call the plugin complete until all applicable items are true:

- [ ] Instrumentation points represent stable logical operations.
- [ ] Matchers are precise and avoid duplicate spans.
- [ ] Supported and unsupported version boundaries are explicit.
- [ ] Class-loader guards cover every external advice/helper dependency.
- [ ] Every production package has a licensed `package-info.java` with `@NonnullApi`.
- [ ] Nullable advice returns, `@Advice.Enter` state, parameters, fields, and helper results are annotated consistently.
- [ ] All plugin advice classes were audited for the same nullness contract, not only the initially failing class.
- [ ] Unsupported versions are rejected in isolated compatibility tests.
- [ ] Sync spans activate, deactivate, capture errors, and end once.
- [ ] Async/streaming spans start and end at real execution boundaries.
- [ ] Streaming output aggregates content chunks and terminal metadata.
- [ ] Downstream spans have the intended parent.
- [ ] Semantic-convention attributes use correct names and types.
- [ ] Every semantic value has stable provenance; no class-name, package-name, substring, wrapper, or proxy heuristic supplies identity metadata.
- [ ] Semantic attributes are kept separate from schema-constrained APM span type/subtype/action fields.
- [ ] Metrics use correct event timing, unit, description, and dimensions.
- [ ] Standard metrics either provide required dimensions reliably or are omitted/redesigned rather than populated with guessed values.
- [ ] Sensitive content is opt-in, truncated, and absent when disabled.
- [ ] Structured content remains valid and within the complete attribute-size limit after truncation.
- [ ] Tool calls and tool responses are represented as structured content.
- [ ] Plugin/configuration service descriptors are present.
- [ ] Module is included in both the reactor and assembled agent.
- [ ] Focused tests and clean module tests pass on the required JDK.
- [ ] Applicable Error Prone/NullAway or repository static-analysis checks pass without unjustified suppressions.
- [ ] Full agent assembles, contains the plugin, and starts successfully.
- [ ] The chosen integration-test topology is the smallest one that proves the relevant runtime boundaries.
- [ ] Supported-version behavior runs in an isolated class loader where application-class-path leakage is possible.
- [ ] External fixtures are deterministic, local, credential-free, parallel-safe, and cleaned up reliably.
- [ ] Host/container endpoints use the correct Testcontainers address space and mapped-port semantics.
- [ ] Tests use repository assertion and test-helper conventions without unnecessary local workarounds.
- [ ] A packaged-agent/forked-app test exists only when a named assembly, auto-configuration, attach, service-loading, child-process, or serialized-intake boundary cannot be proven by smaller tests.
- [ ] Intake assertions cover trace relationships, captured content, and exported metrics where applicable.
- [ ] Any standalone integration-test module is registered in the reactor, selected by CI, and excluded from publishing where required.
- [ ] A real application produces the expected complete trace.
- [ ] Changelog, supported technologies, config docs, maturity, and version are updated.
- [ ] PR checklist claims match work actually performed.
- [ ] Only intended files are staged; analysis documents remain uncommitted unless explicitly requested.
