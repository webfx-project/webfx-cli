package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.lib.reusablestream.ReusableStream;

/**
 * @author Bruno Salmon
 */
public interface MavenPomModuleFile extends XmlGavModuleFile, PathBasedModuleFile {

    default boolean isAggregate() {
        return lookupNode("/project[1]/modules[1]") != null;
    }

    default ReusableStream<String> getChildrenModuleNames() {
        return lookupNodeListTextContent("/project[1]/modules[1]//module");
    }

}
