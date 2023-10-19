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
package co.elastic.apm.springboot;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Elastic APM.
 *
 * Initializes Elastic APM with configuration specified in {@code elastic.apm} configuration properties.
 *
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(co.elastic.apm.attach.ElasticApmAttacher.class)
@EnableConfigurationProperties(ElasticApmProperties.class)
public class ElasticApmAgentAutoConfiguration {
	@Bean
	static ElasticApmAttacher elasticApmAttacher() {
		return new ElasticApmAttacher();
	}

	/**
	 * The Elastic APM agent should be attached as early as possible.
	 * {@link BeanFactoryPostProcessor} is the earliest available option that has the necessary dependencies to allow proper configuration of the agent.
	 */
	private static class ElasticApmAttacher implements BeanFactoryPostProcessor, EnvironmentAware {
		private Environment environment;

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			Assert.notNull(environment, "environment cannot be null");

			// Beans are not fully initialized yet so beanFactory.getBean(ElasticApmProperties.class) would return an uninitialized bean without any properties set
			ElasticApmProperties elasticApmProperties = Binder.get(environment).bind("elastic", ElasticApmProperties.class).get();

			final Map<String, String> configuration = new HashMap<>(elasticApmProperties.getApm());
			// If the application follows the Spring Boot best practice of having a @SpringBootApplication annotated class in the parent package of project,
			// then Elastic APM can be configured based on that class.
			// See: https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.using-the-springbootapplication-annotation
			Map<String, Object> springBootApplications = beanFactory.getBeansWithAnnotation(SpringBootApplication.class);
			if(springBootApplications.size() == 1) {
				Object springBootApplicationBean = springBootApplications.values().iterator().next();
				if ( ! configuration.containsKey("service_name")) {
					String implementationTitle = springBootApplicationBean.getClass().getPackage().getImplementationTitle();
					if (implementationTitle != null) {
						configuration.put("service_name", implementationTitle);
					}
				}
				if ( ! configuration.containsKey("service_version")) {
					String implementationVersion = springBootApplicationBean.getClass().getPackage().getImplementationVersion();
					if (implementationVersion != null) {
						configuration.putIfAbsent("service_version", implementationVersion);
					}
				}
				if ( ! configuration.containsKey("application_packages")) {
					configuration.put("application_packages", springBootApplicationBean.getClass().getPackageName());
				}
			}
			co.elastic.apm.attach.ElasticApmAttacher.attach(configuration);
		}

		@Override
		public void setEnvironment(Environment environment) {
			this.environment = environment;
		}
	}
}
