package dev.webfx.buildtool.modulefiles.abstr;

import dev.webfx.buildtool.*;
import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public interface WebFxModuleFile extends XmlGavModuleFile {

    default boolean isExecutable() {
        return getBooleanProjectAttributeValue("executable");
    }

    default boolean isInterface() {
        return getBooleanProjectAttributeValue("interface");
    }

    default boolean isAutomatic() {
        return lookupNode("auto-conditions") != null;
    }

    default boolean isAggregate() {
        return lookupNode("modules") != null;
    }

    default boolean shouldSubdirectoriesChildrenModulesBeAdded() {
        return lookupNode("modules/subdirectories-modules") != null;
    }

    default ReusableStream<String> getChildrenModuleNames() {
        return lookupNodeListTextContent(("modules//module"));
    }

    default ReusableStream<String> getExplicitExportedPackages() {
        return lookupNodeListTextContent("exported-packages//package");
    }

    default boolean areSourcePackagesAutomaticallyExported() {
        return lookupNode("exported-packages/source-packages") != null;
    }

    default ReusableStream<String> getExcludedPackagesFromSourcePackages() {
        return lookupNodeListTextContent("exported-packages/source-packages//exclude-package");
    }

    default ReusableStream<String> getResourcePackages() {
        return lookupNodeListTextContent("exported-packages//resource-package");
    }

    default ReusableStream<String> implementedInterfaces() {
        return lookupNodeListTextContent("implements//module");
    }

    default ReusableStream<ModuleProperty> getModuleProperties() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("properties//*"), node -> new ModuleProperty(node.getNodeName(), node.getTextContent()));
    }

    default boolean areUsedBySourceModulesDependenciesAutomaticallyAdded() {
        return lookupNode("dependencies/used-by-source-modules") != null;
    }

    default ReusableStream<ModuleDependency> getUndiscoveredUsedBySourceModulesDependencies() {
        return lookupDependencies("dependencies/used-by-source-modules//undiscovered-used-by-source-module", ModuleDependency.Type.SOURCE, null);
    }

    default ReusableStream<ModuleDependency> getExplicitSourceModulesDependencies() {
        return lookupDependencies("dependencies/used-by-source-modules//source-module", ModuleDependency.Type.SOURCE, null);
    }

    default ReusableStream<ModuleDependency> getPluginModuleDependencies() {
        return lookupDependencies("dependencies//plugin-module", ModuleDependency.Type.PLUGIN, "runtime");
    }

    default ReusableStream<ModuleDependency> getResourceModuleDependencies() {
        return lookupDependencies("dependencies//resource-module", ModuleDependency.Type.RESOURCE, "runtime");
    }

    default ReusableStream<LibraryModule> getLibraryModules() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("libraries//library"), LibraryModule::new);
    }

    default ReusableStream<String> getPackagesAutoCondition() {
        return lookupNodeListTextContent("auto-conditions//if-uses-java-package");
    }

    default ReusableStream<String> getEmbedResources() {
        return lookupNodeListTextContent("embed-resources//resource");
    }

    default ReusableStream<String> getSystemProperties() {
        return lookupNodeListTextContent("system-properties//property");
    }

    default ReusableStream<String> getArrayNewInstanceClasses() {
        return lookupNodeListTextContent("reflect/array-new-instance//class");
    }

    default String getGraalVmReflectionJson() {
        return lookupNodeTextContent("graalvm-reflection-json");
    }

    default ReusableStream<ServiceProvider> providedServerProviders() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("providers//*"), node -> new ServiceProvider(node.getNodeName(), node.getTextContent()));
    }

    default Node getHtmlNode() {
        return lookupNode("html");
    }

    private boolean getBooleanProjectAttributeValue(String attribute) {
        return XmlUtil.getBooleanAttributeValue(getXmlNode(), attribute);
    }

    @Override
    default Document createInitialDocument() {
        return XmlUtil.parseXmlString(ResourceTextFileReader.readTemplate("webfx.xml"));
    }

    default void setExecutable(boolean executable) {
        if (executable != isExecutable()) {
            Attr attribute = getOrCreateDocument().createAttribute("executable");
            attribute.setValue(String.valueOf(executable));
            getXmlNode().getAttributes().setNamedItem(attribute);
        }
    }

    default void addProvider(String spiClassName, String providerClassName) {
        appendTextNodeIfNotAlreadyExists("providers/" + spiClassName, providerClassName, true);
    }

    default boolean updateDocument(Document document) {
        // Nothing to update as this is the source
        return false;
    }
}
