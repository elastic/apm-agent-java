package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.bci.VisibleForAdvice;

import javax.annotation.Nullable;

@VisibleForAdvice
public class OkHttpClientHelper {

    /**
     * Used to avoid allocations when calculating destination host name.
     */
    private static final ThreadLocal<StringBuilder> destinationHostName = new ThreadLocal<StringBuilder>() {
        @Override
        protected StringBuilder initialValue() {
            return new StringBuilder();
        }
    };

    @VisibleForAdvice
    @Nullable
    public static CharSequence computeHostName(@Nullable String originalHostName) {
        CharSequence hostName = originalHostName;
        // okhttp represents IPv6 addresses without square brackets, as opposed to all others, so we should add them
        if (originalHostName != null && originalHostName.contains(":") && !originalHostName.startsWith("[")) {
            StringBuilder sb = destinationHostName.get();
            sb.setLength(0);
            sb.append("[").append(originalHostName).append("]");
            hostName = sb;
        }
        return hostName;
    }
}
