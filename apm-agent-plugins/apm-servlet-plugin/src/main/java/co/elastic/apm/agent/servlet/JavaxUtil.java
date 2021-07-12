package co.elastic.apm.agent.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import javax.annotation.Nullable;

public final class JavaxUtil {

    private JavaxUtil() {}

    @Nullable
    public static Object[] getInfoFromServletContext(@Nullable ServletConfig servletConfig) {
        if (servletConfig != null) {
            ServletContext servletContext = servletConfig.getServletContext();
            if (null != servletContext) {
                return new Object[]{servletContext.getMajorVersion(), servletContext.getMinorVersion(), servletContext.getServerInfo()};
            }
        }
        return null;
    }
}
