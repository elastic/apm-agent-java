/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.configuration.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PropertyFileConfigurationSourceTest {
	private PropertyFileConfigurationSource source = null;
	private File configFile;

	void writeToFile(String text) throws FileNotFoundException {
		try (PrintWriter out = new PrintWriter(configFile.getAbsolutePath())) {
		    out.println(text);
		}
	}

	@BeforeEach
	void setUp() throws IOException {
		configFile = File.createTempFile("elasticapm-", ".properties");
		configFile.deleteOnExit();
		writeToFile("server_urls=https://old.value.com");
		source = new PropertyFileConfigurationSource(configFile.getAbsolutePath(), 500);
	}

	@Test
	void testReload() throws InterruptedException, FileNotFoundException {
		assertThat(source.getValue("server_urls")).isEqualTo("https://old.value.com");
		writeToFile("server_urls=https://new.value.com");
		Thread.sleep(500);
		assertThat(source.getValue("server_urls")).isEqualTo("https://new.value.com");
	}
}