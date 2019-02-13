package co.elastic.apm.agent.jaxrs.resources;

public class TestResourceWithPathOnInterface implements TestInterfaceWithPath {
    public String testMethod() {
        return "ok";
    }
}
