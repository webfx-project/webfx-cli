package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.core.*;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Bruno Salmon
 */
public class WebFxModuleFileCache implements WebFxModuleFileDelegate {

    private final WebFxModuleFile webFxModuleFile;

    public WebFxModuleFileCache(WebFxModuleFile webFxModuleFile) {
        this.webFxModuleFile = webFxModuleFile;
    }

    @Override
    public WebFxModuleFile getDelegate() {
        return webFxModuleFile;
    }

    private Boolean executable;

    @Override
    public boolean isExecutable() {
        if (executable == null)
            executable = WebFxModuleFileDelegate.super.isExecutable();
        return executable;
    }

    private Boolean pwa;

    @Override
    public boolean isPwa() {
        if (pwa == null)
            pwa = WebFxModuleFileDelegate.super.isPwa();
        return pwa;
    }

    private Boolean requiresTimeZoneData;
    @Override
    public boolean requiresTimeZoneData() {
        if (requiresTimeZoneData == null)
            requiresTimeZoneData = WebFxModuleFileDelegate.super.requiresTimeZoneData();
        return requiresTimeZoneData;
    }

    private String name;
    @Override
    public String getName() {
        if (name == null)
            name = WebFxModuleFileDelegate.super.getName();
        return name;
    }

    private Boolean hasMainJavaSourceDirectory;
    @Override
    public boolean hasMainJavaSourceDirectory() {
        if (hasMainJavaSourceDirectory == null)
            hasMainJavaSourceDirectory = WebFxModuleFileDelegate.super.hasMainJavaSourceDirectory();
        return hasMainJavaSourceDirectory;
    }

    private Boolean hasMainWebFxSourceDirectory;
    @Override
    public boolean hasMainWebFxSourceDirectory() {
        if (hasMainWebFxSourceDirectory == null)
            hasMainWebFxSourceDirectory = WebFxModuleFileDelegate.super.hasMainWebFxSourceDirectory();
        return hasMainWebFxSourceDirectory;
    }

    private String applicationId;
    @Override
    public String getApplicationId() {
        if (applicationId == null)
            applicationId = WebFxModuleFileDelegate.super.getApplicationId();
        return applicationId;
    }

    private String applicationLabel;
    @Override
    public String getApplicationLabel() {
        if (applicationLabel == null)
            applicationLabel = WebFxModuleFileDelegate.super.getApplicationLabel();
        return applicationLabel;
    }

    private Boolean isInterface;
    @Override
    public boolean isInterface() {
        if (isInterface == null)
            isInterface = WebFxModuleFileDelegate.super.isInterface();
        return isInterface;
    }

    private Boolean isPreview;
    @Override
    public boolean isPreview() {
        if (isPreview == null)
            isPreview = WebFxModuleFileDelegate.super.isPreview();
        return isPreview;
    }

    private Boolean isDeprecated;
    @Override
    public boolean isDeprecated() {
        if (isDeprecated == null)
            isDeprecated = WebFxModuleFileDelegate.super.isDeprecated();
        return isDeprecated;
    }

    private String deployRepositoryId;
    @Override
    public String getDeployRepositoryId() {
        if (deployRepositoryId == null)
            deployRepositoryId = WebFxModuleFileDelegate.super.getDeployRepositoryId();
        return deployRepositoryId;
    }

    private Boolean hasAutoInjectionConditions;
    @Override
    public boolean hasAutoInjectionConditions() {
        if (hasAutoInjectionConditions == null)
            hasAutoInjectionConditions = WebFxModuleFileDelegate.super.hasAutoInjectionConditions();
        return hasAutoInjectionConditions;
    }

    private Boolean isAggregate;
    @Override
    public boolean isAggregate() {
        if (isAggregate == null)
            isAggregate = WebFxModuleFileDelegate.super.isAggregate();
        return isAggregate;
    }

    private String description;
    @Override
    public String getDescription() {
        if (description == null)
            description = WebFxModuleFileDelegate.super.getDescription();
        return description;
    }

