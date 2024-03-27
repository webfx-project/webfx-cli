package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.core.*;
import dev.webfx.cli.core.Module;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Bruno Salmon
 */
public interface WebFxModuleFileDelegate extends WebFxModuleFile {

    WebFxModuleFile getDelegate();

    @Override
    default boolean isSnapshotVersion() {
        return getDelegate().isSnapshotVersion();
    }

    @Override
    default boolean fileExists() {
        return getDelegate().fileExists();
    }

    @Override
    default Module getModule() {
        return getDelegate().getModule();
    }

    @Override
    default ProjectModule getProjectModule() {
        return getDelegate().getProjectModule();
    }

    @Override
    default boolean isExecutable() {
        return getDelegate().isExecutable();
    }

    @Override
    default boolean requiresTimeZoneData() {
        return getDelegate().requiresTimeZoneData();
    }

    @Override
    default String getName() {
        return getDelegate().getName();
    }

    @Override
    default boolean hasMainJavaSourceDirectory() {
        return getDelegate().hasMainJavaSourceDirectory();
    }

    @Override
    default boolean hasMainWebFxSourceDirectory() {
        return getDelegate().hasMainWebFxSourceDirectory();
    }

    @Override
    default String getApplicationId() {
        return getDelegate().getApplicationId();
    }

    @Override
    default String getApplicationLabel() {
        return getDelegate().getApplicationLabel();
    }

    @Override
    default boolean isInterface() {
        return getDelegate().isInterface();
    }

    @Override
    default String getDeployRepositoryId() {
        return getDelegate().getDeployRepositoryId();
    }

    @Override
    default boolean hasAutoInjectionConditions() {
        return getDelegate().hasAutoInjectionConditions();
    }

    @Override
    default boolean isAggregate() {
        return getDelegate().isAggregate();
    }

    @Override
    default String getDescription() {
        return getDelegate().getDescription();
    }

    @Override
    default boolean shouldTakeChildrenModuleNamesFromPomInstead() {
        return getDelegate().shouldTakeChildrenModuleNamesFromPomInstead();
    }

    @Override
    default boolean shouldSubdirectoriesChildrenModulesBeAdded() {
        return getDelegate().shouldSubdirectoriesChildrenModulesBeAdded();
    }

    @Override
    default ReusableStream<String> getChildrenModuleNames() {
        return getDelegate().getChildrenModuleNames();
    }

    @Override
    default ReusableStream<String> getExplicitExportedPackages() {
        return getDelegate().getExplicitExportedPackages();
    }

    @Override
    default boolean areSourcePackagesAutomaticallyExported() {
        return getDelegate().areSourcePackagesAutomaticallyExported();
    }

    @Override
    default ReusableStream<String> getExcludedPackagesFromSourcePackages() {
        return getDelegate().getExcludedPackagesFromSourcePackages();
    }

    @Override
    default ReusableStream<String> getExplicitResourcePackages() {
        return getDelegate().getExplicitResourcePackages();
    }

    @Override
    default boolean areResourcePackagesAutomaticallyExported() {
        return getDelegate().areResourcePackagesAutomaticallyExported();
    }

    @Override
    default ReusableStream<String> implementedInterfaces() {
        return getDelegate().implementedInterfaces();
    }

    @Override
    default ReusableStream<ModuleProperty> getModuleProperties() {
        return getDelegate().getModuleProperties();
    }

    @Override
    default ReusableStream<ModuleProperty> getConfigurationVariables() {
        return getDelegate().getConfigurationVariables();
    }

    @Override
    default boolean areUsedBySourceModulesDependenciesAutomaticallyAdded() {
        return getDelegate().areUsedBySourceModulesDependenciesAutomaticallyAdded();
    }

    @Override
    default ReusableStream<ModuleDependency> getUndetectedUsedBySourceModulesDependencies() {
        return getDelegate().getUndetectedUsedBySourceModulesDependencies();
    }

    @Override
    default ReusableStream<String> getUndetectedUsedBySourcePackages() {
        return getDelegate().getUndetectedUsedBySourcePackages();
    }

    @Override
    default ReusableStream<ModuleDependency> getExplicitSourceModulesDependencies() {
        return getDelegate().getExplicitSourceModulesDependencies();
    }

    @Override
    default ReusableStream<ModuleDependency> getPluginModuleDependencies() {
        return getDelegate().getPluginModuleDependencies();
    }

    @Override
    default ReusableStream<ModuleDependency> getResourceModuleDependencies() {
        return getDelegate().getResourceModuleDependencies();
    }

