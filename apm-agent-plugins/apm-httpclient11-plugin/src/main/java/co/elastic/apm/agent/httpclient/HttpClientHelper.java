package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.bci.VisibleForAdvice;

import javax.annotation.Nullable;

@VisibleForAdvice
public class HttpClientHelper {

    @VisibleForAdvice
    @Nullable
    public static CharSequence computeHostName(@Nullable String originalHostName) {
        CharSequence hostName = originalHostName;
        if (originalHostName != null && originalHostName.contains(":") && !originalHostName.startsWith("[")) {
            StringBuilder sb = new StringBuilder();
            sb.setLength(0);
            sb.append("[").append(originalHostName).append("]");
            hostName = sb;
        }
        return hostName;
    }
}
