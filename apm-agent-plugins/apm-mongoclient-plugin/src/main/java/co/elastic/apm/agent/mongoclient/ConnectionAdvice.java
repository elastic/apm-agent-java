package co.elastic.apm.agent.mongoclient;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Span;
import com.mongodb.MongoNamespace;
import net.bytebuddy.asm.Advice;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class ConnectionAdvice {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(ConnectionAdvice.class);

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Span onEnter(@Advice.Argument(0) Object databaseOrMongoNamespace, @Advice.Argument(1) BsonDocument command) {
        Span span = ElasticApmInstrumentation.createExitSpan();

        if (span == null) {
            return null;
        }

        String database = "";
        String collection = null;
        if (databaseOrMongoNamespace instanceof String) {
            database = (String) databaseOrMongoNamespace;
        } else if (databaseOrMongoNamespace instanceof MongoNamespace) {
            MongoNamespace namespace = (MongoNamespace) databaseOrMongoNamespace;
            database = namespace.getDatabaseName();
            collection = namespace.getCollectionName();
        }

        span.withType("db").withSubtype("mongodb").withAction("query")
            .appendToName(database).getContext().getDb().withType("mongodb");
        try {
            String cmd =
                command.containsKey("find")   ? "find"   :
                command.containsKey("insert") ? "insert" :
                command.containsKey("count")  ? "count"  :
                command.containsKey("drop")   ? "drop"   :
                command.containsKey("update") ? "update" :
                command.containsKey("delete") ? "delete" :
                command.containsKey("create") ? "create" :
                command.keySet().iterator().next();
            if (collection == null) {
                collection = command.getString(cmd).getValue();
            }
            span.appendToName(".").appendToName(collection).appendToName(".").appendToName(cmd);
        } catch (RuntimeException e) {
            logger.error("Exception while determining MongoDB command and collection", e);
        }
        span.activate();
        return span;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
    public static void onExit(@Nullable @Advice.Enter Span span, @Advice.Thrown Throwable thrown, @Advice.Origin("#m") String methodName) {
        if (span != null) {
            span.deactivate().captureException(thrown);
            if (!methodName.endsWith("Async")) {
                span.end();
            }
        }
    }

}
