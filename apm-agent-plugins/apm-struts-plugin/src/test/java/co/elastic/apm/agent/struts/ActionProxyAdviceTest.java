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
package co.elastic.apm.agent.struts;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.StrutsTestCase;

import javax.servlet.ServletException;
import java.io.UnsupportedEncodingException;

import static org.assertj.core.api.Assertions.assertThat;

public class ActionProxyAdviceTest extends StrutsTestCase {

    public static class TestAction extends ActionSupport {

        @SuppressWarnings("unused")
        public String customMethod() {
            return SUCCESS;
        }

        @SuppressWarnings("unused")
        public String chainedAction() {
            return SUCCESS;
        }
    }

    public synchronized void setUp() throws Exception {
        super.setUp();
        AbstractInstrumentationTest.beforeAll();
    }

    public synchronized void tearDown() throws Exception {
        AbstractInstrumentationTest.afterAll();
        super.tearDown();
    }

    public void testExecuteAction() throws ServletException, UnsupportedEncodingException {
        Transaction transaction = AbstractInstrumentationTest.getTracer().startRootTransaction(null).withName("struts-test").activate();
        executeAction("/test1");
        transaction.end();

        assertThat(AbstractInstrumentationTest.getReporter().getFirstTransaction().getNameAsString()).isEqualTo("TestAction#execute");
    }

    public void testExecuteCustomMethodAction() throws ServletException, UnsupportedEncodingException {
        Transaction transaction = AbstractInstrumentationTest.getTracer().startRootTransaction(null).withName("struts-test").activate();
        executeAction("/test2");
        transaction.end();

        assertThat(AbstractInstrumentationTest.getReporter().getFirstTransaction().getNameAsString()).isEqualTo("TestAction#customMethod");
    }

    public void testChainedAction() throws ServletException, UnsupportedEncodingException {
        Transaction transaction = AbstractInstrumentationTest.getTracer().startRootTransaction(null).withName("struts-test").activate();
        executeAction("/test3");
        transaction.end();

        assertThat(AbstractInstrumentationTest.getReporter().getFirstTransaction().getNameAsString()).isEqualTo("TestAction#chainedAction");
        Span span = AbstractInstrumentationTest.getReporter().getFirstSpan();
        assertThat(span.getNameAsString()).isEqualTo("TestAction#execute");
        assertThat(span.getType()).isEqualTo("app");
        assertThat(span.getSubtype()).isEqualTo("internal");
    }
}
