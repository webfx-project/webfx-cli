package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.abstr.XmlGavApi;
import dev.webfx.cli.modulefiles.abstr.XmlGavUtil;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public class LibraryModule extends ModuleImpl implements XmlGavApi {

    private final Node moduleNode;
    private final boolean webFx;
    private Module rootModule; // Non-null for libraries that are actually transitive libraries from a root one (ex: junit-jupiter for junit-jupiter-api, junit-jupiter-params, etc...)
    // This is this rootModule that will be listed in pom.xml and not the transitive libraries

    public LibraryModule(Node moduleNode, boolean webFx) {
        super(XmlGavUtil.lookupName(moduleNode));
        this.moduleNode = moduleNode;
        groupId = lookupGroupId();
        artifactId = lookupArtifactId();
        version = lookupVersion();
        type = lookupType();
        this.webFx = webFx;
    }

    public LibraryModule(Module descriptor, Module rootModule) {
        super(descriptor.getName());
        moduleNode = null;
        groupId = descriptor.getGroupId();
        artifactId = descriptor.getArtifactId();
        version = descriptor.getVersion();
        type = descriptor.getType();
        this.rootModule = rootModule;
        webFx = false;
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

    public Module getRootModule() {
        return rootModule;
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
