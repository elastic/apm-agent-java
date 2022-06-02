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
package co.elastic.apm.agent.log4j2;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/**
 * This class only delegates tests to the current-version log4j2 tests through JUnit 4, so that it can be ran using
 * {@link TestClassWithDependencyRunner} in a dedicated CL where an older log4j2 version is loaded.
 * This is required because the agent is using log4j2 and in tests they retain their original packages (relocation only
 * takes place during packaging).
 */
@Ignore
public class Log4j2InstrumentationTestVersions extends Log4j2InstrumentationTest {

    @BeforeClass
    public static void resetConfigFactory() {
        ConfigurationFactory.resetConfigurationFactory();
    }

    @AfterClass
    public static void reInitLogging() {
        LoggingConfiguration.init(List.of(), "");
    }

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
    }

    @After
    @Override
    public void closeLogger() {
        super.closeLogger();
    }

    @Test
    @Override
    public void testSimpleLogReformatting() throws Exception {
        super.testSimpleLogReformatting();
    }

    @Test
    @Override
    public void testMarkers() throws Exception {
        super.testMarkers();
    }

    @Test
    @Override
    public void testShadingIntoOriginalLogsDir() throws Exception {
        super.testShadingIntoOriginalLogsDir();
    }

    @Test
    @Override
    public void testLazyEcsFileCreation() throws Exception {
        super.testLazyEcsFileCreation();
    }

    @Test
    @Override
    public void testLogReformattingReplaceOriginal() throws IOException {
        super.testLogReformattingReplaceOriginal();
    }

    @Test
    @Override
    public void testDynamicConfiguration() throws Exception {
        super.testDynamicConfiguration();
    }

    @Test
    @Override
    public void testLogOverride() throws IOException {
        super.testLogOverride();
    }

    @Test
    @Override
    public void testEmptyFormatterAllowList() throws Exception {
        super.testEmptyFormatterAllowList();
    }

    @Test
    @Override
    public void testReformattedLogRolling() throws IOException {
        super.testReformattedLogRolling();
    }
}
