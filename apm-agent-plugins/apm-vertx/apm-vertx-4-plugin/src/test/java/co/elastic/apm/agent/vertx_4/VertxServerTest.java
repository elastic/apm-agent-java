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
package co.elastic.apm.agent.vertx_4;

import co.elastic.apm.agent.vertx.helper.CommonVertxWebTest;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;


public class VertxServerTest extends CommonVertxWebTest {

    @Override
    protected Handler<RoutingContext> getDefaultHandlerImpl() {
        return routingContext -> routingContext.response().end(DEFAULT_RESPONSE_BODY);
    }

    @Override
    protected boolean useSSL() {
        return false;
    }

    @Override
    protected String expectedVertxVersion() {
        return "4.0";
    }
}
