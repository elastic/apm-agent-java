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
package co.elastic.apm.agent.benchmark.sql;

import co.elastic.apm.agent.benchmark.AbstractBenchmark;
import co.elastic.apm.agent.db.signature.SignatureParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.RunnerException;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SignatureParserBenchmark extends AbstractBenchmark {

    private SignatureParser signatureParser;
    private StringBuilder stringBuilder;

    public static void main(String[] args) throws RunnerException {
        run(SignatureParserBenchmark.class);
    }

    @Setup
    public void setUp() {
        stringBuilder = new StringBuilder();
        signatureParser = new SignatureParser();
    }

    @Benchmark
    public StringBuilder parseShortQuery() {
        stringBuilder.setLength(0);
        signatureParser.querySignature("SELECT * FROM foo", stringBuilder, false);
        return stringBuilder;
    }

    @Benchmark
    public StringBuilder parseLongQuery() {
        stringBuilder.setLength(0);
        signatureParser.querySignature("SELECT *,(SELECT COUNT(*) FROM table2 WHERE table2.field1 = table1.id) AS count FROM table1 WHERE table1.field1 = 'value'", stringBuilder, false);
        return stringBuilder;
    }

    @Benchmark
    public void consumeCpu() {
        // to get a feel for the jitter of this machine (most notable in higher percentiles)
        Blackhole.consumeCPU(1);
    }
}
