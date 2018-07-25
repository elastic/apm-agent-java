package co.elastic.apm.report;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class ApmServerHealthChecker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ApmServerHealthChecker.class);
    private final OkHttpClient httpClient;
    private final ReporterConfiguration reporterConfiguration;

    ApmServerHealthChecker(OkHttpClient httpClient, ReporterConfiguration reporterConfiguration) {
        this.httpClient = httpClient;
        this.reporterConfiguration = reporterConfiguration;
    }

    @Override
    public void run() {
        boolean success;
        String message = null;
        try {
            final int status = httpClient.newCall(new Request.Builder()
                .url(reporterConfiguration.getServerUrl() + "/healthcheck")
                .build())
                .execute()
                .code();
            success = status == 200;
            if (!success) {
                message = Integer.toString(status);
            }
        } catch (IOException e) {
            message = e.getMessage();
            success = false;
        }

        if (success) {
            logger.info("Elastic APM server is available");
        } else {
            logger.warn("Elastic APM server is not available ({})", message);
        }
    }
}
