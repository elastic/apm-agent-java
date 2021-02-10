/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.attach;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public enum Jps {
    INSTANCE;

    private final boolean available;

    Jps() {
        boolean tempAvailable = false;
        try {
            tempAvailable = new ProcessBuilder(jpsCommand())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start().waitFor() == 0;
        } catch (Exception ignore) {
        }
        available = tempAvailable;
    }

    public String getCommand(String pid, Users.User user) throws Exception {
        Process jps;
        if (user.isCurrentUser()) {
            jps = new ProcessBuilder(jpsCommand()).start();
        } else if (user.canSwitchToUser()) {
            jps = user.runAs(JvmDiscoverer.JpsFinder.getJpsPath().toString(), "-lv").start();
        } else {
            throw new IllegalStateException("Can't switch to user " + user.getUsername());
        }
        if (jps.waitFor() == 0) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(jps.getInputStream()));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                final int firstSpace = line.indexOf(' ');
                String linePid = line.substring(0, firstSpace);
                if (pid.equals(linePid)) {
                    return line.substring(firstSpace + 1);
                }
            }
        } else {
            throw new IllegalStateException(RemoteAttacher.toString(jps.getErrorStream()));
        }
        return null;
    }


    public boolean isAvailable() {
        return this.available;
    }

    private static List<String> jpsCommand() {
        return Arrays.asList(JvmDiscoverer.JpsFinder.getJpsPath().toString(), "-lv");
    }

}
