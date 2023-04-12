package co.elastic.apm.spring.boot;

public class SpringBoot3TomcatIT extends SpringBootTomcatIT {

    @Override
    protected String getExpectedSpringVersionRegex() {
        return "6\\.[0-9]+\\.[0-9]+";
    }
}
