package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.M2ProjectModule;
import dev.webfx.buildtool.modulefiles.abstr.PathBasedXmlModuleFileImpl;
import dev.webfx.buildtool.modulefiles.abstr.WebFxModuleFile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class M2WebFxModuleFile extends PathBasedXmlModuleFileImpl implements WebFxModuleFile {

    public M2WebFxModuleFile(M2ProjectModule module) {
        super(module, getWebFxModuleFilePathAndDownloadIfMissing(module));
    }

    @Override
    public boolean isAggregate() {
        return false;
    }

    private static Path getWebFxModuleFilePathAndDownloadIfMissing(M2ProjectModule module) {
        Path path = module.getM2ArtifactSubPath("-webfx.xml");
        if (module.isWebFxModuleFileExpected() && !Files.exists(path))
            module.downloadArtifactClassifier("xml:webfx");
        return path;
    }
}
