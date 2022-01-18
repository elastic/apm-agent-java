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

import co.elastic.apm.agent.impl.transaction.AbstractHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;

public class HeaderExtractorBridge extends AbstractHeaderGetter<String, Object> implements TextHeaderGetter<Object> {

    private static final Logger logger = LoggerFactory.getLogger(HeaderExtractorBridge.class);

    @Nullable
    private static HeaderExtractorBridge INSTANCE;

    private final MethodHandle getFirstHeaderMethod;

    private HeaderExtractorBridge(MethodHandle getFirstHeaderMethod) {
        this.getFirstHeaderMethod = getFirstHeaderMethod;
    }

    public static HeaderExtractorBridge get(MethodHandle getFirstHeaderMethod) {
        if (INSTANCE == null) {
            INSTANCE = new HeaderExtractorBridge(getFirstHeaderMethod);
        }
        return INSTANCE;
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Object carrier) {
        String value = null;
        try {
            value = (String) getFirstHeaderMethod.invoke(carrier, headerName);
        } catch (Throwable throwable) {
            logger.error("Failed to extract trace context headers", throwable);
        }
        return value;
    }

}
