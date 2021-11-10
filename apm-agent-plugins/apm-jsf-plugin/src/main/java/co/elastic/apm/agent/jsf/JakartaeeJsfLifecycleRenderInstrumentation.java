package co.elastic.apm.agent.jsf;

public class JakartaeeJsfLifecycleRenderInstrumentation extends AbstractJsfLifecycleRenderInstrumentation {
    @Override
    String lifecycleClassName() {
        return "jakarta.faces.lifecycle.Lifecycle";
    }

    @Override
    String facesContextClassName() {
        return "jakarta.faces.context.FacesContext";
    }
}
