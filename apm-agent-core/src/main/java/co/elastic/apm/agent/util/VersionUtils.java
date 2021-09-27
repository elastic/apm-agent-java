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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.jar.JarInputStream;

public final class VersionUtils {

    private static final WeakMap<Class<?>, String> versionsCache = WeakConcurrent.createMap();
    private static final String UNKNOWN_VERSION = "UNKNOWN_VERSION";
    @Nullable
    private static final String AGENT_VERSION;

    static {
        String version = getVersion(VersionUtils.class, "co.elastic.apm", "elastic-apm-agent");
        if (version != null && version.endsWith("SNAPSHOT")) {
            String gitRev = getManifestEntry(ElasticApmAgent.getAgentJarFile(), "SCM-Revision");
            if (gitRev != null) {
                version = version + "." + gitRev;
            }
        }
        AGENT_VERSION = version;
    }

    private VersionUtils() {
    }

    @Nullable
    public static String getAgentVersion() {
        return AGENT_VERSION;
    }

    @Nullable
    public static String getVersion(Class<?> clazz, String groupId, String artifactId) {
        String version = versionsCache.get(clazz);
        if (version != null) {
            return version != UNKNOWN_VERSION ? version : null;
        }
        version = getVersionFromPomProperties(clazz, groupId, artifactId);
        if (version == null) {
            version = getVersionFromPackage(clazz);
        }
        versionsCache.put(clazz, version != null ? version : UNKNOWN_VERSION);
        return version;
    }

    @Nullable
    static String getVersionFromPackage(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        if (pkg != null) {
            return pkg.getImplementationVersion();
        }
        return null;
    }

    @Nullable
    static String getVersionFromPomProperties(Class<?> clazz, String groupId, String artifactId) {
        final String classpathLocation = "/META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
        final Properties pomProperties = getFromClasspath(classpathLocation, clazz);
        if (pomProperties != null) {
            return pomProperties.getProperty("version");
        }
        return null;
    }

    @Nullable
    private static Properties getFromClasspath(String classpathLocation, Class<?> clazz) {
        final Properties props = new Properties();
        try (InputStream resourceStream = clazz.getResourceAsStream(classpathLocation)) {
            if (resourceStream != null) {
                props.load(resourceStream);
                return props;
            }
        } catch (IOException ignore) {
        }
        return null;
    }

    @Nullable
    public static String getManifestEntry(@Nullable File jarFile, String manifestAttribute) {
        if (jarFile == null) {
            return null;
        }
        try (JarInputStream jarInputStream = new JarInputStream(new FileInputStream(jarFile))) {
            return jarInputStream.getManifest().getMainAttributes().getValue(manifestAttribute);
        } catch (IOException e) {
            return null;
        }
    }

}
