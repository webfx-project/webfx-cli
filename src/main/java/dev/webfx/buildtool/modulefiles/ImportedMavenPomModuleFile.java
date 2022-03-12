package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import org.w3c.dom.Element;

/**
 * @author Bruno Salmon
 */
public class ImportedMavenPomModuleFile extends XmlModuleFileImpl implements MavenPomModuleFile {

    public ImportedMavenPomModuleFile(Module module, Element moduleElement) {
        super(module, moduleElement);
    }
}
