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
package co.elastic.apm.agent.pluginapi;

import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;

@SuppressWarnings("unused")
public class HeaderInjectorBridge implements TextHeaderSetter<Object> {

    private static final Logger logger = LoggerFactory.getLogger(HeaderInjectorBridge.class);

    @Nullable
    private static HeaderInjectorBridge INSTANCE;

    public static HeaderInjectorBridge get(MethodHandle addHeaderMethod) {
        if (INSTANCE == null) {
            INSTANCE = new HeaderInjectorBridge(addHeaderMethod);
        }
        return INSTANCE;
    }

    private final MethodHandle addHeaderMethod;

    private HeaderInjectorBridge(MethodHandle addHeaderMethod) {
        this.addHeaderMethod = addHeaderMethod;
    }

    @Override
    public void setHeader(String headerName, String headerValue, Object carrier) {
        try {
            addHeaderMethod.invoke(carrier, headerName, headerValue);
        } catch (Throwable throwable) {
            logger.error("Failed to add trace context headers", throwable);
        }
    }
}
