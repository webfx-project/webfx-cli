package dev.webfx.buildtool;

import dev.webfx.buildtool.modulefiles.abstr.XmlGavApi;
import dev.webfx.buildtool.modulefiles.abstr.XmlGavUtil;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public class LibraryModule extends ModuleImpl implements XmlGavApi {

    private final Node moduleNode;

    public LibraryModule(Node moduleNode) {
        super(XmlGavUtil.lookupName(moduleNode));
        this.moduleNode = moduleNode;
        groupId = lookupGroupId();
        artifactId = lookupArtifactId();
        version = lookupVersion();
        type = lookupType();
    }

    @Override
    public Node getXmlNode() {
        return moduleNode;
    }

    public ReusableStream<String> getExportedPackages() {
        return XmlUtil.nodeListToTextContentReusableStream(XmlUtil.lookupNodeList(moduleNode, "exported-packages//package"));
    }

}
