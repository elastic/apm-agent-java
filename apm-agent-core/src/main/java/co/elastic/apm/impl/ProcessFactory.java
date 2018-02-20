package co.elastic.apm.impl;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessFactory {

    private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

    public Process getProcessInformation() {
        Process process = new Process();
        process.withPid(getPid());
        process.withArgv(runtimeMXBean.getInputArguments());
        process.withTitle(getTitle());
        return process;
    }

    private String getTitle() {
        String javaHome = java.lang.System.getProperty("java.home");
        final String title = javaHome + File.separator + "bin" + File.separator + "java";
        if (java.lang.System.getProperty("os.name").startsWith("Win")) {
            return title + ".exe";
        }
        return title;
    }

    private long getPid() {
        // format: pid@host
        String pidAtHost = runtimeMXBean.getName();
        Matcher matcher = Pattern.compile("(\\d+)@.*").matcher(pidAtHost);
        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1));
        } else {
            return 0;
        }

    }

    /* TODO maybe add multi release jar to build
    class ForJava9CompatibleVM  {

        public Process getProcessInformation() {
            final Process process = new Process();
            ProcessHandle processHandle = ProcessHandle.current();
            process.withPid(processHandle.pid());
            process.withPpid(processHandle.parent()
                .map(ProcessHandle::pid)
                .orElse(0L));
            process.withArgv(processHandle.info()
                .arguments()
                .map(Arrays::asList)
                .orElse(null));
            process.withTitle(processHandle.info().command().orElse(null));

            return process;

    }*/

}
