package co.elastic.apm.servlet;

import co.elastic.apm.intake.ProcessFactory;
import co.elastic.apm.intake.ServiceFactory;
import co.elastic.apm.intake.SystemFactory;
import co.elastic.apm.intake.transactions.Transaction;
import co.elastic.apm.report.ApmServerHttpPayloadSender;
import co.elastic.apm.report.Reporter;
import co.elastic.apm.report.serialize.JacksonPayloadSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ApmFilter implements Filter {

    private Reporter reporter;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext servletContext = filterConfig.getServletContext();
        final ObjectMapper objectMapper = new ObjectMapper();
        reporter = new Reporter(new ServiceFactory().createService("Servlet API",
            String.format("%d.%d", servletContext.getMajorVersion(), servletContext.getMinorVersion())),
            new ProcessFactory().getProcessInformation(),
            new SystemFactory().getSystem(),
            // TODO configuration
            new ApmServerHttpPayloadSender(new OkHttpClient(), "http://localhost:8200", new JacksonPayloadSerializer(objectMapper)), true);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            long start = System.nanoTime();
            try {
                filterChain.doFilter(request, response);
            } finally {
                Transaction transaction = ServletTransactionFactory.createTransaction(httpRequest, (HttpServletResponse) response, System.nanoTime() - start);
                reporter.report(transaction);
            }
        }
    }

    @Override
    public void destroy() {
    }
}