    private Boolean shouldTakeChildrenModuleNamesFromPomInstead;
    @Override
    public boolean shouldTakeChildrenModuleNamesFromPomInstead() {
        if (shouldTakeChildrenModuleNamesFromPomInstead == null)
            shouldTakeChildrenModuleNamesFromPomInstead = WebFxModuleFileDelegate.super.shouldTakeChildrenModuleNamesFromPomInstead();
        return shouldTakeChildrenModuleNamesFromPomInstead;
    }

    private Boolean shouldSubdirectoriesChildrenModulesBeAdded;
    @Override
    public boolean shouldSubdirectoriesChildrenModulesBeAdded() {
        if (shouldSubdirectoriesChildrenModulesBeAdded == null)
            shouldSubdirectoriesChildrenModulesBeAdded = WebFxModuleFileDelegate.super.shouldSubdirectoriesChildrenModulesBeAdded();
        return shouldSubdirectoriesChildrenModulesBeAdded;
    }

    private ReusableStream<String> childrenModuleNames;
    @Override
    public ReusableStream<String> getChildrenModuleNames() {
        if (childrenModuleNames == null)
            childrenModuleNames = WebFxModuleFileDelegate.super.getChildrenModuleNames().cache();
        return childrenModuleNames;
    }

    private ReusableStream<String> explicitExportedPackages;
    @Override
    public ReusableStream<String> getExplicitExportedPackages() {
        if (explicitExportedPackages == null)
            explicitExportedPackages = WebFxModuleFileDelegate.super.getExplicitExportedPackages().cache();
        return explicitExportedPackages;
    }

    private Boolean areSourcePackagesAutomaticallyExported;
    @Override
    public boolean areSourcePackagesAutomaticallyExported() {
        if (areSourcePackagesAutomaticallyExported == null)
            areSourcePackagesAutomaticallyExported = WebFxModuleFileDelegate.super.areSourcePackagesAutomaticallyExported();
        return areSourcePackagesAutomaticallyExported;
    }

    private ReusableStream<String> excludedPackagesFromSourcePackages;
    @Override
    public ReusableStream<String> getExcludedPackagesFromSourcePackages() {
        if (excludedPackagesFromSourcePackages == null)
            excludedPackagesFromSourcePackages = WebFxModuleFileDelegate.super.getExcludedPackagesFromSourcePackages();
        return excludedPackagesFromSourcePackages;
    }

    private ReusableStream<String> explicitResourcePackages;
    @Override
    public ReusableStream<String> getExplicitResourcePackages() {
        if (explicitResourcePackages == null)
            explicitResourcePackages = WebFxModuleFileDelegate.super.getExplicitResourcePackages().cache();
        return explicitResourcePackages;
    }

    private Boolean areResourcePackagesAutomaticallyExported;
    @Override
    public boolean areResourcePackagesAutomaticallyExported() {
        if (areResourcePackagesAutomaticallyExported == null)
            areResourcePackagesAutomaticallyExported = WebFxModuleFileDelegate.super.areResourcePackagesAutomaticallyExported();
        return areResourcePackagesAutomaticallyExported;
    }

    private ReusableStream<String> implementedInterfaces;
    @Override
    public ReusableStream<String> implementedInterfaces() {
        if (implementedInterfaces == null)
            implementedInterfaces = WebFxModuleFileDelegate.super.implementedInterfaces().cache();
        return implementedInterfaces;
    }

    private ReusableStream<ModuleProperty> moduleProperties;
    @Override
    public ReusableStream<ModuleProperty> getModuleProperties() {
        if (moduleProperties == null)
            moduleProperties = WebFxModuleFileDelegate.super.getModuleProperties().cache();
        return moduleProperties;
    }

    private ReusableStream<ModuleProperty> configurationVariables;
    @Override
    public ReusableStream<ModuleProperty> getConfigurationVariables() {
        if (configurationVariables == null)
            configurationVariables = WebFxModuleFileDelegate.super.getConfigurationVariables().cache();
        return configurationVariables;
    }

