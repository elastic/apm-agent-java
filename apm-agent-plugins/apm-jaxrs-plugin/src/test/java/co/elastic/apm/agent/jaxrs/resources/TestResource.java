package co.elastic.apm.agent.jaxrs.resources;

import javax.ws.rs.Path;

@Path("test")
public class TestResource extends AbstractResourceClass {
    public String testMethod() {
        return "ok";
    }
}
