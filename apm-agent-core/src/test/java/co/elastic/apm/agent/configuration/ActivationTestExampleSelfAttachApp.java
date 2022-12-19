package co.elastic.apm.agent.configuration;

import co.elastic.apm.attach.ElasticApmAttacher;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.HashMap;

public class ActivationTestExampleSelfAttachApp {

    public static void main(String[] args) throws InterruptedException {
        // Just sleep for 5 minutes then exit
        //long pid = ProcessHandle.current().pid(); //java 9+
        //Use the old hack - doesn't need to be guaranteed all platforms, it's just for testing
        String pidHost = ManagementFactory.getRuntimeMXBean().getName();
        long pid = Integer.parseInt(pidHost.substring(0,pidHost.indexOf('@')));
        ElasticApmAttacher.attach(""+pid, new HashMap<>(), new File(System.getProperty("ElasticApmAgent.jarfile")));
        Thread.sleep(5*60*1000);
    }
}
