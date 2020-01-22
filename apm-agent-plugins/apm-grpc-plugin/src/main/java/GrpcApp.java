// TODO move to proper package

import co.elastic.apm.agent.grpc.HelloGrpc;
import co.elastic.apm.agent.grpc.Rpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import javax.annotation.Nullable;
import java.io.IOException;

public class GrpcApp {

    private static final int PORT = 50051;

    @Nullable
    private Server server;

    public static void main(String[] args) throws IOException, InterruptedException {
        HelloServer server = new HelloServer();
        server.start(PORT);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.stop();
            }
        });
        server.blockUntilShutdown();
    }


}
