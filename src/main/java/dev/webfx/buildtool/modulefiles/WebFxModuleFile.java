package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.*;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Node;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class WebFxModuleFile extends XmlModuleFile {

    public WebFxModuleFile(ProjectModule module) {
        super(module, true);
    }

    public Path getModulePath() {
        return resolveFromModuleHomeDirectory("webfx.xml");
    }

    public boolean isExecutable() {
        return getBooleanModuleAttributeValue("executable");
    }

    public boolean isInterface() {
        return getBooleanModuleAttributeValue("interface");
    }

    public boolean isAutomatic() {
        return lookupNode("/project/auto-conditions") != null;
    }

    public boolean isAggregate() {
        return lookupNode("/project/modules") != null;
    }

    public boolean areSourcePackagesAutomaticallyAdded() {
        return lookupNode("/project/packages/source-packages") != null;
    }

    public ReusableStream<String> getExplicitSourcePackages() {
        return lookupNodeListTextContent("/project/packages//source-package");
    }

    public ReusableStream<String> getHiddenPackages() {
        return lookupNodeListTextContent("/project/packages//hidden-package");
    }

    public ReusableStream<String> getResourcePackages() {
        return lookupNodeListTextContent("/project/packages//resource-package");
    }

    public ReusableStream<String> implementedInterfaces() {
        return lookupNodeListTextContent("/project/implements//module");
    }

    public ReusableStream<ModuleProperty> getModuleProperties() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("/project/properties//*"), node -> new ModuleProperty(node.getNodeName(), node.getTextContent()));
    }

    public ReusableStream<Path> getChildrenModules() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("/project/modules//module"), node -> resolveFromModuleHomeDirectory(node.getTextContent()));
    }

    public boolean areSourceModuleDependenciesAutomaticallyAdded() {
        return lookupNode("/project/dependencies/source-modules") != null;
    }

    public ReusableStream<ModuleDependency> getSourceModuleDependencies() {
        return lookupDependencies("/project/dependencies//source-module", ModuleDependency.Type.SOURCE);
    }

    public ReusableStream<ModuleDependency> getPluginModuleDependencies() {
        return lookupDependencies("/project/dependencies//plugin-module", ModuleDependency.Type.PLUGIN);
    }

    public ReusableStream<ModuleDependency> getResourceModuleDependencies() {
        return lookupDependencies("/project/dependencies//resource-module", ModuleDependency.Type.RESOURCE);
    }

    public ReusableStream<LibraryModule> getLibraryModules() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("/project/libraries//library"), LibraryModule::new);
    }

    public ReusableStream<String> getPackagesAutoCondition() {
        return lookupNodeListTextContent("/project/auto-conditions//if-uses-java-package");
    }

    public ReusableStream<String> getEmbedResources() {
        return lookupNodeListTextContent("/project/embed-resources//resource");
    }

    public ReusableStream<String> getSystemProperties() {
        return lookupNodeListTextContent("/project/system-properties//property");
    }

    public ReusableStream<String> getArrayNewInstanceClasses() {
        return lookupNodeListTextContent("/project/reflect/array-new-instance//class");
    }

    public String getGraalVmReflectionJson() {
        return lookupNodeTextContent("/project/graalvm-reflection-json");
    }

    public ReusableStream<ServiceProvider> providedServerProviders() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("/project/providers//*"), node -> new ServiceProvider(node.getNodeName(), node.getTextContent()));
    }

    public Node getHtmlNode() {
        return lookupNode("/project/html");
    }

    private boolean getBooleanModuleAttributeValue(String attribute) {
        return XmlUtil.getBooleanAttributeValue(getDocument().getDocumentElement(), attribute);
    }
}
