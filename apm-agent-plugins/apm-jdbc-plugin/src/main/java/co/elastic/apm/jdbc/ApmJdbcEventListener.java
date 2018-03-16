package co.elastic.apm.jdbc;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Span;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.SQLException;

public class ApmJdbcEventListener extends SimpleJdbcEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ApmJdbcEventListener.class);

    private final ElasticApmTracer elasticApmTracer;

    public ApmJdbcEventListener() {
        elasticApmTracer = ElasticApmTracer.get();
    }

    public ApmJdbcEventListener(ElasticApmTracer elasticApmTracer) {
        this.elasticApmTracer = elasticApmTracer;
    }

    @Nullable
    static String getMethod(String sql) {
        if (sql == null) {
            return null;
        }
        // don't allocate objects for the common case
        if (sql.startsWith("SELECT") || sql.startsWith("select")) {
            return "SELECT";
        }
        sql = sql.trim();
        final int indexOfWhitespace = sql.indexOf(' ');
        if (indexOfWhitespace > 0) {
            return sql.substring(0, indexOfWhitespace).toUpperCase();
        } else {
            // for example COMMIT
            return sql.toUpperCase();
        }
    }

    @Override
    public void onAfterGetConnection(ConnectionInformation connectionInformation, SQLException e) {
    }

    @Override
    public void onBeforeAnyExecute(StatementInformation statementInformation) {
        if (elasticApmTracer.currentTransaction() == null) {
            return;
        }
        Span span = elasticApmTracer.startSpan();
        span.setName(getMethod(statementInformation.getStatementQuery()));
        try {
            String dbVendor = getDbVendor(statementInformation.getConnectionInformation().getConnection().getMetaData().getURL());
            span.setType("db." + dbVendor + ".sql");
            span.getContext().getDb()
                .withUser(statementInformation.getConnectionInformation().getConnection().getMetaData().getUserName())
                .withStatement(statementInformation.getStatementQuery())
                .withType("sql");
        } catch (SQLException e) {
            logger.warn("Ignored exception", e);
        }
    }

    String getDbVendor(String url) {
        // jdbc:h2:mem:test
        //     ^
        int indexOfJdbc = url.indexOf("jdbc:") + 5;
        if (indexOfJdbc != -1) {
            // h2:mem:test
            String urlWithoutJdbc = url.substring(indexOfJdbc);
            int indexOfColonAfterVendor = urlWithoutJdbc.indexOf(":");
            if (indexOfColonAfterVendor != -1) {
                // h2
                return urlWithoutJdbc.substring(0, indexOfColonAfterVendor);
            }
        }
        return "unknown";
    }

    @Override
    public void onAfterAnyExecute(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        Span span = elasticApmTracer.currentSpan();
        if (span != null) {
            span.end();
        }
    }

    @Override
    public void onBeforeAnyAddBatch(StatementInformation statementInformation) {
        super.onBeforeAnyAddBatch(statementInformation);
    }

    @Override
    public void onAfterAnyAddBatch(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        super.onAfterAnyAddBatch(statementInformation, timeElapsedNanos, e);
    }
}
