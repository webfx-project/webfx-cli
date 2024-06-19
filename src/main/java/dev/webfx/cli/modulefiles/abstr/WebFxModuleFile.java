package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.core.*;
import dev.webfx.cli.modulefiles.ArtifactResolver;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;

import java.util.List;

import static dev.webfx.cli.util.xml.XmlUtil.nodeListToNodeReusableStream;

/**
 * @author Bruno Salmon
 */
public interface WebFxModuleFile extends XmlGavModuleFile, PathBasedXmlModuleFile {

    default boolean isExecutable() {
        return getBooleanProjectAttributeValue("executable");
    }

    default boolean requiresTimeZoneData() {
        return getBooleanProjectAttributeValue("requiresTimezoneData");
    }

    default String getName() {
        return getProjectAttributeValue("name");
    }

    default boolean hasMainJavaSourceDirectory() {
        return getBooleanProjectAttributeValue("hasMainJavaSourceDirectory");
    }

    default boolean hasMainWebFxSourceDirectory() {
        return getBooleanProjectAttributeValue("hasMainWebFxSourceDirectory");
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

    default String getDeployRepositoryId() {
        return getProjectAttributeValue("deployRepositoryId");
    }

    default boolean hasAutoInjectionConditions() {
        return lookupNode("module-auto-injection-conditions[1]") != null;
    }

    default boolean isAggregate() {
        return lookupNode("modules[1]") != null;
    }

    default String getDescription() {
        return lookupNodeTextContent("description[1]");
    }

    default boolean shouldTakeChildrenModuleNamesFromPomInstead() {
        // Default behaviour: yes if there is no <modules/> section and no directive specific to leaf modules (ex: exported package or dependencies)
        return !isAggregate() && lookupNode("exported-packages[1]") == null && lookupNode("dependencies[1]") == null; // Default behaviour: yes if there is no <modules/> section
    }

    default boolean shouldSubdirectoriesChildrenModulesBeAdded() {
        return lookupNode("modules[1]/subdirectories-modules[1]") != null;
    }

    default ReusableStream<String> getChildrenModuleNames() {
        return lookupNodeListTextContent(("modules[1]/module"));
    }

    default ReusableStream<String> getExplicitExportedPackages() {
        return lookupNodeListTextContent("exported-packages[1]/package");
    }

    default boolean areSourcePackagesAutomaticallyExported() {
        return lookupNode("exported-packages[1]/source-packages") != null;
    }

    default ReusableStream<String> getExcludedPackagesFromSourcePackages() {
        return lookupNodeListTextContent("exported-packages[1]/source-packages[1]/exclude-package");
    }

    default ReusableStream<String> getExplicitResourcePackages() {
        return lookupNodeListTextContent("exported-packages[1]/resource-package");
    }

    default boolean areResourcePackagesAutomaticallyExported() {
        return lookupNode("exported-packages[1]/resource-packages[1]") != null;
    }

    default ReusableStream<String> implementedInterfaces() {
        return lookupNodeListTextContent("implements[1]/module");
    }

    default ReusableStream<ModuleProperty> getModuleProperties() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("properties[1]/*"), node -> new ModuleProperty(node.getName(), node.getText()));
    }

    default ReusableStream<ModuleProperty> getConfigurationVariables() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("configuration-variables[1]/*"), node -> new ModuleProperty(node.getName(), node.getText()));
    }

    default boolean areUsedBySourceModulesDependenciesAutomaticallyAdded() {
        return lookupNode("dependencies[1]/used-by-source-modules[1]") != null;
    }

    default ReusableStream<ModuleDependency> getUndetectedUsedBySourceModulesDependencies() {
        return lookupDependencies("dependencies[1]/used-by-source-modules[1]/undetected-module", ModuleDependency.Type.SOURCE, null);
    }

    default ReusableStream<String> getUndetectedUsedBySourcePackages() {
        return lookupNodeListTextContent("dependencies[1]/used-by-source-modules[1]/undetected-package");
    }

    default ReusableStream<ModuleDependency> getExplicitSourceModulesDependencies() {
        return lookupDependencies("dependencies[1]/used-by-source-modules[1]/module", ModuleDependency.Type.SOURCE, null);
    }

    default ReusableStream<ModuleDependency> getPluginModuleDependencies() {
        return lookupDependencies("dependencies[1]/plugin-module", ModuleDependency.Type.PLUGIN, "runtime");
    }

    default ReusableStream<ModuleDependency> getResourceModuleDependencies() {
        return lookupDependencies("dependencies[1]/resource-module", ModuleDependency.Type.RESOURCE, null);
    }

    default ReusableStream<LibraryModule> getRequiredWebFxLibraryModules() {
        return XmlUtil.nodeListToReusableStream(lookupElementList("required-libraries[1]//webfx-library"), LibraryModule::createWebFxLibraryModule);
    }

    default ReusableStream<LibraryModule> getRequiredThirdPartyLibraryModules() {
        return XmlUtil.nodeListToReusableStream(lookupElementList("required-libraries[1]//library"), LibraryModule::createThirdPartyLibraryModule);
    }

    default ReusableStream<String> getUsesPackagesAutoInjectionConditions() {
        return lookupNodeListTextContent("module-auto-injection-conditions[1]/if-uses-java-package");
    }

    default ReusableStream<String> getEmbedResources() {
        return lookupNodeListTextContent("embed-resources[1]/resource");
    }

    default ReusableStream<String> getSystemProperties() {
        return lookupNodeListTextContent("system-properties[1]/property");
    }

    default ReusableStream<String> getArrayNewInstanceClasses() {
        return lookupNodeListTextContent("reflect[1]/array-new-instance[1]/class");
    }

    /*default String getGraalVmReflectionJson() {
        return lookupNodeTextContent("graalvm-reflection-json[1]");
    }*/

    default ReusableStream<ServiceProvider> providedServiceProviders() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("providers[1]/provider"), node -> {
            String spi = XmlUtil.getAttributeValue((Element) node, "interface");
            String provider = node.getText();
            if (spi == null)
                throw new CliException("Missing interface attribute in " + getModule().getName() + " provider declaration: " + XmlUtil.formatXmlText(node));
            return new ServiceProvider(spi, provider);
        });
    }

    default ReusableStream<MavenRepository> mavenRepositories() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("maven-repositories[1]/*"), node -> {
            String id = XmlUtil.getAttributeValue((Element) node, "id");
            if (id == null)
                throw new CliException("Missing id attribute in " + getModule().getName() + " Maven module declaration: " + XmlUtil.formatXmlText(node));
            String url = node.getText();
            boolean snapshot = node.getName().equals("snapshot-repository");
            return new MavenRepository(id, url, snapshot);
        });
    }

    default ReusableStream<Element> getHtmlNodes() {
        return nodeListToNodeReusableStream(lookupElementList("html"));
    }

    default Element getMavenManualNode() {
        return lookupElement("maven-pom-manual[1]");
    }

    default boolean skipMavenPomUpdate() {
        return !fileExists() || lookupNode("update-options[1]/skip-maven-pom[1]") != null;
    }

    default boolean skipJavaModuleInfoUpdate() {
        return !fileExists() || lookupNode("update-options[1]/skip-java-module-info[1]") != null;
    }

    default ReusableStream<JavaCallbacks> getJavaCallbacks() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("java-callbacks[1]"), node -> {
            JavaCallbacks javaCallbacks = new JavaCallbacks();
            NodeList classes = XmlUtil.lookupNodeList(node, "callback-class");
            for (int i = 0; i < classes.getLength(); i++) {
                Node classNode = classes.item(i);
                String className = XmlUtil.getAttributeValue(classNode, "name");
                NodeList constructors = XmlUtil.lookupNodeList(classNode, "callback-constructor");
                for (int j = 0; j < constructors.getLength(); j++) {
                    Node constructorNode = constructors.item(j);
                    NodeList arguments = XmlUtil.lookupNodeList(constructorNode, "callback-argument");
                    List<String> argumentTypes = XmlUtil.nodeListToList(arguments, argument -> XmlUtil.getAttributeValue(argument, "class"));
                    javaCallbacks.addConstructorCallback(className, argumentTypes.toArray(String[]::new));
                }
                NodeList methods = XmlUtil.lookupNodeList(classNode, "callback-method");
                for (int j = 0; j < methods.getLength(); j++) {
                    Node methodNode = methods.item(j);
                    String methodName = XmlUtil.getAttributeValue(methodNode, "name");
                    NodeList arguments = XmlUtil.lookupNodeList(methodNode, "callback-argument");
                    List<String> argumentTypes = XmlUtil.nodeListToList(arguments, argument -> XmlUtil.getAttributeValue(argument, "class"));
                    javaCallbacks.addMethodCallback(className, methodName, argumentTypes.toArray(String[]::new));
                }
            }
            return javaCallbacks;
        });
    }

    default ReusableStream<TargetTag> ignoredTargetTags() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("target-tags[1]/ignore-tag"), node ->
                TargetTag.fromTagName(node.getTextContent())
        );
    }

    /* Export snapshot reading methods  */

    default boolean generatesExportSnapshot() {
        return lookupNode("update-options[1]/generate-export-snapshot[1]") != null;
    }

    default ReusableStream<String> javaSourcePackagesFromExportSnapshot() {
        return lookupNodeListTextContent("source-packages[1]/package");
    }

    default ReusableStream<String> resourcePackagesFromExportSnapshot() {
        return lookupNodeListTextContent("resource-packages[1]/package");
    }

    default boolean hasDetectedUsedBySourceModulesFromExportSnapshot() {
        return lookupNode("used-by-source-modules[1]") != null;
    }

    default ReusableStream<ModuleDependency> detectedUsedBySourceModulesDependenciesFromExportSnapshot() {
        return lookupDependencies("used-by-source-modules[1]/module", ModuleDependency.Type.SOURCE, null);
    }

    default ReusableStream<String> usedRequiredJavaServicesFromExportSnapshot() {
        return lookupNodeListTextContent("used-services[1]/required-service");
    }

    default ReusableStream<String> usedOptionalJavaServicesFromExportSnapshot() {
        return lookupNodeListTextContent("used-services[1]/optional-service");
    }

    default Node javaPackageUsageNodeFromExportSnapshot(String javaPackage) {
        return lookupNode("/project[1]/export-snapshot[1]/usages[1]/java-package[@name='" + javaPackage + "'][1]");
    }

    default ReusableStream<String> modulesUsingJavaPackageFromExportSnapshot(String javaPackage) {
        return modulesUsingJavaPackageFromExportSnapshot(javaPackageUsageNodeFromExportSnapshot(javaPackage));
    }

    default ReusableStream<String> modulesUsingJavaPackageFromExportSnapshot(Node javaPackageUsageNode) {
        return XmlUtil.nodeListToTextContentReusableStream(XmlUtil.lookupElementList(javaPackageUsageNode, "module"));
    }

    default Node javaClassUsageNodeFromExportSnapshot(String javaClass) {
        return lookupNode("/project[1]/export-snapshot[1]/usages[1]/java-class[@name='" + javaClass + "'][1]");
    }

    default ReusableStream<String> modulesUsingJavaClassFromExportSnapshot(String javaClass) {
        return modulesUsingJavaClassFromExportSnapshot(javaClassUsageNodeFromExportSnapshot(javaClass));
    }

    default ReusableStream<String> modulesUsingJavaClassFromExportSnapshot(Node javaClassUsageNode) {
        return XmlUtil.nodeListToTextContentReusableStream(XmlUtil.lookupElementList(javaClassUsageNode, "module"));
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
            getModuleElement().addAttribute("executable", String.valueOf(executable));
        }
    }

    default void addProvider(String spiClassName, String providerClassName) {
        if (lookupNode("providers[1]/provider[@interface='" + spiClassName + "'][text() = '" + providerClassName + "'][1]") == null)
            appendElementWithTextContent("providers/provider", providerClassName, true).addAttribute("interface", spiClassName);
    }

    default boolean updateDocument(Document document) {
        // Nothing to update as this is the source
        return false;
    }
}
