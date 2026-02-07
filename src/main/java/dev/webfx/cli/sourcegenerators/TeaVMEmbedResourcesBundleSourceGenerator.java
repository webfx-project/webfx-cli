package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class TeaVMEmbedResourcesBundleSourceGenerator {

    private static final String GENERATED_PACKAGE_NAME = "dev.webfx.platform.resource.teavm";

    // Note: the only reason to use a different class name between JS and WASM modules is because this is how the maven
    // profiles identify xxx-teavm-js and xxx-teavm-wasm modules (see auto-teavm-js and auto-teavm-wasm profiles in parent pom.xml)
    private static final String JS_GENERATED_PROVIDER_CLASS_NAME = "TeaVMJsEmbedResourcesBundle";
    private static final String WASM_GENERATED_PROVIDER_CLASS_NAME = "TeaVMWasmEmbedResourcesBundle";

    private static final String JS_GENERATED_PROVIDER_FULL_CLASS_NAME = GENERATED_PACKAGE_NAME + "." + JS_GENERATED_PROVIDER_CLASS_NAME;
    private static final String JS_GENERATED_PROVIDER_FULL_JAVA_FILE = GENERATED_PACKAGE_NAME.replace('.', '/') + "/" + JS_GENERATED_PROVIDER_CLASS_NAME + ".java";

    private static final String WASM_GENERATED_PROVIDER_FULL_CLASS_NAME = GENERATED_PACKAGE_NAME + "." + WASM_GENERATED_PROVIDER_CLASS_NAME;
    private static final String WASM_GENERATED_PROVIDER_FULL_JAVA_FILE = GENERATED_PACKAGE_NAME.replace('.', '/') + "/" + WASM_GENERATED_PROVIDER_CLASS_NAME + ".java";


    static boolean generateTeaVMEmbedResourceBundleSource(DevProjectModule module) {
        StringBuilder resourceDeclaration = new StringBuilder();
        ProjectModule.filterProjectModules(module.getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules())
            .flatMap(ProjectModule::getEmbedResources)
            .sorted()
            .forEach(r -> resourceDeclaration.append("        \"").append(r).append("\",\n"));
        String source = ResourceTextFileReader.readTemplate("TeaVMEmbedResourcesBundle.javat")
            .replace("TeaVMEmbedResourcesBundle", module.isWasmModule() ? WASM_GENERATED_PROVIDER_CLASS_NAME : JS_GENERATED_PROVIDER_CLASS_NAME)
            .replace("${resourceDeclaration}",
                // Removing the last comma and line feed
                resourceDeclaration.substring(0, resourceDeclaration.length() - 2));
        TextFileReaderWriter.writeTextFileIfNewOrModified(source, getJavaFilePath(module));
        return true;
    }

    static Path getJavaFilePath(DevProjectModule module) {
        return module.getMainJavaSourceDirectory().resolve(module.isWasmModule() ? WASM_GENERATED_PROVIDER_FULL_JAVA_FILE : JS_GENERATED_PROVIDER_FULL_JAVA_FILE);
    }

    public static String getProviderClassName(DevProjectModule module) {
        return module.isWasmModule() ? WASM_GENERATED_PROVIDER_FULL_CLASS_NAME : JS_GENERATED_PROVIDER_FULL_CLASS_NAME;
    }
}
