/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2021 Elastic and contributors
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
package co.elastic.apm.servlet.tests;

import co.elastic.apm.servlet.AbstractServletContainerIntegrationTest;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.util.Map;
import java.util.Properties;

public class WildFlyRemoteEJBTestApp extends RemoteEJBTestApp {

    @Override
    protected Context getContext(AbstractServletContainerIntegrationTest test) throws NamingException {
        Properties contextProperties = new Properties();
        contextProperties.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
        contextProperties.put(Context.PROVIDER_URL, "remote+" + test.getBaseUrl());
        contextProperties.put(Context.SECURITY_PRINCIPAL, "ejb-user");
        contextProperties.put(Context.SECURITY_CREDENTIALS, "passw0rd");

        return new InitialContext(contextProperties);
    }

    @Override
    public Map<String, String> getAdditionalFilesToBind() {
        return Map.of(new File("src/test/resources/application-users.properties").getAbsolutePath(), "/opt/jboss/wildfly/standalone/configuration/application-users.properties");
    }

    @Override
    protected boolean worksOnImage(String imageName) {
        // the wildfly-remote-ejb plugin supports WildFly 11+ only
        String imageVersion = imageName.substring(imageName.lastIndexOf(':') + 1);
        int majorVersion = Integer.parseInt(imageVersion.substring(0, imageVersion.indexOf('.')));
        return majorVersion >= 11;
    }
}
