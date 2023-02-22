package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public class EmptyPotentiallyMultiValuedMap implements PotentiallyMultiValuedMap {

    public static final PotentiallyMultiValuedMap INSTANCE = new EmptyPotentiallyMultiValuedMap();

    private EmptyPotentiallyMultiValuedMap() {
    }

    @Override
    public void add(String key, String value) {
    }

    @Nullable
    @Override
    public String getFirst(String key) {
        return null;
    }

    @Nullable
    @Override
    public Object get(String key) {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean containsIgnoreCase(String key) {
        return false;
    }
}
