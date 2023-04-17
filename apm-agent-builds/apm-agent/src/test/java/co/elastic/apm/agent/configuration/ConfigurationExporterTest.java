/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * This is not an actual test.
 * This class auto-generates the config docs based on the {@link ConfigurationOptionProvider}s
 * <p>
 * The reason why this is a test class is that its a simple way to re-generate the the configuration documentation frequently -
 * every time you execute the tests.
 * </p>
 * <p>
 * Also, releases would fail if the documentation is not up-to-date as the maven release plugin executes the tests before the release
 * and checks if there are uncommitted changes.
 * </p>
 * <p>
 * As the apm-agent-java module bundles all plugins/instrumentations,
 * it has access to all configuration options of all plugins,
 * which makes this module a natural fit for generating the docs.
 * </p>
 * <p>
 * Alternatives: there is also the fmpp-maven-plugin which can render freemarker templates during the build.
 * The problem is that the model (the actual {@link ConfigurationRegistry},
 * which includes all plugin configuration classes,
 * can't be stored in a separate model file,
 * but is created dynamically in {@link #setUp()}.
 * This picks up all {@link ConfigurationOptionProvider} classes in all plugins.
 * </p>
 */
class ConfigurationExporterTest {

    private ConfigurationRegistry configurationRegistry;
    private Path renderedDocumentationPath;

    @BeforeEach
    void setUp() {
        renderedDocumentationPath = Paths.get("../../docs/configuration.asciidoc");
        ElasticApmTracer tracer = mock(ElasticApmTracer.class);
        doReturn(tracer).when(tracer).require(ElasticApmTracer.class);
        doReturn(Tracer.TracerState.UNINITIALIZED).when(tracer).getState();
        GlobalTracer.init(tracer);
        configurationRegistry = ConfigurationRegistry.builder()
            .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class))
            .build();
    }

    @AfterEach
    void tearDown() {
        GlobalTracer.setNoop();
    }

    /**
     * This test compares the current state of the configuration docs with the auto-generated documentation and fails if there is a
     * mismatch. As a side effect, it overwrites the docs with the generated ones, unless this capability is disabled through the
     * {@code elastic.apm.overwrite.config.docs} system property. This allows to have different behavior between local tests and CI.
     * Since we allow a limited number of unit-test failures, this test would have always passed without this configuration.
     */
    @Test
    void testGeneratedConfigurationDocsAreUpToDate() throws IOException, TemplateException {
        String renderedDocumentation = renderDocumentation(configurationRegistry);
        String expected = new String(Files.readAllBytes(this.renderedDocumentationPath), StandardCharsets.UTF_8);

        if (Boolean.parseBoolean(System.getProperty("elastic.apm.overwrite.config.docs", Boolean.TRUE.toString()))) {
            // unless explicitly disabled (e.g. on CI) overwrite the current documentation
            Files.write(renderedDocumentationPath, renderedDocumentation.getBytes(StandardCharsets.UTF_8));
        }

        assertThat(renderedDocumentation)
            .withFailMessage("The rendered configuration documentation (/docs/configuration.asciidoc) is not up-to-date.\n" +
                "If you see this error on CI, it means you have to execute the tests locally " +
                "(./mvnw -Dsurefire.failIfNoTests=false -Dtest=ConfigurationExporterTest -pl apm-agent -am clean test) " +
                "which will update the rendered docs.\n" +
                "If you see this error while running the tests locally, there's nothing more to do - the rendered docs have been updated " +
                "and the following test execution should not have failed.")
            .isEqualTo(expected);
    }

    static String renderDocumentation(ConfigurationRegistry configurationRegistry) throws IOException, TemplateException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_27);
        cfg.setClassLoaderForTemplateLoading(ConfigurationExporterTest.class.getClassLoader(), "/");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);

        Template temp = cfg.getTemplate("configuration.asciidoc.ftl");
        StringWriter tempRenderedFile = new StringWriter();
        tempRenderedFile.write("////\n" +
            "This file is auto generated\n" +
            "\n" +
            "Please only make changes in configuration.asciidoc.ftl\n" +
            "////\n");
        final List<ConfigurationOption<?>> nonInternalOptions = configurationRegistry.getConfigurationOptionsByCategory()
            .values()
            .stream()
            .flatMap(List::stream)
            .filter(option -> !option.getTags().contains("internal"))
            .collect(Collectors.toList());
        final Map<String, List<ConfigurationOption<?>>> optionsByCategory = nonInternalOptions.stream()
            .collect(Collectors.groupingBy(ConfigurationOption::getConfigurationCategory, TreeMap::new, Collectors.toList()));
        temp.process(Map.of(
            "config", optionsByCategory,
            "keys", nonInternalOptions.stream().map(ConfigurationOption::getKey).sorted().collect(Collectors.toList())
        ), tempRenderedFile);

        // re-process the rendered template to resolve the ${allInstrumentationGroupNames} placeholder
        StringWriter out = new StringWriter();
        new Template("", tempRenderedFile.toString(), cfg)
            .process(Map.of("allInstrumentationGroupNames", getAllInstrumentationGroupNames()), out);

        return out.toString();
    }

    public static String getAllInstrumentationGroupNames() {
        Set<String> instrumentationGroupNames = new TreeSet<>();
        for (ElasticApmInstrumentation instrumentation : DependencyInjectingServiceLoader.load(ElasticApmInstrumentation.class, MockTracer.create())) {
            instrumentationGroupNames.addAll(instrumentation.getInstrumentationGroupNames());
        }

        StringBuilder allGroups = new StringBuilder();
        for (Iterator<String> iterator = instrumentationGroupNames.iterator(); iterator.hasNext(); ) {
            allGroups.append('`').append(iterator.next()).append('`');
            if (iterator.hasNext()) {
                allGroups.append(", ");
            }
        }
        return allGroups.toString();
    }

}

