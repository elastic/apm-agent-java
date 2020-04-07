package co.elastic.apm.log.shipper;

import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.report.AbstractIntakeApiHandler;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.report.serialize.PayloadSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.zip.Deflater;

public class ApmServerLogShipper extends AbstractIntakeApiHandler implements FileChangeListener {

    public static final String LOGS_ENDPOINT = "/intake/v2/logs";
    private static final Logger logger = LoggerFactory.getLogger(ApmServerLogShipper.class);
    private long httpRequestClosingThreshold;

    public ApmServerLogShipper(ApmServerClient apmServerClient, ReporterConfiguration reporterConfiguration, MetaData metaData, PayloadSerializer payloadSerializer) {
        super(reporterConfiguration, metaData, payloadSerializer, apmServerClient);
        this.deflater = new Deflater(1);
    }

    @Override
    public void onLineAvailable(File file, byte[] line, int offset, int length, boolean eol) {
        if (eol && shouldEndRequest()) {
            endRequest();
        }
        try {
            if (connection == null) {
                connection = startRequest(LOGS_ENDPOINT);
            }
            if (os != null) {
                os.write(line, offset, length);
                if (eol) {
                    os.write('\n');
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    protected HttpURLConnection startRequest(String endpoint) throws IOException {
        HttpURLConnection connection = super.startRequest(endpoint);
        httpRequestClosingThreshold = System.currentTimeMillis() + reporterConfiguration.getApiRequestTime().getMillis();
        return connection;
    }

    @Override
    public void onIdle() {
        if (shouldEndRequest()) {
            endRequest();
        }
    }

    protected boolean shouldEndRequest() {
        return super.shouldEndRequest() || System.currentTimeMillis() > httpRequestClosingThreshold;
    }
}
