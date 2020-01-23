package co.elastic.apm.agent.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class GrpcApp {

    private static final Logger logger = LoggerFactory.getLogger(GrpcApp.class);

    private static final int PORT = 50051;

    public static void main(String[] args) throws IOException, InterruptedException {
        HelloServer server = new HelloServer();
        server.start(PORT);

        HelloClient client = new HelloClient("localhost", PORT);

        sendMessage(client, "bob");
        sendMessage(client, null);

        client.stop();
        server.stop();
    }

    static void sendMessage(HelloClient client, String name) {
        String message = client.sayHello(name);
        logger.info("received message = {}", message);
    }


}
