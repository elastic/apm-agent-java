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
package co.elastic.apm.agent.bci.bytebuddy;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class InstallationListenerImpl extends AgentBuilder.InstallationListener.Adapter {

    @Override
    public void onAfterWarmUp(Map<Class<?>, byte[]> types, ResettableClassFileTransformer classFileTransformer, boolean transformed) {
        Logger logger = null;
        try {
            // should be safe to get a logger by now
            logger = LoggerFactory.getLogger(InstallationListenerImpl.class);
            if (!transformed) {
                logger.warn("Byte Buddy warmup ended without transforming at least one class. The agent may not work as expected.");
            } else  {
                byte[] transformedInstrumentedClass = null;
                for (Map.Entry<Class<?>, byte[]> classEntry : types.entrySet()) {
                    if (classEntry.getKey().equals(Instrumented.class)) {
                        transformedInstrumentedClass = classEntry.getValue();
                        break;
                    }
                }

                String instrumentedClassName = Instrumented.class.getName();
                if (transformedInstrumentedClass != null) {
                    logger.debug("Warmup: bytecode transformation of {} succeeded", instrumentedClassName);

                    // Warm up the complete invokedynamic linkage route.
                    // Byte Buddy's warmup doesn't retransform the class, it only applies the bytecode manipulation.
                    // Therefore, we cannot warm up fully simply by invoking the instrumented method. Instead, we need
                    // to load the instrumented class, instantiate it and invoke through reflection.
                    HashMap<String, byte[]> typeDefinitions = new HashMap<>();
                    typeDefinitions.put(instrumentedClassName, transformedInstrumentedClass);
                    ByteArrayClassLoader.ChildFirst childFirstCL = new ByteArrayClassLoader.ChildFirst(null, typeDefinitions);
                    Class<?> instrumentedClass = childFirstCL.loadClass(instrumentedClassName);
                    Object instrumentedClassInstance = instrumentedClass.getDeclaredConstructor().newInstance();
                    Method isInstrumentedMethod = instrumentedClass.getDeclaredMethod("isInstrumented");
                    // this method invocation kicks the invokedynamic linkage procedure, otherwise it would result with `false`
                    boolean isInstrumented = (boolean) isInstrumentedMethod.invoke(instrumentedClassInstance);
                    if (isInstrumented) {
                        Instrumented.setWarmedUp();
                        logger.debug("Warmup: instrumented bytecode of {} was executed as expected", instrumentedClassName);
                    } else {
                        logger.warn("Warmup: instrumented bytecode of {} does not work as expected", instrumentedClassName);
                    }
                } else {
                    logger.warn("Warmup did not include the {} class as expected", instrumentedClassName);
                }
            }
        } catch (Throwable throwable) {
            if (logger != null) {
                logger.error("Unexpected bytecode instrumentation warmup error", throwable);
            }
        }
    }

    @Override
    public void onWarmUpError(Class<?> type, ResettableClassFileTransformer classFileTransformer, Throwable throwable) {
        try {
            LoggerFactory.getLogger(InstallationListenerImpl.class).error("Error during warmup instrumentation of " + type.getName(), throwable);
        } catch (Throwable loggingError) {
            // error while trying to log
        }
    }
}
