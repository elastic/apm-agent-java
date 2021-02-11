/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
import net.bytebuddy.agent.VirtualMachine;

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

class JvmInfo {
    public static final String CURRENT_PID;
    private static final JvmInfo CURRENT_VM;

    static {
        VirtualMachine.ForOpenJ9.Dispatcher dispatcher = Platform.isWindows()
            ? new VirtualMachine.ForOpenJ9.Dispatcher.ForJnaWindowsEnvironment()
            : new VirtualMachine.ForOpenJ9.Dispatcher.ForJnaPosixEnvironment(15, 100, TimeUnit.MILLISECONDS);
        CURRENT_PID = String.valueOf(dispatcher.pid());
        CURRENT_VM = JvmInfo.of(CURRENT_PID, UserRegistry.getCurrentUserName());
    }

    private final String pid;
    private final String user;
    private Properties properties;

    JvmInfo(String pid, String user) {
        this.pid = pid;
        this.user = user;
    }

    public static JvmInfo withCurrentUser(String pid) {
        return of(pid, UserRegistry.getCurrentUserName());
    }

    public static JvmInfo of(String pid, String user) {
        return new JvmInfo(pid, user);
    }

    public static JvmInfo current() {
        return CURRENT_VM;
    }

    @Override
    public String toString() {
        return "VM{" +
            "pid='" + getPid() + '\'' +
            ", user='" + getUserName() + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JvmInfo jvmInfo = (JvmInfo) o;
        return getPid().equals(jvmInfo.getPid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPid());
    }

    public String getPid() {
        return pid;
    }

    public boolean isCurrentVM() {
        return getPid().equals(CURRENT_PID);
    }

    public String getUserName() {
        return user;
    }

    public UserRegistry.User getUser(UserRegistry registry) {
        return registry.get(user);
    }

    public String getCmd(UserRegistry registry) throws Exception {
        initProperties(registry);
        return properties.getProperty("sun.java.command") + " " + properties.getProperty("sun.jvm.args");
    }

    private void initProperties(UserRegistry registry) throws Exception {
        if (properties == null) {
            properties = GetAgentProperties.getAgentAndSystemProperties(pid, getUser(registry));
        }
    }

    public boolean isVersionSupported(UserRegistry registry) throws Exception {
        initProperties(registry);
        String version = properties.getProperty("java.version");
        // new scheme introduced in java 9, thus we can use it as a shortcut
        if (version.startsWith("1.")) {
            return Character.digit(version.charAt(2), 10) >= 7;
        } else {
            return true;
        }
    }
}
