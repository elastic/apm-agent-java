package co.elastic.apm.servlet;

import okhttp3.Response;

import static org.assertj.core.api.Assertions.assertThat;

class SoapTestApp extends TestApp {
    public SoapTestApp() {
        super("../soap-test", "soap-test.war", "/soap-test/status.html");
    }

    @Override
    void test(AbstractServletContainerIntegrationTest test) throws Exception {
        final Response response = test.executeRequest("/soap-test/execute-soap-request");
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isNotEmpty();
    }
}
