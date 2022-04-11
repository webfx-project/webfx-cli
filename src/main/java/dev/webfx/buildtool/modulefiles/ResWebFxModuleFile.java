package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.xml.XmlUtil;

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

}
