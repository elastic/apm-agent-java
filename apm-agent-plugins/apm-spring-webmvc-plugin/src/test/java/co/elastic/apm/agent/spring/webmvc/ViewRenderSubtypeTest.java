package co.elastic.apm.agent.spring.webmvc;

import de.neuland.jade4j.spring.view.JadeView;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.view.InternalResourceView;
import org.springframework.web.servlet.view.freemarker.FreeMarkerView;
import org.springframework.web.servlet.view.groovy.GroovyMarkupView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;
import org.thymeleaf.spring4.view.ThymeleafView;

import static co.elastic.apm.agent.spring.webmvc.ViewRenderInstrumentation.ViewRenderAdviceService.getSubtype;
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
