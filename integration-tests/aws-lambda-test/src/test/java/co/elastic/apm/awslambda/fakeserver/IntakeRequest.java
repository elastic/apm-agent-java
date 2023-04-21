package co.elastic.apm.awslambda.fakeserver;

import java.util.ArrayList;
import java.util.List;

public class IntakeRequest {

    private final int requestId;

    private final List<IntakeEvent> events = new ArrayList<>();

    public IntakeRequest(int requestId) {
        this.requestId = requestId;
    }

    public List<IntakeEvent> getEvents() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    void addEvent(IntakeEvent event) {
        synchronized (events) {
            events.add(event);
        }
    }
}
