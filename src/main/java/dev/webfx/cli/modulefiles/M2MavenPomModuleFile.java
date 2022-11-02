package dev.webfx.cli.modulefiles;

import dev.webfx.cli.core.M2ProjectModule;
import dev.webfx.cli.core.MavenUtil;
import dev.webfx.cli.modulefiles.abstr.MavenPomModuleFile;
import dev.webfx.cli.modulefiles.abstr.PathBasedXmlModuleFileImpl;

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
        MavenUtil.cleanM2ModuleSnapshotIfRequested(module);
        Path path = module.getM2ArtifactSubPath(".pom");
        if (!Files.exists(path))
            module.downloadArtifactClassifier("pom");
        return path;
    }

}
