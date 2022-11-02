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
import java.lang.management.ThreadMXBean;
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


    /**
     * List of public, exported interface class names from supported JVM implementations.
     */
    private static final List<String> OPERATING_SYSTEM_BEAN_CLASS_NAMES = Arrays.asList(
        // NOTE: THE ORDER IS IMPORTANT AS J9 CONTAINS THE SUN INTERFACE AS WELL
        "com.ibm.lang.management.OperatingSystemMXBean", // J9
        "com.sun.management.OperatingSystemMXBean" // HotSpot
    );

    private static final List<String> THREAD_BEAN_CLASS_NAMES = Arrays.asList(
        "com.sun.management.ThreadMXBean" // HotSpot
    );

    @Nullable
    private static Class<?> operatingSystemBeanClass;
    private static boolean operatingSystemBeanClassInitialized;

    @Nullable
    private static Class<?> threadBeanClass;
    private static boolean threadBeanClassInitialized;

    @Nullable
    public static Method getOperatingSystemMBeanMethod(OperatingSystemMXBean operatingSystemBean, String methodName) {
        // lazy initialization - try loading the classes as late as possible
        initOsBeanClass();
        return tryLookupMethod(operatingSystemBean, operatingSystemBeanClass, methodName);
    }

    public static boolean isIbmOperatingSystemMBean() {
        initThreadBeanClass();
        return threadBeanClass != null && "com.ibm.lang.management.OperatingSystemMXBean".equals(threadBeanClass.getName());
    }

    @Nullable
    public static Method getThreadMBeanMethod(ThreadMXBean threadBean, String methodName, Class<?>... parameterTypes) {
        // lazy initialization - try loading the classes as late as possible
        initThreadBeanClass();
        return tryLookupMethod(threadBean, threadBeanClass, methodName, parameterTypes);
    }

    private static synchronized void initOsBeanClass() {
        if (operatingSystemBeanClassInitialized) {
            return;
        }
        operatingSystemBeanClassInitialized = true;
        operatingSystemBeanClass = tryLoadBeanClass("OperatingSystemMXBean", OPERATING_SYSTEM_BEAN_CLASS_NAMES);
    }

    private static synchronized void initThreadBeanClass() {
        if (threadBeanClassInitialized) {
            return;
        }
        threadBeanClassInitialized = true;
        threadBeanClass = tryLoadBeanClass("ThreadMXBean", THREAD_BEAN_CLASS_NAMES);
    }

    @Nullable
    private static Class<?> tryLoadBeanClass(String beanName, List<String> candidates) {
        try {
            for (String className : candidates) {
                try {
                    Class<?> beanClass = Class.forName(className);
                    logger.info("Found JVM-specific {} interface: {}", beanName, className);
                    return beanClass;
                } catch (ClassNotFoundException ignore) {
                }
            }
        } catch (Throwable throwable) {
            logger.error("Failed to load {} implementation", beanName, throwable);
        }
        return null;
    }

    @Nullable
    private static Method tryLookupMethod(Object beanInstance, @Nullable Class<?> expectedBeanClass, String methodName, Class<?>... parameterTypes) {
        if (expectedBeanClass == null) {
            return null;
        }
        try {
            // ensure the Bean we have is actually an instance of the interface
            expectedBeanClass.cast(beanInstance);
            return expectedBeanClass.getMethod(methodName, parameterTypes);
        } catch (ClassCastException | NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

}
