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
 * Generates the GraalVM reachability metadata (reflection & resources) for executable native modules. The file is
 * generated under META-INF/native-image/ in the module resources, so it is automatically picked up by native-image.
 *
 * @author Bruno Salmon
 */
public final class NativeImageFilesGenerator {

    public static final String NATIVE_IMAGE_RESOURCES_FOLDER = "META-INF/native-image";

    public static boolean generateReachabilityMetadataJson(DevProjectModule nativeModule) {
        // 1) Reflection => java callbacks declared in webfx.xml (ex: methods called from JavaScript)
        AstArray reflectionArray = AST.createArray();
        nativeModule.getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules()
                .filter(ProjectModule.class::isInstance)
                .map(ProjectModule.class::cast)
                .flatMap(pm -> pm.getWebFxModuleFile().getJavaCallbacks())
                .forEach(javaCallbacks -> javaCallbacks.getMethodCallbacks().forEach((className, methods) -> {
                    AstObject typeObject = AST.createObject();
                    typeObject.set("type", className);
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
                    typeObject.set("methods", methodsArray);
                    reflectionArray.push(typeObject);
                }));
        // 2) Resources => all resource files potentially used by the application (files directly under the resource packages)
        AstArray resourcesArray = AST.createArray();
        ProjectModule.filterProjectModules(nativeModule.getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules())
                .flatMap(ProjectModule::getResourcePackages)
                .distinct()
                .stream().sorted()
                .forEach(p -> {
                    AstObject resourceObject = AST.createObject();
                    resourceObject.set("glob", p.replace('.', '/') + "/*");
                    resourcesArray.push(resourceObject);
                });
        if (reflectionArray.isEmpty() && resourcesArray.isEmpty())
            return false;
        AstObject metadata = AST.createObject();
        if (!reflectionArray.isEmpty())
            metadata.set("reflection", reflectionArray);
        if (!resourcesArray.isEmpty())
            metadata.set("resources", resourcesArray);
        TextFileReaderWriter.writeTextFileIfNewOrModified(AST.formatObject(metadata, "json"), getGeneratedFilePath(nativeModule));
        return true;
    }

    private static Path getGeneratedFilePath(DevProjectModule module) {
        return module.getMainResourcesDirectory()
                .resolve(NATIVE_IMAGE_RESOURCES_FOLDER)
                .resolve(module.getName())
                .resolve("reachability-metadata.json");
    }

}
