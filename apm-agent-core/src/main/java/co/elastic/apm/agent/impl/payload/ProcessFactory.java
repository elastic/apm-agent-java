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
package co.elastic.apm.agent.impl.payload;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ProcessFactory} is responsible for creating the {@link ProcessInfo} object, containing information about the current process.
 */
public interface ProcessFactory {

    /**
     * @return the {@link ProcessInfo} information about the current process
     */
    ProcessInfo getProcessInformation();

    /**
     * Redirects to the the best {@link ProcessFactory} strategy for the current VM
     */
    enum ForCurrentVM implements ProcessFactory {

        /**
         * The singleton instance
         */
        INSTANCE;

        /**
         * The best {@link ProcessFactory} for the current VM
         */
        private final ProcessFactory dispatcher;

        ForCurrentVM() {
            ProcessFactory processFactory;
            try {
                processFactory = ForJava9CompatibleVM.make();
            } catch (NoClassDefFoundError | Exception ignore) {
                processFactory = ForLegacyVM.INSTANCE;
            }
            dispatcher = processFactory;
        }

        @Override
        public ProcessInfo getProcessInformation() {
            return dispatcher.getProcessInformation();
        }
    }

    /**
     * A {@link ProcessFactory} for a legacy VM that reads process information from its JMX properties.
     */
    enum ForLegacyVM implements ProcessFactory {

        /**
         * The singleton instance
         */
        INSTANCE;

        private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

        @Override
        public ProcessInfo getProcessInformation() {
            ProcessInfo process = new ProcessInfo(getTitle());
            process.withPid(getPid());
            process.withArgv(runtimeMXBean.getInputArguments());
            return process;
        }

        private String getTitle() {
            String javaHome = java.lang.System.getProperty("java.home");
            final String title = javaHome + File.separator + "bin" + File.separator + "java";
            if (java.lang.System.getProperty("os.name").startsWith("Win")) {
                return title + ".exe";
            }
            return title;
        }

        private int getPid() {
            // format: pid@host
            String pidAtHost = runtimeMXBean.getName();
            Matcher matcher = Pattern.compile("(\\d+)@.*").matcher(pidAtHost);
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group(1));
            } else {
                return 0;
            }

        }

    }

    /**
     * A {@link ProcessFactory} for a Java 9+ VMs that reads process information from the {@link ProcessHandle#current() current}
     * {@link ProcessHandle}.
     */
    @IgnoreJRERequirement
    class ForJava9CompatibleVM implements ProcessFactory {

        /**
         * The {@link ProcessHandle} instance, obtained by {@link ProcessHandle#current()}
         * <p>
         * This is stored in a {@link Object} reference as opposed to a {@link ProcessHandle} reference so that reflectively
         * inspecting the instance variables of this class, does not lead to {@link ClassNotFoundException}s on non Java 9 capable VMs
         * </p>
         */
        private final Object processHandle;

        /**
         * @param current the {@link ProcessHandle#current} method
         */
        ForJava9CompatibleVM(Method current) {
            try {
                processHandle = current.invoke(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Can't access ProcessHandle#current", e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("Can't invoke ProcessHandle#current", e);
            }
        }

        /**
         * @return a {@link ProcessFactory} which depends on APIs intruduced in Java 9. Returns a fallback if not running on
         * Java 9.
         */
        static ProcessFactory make() throws Exception {
            return new ForJava9CompatibleVM(Class.forName("java.lang.ProcessHandle").getMethod("current"));
        }

        /**
         * Ideally, this would only reflectively invoke the Java 9 introduced process API.
         * But this is quite a hassle,
         * especially when handling the {@link java.util.Optional} return types of the process API.
         * As we are directly referring to APIs introduced in Java 9,
         * this project can only be compiled with a JDK 9+.
         */
        @Override
        public ProcessInfo getProcessInformation() {
            ProcessHandle processHandle = (ProcessHandle) this.processHandle;
            final ProcessInfo process = new ProcessInfo(processHandle.info().command().orElse("unknown"));
            process.withPid(processHandle.pid());
            process.withPpid(processHandle.parent()
                .map(new Function<ProcessHandle, Long>() {
                    @Override
                    public Long apply(ProcessHandle processHandle1) {
                        return processHandle1.pid();
                    }
                })
                .orElse(null));
            process.withArgv(processHandle.info()
                .arguments()
                .map(new Function<String[], List<String>>() {
                    @Override
                    public List<String> apply(String[] a) {
                        return Arrays.asList(a);
                    }
                })
                .orElse(null));

            return process;

        }
    }

}
