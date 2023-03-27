package co.elastic.apm.agent.tracer.reference;

public interface ReferenceCounted {

    void incrementReferences();

    void decrementReferences();
}
