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
package co.elastic.apm.agent.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ProcessExecutionUtilTest {

    private static final boolean isWindows = System.getProperty("os.name").startsWith("Windows");

    @Test
    void testQuietExecutionOfNonExistingCommand() {
        final AtomicReference<ProcessExecutionUtil.CommandOutput> cmdOutputRef = new AtomicReference<>();
        assertDoesNotThrow(() -> cmdOutputRef.set(ProcessExecutionUtil.executeCommand(List.of("does", "not", "exist"))));
        ProcessExecutionUtil.CommandOutput commandOutput = cmdOutputRef.get();
        assertThat(commandOutput.getExitCode()).isLessThan(0);
        assertThat(commandOutput.getExceptionThrown()).isNotNull();
    }

    @Test
    void testTimeout() {
        List<String> cmd;
        if (isWindows) {
            // todo implement sleep for Windows
            cmd = List.of();
        } else {
            cmd = List.of("sleep", "0.2");
        }
        ProcessExecutionUtil.CommandOutput commandOutput = ProcessExecutionUtil.executeCommand(cmd, 100);
        System.out.println("commandOutput = " + commandOutput);
        assertThat(commandOutput.getExceptionThrown()).isInstanceOf(TimeoutException.class);
        // exit code 143 is related to the SIGTERM termination signal invoked by killing the process
        assertThat(commandOutput.getExitCode()).isEqualTo(143);

        commandOutput = ProcessExecutionUtil.executeCommand(cmd, 300);
        System.out.println("commandOutput = " + commandOutput);
        assertThat(commandOutput.getExceptionThrown()).isNull();
        assertThat(commandOutput.getExitCode()).isEqualTo(0);
    }

    @Test
    void cmdAsString() {
        assertThat(ProcessExecutionUtil.cmdAsString(List.of())).isEqualTo("\"\"");
        assertThat(ProcessExecutionUtil.cmdAsString(List.of("one"))).isEqualTo("\"one\"");
        assertThat(ProcessExecutionUtil.cmdAsString(List.of("one", "two"))).isEqualTo("\"one two\"");
    }
}
