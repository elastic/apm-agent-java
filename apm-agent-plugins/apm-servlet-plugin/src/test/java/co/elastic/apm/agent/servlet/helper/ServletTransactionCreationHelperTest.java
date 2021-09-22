package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServletTransactionCreationHelperTest extends AbstractInstrumentationTest {

    private WebConfiguration webConfig;
    private ServletTransactionCreationHelper helper;

    @BeforeEach
    void setUp() {
        webConfig = config.getConfig(WebConfiguration.class);
        helper = new ServletTransactionCreationHelper(tracer);
    }

    @Test
    void requestPathNotIgnored() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/not-ignored");

        assertThat(helper.isExcluded(request)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(
        delimiterString = " ", value = {
        "/ignored/from/prefix /ignored*",
        "/ignored/with-suffix.js *.js",
        "/ignored/with/term *with*"})
    void requestPathIgnored(String path, String ignoreExpr) {
        when(webConfig.getIgnoreUrls())
            .thenReturn(parseWildcard(ignoreExpr));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);

        assertThat(helper.isExcluded(request))
            .describedAs("request with path '%s' should be ignored", path)
            .isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " ", value = {
        "anderson and*",
        "anderson smith,anderson"
    })
    void requestUserAgentIgnored(String userAgent, String ignoreExpr) {
        when(webConfig.getIgnoreUserAgents())
            .thenReturn(parseWildcard(ignoreExpr));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/request/path");


        ArgumentCaptor<String> headerName = ArgumentCaptor.forClass(String.class);
        when(request.getHeader(headerName.capture())).thenReturn(userAgent);

        doReturn(userAgent).when(request).getHeader(anyString());

        assertThat(helper.isExcluded(request))
            .describedAs("request with user-agent '%s' should be ignored", userAgent)
            .isTrue();

        verify(request).getHeader(headerName.capture());
        assertThat(headerName.getValue().toLowerCase(Locale.ROOT)).isEqualTo("user-agent");
    }

    private static List<WildcardMatcher> parseWildcard(String expr){
        return Stream.of(expr.split(","))
            .map(WildcardMatcher::valueOf)
            .collect(Collectors.toList());
    }

    @Test
    void safeGetClassLoader(){
        assertThat(helper.getClassloader(null)).isNull();

        ServletContext servletContext = mock(ServletContext.class);
        doThrow(UnsupportedOperationException.class).when(servletContext).getClassLoader();
        assertThat(helper.getClassloader(servletContext)).isNull();

        servletContext = mock(ServletContext.class);
        ClassLoader cl = mock(ClassLoader.class);
        when(servletContext.getClassLoader()).thenReturn(cl);
        assertThat(helper.getClassloader(servletContext)).isSameAs(cl);
    }
}