    private Boolean areUsedBySourceModulesDependenciesAutomaticallyAdded;
    @Override
    public boolean areUsedBySourceModulesDependenciesAutomaticallyAdded() {
        if (areUsedBySourceModulesDependenciesAutomaticallyAdded == null)
            areUsedBySourceModulesDependenciesAutomaticallyAdded = WebFxModuleFileDelegate.super.areUsedBySourceModulesDependenciesAutomaticallyAdded();
        return areUsedBySourceModulesDependenciesAutomaticallyAdded;
    }

    private ReusableStream<ModuleDependency> undetectedUsedBySourceModulesDependencies;
    @Override
    public ReusableStream<ModuleDependency> getUndetectedUsedBySourceModulesDependencies() {
        if (undetectedUsedBySourceModulesDependencies == null)
            undetectedUsedBySourceModulesDependencies = WebFxModuleFileDelegate.super.getUndetectedUsedBySourceModulesDependencies().cache();
        return undetectedUsedBySourceModulesDependencies;
    }

    private ReusableStream<String> undetectedUsedBySourcePackages;
    @Override
    public ReusableStream<String> getUndetectedUsedBySourcePackages() {
        if (undetectedUsedBySourcePackages == null)
            undetectedUsedBySourcePackages = WebFxModuleFileDelegate.super.getUndetectedUsedBySourcePackages().cache();
        return undetectedUsedBySourcePackages;
    }

    public ReusableStream<ModuleDependency> explicitSourceModulesDependencies;
    @Override
    public ReusableStream<ModuleDependency> getExplicitSourceModulesDependencies() {
        if (explicitSourceModulesDependencies == null)
            explicitSourceModulesDependencies = WebFxModuleFileDelegate.super.getExplicitSourceModulesDependencies().cache();
        return explicitSourceModulesDependencies;
    }

    private ReusableStream<ModuleDependency> pluginModuleDependencies;
    @Override
    public ReusableStream<ModuleDependency> getPluginModuleDependencies() {
        if (pluginModuleDependencies == null)
            pluginModuleDependencies = WebFxModuleFileDelegate.super.getPluginModuleDependencies().cache();
        return pluginModuleDependencies;
    }

    private ReusableStream<ModuleDependency> resourceModuleDependencies;
    @Override
    public ReusableStream<ModuleDependency> getResourceModuleDependencies() {
        if (resourceModuleDependencies == null)
            resourceModuleDependencies = WebFxModuleFileDelegate.super.getResourceModuleDependencies().cache();
        return resourceModuleDependencies;
    }

    private ReusableStream<LibraryModule> requiredWebFxLibraryModules;
    @Override
    public ReusableStream<LibraryModule> getRequiredWebFxLibraryModules() {
        if (requiredWebFxLibraryModules == null)
            requiredWebFxLibraryModules = WebFxModuleFileDelegate.super.getRequiredWebFxLibraryModules().cache();
        return requiredWebFxLibraryModules;
    }

    private ReusableStream<LibraryModule> requiredThirdPartyLibraryModules;
    @Override
    public ReusableStream<LibraryModule> getRequiredThirdPartyLibraryModules() {
        if (requiredThirdPartyLibraryModules == null)
            requiredThirdPartyLibraryModules = WebFxModuleFileDelegate.super.getRequiredThirdPartyLibraryModules().cache();
        return requiredThirdPartyLibraryModules;
    }

    private ReusableStream<String> usesPackagesAutoInjectionConditions;
    @Override
    public ReusableStream<String> getUsesPackagesAutoInjectionConditions() {
        if (usesPackagesAutoInjectionConditions == null)
            usesPackagesAutoInjectionConditions = WebFxModuleFileDelegate.super.getUsesPackagesAutoInjectionConditions().cache();
        return usesPackagesAutoInjectionConditions;
    }

