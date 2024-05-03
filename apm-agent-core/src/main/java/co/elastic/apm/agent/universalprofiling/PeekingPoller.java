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

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventPoller;

import java.util.function.Supplier;

/**
 * Wrapper around {@link EventPoller} which allows to "peek" elements. The provided event handling
 * callback can decide to not handle an event. In that case, the event will be provided again as
 * first element on the next call to {@link #poll(Handler)}.
 */
public class PeekingPoller<Event extends MoveableEvent<Event>> {

    public interface Handler<Event extends MoveableEvent<Event>> {

        /**
         * Handles an event fetched from the ring buffer.
         *
         * @return true, if the event was handled and shall be removed. False if the event was not
         * handled, no further invocations of handleEvent are desired and the same event shall be
         * provided for the next {@link PeekingPoller#poll(Handler)} call.
         */
        boolean handleEvent(Event e);
    }

    private final EventPoller<Event> poller;
    private final Event peekedEvent;
    private boolean peekedEventPopulated;

    Handler<? super Event> currentHandler;
    private final EventPoller.Handler<Event> subHandler = new EventPoller.Handler<Event>() {
        @Override
        public boolean onEvent(Event event, long sequence, boolean endOfBatch) throws Exception {
            return handleEvent(event, sequence, endOfBatch);
        }
    };

    public PeekingPoller(EventPoller<Event> wrappedPoller, EventFactory<Event> emptyEventFactory) {
        this.poller = wrappedPoller;
        peekedEvent = emptyEventFactory.newInstance();
        peekedEventPopulated = false;
    }

    public synchronized void poll(Handler<? super Event> handler) throws Exception {
        if (peekedEventPopulated) {
            boolean handled = handler.handleEvent(peekedEvent);
            if (!handled) {
                return;
            }
            peekedEvent.clear();
            peekedEventPopulated = false;
        }
        currentHandler = handler;
        try {
            poller.poll(subHandler);
        } finally {
            currentHandler = null;
        }
    }

    private boolean handleEvent(Event event, long sequence, boolean endOfBatch) {
        boolean handled = currentHandler.handleEvent(event);
        if (handled) {
            return true;
        } else {
            peekedEventPopulated = true;
            event.moveInto(peekedEvent);
            return false;
        }
    }
}
