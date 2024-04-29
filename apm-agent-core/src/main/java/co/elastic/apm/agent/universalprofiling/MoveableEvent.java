package co.elastic.apm.agent.universalprofiling;

public interface MoveableEvent<SELF extends MoveableEvent<?>> {

    /**
     * Moves the content from this event into the provided other event. This event should be in a
     * resetted state after the call.
     */
    void moveInto(SELF other);

    void clear();
}
