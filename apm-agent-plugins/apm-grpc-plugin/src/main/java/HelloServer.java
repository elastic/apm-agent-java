import co.elastic.apm.agent.grpc.HelloGrpc;
import co.elastic.apm.agent.grpc.Rpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import javax.annotation.Nullable;
import java.io.IOException;

public class HelloServer {

    @Nullable
    private Server server;

    public HelloServer(){
    }

    public void start(int port) throws IOException {
        HelloGrpcImpl serverImpl = new HelloGrpcImpl();

        server = ServerBuilder.forPort(port)
            .addService(serverImpl)
            .build()
            .start();
    }

    public void stop() {
        if (null != server) {
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (null != server) {
            server.awaitTermination();
        }
    }

    // service implementation
    static class HelloGrpcImpl extends HelloGrpc.HelloImplBase {
        @Override
        public void sayHello(Rpc.HelloRequest request, StreamObserver<Rpc.HelloReply> responseObserver) {
            Rpc.HelloReply reply = Rpc.HelloReply.newBuilder()
                .setMessage(String.format("Hello %s", request.getUserName()))
                .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
