package co.elastic.apm.objectpool;

public interface RecyclableObjectFactory<T extends Recyclable> {

    T createInstance();
}
