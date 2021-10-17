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

public class ProcessExecutionUtil {

    public static CommandOutput executeCommand(List<String> command) {
        ProcessBuilder buildTheProcess = new ProcessBuilder(command);
        // merge stdout and stderr so we only have to read one stream
        buildTheProcess.redirectErrorStream(true);
        Process spawnedProcess = null;
        int exitValue = -1;
        Throwable exception = null;
        StringBuilder commandOutput = new StringBuilder();
        try {
            spawnedProcess = buildTheProcess.start();

            long timeout = 5 * 1000L; // NOTE 5 second timeout!
            long start = System.currentTimeMillis();
            long now = start;
            boolean isAlive = true;
            byte[] buffer = new byte[4 * 1000];
            try (InputStream in = spawnedProcess.getInputStream()) {
                // stop trying if the time elapsed exceeds the timeout
                while (isAlive && (now - start) < timeout) {
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
                    now = System.currentTimeMillis();
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
