package dev.webfx.cli.modulefiles;

import dev.webfx.cli.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.cli.modulefiles.abstr.XmlModuleFileImpl;
import dev.webfx.cli.core.Module;
import org.w3c.dom.Element;

import java.nio.file.Path;

/**
 * WebFX module file read from resources (only used for reading the JDK modules when initializing the ModuleRegistry)
 *
 * @author Bruno Salmon
 */
public final class ExportedWebFxModuleFile extends XmlModuleFileImpl implements WebFxModuleFile {

    public ExportedWebFxModuleFile(Module module, Element exportElement) {
        super(module, exportElement);
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
