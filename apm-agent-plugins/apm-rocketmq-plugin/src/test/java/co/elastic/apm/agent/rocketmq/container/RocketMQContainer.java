package co.elastic.apm.agent.rocketmq.container;

import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;

public class RocketMQContainer extends GenericContainer<RocketMQContainer> {

    private static final String ROCKETMQ_IMAGE = "rocketmqinc/rocketmq";

    private static final int ROCKETMQ_NAME_SRV_PORT = 9876;

    private static final int ROCKET_BROKER_VIP_PORT = 10909;

    private static final int ROCKETMQ_BROKER_SERVICE_PORT = 10911;

    private String brokerConf;

    public RocketMQContainer() {
        this("4.2.0");
    }

    public RocketMQContainer(String version) {
        super(ROCKETMQ_IMAGE + ":" + version);
        this.brokerConf = "/opt/rocketmq-" + version + "/conf/broker.conf";
        withEnv("NAMESRV_ADDR", "localhost:" + ROCKETMQ_NAME_SRV_PORT);
        withCommand("sh mqbroker -c " + brokerConf);
        withExposedPorts(ROCKETMQ_NAME_SRV_PORT, ROCKET_BROKER_VIP_PORT, ROCKETMQ_BROKER_SERVICE_PORT);
        setPortBindings(Arrays.asList(
            ROCKETMQ_NAME_SRV_PORT + ":" + ROCKETMQ_NAME_SRV_PORT,
            ROCKET_BROKER_VIP_PORT + ":" + ROCKET_BROKER_VIP_PORT,
            ROCKETMQ_BROKER_SERVICE_PORT + ":" + ROCKETMQ_BROKER_SERVICE_PORT
        ));
    }

    public String getNameServer() {
        return "localhost:" + ROCKETMQ_NAME_SRV_PORT;
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
        super.containerIsStarting(containerInfo, reused);

        if (reused) {
            return;
        }

        startNameServer();

        String localIp = getLocalIp();

        if (localIp == null) {
            throw new RuntimeException("Error in get local ip");
        }

        String command = "brokerClusterName = DefaultCluster\n" +
            "brokerName = broker-01\n" +
            "brokerId = 1\n" +
            "deleteWhen = 04\n" +
            "fileReservedTime = 48\n" +
            "brokerRole = ASYNC_MASTER\n" +
            "flushDiskType = ASYNC_FLUSH\n" +
            "brokerIP1 = " + localIp + "\n";

        copyFileToContainer(
            Transferable.of(command.getBytes(StandardCharsets.UTF_8), 700),
            brokerConf
        );

    }

    private void startNameServer() {
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(getContainerId())
            .withCmd("sh", "mqnamesrv")
            .exec();
        dockerClient.execStartCmd(execCreateCmdResponse.getId())
            .exec(new ExecStartResultCallback());
    }

    private String getLocalIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();

                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> ias = ni.getInetAddresses();

                if (!ias.hasMoreElements()) {
                    continue;
                }

                while (ias.hasMoreElements()) {
                    InetAddress ia = ias.nextElement();
                    if (ia.isLoopbackAddress()) {
                        continue;
                    }

                    if (!(ia instanceof Inet4Address)) {
                        continue;
                    }

                    return ia.getHostAddress();
                }
            }
        } catch (Exception ignore){

        }
        return null;
    }

}
