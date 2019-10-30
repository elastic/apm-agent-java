package co.elastic.apm.agent.mongoclient;

import com.mongodb.connection.Connection;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * {@link Connection#command}
 */
public class ConnectionCommandInstrumentation extends MongoClientInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("com.mongodb.")
            .and(hasSuperType(named("com.mongodb.connection.Connection")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("command")
            .and(isPublic())
            .and(takesArgument(0, is(String.class).or(named("com.mongodb.MongoNamespace"))))
            .and(takesArgument(1, named("org.bson.BsonDocument")));
    }

    @Override
    public Class<?> getAdviceClass() {
        return ConnectionAdvice.class;
    }
}
