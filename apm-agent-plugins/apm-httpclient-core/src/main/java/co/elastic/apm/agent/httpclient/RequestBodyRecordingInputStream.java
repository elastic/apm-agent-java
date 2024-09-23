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
import java.io.InputStream;

public class RequestBodyRecordingInputStream extends InputStream {

    private final InputStream delegate;

    private final RequestBodyRecordingHelper recordingHelper;

    public RequestBodyRecordingInputStream(InputStream delegate, Span<?> clientSpan) {
        this.delegate = delegate;
        this.recordingHelper = new RequestBodyRecordingHelper(clientSpan);
    }


    @Override
    public int read() throws IOException {
        int character = delegate.read();
        if (character == -1) {
            recordingHelper.releaseSpan();
        } else {
            recordingHelper.appendToBody((byte) character);
        }
        return character;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int readBytes = delegate.read(b, off, len);
        if (readBytes == -1) {
            recordingHelper.releaseSpan();
        } else {
            recordingHelper.appendToBody(b, off, readBytes);
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
            recordingHelper.releaseSpan();
        } finally {
            delegate.close();
        }
    }
}
