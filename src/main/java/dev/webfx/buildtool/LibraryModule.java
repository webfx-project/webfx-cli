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
    private final boolean webFx;

    public LibraryModule(Node moduleNode, boolean webFx) {
        super(XmlGavUtil.lookupName(moduleNode));
        this.moduleNode = moduleNode;
        groupId = lookupGroupId();
        artifactId = lookupArtifactId();
        version = lookupVersion();
        type = lookupType();
        this.webFx = webFx;
    }

    public boolean isWebFx() {
        return webFx;
    }

    public boolean isThirdParty() {
        return !webFx;
    }

    @Override
    public Node getXmlNode() {
        return moduleNode;
    }

    public ReusableStream<String> getExportedPackages() {
        return XmlUtil.nodeListToTextContentReusableStream(XmlUtil.lookupNodeList(moduleNode, "exported-packages//package"));
    }

    public boolean shouldBeDownloadedInM2() {
        return getGroupId() != null &&
                //getArtifactId() != null &&
                getVersion() != null &&
                getExportedPackages().isEmpty();
    }

    public static LibraryModule createWebFxLibraryModule(Node moduleNode) {
        return new LibraryModule(moduleNode, true);
    }

    public static LibraryModule createThirdPartyLibraryModule(Node moduleNode) {
        return new LibraryModule(moduleNode, false);
    }

}
