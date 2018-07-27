/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.benchmark.reporter;

import co.elastic.apm.impl.MetaData;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.serialize.PayloadSerializer;
import org.openjdk.jmh.runner.RunnerException;

import java.io.IOException;
import java.io.OutputStream;

public class HttpNoopJsonReporterBenchmark extends AbstractHttpReporterBenchmark {

    /**
     * Convenience benchmark run method
     * <p>
     * For more accurate results, execute {@code mvn clean package} and run the benchmark via
     * {@code java -jar apm-agent-benchmarks/target/benchmarks.jar -prof gc}
     */
    public static void main(String[] args) throws RunnerException {
        run(HttpNoopJsonReporterBenchmark.class);
    }

    @Override
    protected PayloadSerializer getPayloadSerializer() {
        return new PayloadSerializer() {
            @Override
            public void serializePayload(OutputStream os, Payload payload) {
                // noop
            }

            @Override
            public void serializeMetaDataNdJson(MetaData metaData) {
                // noop
            }

            @Override
            public void serializeTransactionNdJson(Transaction transaction) {
                // noop
            }

            @Override
            public void serializeSpanNdJson(Span span) {
                // noop
            }

            @Override
            public void serializeErrorNdJson(ErrorCapture error) {
                // noop
            }

            @Override
            public int getBufferSize() {
                return 0;
            }

            @Override
            public void setOutputStream(OutputStream os) {
                // noop
            }

            @Override
            public void flush() {
                // noop
            }
        };
    }
}
