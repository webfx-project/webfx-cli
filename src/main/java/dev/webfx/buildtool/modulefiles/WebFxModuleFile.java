package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.*;
import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class WebFxModuleFile extends XmlModuleFile {

    public WebFxModuleFile(ProjectModule module) {
        super(module, true);
    }

    public Path getModuleFilePath() {
        return resolveFromModuleHomeDirectory("webfx.xml");
    }

    public boolean isExecutable() {
        return getBooleanProjectAttributeValue("executable");
    }

    public boolean isInterface() {
        return getBooleanProjectAttributeValue("interface");
    }

    public boolean isAutomatic() {
        return lookupNode("/project/auto-conditions") != null;
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

    public boolean areUsedBySourceModulesDependenciesAutomaticallyAdded() {
        return lookupNode("/project/dependencies/used-by-source-modules") != null;
    }

    public ReusableStream<ModuleDependency> getUndiscoveredUsedBySourceModulesDependencies() {
        return lookupDependencies("/project/dependencies/used-by-source-modules//undiscovered-used-by-source-module", ModuleDependency.Type.SOURCE);
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

    private boolean getBooleanProjectAttributeValue(String attribute) {
        return XmlUtil.getBooleanAttributeValue(getDocumentElement(), attribute);
    }

    @Override
    Document createInitialDocument() {
        ProjectModule projectModule = getProjectModule();
        String template = ResourceTextFileReader.readTemplate("webfx.xml")
                .replace("${groupId}",    ArtifactResolver.getGroupId(projectModule))
                .replace("${artifactId}", ArtifactResolver.getArtifactId(projectModule))
                .replace("${version}",    ArtifactResolver.getVersion(projectModule))
                ;
        return XmlUtil.parseXmlString(template);
    }

    public void setExecutable(boolean executable) {
        if (executable != isExecutable()) {
            Attr attribute = getOrCreateDocument().createAttribute("executable");
            attribute.setValue(String.valueOf(executable));
            getDocumentElement().getAttributes().setNamedItem(attribute);
        }
    }

    public void addProvider(String spiClassName, String providerClassName) {
        appendTextNodeIfNotAlreadyExists("/project/providers/" + spiClassName, providerClassName);
    }

    void updateDocument(Document document) {
        // Nothing to update as this is the source
    }
}
