package co.elastic.apm.servlet;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsfApplicationServerTestApp extends TestApp {

    public JsfApplicationServerTestApp() {
        super("../jsf-app/jsf-app-dependent", "jsf-http-get.war", "/jsf-http-get/status.html");
    }

    @Override
    void test(AbstractServletContainerIntegrationTest test) throws Exception {
        testJsfRequest(test, "/faces/index.xhtml", "");
        testJsfRequest(test, "/faces/login.xhtml", "?name=Jack");
    }

    private void testJsfRequest(AbstractServletContainerIntegrationTest containerIntegrationTest, String testedPath,
                                String additionalPathInfo) throws IOException, InterruptedException {
        containerIntegrationTest.clearMockServerLog();
        String viewPath = "/jsf-http-get" + testedPath;
        String fullTestPath = viewPath + additionalPathInfo;

        containerIntegrationTest.executeAndValidateRequest(fullTestPath, "HTTP GET", 200);
        JsonNode transaction = containerIntegrationTest.assertTransactionReported(viewPath, 200);
        assertThat(transaction.get("name").textValue()).isEqualTo(testedPath);
        String transactionId = transaction.get("id").textValue();
        List<JsonNode> spans = containerIntegrationTest.assertSpansTransactionId(
            500,
            containerIntegrationTest::getReportedSpans,
            transactionId);
        assertThat(spans.size()).isEqualTo(2);
        Iterator<JsonNode> iterator = spans.iterator();
        JsonNode executeSpan = iterator.next();
        assertThat(executeSpan.get("name").textValue()).isEqualTo("JSF Execute");
        assertThat(executeSpan.get("type").textValue()).isEqualTo("template.jsf.execute");
        JsonNode renderSpan = iterator.next();
        assertThat(renderSpan.get("name").textValue()).isEqualTo("JSF Render");
        assertThat(renderSpan.get("type").textValue()).isEqualTo("template.jsf.render");
    }
}
