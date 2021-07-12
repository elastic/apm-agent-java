package co.elastic.apm.agent.servlet;

import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;

public abstract class JavaxServletVersionInstrumentation extends CommonServletVersionInstrumentation {

    public static class JavaxInit extends Init {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @SuppressWarnings("Duplicates") // duplication is fine here as it allows to inline code
        public static void onEnter(@Advice.Argument(0) @Nullable ServletConfig servletConfig) {
            logServletVersion(() -> JavaxUtil.getInfoFromServletContext(servletConfig));
        }

        @Override
        public String servletVersionTypeMatcherClassName() {
            return getServletVersionTypeMatcherClassName();
        }

        @Override
        public String rootClassNameThatClassloaderCanLoad() {
            return getRootClassNameThatClassloaderCanLoad();
        }

        @Override
        String initMethodArgumentClassName() {
            return "javax.servlet.ServletConfig";
        }
    }

    public static class JavaxService extends Service {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.This Servlet servlet) {
            logServletVersion(() -> JavaxUtil.getInfoFromServletContext(servlet.getServletConfig()));
        }

        @Override
        public String rootClassNameThatClassloaderCanLoad() {
            return getRootClassNameThatClassloaderCanLoad();
        }

        @Override
        String[] getServiceMethodArgumentClassNames() {
            return new String[]{"javax.servlet.ServletRequest", "javax.servlet.ServletResponse"};
        }

        @Override
        public String servletVersionTypeMatcherClassName() {
            return getServletVersionTypeMatcherClassName();
        }
    }

    private static String getServletVersionTypeMatcherClassName() {
        return "javax.servlet.Servlet";
    }

    private static String getRootClassNameThatClassloaderCanLoad() {
        return "javax.servlet.AsyncContext";
    }
}
