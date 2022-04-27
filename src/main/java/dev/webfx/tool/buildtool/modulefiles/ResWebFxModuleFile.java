package dev.webfx.tool.buildtool.modulefiles;

import dev.webfx.tool.buildtool.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.tool.buildtool.modulefiles.abstr.XmlModuleFileImpl;
import dev.webfx.tool.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.tool.buildtool.util.xml.XmlUtil;

/**
 * WebFx module file read from resources (only used for reading the JDK modules when initializing the ModuleRegistry)
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
}
