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

    public static boolean generateExecutableModuleMetaResourceFile(DevProjectModule module) {
        if (module.isExecutable()) {
            ProjectModule applicationModule = module.getApplicationModule();
            String applicationModuleName = applicationModule != null ? applicationModule.getName() : "";
            TextFileReaderWriter.writeTextFileIfNewOrModified(
                ResourceTextFileReader.readTemplate(Meta.META_EXE_RESOURCE_FILE_NAME) // Should be exe.properties
                    .replace("${executableModuleName}", module.getName())
                    .replace("${executableModuleVersion}", module.getVersion())
                    .replace("${applicationModuleName}", applicationModuleName)
                    .replace("${pwa}", String.valueOf(module.isPwa()))
                , module.getMainResourcesDirectory().resolve(Meta.META_EXE_RESOURCE_FILE_PATH));
            /* Note that the file template also contains ${maven.build.timestamp} and ${webfx.meta.environment} which we
               keep as is, but Maven will replace it while copying it to the target folder due to this webfx-parent
               pom.xml section:
               <resource> <!-- filtering (maven variable replacements) for index.html and exe.properties -->
                   <directory>src/main/resources</directory>
                   <filtering>true</filtering>
                   <includes>
                       <include>public/index.html</include> <!-- because of main.css?v=${maven.build.timestamp} to force CSS to reload on each new build -->
                       <include>dev/webfx/platform/meta/exe/exe.properties</include> <!-- because of mavenBuildTimestamp=${maven.build.timestamp} and environment=${webfx.meta.environment} -->
                   </includes>
               </resource>
            */
            return true;
        }
        return false;
    }
}
