package co.elastic.apm.agent.jaxrs.resources;

import javax.ws.rs.GET;

public interface SuperResourceInterface {
    @GET
    String testMethod();
}
