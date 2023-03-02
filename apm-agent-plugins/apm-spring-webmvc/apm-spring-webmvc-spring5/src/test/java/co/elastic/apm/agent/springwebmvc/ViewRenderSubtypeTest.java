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
package co.elastic.apm.agent.springwebmvc;

import de.neuland.jade4j.spring.view.JadeView;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.view.InternalResourceView;
import org.springframework.web.servlet.view.freemarker.FreeMarkerView;
import org.springframework.web.servlet.view.groovy.GroovyMarkupView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;
import org.thymeleaf.spring5.view.ThymeleafView;

import static co.elastic.apm.agent.springwebmvc.ViewRenderInstrumentation.ViewRenderAdviceService.getSubtype;
import static org.assertj.core.api.Assertions.assertThat;

class ViewRenderSubtypeTest {

    @Test
    void testGetUnknownSubtype() {
        assertThat(getSubtype("foo.Foo")).isEqualTo("Foo");
        assertThat(getSubtype("foo.FooViewBar")).isEqualTo("Foo");
        assertThat(getSubtype("FooView")).isEqualTo("Foo");
        assertThat(getSubtype("foo.FooView.Bar")).isEqualTo("Bar");
        assertThat(getSubtype("")).isEqualTo("");
        assertThat(getSubtype(".")).isEqualTo("");
    }

    @Test
    void testGetUnknownSubtypes() {
        assertThat(getSubtype(GroovyMarkupView.class.getName())).isEqualTo("GroovyMarkup");
        assertThat(getSubtype(FreeMarkerView.class.getName())).isEqualTo("FreeMarker");
        assertThat(getSubtype(MappingJackson2JsonView.class.getName())).isEqualTo("MappingJackson2Json");
        assertThat(getSubtype(JadeView.class.getName())).isEqualTo("Jade");
        assertThat(getSubtype(InternalResourceView.class.getName())).isEqualTo("InternalResource");
        assertThat(getSubtype(ThymeleafView.class.getName())).isEqualTo("Thymeleaf");
    }
}