    private ReusableStream<String> embedResources;
    @Override
    public ReusableStream<String> getEmbedResources() {
        if (embedResources == null)
            embedResources = WebFxModuleFileDelegate.super.getEmbedResources().cache();
        return embedResources;
    }

    private ReusableStream<String> systemProperties;
    @Override
    public ReusableStream<String> getSystemProperties() {
        if (systemProperties == null)
            systemProperties = WebFxModuleFileDelegate.super.getSystemProperties().cache();
        return systemProperties;
    }

    private ReusableStream<String> arrayNewInstanceClasses;
    @Override
    public ReusableStream<String> getArrayNewInstanceClasses() {
        if (arrayNewInstanceClasses == null)
            arrayNewInstanceClasses = WebFxModuleFileDelegate.super.getArrayNewInstanceClasses().cache();
        return arrayNewInstanceClasses;
    }

    /*private String graalVmReflectionJson;
    @Override
    public String getGraalVmReflectionJson() {
        if (graalVmReflectionJson == null)
            graalVmReflectionJson = WebFxModuleFileDelegate.super.getGraalVmReflectionJson();
        return graalVmReflectionJson;
    }*/

    private String i18nJavaKeysClass;
    @Override
    public String getI18nJavaKeysClass() {
        if (i18nJavaKeysClass == null)
            i18nJavaKeysClass = WebFxModuleFileDelegate.super.getI18nJavaKeysClass();
        return i18nJavaKeysClass;
    }

    private String cssJavaSelectorsClass;
    @Override
    public String getCssJavaSelectorsClass() {
        if (cssJavaSelectorsClass == null)
            cssJavaSelectorsClass = WebFxModuleFileDelegate.super.getCssJavaSelectorsClass();
        return cssJavaSelectorsClass;
    }

    private ReusableStream<ServiceProvider> providedServiceProviders;
    @Override
    public ReusableStream<ServiceProvider> providedServiceProviders() {
        if (providedServiceProviders == null)
            providedServiceProviders = WebFxModuleFileDelegate.super.providedServiceProviders().cache();
        return providedServiceProviders;
    }

    private ReusableStream<MavenRepository> mavenRepositories;
    @Override
    public ReusableStream<MavenRepository> mavenRepositories() {
        if (mavenRepositories == null)
            mavenRepositories = WebFxModuleFileDelegate.super.mavenRepositories().cache();
        return mavenRepositories;
    }

    private ReusableStream<Element> htmlNode;
    @Override
    public ReusableStream<Element> getHtmlNodes() {
        if (htmlNode == null)
            htmlNode = WebFxModuleFileDelegate.super.getHtmlNodes().cache();
        return htmlNode;
    }

    private Element mavenManualNode;
    @Override
    public Element getMavenManualNode() {
        if (mavenManualNode == null)
            mavenManualNode = WebFxModuleFileDelegate.super.getMavenManualNode();
        return mavenManualNode;
    }

    private Boolean skipMavenPomUpdate;
    @Override
    public boolean skipMavenPomUpdate() {
        if (skipMavenPomUpdate == null)
            skipMavenPomUpdate = WebFxModuleFileDelegate.super.skipMavenPomUpdate();
        return skipMavenPomUpdate;
    }

    private Boolean skipJavaModuleInfoUpdate;
    @Override
    public boolean skipJavaModuleInfoUpdate() {
        if (skipJavaModuleInfoUpdate == null)
            skipJavaModuleInfoUpdate = WebFxModuleFileDelegate.super.skipJavaModuleInfoUpdate();
        return skipJavaModuleInfoUpdate;
    }

    private ReusableStream<JavaCallbacks> javaCallbacks;
    @Override
    public ReusableStream<JavaCallbacks> getJavaCallbacks() {
        if (javaCallbacks == null)
            javaCallbacks = WebFxModuleFileDelegate.super.getJavaCallbacks();
        return javaCallbacks;
    }

