package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.M2ProjectModule;
import dev.webfx.buildtool.modulefiles.abstr.MavenPomModuleFile;
import dev.webfx.buildtool.modulefiles.abstr.PathBasedXmlModuleFileImpl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class M2MavenPomModuleFile extends PathBasedXmlModuleFileImpl implements MavenPomModuleFile {

    public M2MavenPomModuleFile(M2ProjectModule module) {
        super(module, getPomModuleFilePathAndDownloadIfMissing(module));
    }

    private static Path getPomModuleFilePathAndDownloadIfMissing(M2ProjectModule module) {
        Path path = module.getM2ArtifactSubPath(".pom");
        if (!Files.exists(path))
            module.downloadArtifactClassifier("pom");
        return path;
    }

}