package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.core.*;
import dev.webfx.cli.modulefiles.ArtifactResolver;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
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

    default String getName() {
        return getProjectAttributeValue("name");
    }

    default String getApplicationId() {
        return getProjectAttributeValue("applicationId");
    }

    default String getApplicationLabel() {
        return getProjectAttributeValue("applicationLabel");
    }

    default boolean isInterface() {
        return getBooleanProjectAttributeValue("interface");
    }

    default boolean hasAutoInjectionConditions() {
        return lookupNode("module-auto-injection-conditions") != null;
    }

    default boolean isAggregate() {
        return lookupNode("modules") != null;
    }

    default boolean shouldTakeChildrenModuleNamesFromPomInstead() {
        return !isAggregate(); // Default behaviour: yes if there is no <modules/> section
    }

    default boolean shouldSubdirectoriesChildrenModulesBeAdded() {
        return lookupNode("modules/subdirectories-modules") != null;
    }

    default ReusableStream<String> getChildrenModuleNames() {
        return lookupNodeListTextContent(("modules/module"));
    }

    default ReusableStream<String> getExplicitExportedPackages() {
        return lookupNodeListTextContent("exported-packages/package");
    }

    default boolean areSourcePackagesAutomaticallyExported() {
        return lookupNode("exported-packages/source-packages") != null;
    }

    default ReusableStream<String> getExcludedPackagesFromSourcePackages() {
        return lookupNodeListTextContent("exported-packages/source-packages/exclude-package");
    }

    default ReusableStream<String> getExplicitResourcePackages() {
        return lookupNodeListTextContent("exported-packages/resource-package");
    }

    default boolean areResourcePackagesAutomaticallyExported() {
        return lookupNode("exported-packages/resource-packages") != null;
    }

    default ReusableStream<String> implementedInterfaces() {
        return lookupNodeListTextContent("implements/module");
    }

    default ReusableStream<ModuleProperty> getModuleProperties() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("properties/*"), node -> new ModuleProperty(node.getNodeName(), node.getTextContent()));
    }

    default boolean areUsedBySourceModulesDependenciesAutomaticallyAdded() {
        return lookupNode("dependencies/used-by-source-modules") != null;
    }

    default ReusableStream<ModuleDependency> getUndetectedUsedBySourceModulesDependencies() {
        return lookupDependencies("dependencies/used-by-source-modules/undetected-module", ModuleDependency.Type.SOURCE, null);
    }

    default ReusableStream<ModuleDependency> getExplicitSourceModulesDependencies() {
        return lookupDependencies("dependencies/used-by-source-modules/module", ModuleDependency.Type.SOURCE, null);
    }

    default ReusableStream<ModuleDependency> getPluginModuleDependencies() {
        return lookupDependencies("dependencies/plugin-module", ModuleDependency.Type.PLUGIN, "runtime");
    }

    default ReusableStream<ModuleDependency> getResourceModuleDependencies() {
        return lookupDependencies("dependencies/resource-module", ModuleDependency.Type.RESOURCE, "provided");
    }

    default ReusableStream<LibraryModule> getRequiredWebFxLibraryModules() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("required-libraries//webfx-library"), LibraryModule::createWebFxLibraryModule);
    }

    default ReusableStream<LibraryModule> getRequiredThirdPartyLibraryModules() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("required-libraries//library"), LibraryModule::createThirdPartyLibraryModule);
    }

    default ReusableStream<String> getUsesPackagesAutoInjectionConditions() {
        return lookupNodeListTextContent("module-auto-injection-conditions/if-uses-java-package");
    }

    default ReusableStream<String> getEmbedResources() {
        return lookupNodeListTextContent("embed-resources/resource");
    }

    default ReusableStream<String> getSystemProperties() {
        return lookupNodeListTextContent("system-properties/property");
    }

    default ReusableStream<String> getArrayNewInstanceClasses() {
        return lookupNodeListTextContent("reflect/array-new-instance/class");
    }

    default String getGraalVmReflectionJson() {
        return lookupNodeTextContent("graalvm-reflection-json");
    }

    default ReusableStream<ServiceProvider> providedServiceProviders() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("providers/provider"), node -> {
            String spi = XmlUtil.getAttributeValue(node, "interface");
            String provider = node.getTextContent();
            if (spi == null)
                throw new CliException("Missing interface attribute in " + getModule().getName() + " provider declaration: " + XmlUtil.formatXmlText(node));
            return new ServiceProvider(spi, provider);
        });
    }

    default ReusableStream<MavenRepository> mavenRepositories() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("maven-repositories/*"), node -> {
            String id = XmlUtil.getAttributeValue(node, "id");
            if (id == null)
                throw new CliException("Missing id attribute in " + getModule().getName() + " Maven module declaration: " + XmlUtil.formatXmlText(node));
            String url = node.getTextContent();
            boolean snapshot = node.getNodeName().equals("snapshot-repository");
            return new MavenRepository(id, url, snapshot);
        });
    }

    default Node getHtmlNode() {
        return lookupNode("html");
    }

    default Node getMavenManualNode() {
        return lookupNode("maven-pom-manual");
    }

    default boolean skipMavenPomUpdate() {
        return !fileExists() || lookupNode("update-options/skip-maven-pom") != null;
    }

    default boolean skipJavaModuleInfoUpdate() {
        return !fileExists() || lookupNode("update-options/skip-java-module-info") != null;
    }

    default boolean generatesExportSnapshot() {
        return lookupNode("update-options/generate-export-snapshot") != null;
    }

    default ReusableStream<String> javaSourcePackagesFromExportSnapshot() {
        return lookupNodeListTextContent("source-packages/package");
    }

    default ReusableStream<String> resourcePackagesFromExportSnapshot() {
        return lookupNodeListTextContent("resource-packages/package");
    }

    default boolean hasDetectedUsedBySourceModulesFromExportSnapshot() {
        return lookupNode("used-by-source-modules") != null;
    }

    default ReusableStream<ModuleDependency> detectedUsedBySourceModulesDependenciesFromExportSnapshot() {
        return lookupDependencies("used-by-source-modules/module", ModuleDependency.Type.SOURCE, null);
    }

    default ReusableStream<String> usedRequiredJavaServicesFromExportSnapshot() {
        return lookupNodeListTextContent("used-services/required-service");
    }

    default ReusableStream<String> usedOptionalJavaServicesFromExportSnapshot() {
        return lookupNodeListTextContent("used-services/optional-service");
    }

    default Node javaPackageUsageNodeFromExportSnapshot(String javaPackage) {
        return lookupNode("/project/export-snapshot/usages/java-package[@name='" + javaPackage + "']");
    }

    default ReusableStream<String> modulesUsingJavaPackageFromExportSnapshot(String javaPackage) {
        return modulesUsingJavaPackageFromExportSnapshot(javaPackageUsageNodeFromExportSnapshot(javaPackage));
    }

    default ReusableStream<String> modulesUsingJavaPackageFromExportSnapshot(Node javaPackageUsageNode) {
        return XmlUtil.nodeListToTextContentReusableStream(XmlUtil.lookupNodeList(javaPackageUsageNode, "module"));
    }

    default Node javaClassUsageNodeFromExportSnapshot(String javaClass) {
        return lookupNode("/project/export-snapshot/usages/java-class[@name='" + javaClass + "']");
    }

    default ReusableStream<String> modulesUsingJavaClassFromExportSnapshot(String javaClass) {
        return modulesUsingJavaClassFromExportSnapshot(javaClassUsageNodeFromExportSnapshot(javaClass));
    }

    default ReusableStream<String> modulesUsingJavaClassFromExportSnapshot(Node javaClassUsageNode) {
        return XmlUtil.nodeListToTextContentReusableStream(XmlUtil.lookupNodeList(javaClassUsageNode, "module"));
    }

    private String getProjectAttributeValue(String attribute) {
        return XmlUtil.getAttributeValue(getXmlNode(), attribute);
    }

    private boolean getBooleanProjectAttributeValue(String attribute) {
        return XmlUtil.getBooleanAttributeValue(getXmlNode(), attribute);
    }

    @Override
    default Document createInitialDocument() {
        return XmlUtil.parseXmlString(
                ResourceTextFileReader.readTemplate(getModule() instanceof DevRootModule ? "webfx-root.xml" : "webfx.xml")
                        .replace("${groupId}",    ArtifactResolver.getGroupId(getModule()))
                        .replace("${artifactId}", ArtifactResolver.getArtifactId(getModule()))
                        .replace("${version}",    ArtifactResolver.getVersion(getModule()))
        );
    }

    default void setExecutable(boolean executable) {
        if (executable != isExecutable()) {
            Attr attribute = getOrCreateDocument().createAttribute("executable");
            attribute.setValue(String.valueOf(executable));
            getModuleElement().getAttributes().setNamedItem(attribute);
        }
    }

    default void addProvider(String spiClassName, String providerClassName) {
        if (lookupNode("providers/provider[@interface='" + spiClassName + "'][text() = '" + providerClassName + "']") == null)
            appendElementWithTextContent("providers/provider", providerClassName, true).setAttribute("interface", spiClassName);
    }

    default boolean updateDocument(Document document) {
        // Nothing to update as this is the source
        return false;
    }
}
