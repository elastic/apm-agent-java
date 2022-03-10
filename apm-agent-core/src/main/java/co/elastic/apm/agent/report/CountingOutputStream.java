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
package co.elastic.apm.agent.report;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;

class CountingOutputStream extends OutputStream {

    private final OutputStream out;
    private long count;

    CountingOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        count++;
    }

    @Override
    public void write(@Nonnull byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        count += len;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    public long getCount() {
        return count;
    }
}
