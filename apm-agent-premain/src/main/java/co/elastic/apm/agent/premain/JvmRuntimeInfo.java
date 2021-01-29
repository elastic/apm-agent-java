/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.premain;

import javax.annotation.Nullable;

public class JvmRuntimeInfo {

    @SuppressWarnings("NotNullFieldNotInitialized")
    private static String javaVersion;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private static String javaVmName;
    @Nullable private static String javaVmVersion;
    private static int majorVersion;
    private static int updateVersion;
    private static boolean isHotSpot;
    private static boolean isIbmJ9;
    private static boolean isJ9;
    private static boolean isHpUx;

    static {
        parseVmInfo(System.getProperty("java.version"), System.getProperty("java.vm.name"), System.getProperty("java.vm.version"));
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
    static void parseVmInfo(String version, String vmName, @Nullable String vmVersion) {
        javaVersion = version;
        javaVmName = vmName;
        javaVmVersion = vmVersion;

        isHotSpot = vmName.contains("HotSpot(TM)") || vmName.contains("OpenJDK");
        isIbmJ9 = vmName.contains("IBM J9");
        isJ9 = vmName.contains("J9");
        isHpUx = version.endsWith("-hp-ux");

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

        int updateIndex = version.lastIndexOf("_");
        if (updateIndex <= 0) {

            if (isHpUx) {
                try {
                    updateVersion = Integer.parseInt(version.substring(version.lastIndexOf('.') + 1));
                } catch (NumberFormatException e) {
                    updateVersion = -1;
                }
            } else {
                // GA release like '1.8.0'
                updateVersion = 0;
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
            System.err.println("Unsupported format of the java.version system property - " + version);
        }

    }

    public static String getJavaVersion() {
        return javaVersion;
    }

    public static String getJavaVmName() {
        return javaVmName;
    }

    @Nullable
    public static String getJavaVmVersion() {
        return javaVmVersion;
    }

    public static int getMajorVersion() {
        return majorVersion;
    }

    public static int getUpdateVersion() {
        return updateVersion;
    }

    public static boolean isJ9VM() {
        return isJ9;
    }

    public static boolean isHpUx() {
        return isHpUx;
    }

    public static boolean isHotSpot() {
        return isHotSpot;
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
    public static boolean isJavaVersionSupported() {
        if (majorVersion < 7) {
            // given code is compiled with java 7, this one is unlikely in practice
            return false;
        }
        if (isHotSpot) {
            return isHotSpotVersionSupported();
        } else if (isIbmJ9) {
            return isIbmJ9VersionSupported();
        }
        // innocent until proven guilty
        return true;
    }

    private static boolean isHotSpotVersionSupported() {
        if (updateVersion < 0) {
            return true;
        }

        // versions prior to that have unreliable invoke dynamic support according to https://groovy-lang.org/indy.html
        int java7min = 60;
        int java8min = 40;
        if (isHpUx()) {
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

    private static boolean isIbmJ9VersionSupported() {
        switch (majorVersion) {
            case 7:
                return false;
            case 8:
                // early versions crash during invokedynamic bootstrap
                // the exact version that fixes that error is currently not known
                // presumably, service refresh 5 (build 2.8) fixes the issue
                return !"2.8".equals(javaVmVersion);
            default:
                return true;
        }
    }
}
