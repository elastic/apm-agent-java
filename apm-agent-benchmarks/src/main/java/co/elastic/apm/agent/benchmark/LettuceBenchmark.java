package co.elastic.apm.agent.benchmark;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import redis.embedded.RedisServer;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public abstract class LettuceBenchmark extends AbstractMockApmServerBenchmark {

    private RedisServer server;
    private StatefulRedisConnection<String, String> connection;
    protected RedisCommands<String, String> sync;

    public LettuceBenchmark(boolean apmEnabled) {
        super(apmEnabled);
    }

    @Override
    public void setUp(Blackhole blackhole) throws IOException {
        super.setUp(blackhole);
        int redisPort = getAvailablePort();
        server = RedisServer.builder()
            .setting("bind 127.0.0.1")
            .port(redisPort)
            .build();
        server.start();
        RedisClient client = RedisClient.create(RedisURI.create("localhost", redisPort));
        connection = client.connect();
        sync = connection.sync();
        sync.set("foo", "bar");
    }

    private static int getAvailablePort() throws IOException {
        try (ServerSocket socket = ServerSocketFactory.getDefault().createServerSocket(0, 1, InetAddress.getByName("localhost"))) {
            return socket.getLocalPort();
        }
    }

    @Override
    public void tearDown() throws ExecutionException, InterruptedException {
        super.tearDown();
        connection.close();
        server.stop();
    }

}
