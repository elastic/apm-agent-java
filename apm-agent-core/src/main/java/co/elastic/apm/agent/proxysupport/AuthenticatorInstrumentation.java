package co.elastic.apm.agent.proxysupport;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.sdk.state.GlobalThreadLocal;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class AuthenticatorInstrumentation extends ElasticApmInstrumentation {

    private static final CallDepth callDepth = CallDepth.get(AuthenticatorInstrumentation.class);
    private static final GlobalThreadLocal<Boolean> useFallback = GlobalThreadLocal.get(AuthenticatorInstrumentation.class, "fallback");
    private static final AgentAuthenticator agentAuthenticator = new AgentAuthenticator();

    public static void toggleFallback(boolean enable) {
        useFallback.set(enable);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("java.net.Authenticator");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("requestPasswordAuthentication");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        // TODO is it required ? apart from bug workaround on the feature there is no good reason to disable it
        return Collections.singleton("jdk-proxy-auth");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter() {
        callDepth.increment();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    @AssignTo.Return
    @Nullable
    public static PasswordAuthentication onExit(@Advice.Thrown @Nullable Throwable thrown,
                                                @Advice.Return @Nullable PasswordAuthentication rv,
                                                @Advice.AllArguments Object[] arguments) {

        if (thrown != null || rv != null || callDepth.isNestedCallAndDecrement()) {
            return rv;
        }

        if (!Boolean.TRUE.equals(useFallback.get())) {
            return null;
        }

        switch (arguments.length) {
            case 5:
                // requestPasswordAuthentication( InetAddress addr, int port, String protocol, String prompt, String scheme)
                return passwordAuthenticationFallback((String) arguments[2], null);
            case 6:
                // requestPasswordAuthentication( String host, InetAddress addr, int port, String protocol, String prompt, String scheme) {
                return passwordAuthenticationFallback((String) arguments[3], null);
            case 8:
                // requestPasswordAuthentication( String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                return passwordAuthenticationFallback((String) arguments[3], (Authenticator.RequestorType) arguments[7]);
            case 9:
                // requestPasswordAuthentication( Authenticator authenticator, String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                return passwordAuthenticationFallback((String) arguments[4], (Authenticator.RequestorType) arguments[8]);
            default:
                return null;
        }
    }

    @Nullable
    public static PasswordAuthentication passwordAuthenticationFallback(String protocol,
                                                                        @Nullable Authenticator.RequestorType reqType) {

        if (reqType != null && reqType != Authenticator.RequestorType.PROXY) {
            // not a proxy request
            return null;
        }

        String user = System.getProperty(protocol + ".proxyUser");
        String password = System.getProperty(protocol + ".proxyPassword");

        if (user == null || password == null) {
            return null;
        }

        return new PasswordAuthentication(user, password.toCharArray());
    }


    /*
    // -> they don't delegate to each other, thus we have to instrument them all

public static PasswordAuthentication requestPasswordAuthentication( InetAddress addr, int port, String protocol, String prompt, String scheme)
public static PasswordAuthentication requestPasswordAuthentication( String host, InetAddress addr, int port, String protocol, String prompt, String scheme) {
public static PasswordAuthentication requestPasswordAuthentication( String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
public static PasswordAuthentication requestPasswordAuthentication( Authenticator authenticator, String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {

// -> when there is already an instance set, this one is called
public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
     */

    static {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                String protocol = getRequestingProtocol();
                final String user = System.getProperty(protocol + ".proxyUser");
                final String password = System.getProperty(protocol + ".proxyPassword");

                if (getRequestorType() != RequestorType.PROXY) {
                    return null;
                }
                if (user == null || password == null) {
                    return null;
                }

                // extra safety optinal checks: limit credentials usage to known services

                URL requestingURL = getRequestingURL();
                if (requestingURL != null) {
                    // TODO : optional check against request URL, if provided
                }

                return new PasswordAuthentication(user, password.toCharArray());
            }
        });
    }

    public static class AgentAuthenticator extends Authenticator {


        @Nullable
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            String protocol = getRequestingProtocol();
            final String user = System.getProperty(protocol + ".proxyUser");
            final String password = System.getProperty(protocol + ".proxyPassword");

            if (getRequestorType() != RequestorType.PROXY) {
                return null;
            }
            if (user == null || password == null) {
                return null;
            }

            // extra safety checks: limit credentials usage to known services

            URL requestingURL = getRequestingURL();
            if (requestingURL != null) {
                // TODO : optional check against request URL, if provided
            }




            return new PasswordAuthentication(user, password.toCharArray());
        }
    }
}
