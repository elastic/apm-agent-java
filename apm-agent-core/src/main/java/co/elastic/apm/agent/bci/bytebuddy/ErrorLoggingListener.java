/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.bci.bytebuddy;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorLoggingListener extends AgentBuilder.Listener.Adapter {

    private static final Logger logger = LoggerFactory.getLogger(ErrorLoggingListener.class);

    @Override
    public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
        if (throwable instanceof MinimumClassFileVersionValidator.UnsupportedClassFileVersionException) {
            logger.warn("{} uses an unsupported class file version (pre Java 5) and can't be instrumented. " +
                "Consider updating to a newer version of that library.", typeName);
        } else {
            logger.warn("ERROR on transformation " + typeName, throwable);
        }
    }
}
