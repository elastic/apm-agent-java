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

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.dslplatform.json.JsonWriter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public interface PayloadSerializer {

    /**
     * Sets the output stream which the {@code *NdJson} methods should write to.
     *
     * @param os the {@link OutputStream} to which all contents are to be serialized
     */
    void setOutputStream(OutputStream os);

    /**
     * Blocking until this {@link PayloadSerializer} is ready for use.
     *
     * @throws Exception if blocking was interrupted, or timed out or an error occurred in the underlying implementation
     */
    void blockUntilReady() throws Exception;

    /**
     * Appends the serialized metadata to ND-JSON as a {@code metadata} line.
     * <p>
     * NOTE: Must be called after {@link PayloadSerializer#blockUntilReady()} was called and returned, otherwise the
     * cached serialized metadata may not be ready yet.
     * </p>
     *
     * @throws UninitializedException may be thrown if {@link PayloadSerializer#blockUntilReady()} was not invoked
     */
    void appendMetaDataNdJsonToStream() throws UninitializedException;

    /**
     * Appends the serialized metadata to the underlying {@link OutputStream}.
     * <p>
     * NOTE: Must be called after {@link PayloadSerializer#blockUntilReady()} was called and returned, otherwise the
     * cached serialized metadata may not be ready yet.
     * </p>
     *
     * @throws UninitializedException may be thrown if {@link PayloadSerializer#blockUntilReady()} was not invoked
     */
    void appendMetadataToStream() throws UninitializedException;

    void serializeTransactionNdJson(Transaction transaction);

    void serializeSpanNdJson(Span span);

    void serializeErrorNdJson(ErrorCapture error);

    /**
     * Flushes the {@link OutputStream} which has been set via {@link #setOutputStream(OutputStream)}
     * and detaches that {@link OutputStream} from the serializer.
     */
    void fullFlush() throws IOException;

    /**
     * Flushes content that has been written so far to the {@link OutputStream} which has been set
     * via {@link #setOutputStream(OutputStream)}, without flushing the {@link OutputStream} itself.
     * Subsequent serializations will be made to the same {@link OutputStream}.
     */
    void flushToOutputStream();

    /**
     * Gets the number of bytes which are currently buffered
     *
     * @return the number of bytes which are currently buffered
     */
    int getBufferSize();

    void serializeFileMetaData(File file);

    JsonWriter getJsonWriter();

    void writeBytes(byte[] bytes, int len);

    class UninitializedException extends Exception {
        public UninitializedException(String message) {
            super(message);
        }
    }
}
