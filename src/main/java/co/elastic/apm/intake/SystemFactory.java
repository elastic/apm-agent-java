package co.elastic.apm.intake;

import java.net.InetAddress;

public class SystemFactory {

    public System getSystem() {
        return new System()
            .withArchitecture(java.lang.System.getProperty("os.arch"))
            .withPlatform(java.lang.System.getProperty("os.name"))
            .withHostname(getNameOfLocalHost());
    }

    private String getNameOfLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return getHostNameFromEnv();
        }
    }

    private String getHostNameFromEnv() {
        // try environment properties.
        String host = java.lang.System.getenv("COMPUTERNAME");
        if (host == null) {
            host = java.lang.System.getenv("HOSTNAME");
        }
        if (host == null) {
            host = java.lang.System.getenv("HOST");
        }
        return host;
    }
}
