package co.elastic.apm.agent.jsf;

public class JsfLifecycleRenderInstrumentation extends AbstractJsfLifecycleRenderInstrumentation {
    @Override
    String lifecycleClassName() {
        return "javax.faces.lifecycle.Lifecycle";
    }

    @Override
    String facesContextClassName() {
        return "javax.faces.context.FacesContext";
    }
}
