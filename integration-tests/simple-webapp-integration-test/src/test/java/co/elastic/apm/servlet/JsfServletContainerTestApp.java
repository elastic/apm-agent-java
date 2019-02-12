package co.elastic.apm.servlet;

class JsfServletContainerTestApp extends TestApp {
    public JsfServletContainerTestApp() {
        super("../jsf-app/jsf-app-standalone", "jsf-http-get.war", "/jsf-http-get/status.html");
    }

    @Override
    void test(AbstractServletContainerIntegrationTest test) throws Exception {
        new JsfApplicationServerTestApp().test(test);
    }
}
