package co.elastic.apm.agent.servlet;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public abstract class JakartaServletVersionInstrumentation extends CommonServletVersionInstrumentation {

    public static class JakartaInit extends Init {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @SuppressWarnings("Duplicates") // duplication is fine here as it allows to inline code
        public static void onEnter(@Advice.Argument(0) @Nullable ServletConfig servletConfig) {
            logServletVersion(() -> JakartaUtil.getInfoFromServletContext(servletConfig));
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
            return "jakarta.servlet.ServletConfig";
        }
    }

    public static class JakartaService extends Service {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.This Servlet servlet) {
            logServletVersion(() -> JakartaUtil.getInfoFromServletContext(servlet.getServletConfig()));
        }

        @Override
        public String rootClassNameThatClassloaderCanLoad() {
            return getRootClassNameThatClassloaderCanLoad();
        }

        @Override
        String[] getServiceMethodArgumentClassNames() {
            return new String[]{"jakarta.servlet.ServletRequest", "jakarta.servlet.ServletResponse"};
        }

        @Override
        public String servletVersionTypeMatcherClassName() {
            return getServletVersionTypeMatcherClassName();
        }
    }

    private static String getServletVersionTypeMatcherClassName() {
        return "jakarta.servlet.Servlet";
    }

    private static String getRootClassNameThatClassloaderCanLoad() {
        return "jakarta.servlet.AsyncContext";
    }
}
