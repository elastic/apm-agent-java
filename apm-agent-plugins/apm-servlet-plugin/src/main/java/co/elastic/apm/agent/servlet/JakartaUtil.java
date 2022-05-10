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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import javax.annotation.Nullable;

@SuppressWarnings("DuplicatedCode")
public final class JakartaUtil {

    private static final Logger logger = LoggerFactory.getLogger(JakartaUtil.class);

    private JakartaUtil() {}

    @Nullable
    public static Object[] getInfoFromServletContext(@Nullable ServletConfig servletConfig) {
        if (servletConfig != null) {
            try {
                ServletContext servletContext = servletConfig.getServletContext();
                if (null != servletContext) {
                    return new Object[]{servletContext.getMajorVersion(), servletContext.getMinorVersion(), servletContext.getServerInfo()};
                }
            } catch (Exception e) {
                String message = String.format("Failed obtain ServletContext from ServletConfig %s. Stack trace printed in debug level",
                    servletConfig);
                if (logger.isDebugEnabled()) {
                    logger.debug(message, e);
                } else {
                    logger.info(message);
                }
            }
        }
        return null;
    }
}
