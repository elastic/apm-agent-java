package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;

public class Service implements Recyclable {

    @Nullable
    private String type;

    @Nullable
    private String name;

    private final Service origin;
    private final Service target;

    Service() {
        this.origin = new Service();
        this.target = new Service();
    }

    public Service withType(String type) {
        this.type = type;
        return this;
    }

    @Nullable
    public String getType(){
        return type;
    }

    public Service withName(String name) {
        this.name = name;
        return this;
    }

    @Nullable
    public String getName(){
        return name;
    }

    @Override
    public void resetState() {
        type = null;
        name = null;
        origin.resetState();
        target.resetState();
    }

}
