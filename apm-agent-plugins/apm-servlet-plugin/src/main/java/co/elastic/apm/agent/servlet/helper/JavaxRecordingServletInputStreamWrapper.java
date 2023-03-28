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
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.tracer.metadata.Request;
import co.elastic.apm.agent.util.IOUtils;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;

public class JavaxRecordingServletInputStreamWrapper extends ServletInputStream {

    private final Request request;
    private final ServletInputStream servletInputStream;

    public JavaxRecordingServletInputStreamWrapper(Request request, ServletInputStream servletInputStream) {
        this.request = request;
        this.servletInputStream = servletInputStream;
    }

    @Override
    public int read() throws IOException {
        try {
            final int b = servletInputStream.read();
            decode(b);
            return b;
        } catch (IOException e) {
            request.endOfBufferInput();
            throw e;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        try {
            final int read;
            read = servletInputStream.read(b);
            decode(b, 0, read);
            return read;
        } catch (IOException e) {
            request.endOfBufferInput();
            throw e;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            final int read = servletInputStream.read(b, off, len);
            decode(b, off, read);
            return read;
        } catch (IOException e) {
            request.endOfBufferInput();
            throw e;
        }
    }

    @Override
    public int readLine(byte[] b, int off, int len) throws IOException {
        try {
            final int read = servletInputStream.readLine(b, off, len);
            decode(b, off, read);
            return read;
        } catch (IOException e) {
            request.endOfBufferInput();
            throw e;
        }
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        try {
            final byte[] bytes = servletInputStream.readAllBytes();
            decode(bytes, 0, bytes.length);
            return bytes;
        } catch (IOException e) {
            request.endOfBufferInput();
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            servletInputStream.close();
        } finally {
            request.endOfBufferInput();
        }
    }

    @Override
    public long skip(long n) throws IOException {
        return servletInputStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return servletInputStream.available();
    }

    @Override
    public void mark(int readlimit) {
        servletInputStream.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        try {
            servletInputStream.reset();
        } finally {
            // don't read things twice from the stream, assume we have read everything by now
            request.endOfBufferInput();
        }
    }

    @Override
    public boolean markSupported() {
        return servletInputStream.markSupported();
    }

    @Override
    public boolean isFinished() {
        return servletInputStream.isFinished();
    }

    @Override
    public boolean isReady() {
        return servletInputStream.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        servletInputStream.setReadListener(readListener);
    }

    private void decode(byte[] b, int off, int read) {
        if (read == -1) {
            request.endOfBufferInput();
        } else {
            final CharBuffer bodyBuffer = request.getBodyBuffer();
            if (bodyBuffer != null) {
                final CoderResult coderResult = IOUtils.decodeUtf8Bytes(b, off, read, bodyBuffer);
                handleCoderResult(coderResult);
            }
        }
    }

    private void decode(int b) {
        if (b == -1) {
            request.endOfBufferInput();
        } else {
            final CharBuffer bodyBuffer = request.getBodyBuffer();
            if (bodyBuffer != null) {
                final CoderResult coderResult = IOUtils.decodeUtf8Byte((byte) b, bodyBuffer);
                handleCoderResult(coderResult);
            }
        }
    }

    private void handleCoderResult(CoderResult coderResult) {
        if (coderResult.isError()) {
            request.setRawBody("[Non UTF-8 data]");
        } else if (coderResult.isOverflow()) {
            request.endOfBufferInput();
        }
    }
}
