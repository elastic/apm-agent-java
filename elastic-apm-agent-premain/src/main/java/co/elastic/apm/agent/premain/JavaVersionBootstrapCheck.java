/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.premain;

import co.elastic.apm.agent.common.JvmRuntimeInfo;

import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Gracefully abort agent startup is better than unexpected failure down the road when we known a given JVM
 * version is not supported. Agent might trigger known JVM bugs causing JVM crashes, notably on early Java 8
 * versions (but fixed in later versions), given those versions are obsolete and agent can't have workarounds
 * for JVM internals, there is no other option but to use an up-to-date JVM instead.
 */
public class JavaVersionBootstrapCheck implements BootstrapCheck {

    private final JvmRuntimeInfo runtimeInfo;

    public JavaVersionBootstrapCheck(JvmRuntimeInfo runtimeInfo) {
        this.runtimeInfo = runtimeInfo;
    }

    @Override
    public void doBootstrapCheck(BootstrapCheckResult result) {
        if (!isJavaVersionSupported()) {
            result.addError(String.format("JVM version not supported: %s", runtimeInfo));
        } else if (isJavaVersionDeprecated()) {
            result.addWarn(String.format("Java %s support is deprecated and will be removed in a future version",
                runtimeInfo.getMajorVersion()));
        }
    }

    /**
     * Checks if a given version of the JVM is likely supported by this agent.
     * <br>
     * Supports values provided before and after https://openjdk.java.net/jeps/223, in case parsing fails due to an
     * unknown version format, we assume it's supported, thus this method might return false positives, but never false
     * negatives.
     *
     * @return true if the version is supported, false otherwise
     */
    public boolean isJavaVersionSupported() {
        if (runtimeInfo.getMajorVersion() < getMinSupportedMajorJavaVersion()) {
            return false;
        }
        if (runtimeInfo.isHotSpot()) {
            return isHotSpotVersionSupported();
        } else if (runtimeInfo.isIbmJ9()) {
            return isIbmJ9VersionSupported();
        }
        // innocent until proven guilty
        return true;
    }

    private int getMinSupportedMajorJavaVersion() {
        try {
            try (JarFile jarFile = new JarFile(AgentJarLocator.getAgentJarFile())) {
                Manifest manifest = jarFile.getManifest();
                String variant = manifest.getMainAttributes().getValue("Elastic-Apm-Build-Variant");
                if ("java8".equals(variant)) {
                    return 8;
                }
            }
        } catch (Exception e) {
            //silently ignore
        }
        return 7;
    }

    private boolean isJavaVersionDeprecated() {
        return runtimeInfo.getMajorVersion() == 7;
    }

    private boolean isHotSpotVersionSupported() {
        int updateVersion = runtimeInfo.getUpdateVersion();
        int majorVersion = runtimeInfo.getMajorVersion();
        if (updateVersion < 0) {
            return true;
        }

        // versions prior to that have unreliable invoke dynamic support according to https://groovy-lang.org/indy.html
        int java7min = 60;
        int java8min = 40;
        if (runtimeInfo.isHpUx()) {
            java7min = 10; // hotspot 7u65
            java8min = 2; // hotspot 8u45
        }

        switch (majorVersion) {
            case 7:
                return updateVersion >= java7min;
            case 8:
                return updateVersion >= java8min;
            default:
                return true;
        }
    }

    private boolean isIbmJ9VersionSupported() {
        switch (runtimeInfo.getMajorVersion()) {
            case 7:
                return false;
            case 8:
                // early versions crash during invokedynamic bootstrap
                // the exact version that fixes that error is currently not known
                // presumably, service refresh 5 (build 2.8) fixes the issue
                return !"2.8".equals(runtimeInfo.getJavaVmVersion());
            default:
                return true;
        }
    }
}
