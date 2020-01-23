package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.grpc.Rpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class HelloClient {

    private static final Logger logger = LoggerFactory.getLogger(GrpcApp.class);

    private final ManagedChannel channel;
    private final HelloGrpc.HelloBlockingStub blockingStub;

    public HelloClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext() // disable encryption to avoid self-signed certificates
            .build());
    }

    private HelloClient(ManagedChannel channel){
        this.channel = channel;
        this.blockingStub = HelloGrpc.newBlockingStub(channel);
    }

    public String sayHello(String user) {
        Rpc.HelloRequest.Builder request = Rpc.HelloRequest.newBuilder();
        if (user != null) {
            request.setUserName(user);
        }
        Rpc.HelloReply reply;
        try {
            reply = blockingStub.sayHello(request.build());
        } catch (StatusRuntimeException e) {
            logger.error("server error {} {}", e.getStatus(), e.getMessage());
            return null;
        }
        return reply.getMessage();
    }

    public void stop() throws InterruptedException {
        channel.shutdown()
            .awaitTermination(1, TimeUnit.SECONDS);
    }
}
