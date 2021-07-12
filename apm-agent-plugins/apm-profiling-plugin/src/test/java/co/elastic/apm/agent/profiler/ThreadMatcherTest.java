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
package co.elastic.apm.agent.profiler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadMatcherTest {

    private final ThreadMatcher threadMatcher = new ThreadMatcher();

    @Test
    void testLookup() {
        ArrayList<Thread> threads = new ArrayList<>();
        threadMatcher.forEachThread(new ThreadMatcher.NonCapturingPredicate<Thread, Void>() {
            @Override
            public boolean test(Thread thread, Void state) {
                return thread.getId() == Thread.currentThread().getId();
            }
        }, null, new ThreadMatcher.NonCapturingConsumer<Thread, List<Thread>>() {
            @Override
            public void accept(Thread thread, List<Thread> state) {
                state.add(thread);
            }
        }, threads);
        assertThat(threads).isEqualTo(List.of(Thread.currentThread()));
    }
}
