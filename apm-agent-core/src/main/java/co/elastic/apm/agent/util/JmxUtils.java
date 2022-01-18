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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * A utility to obtain JVM-specific MBean implementations, supporting HotSpot and J9.
 *
 * This implementation is based on io.micrometer.core.instrument.binder.system.ProcessorMetrics,
 * under Apache License 2.0
 */
public class JmxUtils {

    private static final Logger logger = LoggerFactory.getLogger(JmxUtils.class);

    private static boolean initialized;

    /**
     * List of public, exported interface class names from supported JVM implementations.
     */
    private static final List<String> OPERATING_SYSTEM_BEAN_CLASS_NAMES = Arrays.asList(
        // NOTE: THE ORDER IS IMPORTANT AS J9 CONTAINS THE SUN INTERFACE AS WELL
        "com.ibm.lang.management.OperatingSystemMXBean", // J9
        "com.sun.management.OperatingSystemMXBean" // HotSpot
    );

    @Nullable
    private static Class<?> operatingSystemBeanClass;

    @Nullable
    public synchronized static Method getOperatingSystemMBeanMethod(OperatingSystemMXBean operatingSystemBean, String methodName) {
        if (!initialized) {
            // lazy initialization - try loading the classes as late as possible
            init();
        }
        if (operatingSystemBeanClass == null) {
            return null;
        }
        try {
            // ensure the Bean we have is actually an instance of the interface
            operatingSystemBeanClass.cast(operatingSystemBean);
            return operatingSystemBeanClass.getMethod(methodName);
        } catch (ClassCastException | NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    private static void init() {
        try {
            for (String className : OPERATING_SYSTEM_BEAN_CLASS_NAMES) {
                try {
                    operatingSystemBeanClass = Class.forName(className);
                    logger.info("Found JVM-specific OperatingSystemMXBean interface: {}", className);
                    break;
                } catch (ClassNotFoundException ignore) {
                }
            }
        } catch (Throwable throwable) {
            logger.error("Failed to load OperatingSystemMXBean implementation", throwable);
        } finally {
            // success or failure - try only once
            initialized = true;
        }
    }
}
