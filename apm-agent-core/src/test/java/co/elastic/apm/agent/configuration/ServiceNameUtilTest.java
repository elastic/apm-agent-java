/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.configuration;

import org.junit.jupiter.api.Test;

import static co.elastic.apm.agent.configuration.ServiceNameUtil.getDefaultServiceName;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ServiceNameUtilTest {

    @Test
    void serviceNameShouldBeNormalizedOrDefaults() {
        assertSoftly(softly -> {
            softly.assertThat(getDefaultServiceName(" my-awesome-service ")).isEqualTo("my-awesome-service");
            softly.assertThat(getDefaultServiceName("")).isEqualTo("my-service");
            softly.assertThat(getDefaultServiceName("  ")).isEqualTo("my-service");
            softly.assertThat(getDefaultServiceName(null)).isEqualTo("my-service");
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

}
