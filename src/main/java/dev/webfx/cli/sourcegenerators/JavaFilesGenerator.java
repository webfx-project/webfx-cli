package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;

/**
 * @author Bruno Salmon
 */
public class JavaFilesGenerator {

    public static boolean generateModuleInfoJavaFile(DevProjectModule module) {
        // Generating module-info.java for this module
        return module.getMainJavaModuleFile().writeFile();
    }

    public static void deleteModuleInfoJavaFile(DevProjectModule module) {
        TextFileReaderWriter.deleteTextFile(module.getMainJavaModuleFile().getModuleFilePath());
    }

    public static int generateMetaInfServicesFiles(DevProjectModule module) {
        // Generating META-INF/services/ files
        // Note: this is the old way of declaring services (new way is in module-info.java) but still required for GraalVM and TeaVM
        // => To be removed as soon as GraalVM and TeaVM supports the new way of declaring services
        int[] metaInfCount = { 0 };
        module.getProvidedJavaServices()
                .forEach(service -> {
                    StringBuilder sb = new StringBuilder();
                    module.getProvidedJavaServiceImplementations(service, false)
                            .forEach(providerClassName ->
                                    sb.append(providerClassName).append('\n')
                            );
                    TextFileReaderWriter.writeTextFileIfNewOrModified(sb.toString(), module.getMetaInfJavaServicesDirectory().resolve(service));
                    metaInfCount[0]++;
                });
        return metaInfCount[0];
    }
}
