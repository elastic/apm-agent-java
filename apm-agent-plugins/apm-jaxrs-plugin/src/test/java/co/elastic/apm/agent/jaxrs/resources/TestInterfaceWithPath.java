package co.elastic.apm.agent.jaxrs.resources;

import javax.ws.rs.Path;

@Path("testInterface")
public interface TestInterfaceWithPath extends SuperResourceInterface {
    String testMethod();
}
