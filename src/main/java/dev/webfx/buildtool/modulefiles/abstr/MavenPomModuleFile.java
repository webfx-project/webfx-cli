package dev.webfx.buildtool.modulefiles.abstr;

import dev.webfx.tools.util.reusablestream.ReusableStream;

/**
 * @author Bruno Salmon
 */
public interface MavenPomModuleFile extends XmlGavModuleFile, PathBasedModuleFile {

    default boolean isAggregate() {
        return lookupNode("modules") != null;
    }

    default ReusableStream<String> getChildrenModuleNames() {
        return lookupNodeListTextContent("modules//module");
    }

}
