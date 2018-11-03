package co.elastic.apm.objectpool.impl;

import co.elastic.apm.objectpool.Recyclable;

public interface Resetter<T> {

    void recycle(T object);

    class ForRecyclable<T extends Recyclable> implements Resetter<T> {
        private static ForRecyclable INSTANCE = new ForRecyclable();

        public static <T extends Recyclable> Resetter<T> get() {
            return INSTANCE;
        }

        @Override
        public void recycle(Recyclable object) {
            object.resetState();
        }
    }

}
