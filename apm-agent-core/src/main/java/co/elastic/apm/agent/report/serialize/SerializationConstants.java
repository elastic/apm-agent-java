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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.configuration.CoreConfiguration;

import javax.annotation.Nullable;

public class SerializationConstants {

    /**
     * Matches default ZLIB buffer size.
     * Lets us assume the ZLIB buffer is always empty,
     * so that {@link DslJsonSerializer.Writer#getBufferSize()} is the total amount of buffered bytes.
     */
    public static final int BUFFER_SIZE = 16384;

    public static final int MAX_VALUE_LENGTH = 1024;

    @Nullable
    private static volatile SerializationConstants INSTANCE;

    private final int maxLongStringValueLength;

    private SerializationConstants(int maxLongStringValueLength) {
        this.maxLongStringValueLength = maxLongStringValueLength;
    }

    public static void init(CoreConfiguration coreConfiguration) {
        INSTANCE = new SerializationConstants(coreConfiguration.getLongFieldMaxLength());
    }

    public static int getMaxLongStringValueLength() {
        return get().maxLongStringValueLength;
    }

    private static SerializationConstants get() {
        if (INSTANCE != null) {
            return INSTANCE;
        }

        throw new IllegalStateException("serialization constants must be initialized first");
    }

}
