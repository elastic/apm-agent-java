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

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.IndyBootstrap;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;

public class InstallationListenerImpl extends AgentBuilder.InstallationListener.Adapter {
    @Override
    public void onAfterWarmUp(Set<Class<?>> types, ResettableClassFileTransformer classFileTransformer, boolean transformed) {
        try {
            // should be safe to get a logger by now
            Logger logger = LoggerFactory.getLogger(InstallationListenerImpl.class);
            if (!transformed) {
                logger.warn("Byte Buddy warmup ended without transforming at least one class. The agent may not work as expected.");
            } else  {
                if (types.contains(Instrumented.class)) {
                    Instrumented.setWarmedUp();
                    logger.debug("Warmup of {} classes ended properly", types.size());
                } else {
                    logger.warn("Warmup did not include the {} class as expected", Instrumented.class.getName());
                }
            }

            // We need to register a CL in order to invoke the bootstrap code path
            String warmupInstrumentationAdviceClassName = "co.elastic.apm.agent.bci.bytebuddy.WarmupInstrumentation$AdviceClass";
            ElasticApmAgent.mapInstrumentationCL2adviceClassName(warmupInstrumentationAdviceClassName, InstallationListenerImpl.class.getClassLoader());

            @SuppressWarnings({"RedundantArrayCreation", "UnnecessaryBoxing"})
            ConstantCallSite onMethodEnter = IndyBootstrap.bootstrap(
                // The agent CL lookup is the right one here
                MethodHandles.lookup(),
                "onMethodEnter",
                MethodType.methodType(Object.class, new Class[]{Object.class, String.class}),
                new Object[]{
                    warmupInstrumentationAdviceClassName,
                    Integer.valueOf(0),
                    Instrumented.class,
                    "isInstrumented"
                }
            );
        } catch (Throwable throwable) {
            // keep quiet, most likely the problem is with getting the logger
        }
    }
}
