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

public class ThreadMatcher {

    private final ThreadGroup systemThreadGroup;
    private Thread[] threads = new Thread[16];

    public ThreadMatcher() {
        ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
        while (threadGroup.getParent() != null) {
            threadGroup = threadGroup.getParent();
        }
        systemThreadGroup = threadGroup;
    }

    public <S1, S2> void forEachThread(NonCapturingPredicate<Thread, S1> predicate, S1 state1, NonCapturingConsumer<Thread, S2> consumer, S2 state2) {
        int count = systemThreadGroup.activeCount();
        do {
            int expectedArrayLength = count + (count / 2) + 1;
            if (threads.length < expectedArrayLength) {
                threads = new Thread[expectedArrayLength]; //slightly grow the array size
            }
            count = systemThreadGroup.enumerate(threads, true);
            //return value of enumerate() must be strictly less than the array size according to javadoc
        } while (count >= threads.length);

        for (int i = 0; i < count; i++) {
            Thread thread = threads[i];
            if (predicate.test(thread, state1)) {
                consumer.accept(thread, state2);
            }
            threads[i] = null;
        }
    }

    interface NonCapturingPredicate<T, S> {
        boolean test(T t, S state);
    }

    interface NonCapturingConsumer<T, S> {
        void accept(T t, S state);
    }

}
