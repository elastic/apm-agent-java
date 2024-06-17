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

import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.utility.JavaModule;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

public class ErrorLoggingListener extends AgentBuilder.Listener.Adapter {

    private static final Logger logger = LoggerFactory.getLogger(ErrorLoggingListener.class);

    @Override
    public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
        if (throwable instanceof MinimumClassFileVersionValidator.UnsupportedClassFileVersionException) {
            logger.warn("{} uses an unsupported class file version (pre Java {})) and can't be instrumented. " +
                "You may try setting the '{}' config option to 'true', but notice that it may cause VerificationErrors or other issues.",
                typeName,
                ((MinimumClassFileVersionValidator.UnsupportedClassFileVersionException)throwable).getMinVersion(),
                CoreConfigurationImpl.INSTRUMENT_ANCIENT_BYTECODE
            );
        } else {
            if (throwable instanceof net.bytebuddy.pool.TypePool.Resolution.NoSuchTypeException) {
                logger.info(typeName + " refers to a missing class.");
                logger.debug("ByteBuddy type resolution stack trace: ", throwable);
            } else {
                logger.warn("Error on transformation " + typeName, throwable);
            }
        }
    }
}
