/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakSet;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.Permission;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

class TestSecurityManager extends SecurityManager {

    private static final AtomicBoolean enabled = new AtomicBoolean(false);

    private TestSecurityManager() {
    }

    public static void enable(){
        enabled.set(false);
        System.setSecurityManager(new TestSecurityManager());
        enabled.set(true);
    }

    public static void disable(){
        enabled.set(false);
        System.setSecurityManager(null);
    }

    @Override
    public void checkPermission(Permission perm) {
        if (!enabled.get()) {
            return;
        }
        checkPrivileged();
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        if (!enabled.get()) {
            return;
        }
        checkPrivileged();
    }

    private static final ThreadLocal<Boolean> nestedCall = new ThreadLocal<>();

    private static final WeakSet<ProtectionDomain> allowedProtectionDomains = WeakConcurrent.buildSet();

    private static final Path cwd = Paths.get(".");

    private static boolean isAgentCode(StackWalker.StackFrame stackFrame) {
        return stackFrame.getClassName().startsWith("co.elastic.apm.");
    }

    private static boolean isAllowedCode(StackWalker.StackFrame stackFrame){
        ProtectionDomain protectionDomain = stackFrame.getDeclaringClass().getProtectionDomain();
        CodeSource codeSource = protectionDomain.getCodeSource();
        if (codeSource == null) {
            // always null for JDK classes
            return true;
        }

        if (allowedProtectionDomains.contains(protectionDomain)) {
            return true;
        }

        return false;
    }

    private static boolean isTestCode(StackWalker.StackFrame stackFrame) {

        ProtectionDomain protectionDomain = stackFrame.getDeclaringClass().getProtectionDomain();
        CodeSource codeSource = protectionDomain.getCodeSource();

        URL location = codeSource.getLocation();
        // simple but efficient heuristic to find test code within 'test-classes' folder which is test code
        if (location != null && location.getProtocol().equals("file")) {

            String file = location.getFile();

            // easy case: test classes in 'test-classes' folder
            boolean isAllowed = file.contains("test-classes"); // test classes
            isAllowed |= file.endsWith("-test.jar"); // packaged tests
            if (!isAllowed && file.endsWith(".jar")) {
                isAllowed = !Paths.get(file).toAbsolutePath().startsWith(cwd);
            }

            if (isAllowed) {
                allowedProtectionDomains.add(protectionDomain);
            }
            return isAllowed;
        } else {
            return true;
        }
    }

    private static final WeakSet<ProtectionDomain> nonAgentCode = WeakConcurrent.buildSet();
    private static final WeakSet<ProtectionDomain> agentTestCode = WeakConcurrent.buildSet();
    private static final WeakSet<ProtectionDomain> agentProductionCode = WeakConcurrent.buildSet();

    private static boolean isAgentCode(Class<?> declaringClass, boolean includeTest) {
        if(declaringClass.getName().contains("$Mockito")){
            return false;
        }
        return isAgentCode(declaringClass.getProtectionDomain(), includeTest);
    }

    private static boolean isAgentCode(ProtectionDomain protectionDomain, boolean includeTest) {
        CodeSource codeSource = protectionDomain.getCodeSource();

        if (codeSource == null || nonAgentCode.contains(protectionDomain)) {
            return false;
        }
        if (agentTestCode.contains(protectionDomain) && includeTest) {
            return true;
        }
        if (agentProductionCode.contains(protectionDomain)) {
            return true;
        }

        boolean isAgentCode;
        boolean isTestCode;

        URL location = codeSource.getLocation();

        // simple but efficient heuristic to find test code within 'test-classes' folder which is test code
        if (location != null && location.getProtocol().equals("file")) {

            Path filePath = Paths.get(location.getPath());
            String fileName = filePath.getFileName().toString();

            if (fileName.endsWith("test-classes") || fileName.endsWith("SNAPSHOT-test.jar")) {
                isAgentCode = true;
                isTestCode = true;
            } else if (fileName.endsWith("classes") || fileName.endsWith("SNAPSHOT.jar")) {
                isAgentCode = true;
                isTestCode = false;
            } else {
                // on release, packaged agent code (production & test) will fit in this case
                // this is not an issue as long as test is mostly executed on SNAPSHOTs
                isAgentCode = false;
                isTestCode = false;
            }

            if (!isAgentCode) {
                nonAgentCode.add(protectionDomain);
                return false;
            }

            if (isTestCode) {
                agentTestCode.add(protectionDomain);
                return includeTest;
            } else {
                agentProductionCode.add(protectionDomain);
                return true;
            }
        }

        return false;
    }

    private static boolean isPrivileged(StackWalker.StackFrame stackFrame) {
        return stackFrame.getClassName().equals("java.security.AccessController") && stackFrame.getMethodName().equals("doPrivileged");
    }

    private static void checkPrivileged() {
        if (nestedCall.get() == Boolean.TRUE) {
            return;
        }

        try {
            nestedCall.set(Boolean.TRUE);

            List<StackWalker.StackFrame> stackFrames = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(stream -> stream.filter(stackFrame -> isPrivileged(stackFrame) || isAgentCode(stackFrame.getDeclaringClass(), true))
                    .collect(Collectors.toList()));


            if (stackFrames.isEmpty()) {
                // agent code not involved, nothing to check
                return;
            }

            for (StackWalker.StackFrame stackFrame: stackFrames) {
                if (isPrivileged(stackFrame)) {
                    return;
                }
            }

            for (StackWalker.StackFrame stackFrame : stackFrames) {
                if (isAgentCode(stackFrame.getDeclaringClass(), false)) {
                    SecurityException securityException = new SecurityException(String.format("missing privileged action in %s", stackFrame));
                    securityException.printStackTrace(); // make this a very visible failure in output in case caller hides it
                    throw securityException;
                }
            }

        } finally {
            nestedCall.set(Boolean.FALSE);
        }
    }
    }
