package dev.webfx.buildtool;

import dev.webfx.buildtool.modulefiles.MavenPomModuleFile;
import dev.webfx.buildtool.modulefiles.WebFxModuleFile;
import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.util.Optional;

/**
 * @author Bruno Salmon
 */
public interface ProjectModule extends Module {

    ProjectModule getParentModule();

        /*************************
         ***** Basic streams *****
         *************************/

    default ReusableStream<String> getChildrenModuleNames() {
        return ReusableStream.create(() -> getMavenModuleFile().getChildrenModuleNames());
    }

    ReusableStream<String> getSubdirectoriesChildrenModules();

    ReusableStream<ProjectModule> getChildrenModules();

    default ReusableStream<ProjectModule> getThisAndChildrenModules() {
        return ReusableStream.concat(ReusableStream.of(this), getChildrenModules());
    }

    ReusableStream<ProjectModule> getChildrenModulesInDepth();

    default ReusableStream<ProjectModule> getThisAndChildrenModulesInDepth() {
        return ReusableStream.concat(ReusableStream.of(this), getChildrenModulesInDepth());
    }

    MavenPomModuleFile getMavenModuleFile();

    WebFxModuleFile getWebFxModuleFile();

    ProjectModule getOrCreateChildProjectModule(String name);

    default void registerLibraryModules() {
        getWebFxModuleFile().getLibraryModules().forEach(getRootModule()::registerLibraryModule);
    }

    default ReusableStream<String> getResourcePackages() {
        return getWebFxModuleFile().getResourcePackages();
    }

    default ReusableStream<String> getEmbedResources() {
        return getWebFxModuleFile().getEmbedResources();
    }

    default ReusableStream<String> getSystemProperties() {
        return getWebFxModuleFile().getSystemProperties();
    }

    default boolean isExecutable() {
        //return getArtifactId().contains("-application-") && getTarget().isMonoPlatform();
        return getWebFxModuleFile().isExecutable();
    }

    default boolean isExecutable(Platform platform) {
        return isExecutable() && getTarget().isPlatformSupported(platform);
    }

    default boolean isInterface() {
        return getWebFxModuleFile().isInterface();
    }

    default boolean isAutomatic() {
        return getWebFxModuleFile().isAutomatic();
    }

    default boolean isAggregate() {
        return getMavenModuleFile().isAggregate() || getWebFxModuleFile().isAggregate();
    }

    default boolean isImplementingInterface() {
        return implementedInterfaces().count() > 0;
    }

    default ReusableStream<String> implementedInterfaces() {
        return getWebFxModuleFile().implementedInterfaces();
    }

    default ReusableStream<String> getProvidedJavaServiceImplementations(String javaService, boolean replaceDollarWithDot) {
        // Providers declared in the webfx module file
        ReusableStream<String> implementations = getWebFxModuleFile().providedServerProviders()
                .filter(p -> p.getSpi().equals(javaService))
                .map(ServiceProvider::getImplementation);
        if (replaceDollarWithDot)
            implementations = implementations.map(s -> s.replace('$', '.'));
        return implementations;
    }

    default ReusableStream<String> getExportedJavaPackages() {
        ReusableStream<String> exportedPackages = getWebFxModuleFile().getExplicitExportedPackages();
        if (getWebFxModuleFile().areSourcePackagesAutomaticallyExported()) {
            exportedPackages = ReusableStream.concat(getDeclaredJavaPackages(), exportedPackages).distinct();
            ReusableStream<String> excludedPackages = getWebFxModuleFile().getExcludedPackagesFromSourcePackages().cache();
            if (excludedPackages.count() > 0)
                exportedPackages = exportedPackages.filter(p -> excludedPackages.noneMatch(p::equals));
        }
        return exportedPackages;
    }


    RootModule getRootModule();

    ReusableStream<String> getDeclaredJavaPackages();

    ReusableStream<JavaFile> getDeclaredJavaFiles();

    default ModuleRegistry getModuleRegistry() {
        return getRootModule().getModuleRegistry();
    }

    /******************************
     ***** Analyzing streams  *****
     ******************************/

    ReusableStream<String> getUsedJavaPackages();

    default boolean usesJavaPackage(String javaPackage) {
        return getUsedJavaPackages().anyMatch(javaPackage::equals);
    }

    ReusableStream<String> getUsedRequiredJavaServices();

    ReusableStream<String> getUsedOptionalJavaServices();

    ReusableStream<String> getUsedJavaServices();

    ReusableStream<String> getDeclaredJavaServices();

    ReusableStream<String> getProvidedJavaServices();

    default boolean declaresJavaService(String javaService) {
        return getDeclaredJavaServices().anyMatch(javaService::equals);
    }