    private Boolean generatesExportSnapshot;
    @Override
    public boolean generatesExportSnapshot() {
        if (generatesExportSnapshot == null)
            generatesExportSnapshot = WebFxModuleFileDelegate.super.generatesExportSnapshot();
        return generatesExportSnapshot;
    }

    private ReusableStream<String> javaSourcePackagesFromExportSnapshot;
    @Override
    public ReusableStream<String> javaSourcePackagesFromExportSnapshot() {
        if (javaSourcePackagesFromExportSnapshot == null)
            javaSourcePackagesFromExportSnapshot = WebFxModuleFileDelegate.super.javaSourcePackagesFromExportSnapshot().cache();
        return javaSourcePackagesFromExportSnapshot;
    }

    private ReusableStream<String> resourcePackagesFromExportSnapshot;
    @Override
    public ReusableStream<String> resourcePackagesFromExportSnapshot() {
        if (resourcePackagesFromExportSnapshot == null)
            resourcePackagesFromExportSnapshot = WebFxModuleFileDelegate.super.resourcePackagesFromExportSnapshot().cache();
        return resourcePackagesFromExportSnapshot;
    }

    private Boolean hasDetectedUsedBySourceModulesFromExportSnapshot;
    @Override
    public boolean hasDetectedUsedBySourceModulesFromExportSnapshot() {
        if (hasDetectedUsedBySourceModulesFromExportSnapshot == null)
            hasDetectedUsedBySourceModulesFromExportSnapshot = WebFxModuleFileDelegate.super.hasDetectedUsedBySourceModulesFromExportSnapshot();
        return hasDetectedUsedBySourceModulesFromExportSnapshot;
    }

    private ReusableStream<ModuleDependency> detectedUsedBySourceModulesDependenciesFromExportSnapshot;
    @Override
    public ReusableStream<ModuleDependency> detectedUsedBySourceModulesDependenciesFromExportSnapshot() {
        if (detectedUsedBySourceModulesDependenciesFromExportSnapshot == null)
            detectedUsedBySourceModulesDependenciesFromExportSnapshot = WebFxModuleFileDelegate.super.detectedUsedBySourceModulesDependenciesFromExportSnapshot().cache();
        return detectedUsedBySourceModulesDependenciesFromExportSnapshot;
    }

    private ReusableStream<String> usedRequiredJavaServicesFromExportSnapshot;
    @Override
    public ReusableStream<String> usedRequiredJavaServicesFromExportSnapshot() {
        if (usedRequiredJavaServicesFromExportSnapshot == null)
            usedRequiredJavaServicesFromExportSnapshot = WebFxModuleFileDelegate.super.usedRequiredJavaServicesFromExportSnapshot().cache();
        return usedRequiredJavaServicesFromExportSnapshot;
    }

    private ReusableStream<String> usedOptionalJavaServicesFromExportSnapshot;
    @Override
    public ReusableStream<String> usedOptionalJavaServicesFromExportSnapshot() {
        if (usedOptionalJavaServicesFromExportSnapshot == null)
            usedOptionalJavaServicesFromExportSnapshot = WebFxModuleFileDelegate.super.usedOptionalJavaServicesFromExportSnapshot().cache();
        return usedOptionalJavaServicesFromExportSnapshot;
    }

    private Node javaPackageUsageNodeFromExportSnapshot;
    @Override
    public Node javaPackageUsageNodeFromExportSnapshot(String javaPackage) {
        if (javaPackageUsageNodeFromExportSnapshot == null)
            javaPackageUsageNodeFromExportSnapshot = WebFxModuleFileDelegate.super.javaPackageUsageNodeFromExportSnapshot(javaPackage);
        return javaPackageUsageNodeFromExportSnapshot;
    }