    @Override
    default ReusableStream<LibraryModule> getRequiredWebFxLibraryModules() {
        return getDelegate().getRequiredWebFxLibraryModules();
    }

    @Override
    default ReusableStream<LibraryModule> getRequiredThirdPartyLibraryModules() {
        return getDelegate().getRequiredThirdPartyLibraryModules();
    }

    @Override
    default ReusableStream<String> getUsesPackagesAutoInjectionConditions() {
        return getDelegate().getUsesPackagesAutoInjectionConditions();
    }

    @Override
    default ReusableStream<String> getEmbedResources() {
        return getDelegate().getEmbedResources();
    }

    @Override
    default ReusableStream<String> getSystemProperties() {
        return getDelegate().getSystemProperties();
    }

    @Override
    default ReusableStream<String> getArrayNewInstanceClasses() {
        return getDelegate().getArrayNewInstanceClasses();
    }

    @Override
    default String getGraalVmReflectionJson() {
        return getDelegate().getGraalVmReflectionJson();
    }

    @Override
    default ReusableStream<ServiceProvider> providedServiceProviders() {
        return getDelegate().providedServiceProviders();
    }

    @Override
    default ReusableStream<MavenRepository> mavenRepositories() {
        return getDelegate().mavenRepositories();
    }

    @Override
    default ReusableStream<Node> getHtmlNodes() {
        return getDelegate().getHtmlNodes();
    }

    @Override
    default Node getMavenManualNode() {
        return getDelegate().getMavenManualNode();
    }

    @Override
    default boolean skipMavenPomUpdate() {
        return getDelegate().skipMavenPomUpdate();
    }

    @Override
    default boolean skipJavaModuleInfoUpdate() {
        return getDelegate().skipJavaModuleInfoUpdate();
    }

    @Override
    default boolean generatesExportSnapshot() {
        return getDelegate().generatesExportSnapshot();
    }

    @Override
    default ReusableStream<String> javaSourcePackagesFromExportSnapshot() {
        return getDelegate().javaSourcePackagesFromExportSnapshot();
    }

    @Override
    default ReusableStream<String> resourcePackagesFromExportSnapshot() {
        return getDelegate().resourcePackagesFromExportSnapshot();
    }

    @Override
    default boolean hasDetectedUsedBySourceModulesFromExportSnapshot() {
        return getDelegate().hasDetectedUsedBySourceModulesFromExportSnapshot();
    }

    @Override
    default ReusableStream<ModuleDependency> detectedUsedBySourceModulesDependenciesFromExportSnapshot() {
        return getDelegate().detectedUsedBySourceModulesDependenciesFromExportSnapshot();
    }

    @Override
    default ReusableStream<String> usedRequiredJavaServicesFromExportSnapshot() {
        return getDelegate().usedRequiredJavaServicesFromExportSnapshot();
    }

    @Override
    default ReusableStream<String> usedOptionalJavaServicesFromExportSnapshot() {
        return getDelegate().usedOptionalJavaServicesFromExportSnapshot();
    }

    @Override
    default Node javaPackageUsageNodeFromExportSnapshot(String javaPackage) {
        return getDelegate().javaPackageUsageNodeFromExportSnapshot(javaPackage);
    }

    @Override
    default ReusableStream<String> modulesUsingJavaPackageFromExportSnapshot(String javaPackage) {
        return getDelegate().modulesUsingJavaPackageFromExportSnapshot(javaPackage);
    }

    @Override
    default ReusableStream<String> modulesUsingJavaPackageFromExportSnapshot(Node javaPackageUsageNode) {
        return getDelegate().modulesUsingJavaPackageFromExportSnapshot(javaPackageUsageNode);
    }

    @Override
    default Node javaClassUsageNodeFromExportSnapshot(String javaClass) {
        return getDelegate().javaClassUsageNodeFromExportSnapshot(javaClass);
    }

    @Override
    default ReusableStream<String> modulesUsingJavaClassFromExportSnapshot(String javaClass) {
        return getDelegate().modulesUsingJavaClassFromExportSnapshot(javaClass);
    }

    @Override
    default ReusableStream<String> modulesUsingJavaClassFromExportSnapshot(Node javaClassUsageNode) {
        return getDelegate().modulesUsingJavaClassFromExportSnapshot(javaClassUsageNode);
    }

    @Override
    default Document createInitialDocument() {
        return getDelegate().createInitialDocument();
    }

    @Override
    default void setExecutable(boolean executable) {
        getDelegate().setExecutable(executable);
    }

    @Override
    default void addProvider(String spiClassName, String providerClassName) {
        getDelegate().addProvider(spiClassName, providerClassName);
    }

