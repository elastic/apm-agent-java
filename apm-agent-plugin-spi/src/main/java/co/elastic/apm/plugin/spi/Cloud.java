package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface Cloud {

    Cloud withRegion(@Nullable String region);
}
