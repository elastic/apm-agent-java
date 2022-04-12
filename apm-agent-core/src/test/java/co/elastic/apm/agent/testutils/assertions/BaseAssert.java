package co.elastic.apm.agent.testutils.assertions;

import org.assertj.core.api.AbstractAssert;

import javax.annotation.Nullable;

public class BaseAssert<SELF extends AbstractAssert<SELF, ACTUAL>, ACTUAL> extends AbstractAssert<SELF, ACTUAL> {

    protected BaseAssert(ACTUAL actual, Class<SELF> selfType) {
        super(actual, selfType);
    }

    protected static String normalizeToString(CharSequence cs) {
        return cs == null ? null : cs.toString();
    }

    protected void checkString(String msg, String expected, @Nullable String actual) {
        if (!expected.equals(actual)) {
            failWithMessage(msg, expected, actual);
        }
    }

    protected void checkInt(String msg, int expected, int actual){
        if (expected != actual) {
            failWithMessage(msg, expected, actual);
        }
    }

    protected void checkNull(String msg, @Nullable Object actual) {
        if (actual != null) {
            failWithMessage(msg, actual);
        }
    }

    protected void checkTrue(String msg, boolean expectedTrue) {
        if (!expectedTrue) {
            failWithMessage(msg);
        }
    }
}
