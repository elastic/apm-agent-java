package co.elastic.apm.spring.boot;

public class SpringBoot3JettyIT extends SpringBootJettyIT {

    @Override
    protected String getExpectedSpringVersionRegex() {
        return "6\\.[0-9]+\\.[0-9]+";
    }
}
