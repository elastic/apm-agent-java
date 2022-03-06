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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.util.CustomEnvVariables;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ServiceInfoTest extends CustomEnvVariables {

    private static String getDefaultServiceName(@Nullable String sunJavaCommand) {
        Properties properties = new Properties();
        if (sunJavaCommand != null) {
            properties.setProperty("sun.java.command", sunJavaCommand);
        }

        return ServiceInfo.autoDetect(properties).getServiceName();
    }

    @Test
    void serviceNameShouldBeNormalizedOrDefaults() {
        assertSoftly(softly -> {
            softly.assertThat(getDefaultServiceName(" my-awesome-service ")).isEqualTo("my-awesome-service");
            softly.assertThat(getDefaultServiceName("")).isEqualTo("unknown-java-service");
            softly.assertThat(getDefaultServiceName("  ")).isEqualTo("unknown-java-service");
            softly.assertThat(getDefaultServiceName(null)).isEqualTo("unknown-java-service");
        });
    }

    @Test
    void testDefaultServiceName() {
        assertSoftly(softly -> {
            softly.assertThat(getDefaultServiceName("foo.bar.Baz")).isEqualTo("Baz");
            softly.assertThat(getDefaultServiceName("foo.bar.Baz$Qux")).isEqualTo("Baz-Qux");
            softly.assertThat(getDefaultServiceName("foo.bar.Baz foo")).isEqualTo("Baz");
            softly.assertThat(getDefaultServiceName("my-app.jar bar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("my-app-4-j.jar bar")).isEqualTo("my-app-4-j");
            softly.assertThat(getDefaultServiceName("my-app-2.jar bar")).isEqualTo("my-app-2");
            softly.assertThat(getDefaultServiceName("my-app-1.0.jar bar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("my-app-1.0.0.jar bar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("my-app-1.0.0.0.jar bar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("my-app-1.0.0-SNAPSHOT.jar bar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("my-app-1.0.0-BUILD-SNAPSHOT.jar bar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("my-app-1.0.0.BUILD-SNAPSHOT.jar bar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("my-app-1.0.0?and0m.jar bar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("my-app-1.0.0-RC1.jar bar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("my-app-1.0.0.RC1.jar bar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("foo/my-app-1.0.0-RC1.jar baz")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("C:\\foo\\my-app.jar")).isEqualTo("my-app");
            softly.assertThat(getDefaultServiceName("C:\\foo bar\\my-app-1.0.0-BUILD-SNAPSHOT.jar qux")).isEqualTo("my-app");
        });
    }

    @Test
    void testDefaultsWithLambda() throws Exception {
        final Map<String, String> awsLambdaEnvVariables = new HashMap<>();
        awsLambdaEnvVariables.put("AWS_LAMBDA_FUNCTION_NAME", "my-lambda-function");
        awsLambdaEnvVariables.put("AWS_LAMBDA_FUNCTION_VERSION", "24");
        final StringBuilder defaultServiceName = new StringBuilder();
        final StringBuilder defaultServiceVersion = new StringBuilder();
        callWithCustomEnvVariables(awsLambdaEnvVariables, () -> {
            defaultServiceName.append(getDefaultServiceName(null));
            defaultServiceVersion.append(ServiceInfo.autoDetect(new Properties()).getServiceVersion());
            return null;
        });
        assertSoftly(softly -> {
            softly.assertThat(defaultServiceName.toString()).isEqualTo("my-lambda-function");
            softly.assertThat(defaultServiceVersion.toString()).isEqualTo("24");
        });
    }

    @Test
    void parseApplicationServers() {
        assertSoftly(softly -> {
            softly.assertThat(getDefaultServiceName("org.eclipse.jetty.xml.XmlConfiguration"))
                .isEqualTo("jetty-application");
            softly.assertThat(getDefaultServiceName("org.apache.catalina.startup.Bootstrap start"))
                .isEqualTo("tomcat-application");
            softly.assertThat(getDefaultServiceName("com.sun.enterprise.glassfish.bootstrap.ASMain -upgrade false -read-stdin true -postbootcommandfile /opt/payara5/post-boot-commands.asadmin -domainname domain1 -domaindir /opt/payara5/glassfish/domains/domain1 -asadmin-args --host,,,localhost,,,--port,,,4848,,,--passwordfile,,,/opt/pwdfile,,,--secure=false,,,--terse=false,,,--echo=false,,,--interactive=false,,,start-domain,,,--verbose=false,,,--watchdog=false,,,--debug=false,,,--domaindir,,,/opt/payara5/glassfish/domains,,,domain1 -instancename server -type DAS -verbose false -asadmin-classpath /opt/payara5/glassfish/lib/client/appserver-cli.jar -debug false -asadmin-classname com.sun.enterprise.admin.cli.AdminMain -watchdog false"))
                .isEqualTo("glassfish-application");
            softly.assertThat(getDefaultServiceName("/opt/ibm/wlp/bin/tools/ws-server.jar defaultServer"))
                .isEqualTo("websphere-application");
            softly.assertThat(getDefaultServiceName("/opt/jboss/wildfly/jboss-modules.jar -mp /opt/jboss/wildfly/modules org.jboss.as.standalone -Djboss.home.dir=/opt/jboss/wildfly -Djboss.server.base.dir=/opt/jboss/wildfly/standalone -b 0.0.0.0"))
                .isEqualTo("jboss-application");
            softly.assertThat(getDefaultServiceName("weblogic.Server"))
                .isEqualTo("weblogic-application");

        });
    }

    @Test
    void testNormalizedName() {
        checkServiceInfoEmpty(ServiceInfo.of(""));
        checkServiceInfoEmpty(ServiceInfo.of(" "));

        assertThat(ServiceInfo.of(" a")).isEqualTo(ServiceInfo.of("a"));
        assertThat(ServiceInfo.of(" !web# ")).isEqualTo(ServiceInfo.of("-web-"));
    }

    @Test
    void createEmpty() {
        checkServiceInfoEmpty(ServiceInfo.empty());
        assertThat(ServiceInfo.empty())
            .isEqualTo(ServiceInfo.empty());

    }

    @Test
    void of() {
        checkServiceInfoEmpty(ServiceInfo.of(null));
        checkServiceInfoEmpty(ServiceInfo.of(null, null));

        checkServiceInfo(ServiceInfo.of("service"), "service", null);
        checkServiceInfo(ServiceInfo.of("service", null), "service", null);
        checkServiceInfo(ServiceInfo.of("service", "1.2.3"), "service", "1.2.3");

    }

    @Test
    void checkEquality() {
        checkEquality(ServiceInfo.of(null), ServiceInfo.empty());
        checkEquality(ServiceInfo.of(""), ServiceInfo.empty());
        checkEquality(ServiceInfo.of(null, null), ServiceInfo.empty());
        checkEquality(ServiceInfo.of("", ""), ServiceInfo.empty());
    }

    private static void checkEquality(ServiceInfo first, ServiceInfo second){
        assertThat(first)
            .isEqualTo(second);

        assertThat(first.hashCode())
            .isEqualTo(second.hashCode());
    }

    @Test
    void fromManifest() {
        checkServiceInfoEmpty(ServiceInfo.fromManifest(null));
        checkServiceInfoEmpty(ServiceInfo.fromManifest(null));
        checkServiceInfoEmpty(ServiceInfo.fromManifest(new Manifest()));

        ServiceInfo serviceInfo = ServiceInfo.fromManifest(manifest(Map.of(
            Attributes.Name.IMPLEMENTATION_TITLE.toString(), "service-name"
        )));
        checkServiceInfo(serviceInfo, "service-name", null);

        serviceInfo = ServiceInfo.fromManifest(manifest(Map.of(
            Attributes.Name.IMPLEMENTATION_TITLE.toString(), "my-service",
            Attributes.Name.IMPLEMENTATION_VERSION.toString(), "v42"
        )));
        checkServiceInfo(serviceInfo, "my-service", "v42");
    }

    private static Manifest manifest(Map<String, String> entries) {
        Manifest manifest = new Manifest();

        Attributes attributes = manifest.getMainAttributes();
        entries.forEach(attributes::putValue);

        return manifest;
    }

    private static void checkServiceInfoEmpty(ServiceInfo serviceInfo) {
        assertThat(serviceInfo.isEmpty()).isTrue();
        assertThat(serviceInfo.getServiceName()).isEqualTo("unknown-java-service");
        assertThat(serviceInfo.hasServiceName()).isFalse();
        assertThat(serviceInfo.getServiceVersion()).isNull();

        assertThat(serviceInfo).isEqualTo(ServiceInfo.empty());
    }

    private static void checkServiceInfo(ServiceInfo serviceInfo, String expectedServiceName, @Nullable String expectedServiceVersion) {
        assertThat(serviceInfo.isEmpty()).isFalse();
        assertThat(serviceInfo.getServiceName()).isEqualTo(expectedServiceName);
        assertThat(serviceInfo.hasServiceName()).isTrue();
        if (expectedServiceVersion == null) {
            assertThat(serviceInfo.getServiceVersion()).isNull();
        } else {
            assertThat(serviceInfo.getServiceVersion()).isEqualTo(expectedServiceVersion);
        }

        assertThat(serviceInfo).isEqualTo(ServiceInfo.of(expectedServiceName, expectedServiceVersion));
    }

}
