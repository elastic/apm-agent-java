package co.elastic.apm.plugin.api;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class TransactionInstrumentation extends ElasticApmInstrumentation {

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public TransactionInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.api.TransactionImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class SetNameInstrumentation extends TransactionInstrumentation {
        public SetNameInstrumentation() {
            super(named("setName"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void setName(@Advice.FieldValue(value = "transaction", typing = Assigner.Typing.DYNAMIC) Transaction transaction,
                                   @Advice.Argument(0) String name) {
            transaction.setName(name);
        }
    }

    public static class SetTypeInstrumentation extends TransactionInstrumentation {
        public SetTypeInstrumentation() {
            super(named("setType"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void setType(@Advice.FieldValue(value = "transaction", typing = Assigner.Typing.DYNAMIC) Transaction transaction,
                                   @Advice.Argument(0) String type) {
            transaction.setType(type);
        }
    }

    public static class AddTagInstrumentation extends TransactionInstrumentation {
        public AddTagInstrumentation() {
            super(named("addTag"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void addTag(@Advice.FieldValue(value = "transaction", typing = Assigner.Typing.DYNAMIC) Transaction transaction,
                                  @Advice.Argument(0) String key, @Advice.Argument(1) String value) {
            transaction.addTag(key, value);
        }
    }

    public static class SetUserInstrumentation extends TransactionInstrumentation {
        public SetUserInstrumentation() {
            super(named("setUser"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void setUser(@Advice.FieldValue(value = "transaction", typing = Assigner.Typing.DYNAMIC) Transaction transaction,
                                   @Advice.Argument(0) String id, @Advice.Argument(1) String email, @Advice.Argument(2) String username) {
            transaction.setUser(id, email, username);
        }
    }

    public static class EndInstrumentation extends TransactionInstrumentation {
        public EndInstrumentation() {
            super(named("end"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void end(@Advice.FieldValue(value = "transaction", typing = Assigner.Typing.DYNAMIC) Transaction transaction) {
            transaction.end();
        }
    }
}
