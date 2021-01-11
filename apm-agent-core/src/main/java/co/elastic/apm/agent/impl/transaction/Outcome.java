package co.elastic.apm.agent.impl.transaction;

public enum Outcome {
    SUCCESS("success"),
    FAILURE("failure"),
    UNKNOWN("unknown");

    /**
     * String value used for serialization
     */
    private final String stringValue;

    Outcome(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
