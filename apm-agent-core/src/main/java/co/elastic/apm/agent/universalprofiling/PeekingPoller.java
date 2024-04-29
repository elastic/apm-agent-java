package co.elastic.apm.agent.universalprofiling;

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
    private final EventPoller.Handler<Event> subHandler = this::handleEvent;

    public PeekingPoller(EventPoller<Event> wrappedPoller, Supplier<Event> emptyEventFactory) {
        this.poller = wrappedPoller;
        peekedEvent = emptyEventFactory.get();
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
