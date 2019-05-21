/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.payload.Payload;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;

import java.io.IOException;
import java.io.OutputStream;

public interface PayloadSerializer {

    void serializePayload(OutputStream os, Payload payload) throws IOException;

    /**
     * Sets the output stream which the {@code *NdJson} methods should write to.
     *
     * @param os
     */
    void setOutputStream(OutputStream os);

    void serializeMetaDataNdJson(MetaData metaData);

    void serializeMetadata(MetaData metaData);

    void serializeTransactionNdJson(Transaction transaction);

    void serializeSpanNdJson(Span span);

    void serializeErrorNdJson(ErrorCapture error);

    /**
     * Flushes the {@link OutputStream} which has been set via {@link #setOutputStream(OutputStream)}
     * and detaches that {@link OutputStream} from the serializer.
     */
    void flush() throws IOException;

    /**
     * Gets the number of bytes which are currently buffered
     *
     * @return the number of bytes which are currently buffered
     */
    int getBufferSize();

    void serializeMetrics(MetricRegistry metricRegistry);
}
