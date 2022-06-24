package co.elastic.apm.agent.testutils.assertions;

import co.elastic.apm.agent.impl.transaction.Span;

public class SpanAssert extends BaseAssert<SpanAssert, Span> {

    protected SpanAssert(Span actual) {
        super(actual, SpanAssert.class);
    }

    public SpanAssert hasName(String name) {
        isNotNull();
        checkString("Expected span with name '%s' but was '%s'", name, normalizeToString(actual.getNameForSerialization()));
        return this;
    }

    public SpanAssert hasType(String type) {
        isNotNull();
        checkString("Expected span with type '%s' but was '%s'", type, actual.getType());
        return this;
    }

    public SpanAssert hasDbStatement(String statement) {
        isNotNull();
        checkString("Expected span with DB statement '%s' but was '%s'", statement, actual.getContext().getDb().getStatement());
        return this;
    }

    public SpanAssert hasDbInstance(String instance) {
        isNotNull();
        checkString("Expected span with DB instance '%s' but was '%s'", instance, actual.getContext().getDb().getInstance());
        return this;
    }
}
