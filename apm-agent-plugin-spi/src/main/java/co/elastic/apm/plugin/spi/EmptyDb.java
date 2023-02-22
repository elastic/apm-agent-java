package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.nio.CharBuffer;

public class EmptyDb implements Db {

    public static final Db INSTANCE = new EmptyDb();

    private EmptyDb() {
    }

    @Override
    public Db withInstance(@Nullable String instance) {
        return this;
    }

    @Override
    public Db withType(@Nullable String type) {
        return this;
    }

    @Override
    public Db withStatement(@Nullable String statement) {
        return this;
    }

    @Override
    public Db withUser(@Nullable String user) {
        return this;
    }

    @Override
    public Db withAffectedRowsCount(long returnValue) {
        return this;
    }

    @Nullable
    @Override
    public CharBuffer getStatementBuffer() {
        return null;
    }

    @Override
    @Nullable
    public CharBuffer withStatementBuffer() {
        return null;
    }
}
