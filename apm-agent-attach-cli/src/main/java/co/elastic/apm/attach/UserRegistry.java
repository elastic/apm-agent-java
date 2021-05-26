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

import com.sun.jna.Platform;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserRegistry {

    private final Map<String, User> users;

    private UserRegistry(List<User> users) {
        this.users = new HashMap<>();
        for (User user : users) {
            this.users.put(user.username, user);
        }
    }

    public static UserRegistry getAllUsersMacOs() throws IOException, InterruptedException {
        List<User> users = new ArrayList<>();
        Process dscl = new ProcessBuilder("dscl", ".", "list", "/Users").start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(dscl.getInputStream()));
        for (String user; (user = reader.readLine()) != null; ) {
            // system users
            if (!user.startsWith("_")) {
                users.add(User.of(user));
            }
        }
        if (dscl.waitFor() != 0) {
            throw new IllegalStateException();
        }
        return new UserRegistry(users);
    }

    public static UserRegistry empty() {
        return new UserRegistry(Collections.<User>emptyList());
    }

    public static String getCurrentUserName() {
        return System.getProperty("user.name");
    }

    /**
     * Prints the temp dir of the current user to the console.
     * <p>
     * Executed within {@link User#runAsUserWithCurrentClassPath(java.lang.Class)}, to discover the temp dir of a given user in macOS.
     * Forks a new JVM that runs in the context of a given user and runs this main method.
     * This indirection is needed as each user has their own temp directory in macOS.
     * </p>
     */
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.io.tmpdir"));
    }

    public User getCurrentUser() {
        return get(getCurrentUserName());
    }

    public Collection<String> getAllTempDirs() throws IOException, InterruptedException {
        Set<String> tempDirs = new HashSet<>();
        for (User user : users.values()) {
            tempDirs.add(findTempDir(user));
        }
        tempDirs.remove(null);
        return tempDirs;
    }

    private String findTempDir(User user) throws IOException, InterruptedException {
        if (Platform.isWindows()) {
            throw new IllegalStateException("Discovering the temp dir of a given user is not supported in Windows as the runAs method has no implementation for Windows");
        }
        if (user.canSwitchToUser()) {
            // every user has their own temp folder on MacOS
            // to discover it, we're starting a simple Java program in the context of the user
            // that outputs the value of the java.io.tmpdir system property
            Process process = user.runAsUserWithCurrentClassPath(UserRegistry.class).start();
            process.waitFor();
            if (process.exitValue() == 0) {
                return new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
            }
        }
        return null;
    }

    public Collection<String> getAllUserNames() {
        return users.keySet();
    }

    public User get(String username) {
        if (!users.containsKey(username)) {
            users.put(username, User.of(username));
        }
        return users.get(username);
    }

    public static class User {
        private final String username;
        private final boolean canSwitchToUser;

        private User(String username, boolean canSwitchToUser) {
            this.username = username;
            this.canSwitchToUser = canSwitchToUser;
        }

        private static User of(String username) {
            try {
                if (Platform.isWindows()) {
                    return new User(username, false);
                } else {
                    return new User(username, canSwitchToUser(username));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static boolean canSwitchToUser(String user) {
            if (getCurrentUserName().equals(user)) {
                return true;
            }

            try {
                return new ProcessBuilder(sudoCmd(user, Arrays.asList("echo", "ok")))
                    .inheritIO() // ensures that we get some hint if sudo prompts/prints something
                    .start()
                    .waitFor() == 0;
            } catch (Exception ignore) {
                return false;
            }
        }

        public static String getCurrentJvm() {
            return System.getProperty("java.home") +
                File.separator +
                "bin" +
                File.separator +
                "java" +
                (Platform.isWindows() ? ".exe" : "");
        }


        public ProcessBuilder runAsUserWithCurrentClassPath(Class<?> mainClass) {
            return runAsUserWithCurrentClassPath(mainClass, Collections.<String>emptyList());
        }

        public ProcessBuilder runAsUserWithCurrentClassPath(Class<?> mainClass, List<String> args) {
            List<String> cmd = new ArrayList<>();
            cmd.add(getCurrentJvm());
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add(mainClass.getName());
            cmd.addAll(args);
            return runAs(cmd);

        }

        public ProcessBuilder runAs(List<String> cmd) {
            if (!canSwitchToUser) {
                throw new IllegalStateException(String.format("Cannot run as user %s", username));
            }
            if (!username.equals(getCurrentUserName())) {
                // sudo only when required
                cmd = sudoCmd(username, cmd);
            }
            return new ProcessBuilder(cmd);
        }

        /**
         * Builds a sudo command from a regular command
         *
         * @param user user to run cmd as
         * @param cmd  original command
         * @return original command wrapped in a sudo command
         */
        private static List<String> sudoCmd(String user, List<String> cmd) {
            List<String> fullCmd = new ArrayList<>();
            fullCmd.add("sudo");
            fullCmd.add("-n"); // --non-interactive long option might not be always supported
            fullCmd.add("-u");
            fullCmd.add(user);
            fullCmd.addAll(cmd);
            return fullCmd;
        }

        public boolean canSwitchToUser() {
            return canSwitchToUser;
        }

        public boolean isCurrentUser() {
            return username.equals(getCurrentUserName());
        }

        public String getUsername() {
            return username;
        }
    }
}
