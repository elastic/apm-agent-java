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
package co.elastic.apm.agent.springwebmvc.template.jade4j;

import de.neuland.jade4j.JadeConfiguration;
import de.neuland.jade4j.spring.template.SpringTemplateLoader;
import de.neuland.jade4j.spring.view.JadeViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.ServletContext;

@Configuration
@EnableWebMvc
public class Jade4jTemplateConfiguration {

    @Bean
    public SpringTemplateLoader templateLoader(ServletContext servContext) {
        SpringTemplateLoader templateLoader = new SpringTemplateLoader();
        templateLoader.setBasePath("/jade/");
        templateLoader.setSuffix(".jade");
        templateLoader.setServletContext(servContext);
        templateLoader.init();
        return templateLoader;
    }

    @Bean
    public JadeConfiguration jadeConfiguration(SpringTemplateLoader loader) {
        JadeConfiguration configuration
            = new JadeConfiguration();
        configuration.setCaching(false);
        configuration.setTemplateLoader(loader);
        return configuration;
    }

    @Bean
    public ViewResolver viewResolver(JadeConfiguration config) {
        JadeViewResolver viewResolver = new JadeViewResolver();
        viewResolver.setConfiguration(config);
        return viewResolver;
    }
}
