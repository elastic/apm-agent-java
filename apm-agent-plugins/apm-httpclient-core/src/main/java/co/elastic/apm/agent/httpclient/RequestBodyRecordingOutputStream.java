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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.tracer.Span;

import java.io.IOException;
import java.io.OutputStream;

public class RequestBodyRecordingOutputStream extends OutputStream {

    private final OutputStream delegate;

    private final RequestBodyRecordingHelper recordingHelper;

    public RequestBodyRecordingOutputStream(OutputStream delegate, Span<?> clientSpan) {
        this.delegate = delegate;
        this.recordingHelper = new RequestBodyRecordingHelper(clientSpan);
    }

    @Override
    public void write(int b) throws IOException {
        try {
            recordingHelper.appendToBody((byte) b);
        } finally {
            delegate.write(b);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        try {
            recordingHelper.appendToBody(b, 0, b.length);
        } finally {
            delegate.write(b);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            recordingHelper.appendToBody(b, off, len);
        } finally {
            delegate.write(b, off, len);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            recordingHelper.releaseSpan();
        } finally {
            delegate.close();
        }
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    public void releaseSpan() {
        recordingHelper.releaseSpan();
    }
}
