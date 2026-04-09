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
package co.elastic.apm.agent.common;

import co.elastic.apm.agent.common.util.SystemStandardOutputLogger;

import javax.annotation.Nullable;

public class JvmRuntimeInfo {

    private static final JvmRuntimeInfo CURRENT_VM = new JvmRuntimeInfo(System.getProperty("java.version"),
        System.getProperty("java.vm.name"), System.getProperty("java.vendor"), System.getProperty("java.vm.version"),
        System.getProperty("os.name"));

    private final String javaVersion;
    private final String javaVmName;
    @Nullable private final String javaVmVersion;
    private final int majorVersion;
    private final int updateVersion;
    private final boolean isHotSpot;
    private final boolean isIbmJ9;
    private final boolean isJ9;
    private final boolean isHpUx;
    private final boolean isCoretto;
    private final boolean isZos;
    private final boolean isOs400;

    public static JvmRuntimeInfo ofCurrentVM() {
        return CURRENT_VM;
    }

    /**
     * Parses Java major version, update version and JVM vendor.
     *
     * NOTE: THIS METHOD IS ONLY FOR UNIT TESTING. THE JVM INFO SHOULD COME FROM SYSTEM PROPERTIES
     *
     * @param version   jvm version, from {@code System.getProperty("java.version")}
     * @param vmName    jvm name, from {@code System.getProperty("java.vm.name")}
     * @param vmVersion jvm version, from {@code System.getProperty("java.vm.version")}
     */
    public JvmRuntimeInfo(String version, String vmName, String vendorName, @Nullable String vmVersion) {
        this(version, vmName, vendorName, vmVersion, null);
    }

    private JvmRuntimeInfo(String version, String vmName, String vendorName, @Nullable String vmVersion, @Nullable String osName) {
        javaVersion = version;
        javaVmName = vmName;
        javaVmVersion = vmVersion;

        isHotSpot = vmName.contains("HotSpot(TM)") || vmName.contains("OpenJDK");
        isIbmJ9 = vmName.contains("IBM J9");
        isJ9 = vmName.contains("J9");
        isHpUx = version.endsWith("-hp-ux");
        isCoretto = vendorName != null && vendorName.contains("Amazon");
        isZos = (osName != null) && osName.toLowerCase().contains("z/os");
        isOs400 = (osName != null) && osName.toLowerCase().contains("os/400");

        if (isHpUx) {
            // remove extra hp-ux suffix for parsing
            version = version.substring(0, version.length() - 6);
        }

        // new scheme introduced in java 9, thus we can use it as a shortcut
        if (version.startsWith("1.")) {
            majorVersion = Character.digit(version.charAt(2), 10);
        } else {
            String majorAsString = version.split("\\.")[0];
            int indexOfDash = majorAsString.indexOf('-');
            if (indexOfDash > 0) {
                majorAsString = majorAsString.substring(0, indexOfDash);
            }
            majorVersion = Integer.parseInt(majorAsString);
        }

        updateVersion = getUpdateVersion(version);
    }

    private int getUpdateVersion(String version) {
        int updateVersion;
        int updateIndex = version.lastIndexOf("_");
        if (updateIndex <= 0) {

            if (isHpUx) {
                try {
                    updateVersion = Integer.parseInt(version.substring(version.lastIndexOf('.') + 1));
                } catch (NumberFormatException e) {
                    updateVersion = -1;
                }
            } else {
                // Try hyphen format (e.g., OpenLogic's "1.8.0-292" or "1.8.0-292-b10")
                updateVersion = parseHyphenVersionFormat(version);
            }

        } else {
            String updateVersionString;
            int versionSuffixIndex = version.indexOf('-', updateIndex + 1);
            if (versionSuffixIndex <= 0) {
                updateVersionString = version.substring(updateIndex + 1);
            } else {
                updateVersionString = version.substring(updateIndex + 1, versionSuffixIndex);
            }
            try {
                updateVersion = Integer.parseInt(updateVersionString);
            } catch (NumberFormatException e) {
                // in case of unknown format, we just support by default

                updateVersion = -1;
            }
        }

        if (updateVersion < 0) {
            SystemStandardOutputLogger.stdErrWarn("Unsupported format of the java.version system property - " + version);
        }
        return updateVersion;
    }

    /**
     * Parses version strings that use hyphen format like "1.8.0-292" or "1.8.0-292-b10".
     * This format is used by some OpenJDK vendors like OpenLogic.
     *
     * @param version the version string to parse
     * @return the update version number, or 0 if it's a GA release or non-numeric suffix
     */
    private int parseHyphenVersionFormat(String version) {
        int lastDotIndex = version.lastIndexOf('.');
        if (lastDotIndex <= 0) {
            return 0;
        }

        int hyphenAfterDot = version.indexOf('-', lastDotIndex + 1);
        if (hyphenAfterDot <= 0) {
            // No hyphen found, likely a GA release like '1.8.0'
            return 0;
        }

        // Extract the update version between the hyphen and the next hyphen (or end of string)
        int nextHyphen = version.indexOf('-', hyphenAfterDot + 1);
        String updateVersionString;
        if (nextHyphen <= 0) {
            updateVersionString = version.substring(hyphenAfterDot + 1);
        } else {
            updateVersionString = version.substring(hyphenAfterDot + 1, nextHyphen);
        }

        try {
            return Integer.parseInt(updateVersionString);
        } catch (NumberFormatException e) {
            // Non-numeric suffix like "1.8.0-hello" should be treated as GA release
            return 0;
        }
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getJavaVmName() {
        return javaVmName;
    }

    @Nullable
    public String getJavaVmVersion() {
        return javaVmVersion;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getUpdateVersion() {
        return updateVersion;
    }

    public boolean isJ9VM() {
        return isJ9;
    }

    public boolean isHpUx() {
        return isHpUx;
    }

    public boolean isHotSpot() {
        return isHotSpot;
    }

    public boolean isIbmJ9() {
        return isIbmJ9;
    }

    public boolean isCoretto() {
        return isCoretto;
    }

    public boolean isZos() {
        return isZos;
    }

    public boolean isOs400() {
        return isOs400;
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", javaVersion, javaVmName, javaVmVersion);
    }
}
