package dev.webfx.cli.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Bruno Salmon
 */
public final class JavaCallbacks {

    private final Map<String /* java class name */, List<JavaMethodSignature>> methodCallbacks = new HashMap<>();

    public Map<String, List<JavaMethodSignature>> getMethodCallbacks() {
        return methodCallbacks;
    }

    public void addMethodCallback(String className, String methodName, String[] parameterTypes) {
        addMethodCallback(new JavaMethodSignature(className, methodName, parameterTypes));
    }

    public void addConstructorCallback(String className, String[] parameterTypes) {
        addMethodCallback(new JavaMethodSignature(className, className, parameterTypes));
    }

    public void addMethodCallback(JavaMethodSignature signature) {
        methodCallbacks.computeIfAbsent(signature.className, k -> new ArrayList<>()).add(signature);
    }

    public static final class JavaMethodSignature {
        private final String className;
        private final String methodName;
        private final String[] parameterTypes;

        public JavaMethodSignature(String className, String methodName, String[] parameterTypes) {
            this.className = className;
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
        }

        public boolean isConstructor() {
            return methodName.equals(className);
        }

        public String getClassName() {
            return className;
        }

        public String getMethodName() {
            return methodName;
        }

        public String[] getParameterTypes() {
            return parameterTypes;
        }
    }
}
