package co.elastic.apm.agent.mongoclient;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * {@link com.mongodb.connection.AsyncConnection#commandAsync}
 * {@link com.mongodb.connection.AsyncConnection#queryAsync}
 * This class is Work in progress an not active yet
 */
public class AsyncConnectionInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("com.mongodb.")
            .and(hasSuperType(named("com.mongodb.connection.AsyncConnection")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("commandAsync")
                .or(named("queryAsync"))
            .and(isPublic())
            .and(takesArgument(0, is(String.class).or(named("com.mongodb.MongoNamespace"))))
            .and(takesArgument(1, named("org.bson.BsonDocument")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("mongodb-client");
    }

    @Override
    public Class<?> getAdviceClass() {
        return ConnectionAdvice.class;
    }
}
