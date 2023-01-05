package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.platform.meta.Meta;

/**
 * @author Bruno Salmon
 */
public final class MetaFileGenerator {
    public static void generateExecutableModuleMetaResourceFile(DevProjectModule module) {
        if (module.isExecutable()) {
            ProjectModule applicationModule = module.getApplicationModule();
            String applicationModuleName = applicationModule != null ? applicationModule.getName() : "";
            TextFileReaderWriter.writeTextFileIfNewOrModified(
                    ResourceTextFileReader.readTemplate(Meta.META_EXE_RESOURCE_FILE_NAME) // Should be exe.properties
                            .replace("${executableModuleName}", module.getName())
                            .replace("${executableModuleVersion}", module.getVersion())
                            .replace("${applicationModuleName}", applicationModuleName)
                    , module.getMainResourcesDirectory().resolve(Meta.META_EXE_RESOURCE_FILE_PATH));
        }
    }
}
