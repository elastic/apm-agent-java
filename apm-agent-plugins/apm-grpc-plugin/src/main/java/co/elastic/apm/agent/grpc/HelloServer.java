package co.elastic.apm.agent.grpc;

import com.google.protobuf.Descriptors;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class HelloServer {

    private static final Logger logger = LoggerFactory.getLogger(HelloServer.class);

    @Nullable
    private Server server;

    public HelloServer(){
    }

    public void start(int port) throws IOException {
        logger.info("starting grpc server on port {}", port);
        HelloGrpcImpl serverImpl = new HelloGrpcImpl();

        server = ServerBuilder.forPort(port)
            .addService(serverImpl)
            .build()
            .start();
    }

    public void stop() throws InterruptedException {
        logger.info("stopping grpc server");
        if (null != server) {
            server.shutdown().awaitTermination();
        }
        logger.info("grpc server shutdown complete");
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (null != server) {
            server.awaitTermination();
        }
        logger.info("grpc server shutdown complete");
    }

    // service implementation
    static class HelloGrpcImpl extends HelloGrpc.HelloImplBase {
        @Override
        public void sayHello(Rpc.HelloRequest request, StreamObserver<Rpc.HelloReply> responseObserver) {

            String userName = request.getUserName();

            if (userName.isEmpty()) {
                // this seems to be the preferred way to deal with errors on server implementation
                responseObserver.onError(new StatusRuntimeException(Status.INVALID_ARGUMENT));
                return;
            }

            Rpc.HelloReply reply = Rpc.HelloReply.newBuilder()
                .setMessage(String.format("Hello %s", userName))
                .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
