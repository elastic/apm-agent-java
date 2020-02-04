/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.kafka.helper;

import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * An implementation of the Kafka record {@link Header} interface, meant for reducing memory allocations by reusing.
 * This implementation assumes that the thread asking for the {@link ElasticHeaderImpl#value()} is the same one setting
 * it. If that's not the case, distributed tracing through Kafka may be impaired, therefore a warning is logged and
 * the returned value is null.
 */
public class ElasticHeaderImpl implements Header {

    public static final Logger logger = LoggerFactory.getLogger(ElasticHeaderImpl.class);

    private final String key;
    private final byte[] value;

    private long settingThreadId;

    public ElasticHeaderImpl(String key) {
        this.key = key;
        value = new byte[TraceContext.BINARY_FORMAT_EXPECTED_LENGTH];
    }

    @Override
    public String key() {
        return key;
    }

    /**
     * Used when the value is required in order to be set
     *
     * @return the byte array representing the value
     */
    public byte[] valueForSetting() {
        settingThreadId = Thread.currentThread().getId();
        return value;
    }

    /**
     * The actual {@link Header#value()} implementation - typically used by producers during serialization
     *
     * @return the set value if same thread set it; null otherwise
     */
    @Override
    @Nullable
    public byte[] value() {
        if (Thread.currentThread().getId() != settingThreadId) {
            logger.warn("The assumption of same thread setting and serializing the header is invalid. Distributed tracing will not work");
            return null;
        }
        return value;
    }
}
