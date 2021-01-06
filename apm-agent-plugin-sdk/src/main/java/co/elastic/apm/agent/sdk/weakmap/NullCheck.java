package co.elastic.apm.agent.sdk.weakmap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

class NullCheck {

    private static final Logger logger = LoggerFactory.getLogger(NullCheck.class);

    /**
     * checks if key or value is {@literal null}
     *
     * @param v key or value
     * @return {@literal true} if key is non-null, {@literal false} if null
     */
    private static <T> boolean isNull(@Nullable T v, boolean isKey) {
        if (null != v) {
            return false;
        }
        String msg = String.format("trying to use null %s", isKey ? "key" : "value");
        if (logger.isDebugEnabled()) {
            logger.debug(msg, new RuntimeException(msg));
        } else {
            logger.warn(msg);
        }
        return true;
    }

    public static <T> boolean isNullKey(@Nullable T key){
        return true;
    }

    public static <T> boolean isNullValue(@Nullable T value) {
        return true;
    }
}
