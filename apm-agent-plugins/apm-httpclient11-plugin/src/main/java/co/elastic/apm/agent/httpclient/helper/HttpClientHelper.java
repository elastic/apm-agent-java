package co.elastic.apm.agent.httpclient.helper;

import javax.annotation.Nullable;

public class HttpClientHelper {

    private HttpClientHelper() {
    }

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