    default boolean providesJavaService(String javaService) {
        return getProvidedJavaServices()
                .anyMatch(javaService::equals)
                ;
    }

    ReusableStream<Providers> getExecutableProviders();

    ///// Dependencies
    ReusableStream<ModuleDependency> getDirectDependencies();

    ReusableStream<ModuleDependency> getTransitiveDependencies();

    ReusableStream<ModuleDependency> getTransitiveDependenciesWithoutImplicitProviders();

    ReusableStream<ModuleDependency> getDiscoveredByCodeAnalyzerSourceDependencies();

    ReusableStream<ModuleDependency> getDirectDependenciesWithoutFinalExecutableResolutions();

    default ReusableStream<Module> getDirectModules() {
        return mapDestinationModules(getDirectDependencies());
    }

    default ReusableStream<Module> getThisAndDirectModules() {
        return ReusableStream.concat(
                ReusableStream.of(this),
                getDirectModules()
        );
    }

    default ReusableStream<ProjectModule> getThisOrChildrenModulesInDepthDirectlyDependingOn(String moduleArtifactId) {
        return getThisAndChildrenModulesInDepth()
                .filter(module -> module.isDirectlyDependingOn(moduleArtifactId))
                ;
    }

    default boolean isDirectlyDependingOn(String moduleName) {
        return getDirectModules().anyMatch(m -> moduleName.equals(m.getName()));
    }

    default ReusableStream<Module> getTransitiveModules() {
        return mapDestinationModules(getTransitiveDependencies());
    }

    default ReusableStream<Module> getThisAndTransitiveModules() {
        return ReusableStream.concat(
                ReusableStream.of(this),
                getTransitiveDependencies().map(ModuleDependency::getDestinationModule)
        );
    }

    default boolean implementsModule(Module module) {
        return this != module && (getName().startsWith(module.getName()) || getWebFxModuleFile().implementedInterfaces().anyMatch(m -> module.getName().equals(m)));
    }

    Target getTarget();

    default boolean isCompatibleWithTargetModule(ProjectModule targetModule) {
        return isCompatibleWithTarget(targetModule.getTarget());
    }

    default boolean isCompatibleWithTarget(Target target) {
        return gradeTargetMatch(target) >= 0;
    }

    default int gradeTargetMatch(Target target) {
        return getTarget().gradeTargetMatch(target);
    }

    default ProjectModule searchProjectModuleWithinSearchScopeWithoutRegisteringLibrariesAndPackages(String name) {
        return searchProjectModuleWithinSearchScopeWithoutRegisteringLibrariesAndPackages(name, false);
    }

    default ProjectModule searchProjectModuleWithinSearchScopeWithoutRegisteringLibrariesAndPackages(String name, boolean silent) {
        Optional<ProjectModule> projectModule;
        projectModule = getProjectModuleSearchScope() // Trying first in the scope of this project
                .filter(module -> module.getName().equals(name))
                .findFirst()
                .or(() -> getRootModule().getProjectModuleSearchScope() // Otherwise in the widest scope
                        .filter(module -> module.getName().equals(name))
                        .findFirst());
        if (projectModule.isPresent())
            return projectModule.get();
        if (silent)
            return null;
        throw new UnresolvedException("Unable to find " + name + " module under " + getName() + " module");
    }

    default ReusableStream<ProjectModule> getProjectModuleSearchScope() {
        return getChildrenModulesInDepth();
    }

    //// Static utility methods

    static ReusableStream<ProjectModule> filterProjectModules(ReusableStream<Module> modules) {
        return modules
                .filter(m -> m instanceof ProjectModule)
                .map(m -> (ProjectModule) m);
    }

    static boolean modulesUsesJavaPackage(ReusableStream<ProjectModule> modules, String javaPackage) {
        return modules.anyMatch(m -> m.usesJavaPackage(javaPackage));
    }

    static boolean modulesUsesJavaClass(ReusableStream<ProjectModule> modules, String javaClass) {
        int lastDotIndex = javaClass.lastIndexOf('.');
        String packageName = javaClass.substring(0, lastDotIndex);
        boolean excludeWebFxKit = packageName.startsWith("javafx.");
        return modules.anyMatch(m -> {
            if (excludeWebFxKit && m.getName().startsWith("webfx-kit-"))
                return false;
            return m.usesJavaPackage(packageName) && m.getDeclaredJavaFiles().anyMatch(jc -> jc.usesJavaClass(javaClass));
        });
    }

    static ReusableStream<ProjectModule> filterDestinationProjectModules(ReusableStream<ModuleDependency> dependencies) {
        return filterProjectModules(mapDestinationModules(dependencies));
    }

    static ReusableStream<Module> mapDestinationModules(ReusableStream<ModuleDependency> dependencies) {
        return dependencies.map(ModuleDependency::getDestinationModule);
    }

}