    private final Map<String, ReusableStream<String>> modulesUsingJavaPackageFromExportSnapshotCache = new HashMap<>();
    @Override
    public ReusableStream<String> modulesUsingJavaPackageFromExportSnapshot(String javaPackage) {
        ReusableStream<String> stream = modulesUsingJavaPackageFromExportSnapshotCache.get(javaPackage);
        if (stream == null) {
            stream = WebFxModuleFileDelegate.super.modulesUsingJavaPackageFromExportSnapshot(javaPackage).cache();
            modulesUsingJavaPackageFromExportSnapshotCache.put(javaPackage, stream);
        }
        return stream;
    }

    private final Map<Node, ReusableStream<String>> modulesUsingJavaPackageFromExportSnapshotNodeCache = new HashMap<>();
    @Override
    public ReusableStream<String> modulesUsingJavaPackageFromExportSnapshot(Node javaPackageUsageNode) {
        ReusableStream<String> stream = modulesUsingJavaPackageFromExportSnapshotNodeCache.get(javaPackageUsageNode);
        if (stream == null) {
            stream = WebFxModuleFileDelegate.super.modulesUsingJavaPackageFromExportSnapshot(javaPackageUsageNode).cache();
            modulesUsingJavaPackageFromExportSnapshotNodeCache.put(javaPackageUsageNode, stream);
        }
        return stream;
    }

    private final Map<String, Node> javaClassUsageNodeFromExportSnapshotCache = new HashMap<>();

    @Override
    public Node javaClassUsageNodeFromExportSnapshot(String javaClass) {
        Node node = javaClassUsageNodeFromExportSnapshotCache.get(javaClass);
        if (node == null) {
            node = WebFxModuleFileDelegate.super.javaClassUsageNodeFromExportSnapshot(javaClass);
            javaClassUsageNodeFromExportSnapshotCache.put(javaClass, node);
        }
        return node;
    }

    private final Map<String, ReusableStream<String>> modulesUsingJavaClassFromExportSnapshotCache = new HashMap<>();
    @Override
    public ReusableStream<String> modulesUsingJavaClassFromExportSnapshot(String javaClass) {
        ReusableStream<String> stream = modulesUsingJavaClassFromExportSnapshotCache.get(javaClass);
        if (stream == null) {
            stream = WebFxModuleFileDelegate.super.modulesUsingJavaClassFromExportSnapshot(javaClass).cache();
            modulesUsingJavaClassFromExportSnapshotCache.put(javaClass, stream);
        }
        return stream;
    }

    private final Map<Node, ReusableStream<String>> modulesUsingJavaClassFromExportSnapshotNodeCache = new HashMap<>();
    @Override
    public ReusableStream<String> modulesUsingJavaClassFromExportSnapshot(Node javaClassUsageNode) {
        ReusableStream<String> stream = modulesUsingJavaClassFromExportSnapshotNodeCache.get(javaClassUsageNode);
        if (stream == null) {
            stream = WebFxModuleFileDelegate.super.modulesUsingJavaClassFromExportSnapshot(javaClassUsageNode).cache();
            modulesUsingJavaClassFromExportSnapshotNodeCache.put(javaClassUsageNode, stream);
        }
        return stream;
    }

    @Override
    public Document createInitialDocument() {
        return WebFxModuleFileDelegate.super.createInitialDocument();
    }

    @Override
    public void setExecutable(boolean executable) {
        WebFxModuleFileDelegate.super.setExecutable(executable);
    }

    @Override
    public void addProvider(String spiClassName, String providerClassName) {
        WebFxModuleFileDelegate.super.addProvider(spiClassName, providerClassName);
    }

    @Override
    public boolean updateDocument(Document document) {
        return WebFxModuleFileDelegate.super.updateDocument(document);
    }

    @Override
    public Document getDocument() {
        return getDelegate().getDocument();
    }

    @Override
    public void setDocument(Document document) {
        getDelegate().setDocument(document);
    }

    @Override
    public boolean fileExists() {
        return getDelegate().fileExists();
    }

    @Override
    public Path getModuleFilePath() {
        return getDelegate().getModuleFilePath();
    }
}
