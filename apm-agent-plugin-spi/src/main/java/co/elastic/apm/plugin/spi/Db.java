package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.nio.CharBuffer;

public interface Db {

    Db withInstance(@Nullable String instance);

    Db withType(@Nullable String type);

    Db withStatement(@Nullable String statement);

    Db withUser(@Nullable String user);

    Db withAffectedRowsCount(long returnValue);

    CharBuffer withStatementBuffer();
}
