package co.elastic.apm.esjavaclient;

public class FooBar {

    private String foo;

    public FooBar() {

    }

    public FooBar(String foo) {
        this.foo = foo;
    }

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }
}
