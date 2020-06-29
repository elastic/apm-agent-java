package co.elastic.apm.servlet.tests;

import co.elastic.apm.servlet.AbstractServletContainerIntegrationTest;

import java.util.Map;

public class JBossServletApiTestApp extends ServletApiTestApp {

    @Override
    public void test(AbstractServletContainerIntegrationTest test) throws Exception {
        super.test(test);
        test.executeAndValidateRequest("/simple-webapp/jboss-mbeans", "Found jboss.as:* MBeans", 200, Map.of());
    }
}