    @Override
    default boolean updateDocument(Document document) {
        return getDelegate().updateDocument(document);
    }

    @Override
    default String getGroupId() {
        return getDelegate().getGroupId();
    }

    @Override
    default String getArtifactId() {
        return getDelegate().getArtifactId();
    }

    @Override
    default String getVersion() {
        return getDelegate().getVersion();
    }

    @Override
    default String lookupGroupId() {
        return getDelegate().lookupGroupId();
    }

    @Override
    default String lookupArtifactId() {
        return getDelegate().lookupArtifactId();
    }

    @Override
    default String lookupVersion() {
        return getDelegate().lookupVersion();
    }

    @Override
    default String lookupType() {
        return getDelegate().lookupType();
    }

    @Override
    default String lookupParentGroupId() {
        return getDelegate().lookupParentGroupId();
    }

    @Override
    default String lookupParentVersion() {
        return getDelegate().lookupParentVersion();
    }

    @Override
    default String lookupParentName() {
        return getDelegate().lookupParentName();
    }

    @Override
    default Element getXmlNode() {
        return getDelegate().getXmlNode();
    }

    @Override
    default Element getModuleElement() {
        return getDelegate().getModuleElement();
    }

    @Override
    default ReusableStream<ModuleDependency> lookupDependencies(String xPathExpression, ModuleDependency.Type type, String defaultScope) {
        return getDelegate().lookupDependencies(xPathExpression, type, defaultScope);
    }

    @Override
    Document getDocument();

    @Override
    void setDocument(Document document);

    @Override
    default Document getOrCreateDocument() {
        return getDelegate().getOrCreateDocument();
    }

    @Override
    default void createDocument() {
        getDelegate().createDocument();
    }

    @Override
    default Node getOrCreateXmlNode() {
        return getDelegate().getOrCreateXmlNode();
    }

    @Override
    default String getXmlContent() {
        return getDelegate().getXmlContent();
    }

    @Override
    default NodeList lookupNodeList(String xpathExpression) {
        return getDelegate().lookupNodeList(xpathExpression);
    }

    @Override
    default Node lookupNode(String xpathExpression) {
        return getDelegate().lookupNode(xpathExpression);
    }

    @Override
    default Node lookupOrCreateNode(String xpath) {
        return getDelegate().lookupOrCreateNode(xpath);
    }

    @Override
    default Node lookupOrCreateAndAppendNode(String xpath, boolean... linefeeds) {
        return getDelegate().lookupOrCreateAndAppendNode(xpath, linefeeds);
    }

    @Override
    default Element createAndAppendElement(String xpath, boolean... linefeeds) {
        return getDelegate().createAndAppendElement(xpath, linefeeds);
    }

    @Override
    default Element appendElementWithTextContentIfNotAlreadyExists(String xpath, String text, boolean... linefeeds) {
        return getDelegate().appendElementWithTextContentIfNotAlreadyExists(xpath, text, linefeeds);
    }

    @Override
    default Element prependElementWithTextContentIfNotAlreadyExists(String xpath, String text, boolean... linefeeds) {
        return getDelegate().prependElementWithTextContentIfNotAlreadyExists(xpath, text, linefeeds);
    }

    @Override
    default Node lookupNodeWithTextContent(String xpath, String text) {
        return getDelegate().lookupNodeWithTextContent(xpath, text);
    }

    @Override
    default void appendIndentNode(Node node, boolean linefeed) {
        getDelegate().appendIndentNode(node, linefeed);
    }

    @Override
    default void prependIndentNode(Node node, boolean linefeed) {
        getDelegate().prependIndentNode(node, linefeed);
    }

    @Override
    default Element appendElementWithTextContent(String xpath, String text, boolean... linefeeds) {
        return getDelegate().appendElementWithTextContent(xpath, text, linefeeds);
    }

    @Override
    default Element prependElementWithTextContent(String xpath, String text, boolean... linefeeds) {
        return getDelegate().prependElementWithTextContent(xpath, text, linefeeds);
    }

    @Override
    default Element createAndPrependElement(String xpath, boolean... linefeeds) {
        return getDelegate().createAndPrependElement(xpath, linefeeds);
    }

    @Override
    default String lookupNodeTextContent(String xpathExpression) {
        return getDelegate().lookupNodeTextContent(xpathExpression);
    }

    @Override
    default ReusableStream<String> lookupNodeListTextContent(String xPathExpression) {
        return getDelegate().lookupNodeListTextContent(xPathExpression);
    }
}
