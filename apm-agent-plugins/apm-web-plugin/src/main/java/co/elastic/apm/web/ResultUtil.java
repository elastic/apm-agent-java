package co.elastic.apm.web;

import javax.annotation.Nullable;

public class ResultUtil {

    @Nullable
    public static String getResultByHttpStatus(int status) {
        if (status >= 200 && status < 300) {
            return "HTTP 2xx";
        }
        if (status >= 300 && status < 400) {
            return "HTTP 3xx";
        }
        if (status >= 400 && status < 500) {
            return "HTTP 4xx";
        }
        if (status >= 500 && status < 600) {
            return "HTTP 5xx";
        }
        if (status >= 100 && status < 200) {
            return "HTTP 1xx";
        }
        return null;
    }
}
