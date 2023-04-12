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
package co.elastic.apm.agent.grails;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.tracer.Scope;
import grails.core.GrailsControllerClass;
import grails.web.mapping.UrlMappingInfo;
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo;
import org.grails.web.mapping.mvc.UrlMappingsInfoHandlerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class GrailsTransactionNameInstrumentationTest extends AbstractInstrumentationTest {

    private UrlMappingsInfoHandlerAdapter handlerAdapter;

    @BeforeEach
    void setUp() {
        handlerAdapter = new UrlMappingsInfoHandlerAdapter();
    }

    @Test
    void testSetGrailsTransactionName() throws Exception {
        final GrailsControllerClass controllerClass = mock(GrailsControllerClass.class);
        doReturn("Foo").when(controllerClass).getShortName();
        final UrlMappingInfo mappingInfo = mock(UrlMappingInfo.class);
        doReturn("bar").when(mappingInfo).getActionName();
        final Transaction transaction = tracer.startRootTransaction(null).withName("override me");
        try (Scope scope = transaction.activateInScope()) {
            handlerAdapter.handle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), new GrailsControllerUrlMappingInfo(controllerClass, mappingInfo));
        } catch (Exception ignore) {
        }
        transaction.end();
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("Foo#bar");
    }
}
