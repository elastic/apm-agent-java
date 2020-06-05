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
package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.impl.transaction.StackFrame;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static co.elastic.apm.agent.matcher.WildcardMatcher.caseSensitiveMatcher;
import static org.assertj.core.api.Assertions.assertThat;

class JfrParserTest {

    private static final int MAX_STACK_DEPTH = 4;

    @Test
    void name() throws Exception {
        // using the smallest prime number possible for the buffer
        // should trigger most edge cases in the buffer being exhausted
        JfrParser jfrParser = new JfrParser(ByteBuffer.allocate(113), ByteBuffer.allocate(113));

        File file = Paths.get(JfrParserTest.class.getClassLoader().getResource("recording.jfr").toURI()).toFile();

        jfrParser.parse(file, List.of(), List.of(caseSensitiveMatcher("co.elastic.apm.*")));
        AtomicInteger stackTraces = new AtomicInteger();
        ArrayList<StackFrame> stackFrames = new ArrayList<>();
        jfrParser.consumeStackTraces((threadId, stackTraceId, nanoTime) -> {
            jfrParser.resolveStackTrace(stackTraceId, true, stackFrames, MAX_STACK_DEPTH);
            if (!stackFrames.isEmpty()) {
                stackTraces.incrementAndGet();
                assertThat(stackFrames.get(stackFrames.size() - 1).getMethodName()).isEqualTo("testProfileTransaction");
                assertThat(stackFrames).hasSizeLessThanOrEqualTo(MAX_STACK_DEPTH);
            }
            stackFrames.clear();
        });
        assertThat(stackTraces.get()).isEqualTo(97);
    }

}
