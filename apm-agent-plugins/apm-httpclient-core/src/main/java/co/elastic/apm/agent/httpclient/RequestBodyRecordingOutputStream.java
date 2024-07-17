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
import co.elastic.apm.agent.tracer.metadata.BodyCapture;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;

public class RequestBodyRecordingOutputStream extends OutputStream {

    private final OutputStream delegate;

    @Nullable
    private Span<?> clientSpan;

    public RequestBodyRecordingOutputStream(OutputStream delegate, Span<?> clientSpan) {
        this.delegate = delegate;
        clientSpan.incrementReferences();
        this.clientSpan = clientSpan;
    }

    @Override
    public void write(int b) throws IOException {
        try {
            appendToBody((byte) b);
        } finally {
            delegate.write(b);
        }
    }

    protected void appendToBody(byte b) {
        if (clientSpan != null) {
            BodyCapture body = clientSpan.getContext().getHttp().getRequestBody();
            body.append(b);
            if (body.isFull()) {
                releaseSpan();
            }
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        try {
            appendToBody(b, 0, b.length);
        } finally {
            delegate.write(b);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            appendToBody(b, off, len);
        } finally {
            delegate.write(b, off, len);
        }
    }

    protected void appendToBody(byte[] b, int off, int len) {
        if (clientSpan != null) {
            BodyCapture body = clientSpan.getContext().getHttp().getRequestBody();
            body.append(b, off, len);
            if (body.isFull()) {
                releaseSpan();
            }
        }
    }

    public void releaseSpan() {
        if (clientSpan != null) {
            clientSpan.decrementReferences();
            clientSpan = null;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            releaseSpan();
        } finally {
            delegate.close();
        }
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }
}
