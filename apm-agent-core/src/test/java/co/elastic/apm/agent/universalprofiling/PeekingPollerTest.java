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
package co.elastic.apm.agent.universalprofiling;

import static org.assertj.core.api.Assertions.assertThat;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

public class PeekingPollerTest {

    private static class DummyEvent implements MoveableEvent<DummyEvent> {

        private int val;

        public DummyEvent() {
            this(-1);
        }

        public DummyEvent(int val) {
            this.val = val;
        }

        @Override
        public void moveInto(DummyEvent other) {
            other.val = val;
            clear();
        }

        @Override
        public void clear() {
            val = -1;
        }
    }

    private static class RecordingHandler implements PeekingPoller.Handler<DummyEvent> {

        List<Integer> invocations = new ArrayList<>();
        Function<Integer, Boolean> resultProvider;

        @Override
        public boolean handleEvent(DummyEvent event) {
            invocations.add(event.val);
            return resultProvider.apply(event.val);
        }

        void reset() {
            invocations.clear();
        }
    }

    @Test
    public void testPeekingFunction() throws Exception {
        RingBuffer<DummyEvent> rb =
            RingBuffer.createMultiProducer(DummyEvent::new, 4, new YieldingWaitStrategy());
        EventPoller<DummyEvent> nonPeekingPoller = rb.newPoller();
        rb.addGatingSequences(nonPeekingPoller.getSequence());

        PeekingPoller<DummyEvent> poller =
            new PeekingPoller<DummyEvent>(nonPeekingPoller, DummyEvent::new);

        RecordingHandler handler = new RecordingHandler();
        handler.resultProvider = val -> false;
        poller.poll(handler);
        assertThat(handler.invocations).isEmpty();

        assertThat(rb.tryPublishEvent((ev, srq) -> ev.val = 1)).isTrue();
        assertThat(rb.tryPublishEvent((ev, srq) -> ev.val = 2)).isTrue();
        assertThat(rb.tryPublishEvent((ev, srq) -> ev.val = 3)).isTrue();
        assertThat(rb.tryPublishEvent((ev, srq) -> ev.val = 4)).isTrue();
        assertThat(rb.tryPublishEvent((ev, srq) -> ev.val = 5)).isFalse();

        poller.poll(handler);
        assertThat(handler.invocations).containsExactly(1);
        poller.poll(handler);
        assertThat(handler.invocations).containsExactly(1, 1);

        // It should now be possible to add one more element to the buffer
        assertThat(rb.tryPublishEvent((ev, srq) -> ev.val = 5)).isTrue();
        assertThat(rb.tryPublishEvent((ev, srq) -> ev.val = 6)).isFalse();

        poller.poll(handler);
        assertThat(handler.invocations).containsExactly(1, 1, 1);

        // now consume elements up to index 2
        handler.reset();
        handler.resultProvider = i -> i != 3;

        poller.poll(handler);
        assertThat(handler.invocations).containsExactly(1, 2, 3);

        // It should now be possible to add two more element to the buffer
        assertThat(rb.tryPublishEvent((ev, srq) -> ev.val = 6)).isTrue();
        assertThat(rb.tryPublishEvent((ev, srq) -> ev.val = 7)).isTrue();
        assertThat(rb.tryPublishEvent((ev, srq) -> ev.val = 8)).isFalse();

        poller.poll(handler);
        assertThat(handler.invocations).containsExactly(1, 2, 3, 3);

        // drain remaining elements
        handler.reset();
        handler.resultProvider = i -> true;

        poller.poll(handler);
        assertThat(handler.invocations).containsExactly(3, 4, 5, 6, 7);
    }
}
