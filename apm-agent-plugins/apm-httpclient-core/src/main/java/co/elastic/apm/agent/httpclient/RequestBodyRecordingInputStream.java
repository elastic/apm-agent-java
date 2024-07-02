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

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RequestBodyRecordingInputStream extends InputStream {

    public static final int MAX_LENGTH = 1024;

    private final InputStream delegate;

    @Nullable
    private Span<?> clientSpan;

    public RequestBodyRecordingInputStream(InputStream delegate, Span<?> clientSpan) {
        this.delegate = delegate;
        clientSpan.incrementReferences();
        this.clientSpan = clientSpan;
    }


    protected void appendToBody(char b) {
        if (clientSpan != null) {
            StringBuilder body = clientSpan.getContext().getHttp().getRequestBody(true);
            if (body.length() < MAX_LENGTH) {
                body.append(b);
            }
        }
    }

    protected void appendToBody(byte[] b, int off, int len) {
        if (clientSpan != null) {
            StringBuilder body = clientSpan.getContext().getHttp().getRequestBody(true);
            int remaining = Math.min(MAX_LENGTH - body.length(), len);
            for (int i = 0; i < remaining; i++) {
                body.append((char) b[off + i]);
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
    public int read() throws IOException {
        int character = delegate.read();
        if (character == -1) {
            releaseSpan();
        } else {
            appendToBody((char) character);
        }
        return character;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int readBytes = delegate.read(b, off, len);
        if (readBytes == -1) {
            releaseSpan();
        } else {
            appendToBody(b, off, readBytes);
        }
        return readBytes;
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        try {
            releaseSpan();
        } finally {
            delegate.close();
        }
    }
}
