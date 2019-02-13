package co.elastic.apm.agent.jaxrs.resources;

public class TestResourceWithPathOnAbstract extends AbstractResourceClassWithPath {
    public String testMethod() {
        return "ok";
    }
}
