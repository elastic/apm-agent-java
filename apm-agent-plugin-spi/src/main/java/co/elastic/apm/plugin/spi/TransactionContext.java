package co.elastic.apm.plugin.spi;

public interface TransactionContext extends AbstractContext {

    Request getRequest();

    Response getResponse();

    User getUser();

    CloudOrigin getCloudOrigin();
}
