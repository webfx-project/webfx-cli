package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.abstr.XmlGavApi;
import dev.webfx.cli.modulefiles.abstr.XmlGavUtil;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.dom4j.Element;

/**
 * @author Bruno Salmon
 */
public class LibraryModule extends ModuleImpl implements XmlGavApi {

    private final Element moduleNode;
    private final boolean webFx;
    private Module rootModule; // Non-null for libraries that are actually transitive libraries from a root one (ex: junit-jupiter for junit-jupiter-api, junit-jupiter-params, etc...)
    // This is this rootModule that will be listed in pom.xml and not the transitive libraries
    private final ReusableStream<String> exportedModules;

    public LibraryModule(Element moduleNode, boolean webFx) {
        super(XmlGavUtil.lookupName(moduleNode));
        this.moduleNode = moduleNode;
        groupId = lookupGroupId();
        artifactId = lookupArtifactId();
        version = lookupVersion();
        type = lookupType();
        this.webFx = webFx;
        exportedModules = XmlUtil.nodeListToTextContentReusableStream(XmlUtil.lookupElementList(moduleNode, "exported-packages//package"));
        if (exportedModules.anyMatch(p -> p.equals("java.time") || p.equals("java.nio"))) {
            setJavaBaseEmulationModule(true);
        }
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
        exportedModules = ReusableStream.empty();
    }

    public boolean isWebFx() {
        return webFx;
    }

    public boolean isThirdParty() {
        return !webFx;
    }

    @Override
    public Element getXmlNode() {
        return moduleNode;
    }

    public Module getRootModule() {
        return rootModule;
    }

    public ReusableStream<String> getExportedPackages() {
        return exportedModules;
    }

    public boolean shouldBeDownloadedInM2() {
        return getGroupId() != null &&
                //getArtifactId() != null &&
                getVersion() != null &&
                getExportedPackages().isEmpty();
    }

    public static LibraryModule createWebFxLibraryModule(Element moduleNode) {
        return new LibraryModule(moduleNode, true);
    }

    public static LibraryModule createThirdPartyLibraryModule(Element moduleNode) {
        return new LibraryModule(moduleNode, false);
    }

}
