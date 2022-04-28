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
package co.elastic.apm.agent.log4j2.error;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This class only delegates tests to the current-version log4j2 tests through JUnit 4, so that it can be ran using
 * {@link TestClassWithDependencyRunner} in a dedicated CL where an older log4j2 version is loaded.
 * This is required because the agent is using log4j2 and in tests they retain their original packages (relocation only
 * takes place during packaging).
 */
@Ignore
public class Log4j2ErrorCapturingTestVersions extends Log4j2LoggerErrorCapturingInstrumentationTest {

    @Test
    @Override
    public void captureErrorExceptionWithStringMessage() {
        super.captureErrorExceptionWithStringMessage();
    }

    @Test
    @Override
    public void captureErrorExceptionWithMessageMessage() {
        super.captureErrorExceptionWithMessageMessage();
    }

    @Test
    @Override
    public void captureFatalException() {
        super.captureFatalException();
    }
}
