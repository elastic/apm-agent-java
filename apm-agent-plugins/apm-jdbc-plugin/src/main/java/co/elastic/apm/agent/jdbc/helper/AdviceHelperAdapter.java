package co.elastic.apm.agent.jdbc.helper;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.RegisterMethodHandle;
import co.elastic.apm.agent.impl.transaction.Span;

import javax.annotation.Nullable;
import java.sql.Statement;

public class AdviceHelperAdapter {

    private static final JdbcHelper INSTANCE = new JdbcHelper(ElasticApmInstrumentation.tracer);

    @RegisterMethodHandle
    public static void mapStatementToSql(Statement statement, String sql) {
        INSTANCE.mapStatementToSql(statement, sql);
    }

    @RegisterMethodHandle
    public static void clearInternalStorage() {
        INSTANCE.clearInternalStorage();
    }

    @Nullable
    @RegisterMethodHandle
    public static Span createJdbcSpanLookupSql(Statement statement, boolean preparedStatement) {
        return INSTANCE.createJdbcSpanLookupSql(statement, preparedStatement);
    }

    @Nullable
    @RegisterMethodHandle
    public static Span createJdbcSpan(@Nullable String sql, Statement statement, boolean preparedStatement) {
        return INSTANCE.createJdbcSpan(sql, statement, preparedStatement);
    }

    @RegisterMethodHandle
    public static void onAfterExecuteQuery(Statement statement, @Nullable Span span, @Nullable Throwable t) {
        INSTANCE.onAfterExecuteQuery(statement, span, t);
    }

    @RegisterMethodHandle
    public static void onAfterExecuteBatch(@Nullable Span span, Throwable t, @Nullable Object returnValue) {
        INSTANCE.onAfterExecuteBatch(span, t, returnValue);
    }

    @RegisterMethodHandle
    public static void onAfterPreparedStatementExecuteQuery(Statement statement, @Nullable Span span, @Nullable Throwable t) {
        INSTANCE.onAfterPreparedStatementExecuteQuery(statement, span, t);
    }

    @RegisterMethodHandle
    public static void onAfter(@Nullable Span span, @Nullable Throwable t, long affectedCount) {
        INSTANCE.onAfter(span, t, affectedCount);
    }
}
