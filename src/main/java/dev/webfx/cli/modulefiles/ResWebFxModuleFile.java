package dev.webfx.cli.modulefiles;

import dev.webfx.cli.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.cli.modulefiles.abstr.XmlModuleFileImpl;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.xml.XmlUtil;

import java.nio.file.Path;

/**
 * WebFX module file read from resources (only used for reading the JDK modules when initializing the ModuleRegistry)
 *
 * @author Bruno Salmon
 */
public final class ResWebFxModuleFile extends XmlModuleFileImpl implements WebFxModuleFile {

    public ResWebFxModuleFile(String resourcePath) {
        super(null, // There is no module to represent the JDK itself
                XmlUtil.parseXmlString(ResourceTextFileReader.uncheckedReadResourceTextFile(resourcePath)));
    }

    @Override
    public boolean fileExists() {
        return true;
    }

    @Override
    public Path getModuleFilePath() {
        return null; // Actually never called
    }

}
