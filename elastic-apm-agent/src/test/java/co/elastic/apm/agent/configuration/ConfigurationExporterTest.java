/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.configuration;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.ServiceLoader;
import java.util.TreeMap;

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

    @BeforeEach
    void setUp() {
        configurationRegistry = ConfigurationRegistry.builder()
            .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class))
            .build();
    }

    @Test
    void exportConfiguration() throws IOException, TemplateException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_27);
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "/");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);

        Template temp = cfg.getTemplate("configuration.asciidoc.ftl");
        Writer out = new FileWriter("../docs/configuration.asciidoc");
        out.write("////\n" +
            "This file is auto generated\n" +
            "\n" +
            "Please only make changes in configuration.asciidoc.ftl\n" +
            "////\n");
        temp.process(Collections.singletonMap("config", new TreeMap<>(configurationRegistry.getConfigurationOptionsByCategory())), out);
    }

}

