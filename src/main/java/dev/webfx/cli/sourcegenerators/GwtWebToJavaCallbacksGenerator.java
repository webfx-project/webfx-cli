package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.JavaCallbacks;
import dev.webfx.cli.util.hashlist.HashList;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.platform.util.collection.Collections;

import java.nio.file.Path;
import java.util.List;

/**
 * @author Bruno Salmon
 */
final class GwtWebToJavaCallbacksGenerator {

    private static final String GENERATED_FILE = "super/dev/webfx/kit/registry/javafxweb/WebToJavaCallbacks.java";

    static boolean generateWebToJavaCallbacksSuperSource(DevProjectModule module) {
        StringBuilder body = new StringBuilder();
        HashList<Integer> parameterCounts = new HashList<>();
        module.getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules()
                .filter(DevProjectModule.class::isInstance)
                .map(DevProjectModule.class::cast)
                .flatMap(pm -> pm.getWebFxModuleFile().getJavaCallbacks())
                .forEach(javaCallbacks -> javaCallbacks.getMethodCallbacks().forEach((className, constructorOrMethods) -> {
                    // Ignoring constructors (only for Gluon/GraalVM)
                    List<JavaCallbacks.JavaMethodSignature> methods = Collections.filter(constructorOrMethods, m -> !m.isConstructor());
                    if (methods.isEmpty())
                        return;
                    if (body.length() == 0)
                        body.append("        ");
                    else
                        body.append(" else ");
                    body.append("if (javaInstance instanceof ").append(className).append(") {\n");
                    body.append("            ").append(className).append(" castedInstance = (").append(className).append(") javaInstance;\n");
                    methods.forEach(method -> {
                        String methodName = method.getMethodName();
                        int parameterCount = method.getParameterTypes().length;
                        parameterCounts.add(parameterCount);
                        body.append("            pm.set(\"").append(methodName).append("\", (JsVoidFn").append(parameterCount).append("Arg");
                        for (int i = 1; i <= parameterCount; i++) {
                            if (i == 1)
                                body.append('<');
                            else
                                body.append(", ");
                            body.append(method.getParameterTypes()[i - 1]);
                            if (i == parameterCount)
                                body.append('>');
                        }
                        body.append(") castedInstance::").append(methodName).append(");\n");
                    });
                    body.append("        }");
                }));
        if (body.length() == 0)
            return false;
        StringBuilder jsFunctions = new StringBuilder();
        parameterCounts.sort(Integer::compareTo);
        parameterCounts.forEach(parameterCount -> {
            jsFunctions.append("\n" +
                      "    @JsFunction\n" +
                      "    public interface JsVoidFn").append(parameterCount).append("Arg");
            for (int i = 1; i <= parameterCount; i++) {
                if (i == 1)
                    jsFunctions.append('<');
                else
                    jsFunctions.append(", ");
                jsFunctions.append('T').append(i);
                if (i == parameterCount)
                    jsFunctions.append('>');
            }
            jsFunctions.append(" {\n        void apply(");
            for (int i = 1; i <= parameterCount; i++) {
                jsFunctions.append('T').append(i).append(" arg").append(i);
                if (i < parameterCount)
                    jsFunctions.append(", ");
            }
            jsFunctions.append(");\n" +
                      "    }\n");
        });
        String source = ResourceTextFileReader.readTemplate("GwtWebToJavaCallbacks.javat")
                .replace("${bindCallbackMethodsBody}", body)
                .replace("${JsFunctionsDeclaration}", jsFunctions);
        TextFileReaderWriter.writeTextFileIfNewOrModified(source, getJavaFilePath(module));
        return true;
    }

    static Path getJavaFilePath(DevProjectModule module) {
        return module.getMainResourcesDirectory().resolve(GENERATED_FILE);
    }

}
