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
package co.elastic.apm.agent.log4j2;

import co.elastic.apm.agent.logging.LogEcsReformatting;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.io.Serializable;

/**
 * This is not really an {@link org.apache.logging.log4j.core.Appender Appender}, it is just used in order to hold the
 * {@link co.elastic.logging.log4j2.EcsLayout EcsLayout} mapped to the original appender. This way, we can use the same map
 * between the original appender and the corresponding ECS-formatting counterpart, regardless of the configured
 * {@link LogEcsReformatting} - while {@link LogEcsReformatting#SHADE SHADE} and {@link LogEcsReformatting#REPLACE REPLACE}
 * require a real ECS appender, {@link LogEcsReformatting#OVERRIDE OVERRIDE} requires only an
 * {@link co.elastic.logging.log4j2.EcsLayout EcsLayout}.
 */
public class EcsLayoutHolder extends AbstractAppender {

    public EcsLayoutHolder(String name, Layout<? extends Serializable> layout) {
        super(name, null, layout);
    }

    @Override
    public void append(LogEvent event) {
        // this Appender should not append anything, it is just used as an EcsLayout holder
    }
}
