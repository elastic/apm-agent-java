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

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ProcessExecutionUtil {

    public static CommandOutput executeCommand(List<String> command) {
        return executeCommand(command, TimeUnit.SECONDS.toMillis(5));
    }

    /**
     * Spawns a process to execute the provided command, applying the requested timeout.
     * This method always exists quietly.
     * Process error stream is redirected to the standard output stream.
     * If the execution of the requested command times out, the process is terminated and this method returns immediately.
     * The returned output will reflect that by containing a {@link TimeoutException} in its {@link CommandOutput#exceptionThrown}.
     * @param command the command to execute on a separate process
     * @param timeoutMillis timeout in millis, after which the process is terminated and this method returns
     * @return the result of the command execution, including output, exit code and exceptions thrown by the current process.
     */
    public static CommandOutput executeCommand(List<String> command, long timeoutMillis) {
        ProcessBuilder buildTheProcess = new ProcessBuilder(command);
        // merge stdout and stderr so we only have to read one stream
        buildTheProcess.redirectErrorStream(true);
        Process spawnedProcess = null;
        int exitValue = -1;
        Throwable exception = null;
        StringBuilder commandOutput = new StringBuilder();
        try {
            spawnedProcess = buildTheProcess.start();

            long start = System.currentTimeMillis();
            long duration = 0L;
            boolean isAlive = true;
            byte[] buffer = new byte[4 * 1000];
            try (InputStream in = spawnedProcess.getInputStream()) {
                // stop trying if the time elapsed exceeds the timeout
                while (isAlive && duration < timeoutMillis) {
                    while (in.available() > 0) {
                        int lengthRead = in.read(buffer, 0, buffer.length);
                        commandOutput.append(new String(buffer, 0, lengthRead));
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        //no action, just means the next loop iteration checking
                        //for timeout or process dead, is earlier
                    }
                    duration = System.currentTimeMillis() - start;
                    // if it's not alive but there is still readable input, then continue reading
                    isAlive = processIsAlive(spawnedProcess) || in.available() > 0;
                }
                //would like to call waitFor(TIMEOUT) here, but that is 1.8+
                //so pause for a bit, and just ensure that the output buffers are empty
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    //no action, just means the exit is earlier
                }

                if (duration >= timeoutMillis) {
                    spawnedProcess.destroy();
                    throw new TimeoutException(String.format(
                        "Execution of %s exceeded the specified timeout of %sms. Process killed.",
                        cmdAsString(command),
                        timeoutMillis)
                    );
                }

                //handle edge case where process terminated but still has unread IO
                //and in.available() could have returned 0 from IO buffering
                while (in.available() > 0) {
                    int lengthRead = in.read(buffer, 0, buffer.length);
                    commandOutput.append(new String(buffer, 0, lengthRead));
                }
            }

        } catch (Throwable e1) {
            exception = e1;
        } finally {
            // Cleanup as well as we can
            if (spawnedProcess != null && processIsAlive(spawnedProcess)) {
                spawnedProcess.destroy();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    //no action, just means the next loop iteration is earlier
                }
                // when no longer need 1.7 compatibility, add these lines
                // try{Thread.sleep(50);}catch (InterruptedException e) {}
                // if (p.isAlive()) {
                // p.destroyForcibly();
                // }
            }
            if (spawnedProcess != null) {
                try {
                    exitValue = spawnedProcess.exitValue();
                } catch (IllegalThreadStateException e2) {
                    if (exception == null) {
                        exception = e2;
                    }
                }
            }
        }
        return new CommandOutput(commandOutput, exitValue, exception);
    }

    public static boolean processIsAlive(Process proc) {
        //1.7 doesn't have Process.isAlive() so need to implement it
        //This implementation is essentially what it does in 1.8
        try {
            proc.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    public static String cmdAsString(List<String> cmd) {
        StringBuilder cmdToString = new StringBuilder("\"");
        for (int i = 0; i < cmd.size();) {
            cmdToString.append(cmd.get(i));
            if (++i < cmd.size()) {
                cmdToString.append(" ");
            }
        }
        cmdToString.append("\"");
        return cmdToString.toString();
    }

    public static class CommandOutput {
        StringBuilder output;
        int exitCode;
        Throwable exceptionThrown;

        public CommandOutput(StringBuilder output, int exitCode, Throwable exception) {
            super();
            this.output = output;
            this.exitCode = exitCode;
            this.exceptionThrown = exception;
        }

        public StringBuilder getOutput() {
            return output;
        }

        public boolean exitedNormally() {
            return getExceptionThrown() == null && exitCode == 0;
        }

        public int getExitCode() {
            return exitCode;
        }

        public Throwable getExceptionThrown() {
            return exceptionThrown;
        }

        public String toString() {
            if (this.exceptionThrown != null) {
                return "Exit Code: " + this.exitCode + "; Output: " + this.output.toString() +
                    "\r\nException: " + this.exceptionThrown;
            } else {
                return "Exit Code: " + this.exitCode + "; Output: " + this.output.toString();
            }
        }
    }
}
