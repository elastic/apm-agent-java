package co.elastic.apm.agent.testutils.assertions;

import co.elastic.apm.agent.impl.context.Destination;

public class DestinationAssert extends BaseAssert<DestinationAssert, Destination> {

    public DestinationAssert(Destination actual) {
        super(actual, DestinationAssert.class);
    }

    public DestinationAssert hasAddress(String address) {
        isNotNull();
        checkString("Expected destination with address '%s' but was '%s'", address, normalizeToString(actual.getAddress()));
        return this;
    }

    public DestinationAssert hasEmptyAddress() {
        isNotNull();
        checkString("Expected destination with empty address '%s' but was '%s'", "", normalizeToString(actual.getAddress()));
        return this;
    }

    public DestinationAssert hasPort(int port) {
        isNotNull();
        checkInt("Expected destination with port '%d' but was '%d'", port, actual.getPort());
        return this;
    }

    public DestinationAssert hasNoPort() {
        isNotNull();
        checkTrue("Expected destination without port", actual.getPort() < 0);
        return this;
    }

    public DestinationAssert isEmpty() {
        isNotNull();
        checkTrue("Expected empty destination", !actual.hasContent());
        return this;
    }
}
