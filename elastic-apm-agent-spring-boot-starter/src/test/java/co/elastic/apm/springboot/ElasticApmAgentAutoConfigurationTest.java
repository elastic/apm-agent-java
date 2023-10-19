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

import static org.mockito.Mockito.only;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedStatic.Verification;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.context.ApplicationContext;

import co.elastic.apm.attach.ElasticApmAttacher;

class ElasticApmAgentAutoConfigurationTest {
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	void testAttach() {
		final String PROPERTY_KEY = "testkey1";
		final String PROPERTY_VALUE = "testvalue1";
		try(MockedStatic<ElasticApmAttacher> elasticApmAttacher =  Mockito.mockStatic(ElasticApmAttacher.class)){
			contextRunner.withPropertyValues("elastic.test=taco", "elastic.apm." + PROPERTY_KEY + "=" + PROPERTY_VALUE).withUserConfiguration(TestApplication.class).run(new ContextConsumer<ApplicationContext>() {
				@Override
				public void accept(ApplicationContext context) throws Throwable {
					Verification verification = new Verification() {
						@Override
						public void apply() throws Throwable {
							Map<String, String> configuration = new HashMap<>();
							configuration.put("application_packages", TestApplication.class.getPackageName());
							configuration.put(PROPERTY_KEY, PROPERTY_VALUE);
							ElasticApmAttacher.attach(configuration);
						}
					};
					elasticApmAttacher.verify(verification, only());
				}
			});
		}
	}

	@SpringBootApplication
	public static class TestApplication {
	}
}
