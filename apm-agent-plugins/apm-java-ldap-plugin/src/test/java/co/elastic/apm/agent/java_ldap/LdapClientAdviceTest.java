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
package co.elastic.apm.agent.java_ldap;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.testutils.TestPort;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldif.LDIFReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.naming.Context;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// In order to run this test from intelliJ you need to add --add-exports java.naming/com.sun.jndi.ldap=ALL-UNNAMED
// to the module compiler overrides within intelliJ
public class LdapClientAdviceTest extends AbstractInstrumentationTest {

    private static InMemoryDirectoryServer ldapServer;

    @BeforeAll
    static void startServer() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=com");
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("test", TestPort.getAvailableRandomPort()));

        ldapServer = new InMemoryDirectoryServer(config);
        ldapServer.importFromLDIF(true, new LDIFReader(LdapClientAdviceTest.class.getResourceAsStream("/test.ldif")));
        ldapServer.startListening();
    }

    @AfterAll
    static void stopServer() {
        ldapServer.shutDown(true);
    }

    @Test
    void testSuccessfulAuthentication() throws Exception {
        Hashtable<String, String> environment = getEnvironment();

        Transaction transaction = startTestRootTransaction();
        try {
            new InitialDirContext(environment).close();
        } catch (Exception ignored) {
        } finally {
            transaction.deactivate().end();
        }

        List<Span> spans = reporter.getSpans();
        assertThat(spans.size()).isEqualTo(1);

        assertSpan(spans.get(0), "authenticate", Outcome.SUCCESS);
    }

    @Test
    void testUnsuccessfulAuthentication() {
        Hashtable<String, String> environment = getEnvironment();
        environment.put(Context.SECURITY_CREDENTIALS, "wrong password");

        Transaction transaction = startTestRootTransaction();
        try {
            new InitialDirContext(environment).close();
        } catch (Exception ignored) {
            ignored.printStackTrace();
        } finally {
            transaction.deactivate().end();
        }

        List<Span> spans = reporter.getSpans();
        assertThat(spans.size()).isEqualTo(1);

        assertSpan(spans.get(0), "authenticate", Outcome.FAILURE);
    }

    @Test
    void testSearch() {
        Hashtable<String, String> environment = getEnvironment();

        Transaction transaction = startTestRootTransaction();
        try {
            InitialDirContext context = new InitialDirContext(environment);
            context.search("dc=example,dc=com", "(&(objectClass=person)(uid=tobiasstadler))", null);
            context.close();
        } catch (Exception ignored) {
        } finally {
            transaction.deactivate().end();
        }

        List<Span> spans = reporter.getSpans();
        assertThat(spans.size()).isEqualTo(2);

        assertSpan(spans.get(0), "authenticate", Outcome.SUCCESS);
        assertSpan(spans.get(1), "search", Outcome.SUCCESS);
    }

    private static Hashtable<String, String> getEnvironment() {
        Hashtable<String, String> environment = new Hashtable<>();

        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, "ldap://localhost:" + ldapServer.getListenPort());
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");
        environment.put(Context.SECURITY_PRINCIPAL, "cn=Tobias Stadler,ou=Users,dc=example,dc=com");
        environment.put(Context.SECURITY_CREDENTIALS, "123456");

        return environment;
    }

    static void assertSpan(Span span, String method, Outcome outcome) {
        assertThat(span.getNameAsString()).isEqualTo("LDAP " + method);
        assertThat(span.getType()).isEqualTo("external");
        assertThat(span.getSubtype()).isEqualTo("ldap");
        assertThat(span.getOutcome()).isEqualTo(outcome);
        assertThat(span.getContext().getDestination().getAddress().toString()).isEqualTo("localhost");
        assertThat(span.getContext().getDestination().getPort()).isEqualTo(ldapServer.getListenPort());
    }
}
