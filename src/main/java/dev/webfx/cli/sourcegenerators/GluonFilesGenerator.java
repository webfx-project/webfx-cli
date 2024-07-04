package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.platform.ast.AST;
import dev.webfx.platform.ast.AstArray;
import dev.webfx.platform.ast.AstObject;
import dev.webfx.platform.util.Arrays;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class GluonFilesGenerator {

    private static final String GENERATED_FILE = "main/graalvm_conf/reflection.json";

    public static boolean generateGraalVmReflectionJson(DevProjectModule gluonModule) {
        AstArray reflectionArray = AST.createArray();
        gluonModule.getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules()
                .filter(ProjectModule.class::isInstance)
                .map(ProjectModule.class::cast)
                .flatMap(pm -> pm.getWebFxModuleFile().getJavaCallbacks())
                .forEach(javaCallbacks -> javaCallbacks.getMethodCallbacks().forEach((className, methods) -> {
                    AstObject classObject = AST.createObject();
                    classObject.set("name", className);
                    AstArray methodsArray = AST.createArray();
                    methods.forEach(method -> {
                        AstObject methodObject = AST.createObject();
                        String methodName = method.isConstructor() ? "<init>" : method.getMethodName();
                        methodObject.set("name", methodName);
                        AstArray parameterTypesArray = AST.createArray();
                        Arrays.forEach(method.getParameterTypes(), parameterTypesArray::push);
                        methodObject.set("parameterTypes", parameterTypesArray);
                        methodsArray.push(methodObject);
                    });
                    classObject.set("methods", methodsArray);
                    reflectionArray.push(classObject);
                }));
        if (reflectionArray.isEmpty())
            return false;
        TextFileReaderWriter.writeTextFileIfNewOrModified(AST.formatArray(reflectionArray, "json"), getGeneratedFilePath(gluonModule));
        return true;
    }

    private static Path getGeneratedFilePath(DevProjectModule module) {
        return module.getSourceDirectory().resolve(GENERATED_FILE);
    }

}
