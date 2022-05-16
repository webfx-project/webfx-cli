package dev.webfx.tool.cli.core;

import dev.webfx.tool.cli.modulefiles.abstr.MavenPomModuleFile;
import dev.webfx.tool.cli.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * @author Bruno Salmon
 */
public interface ProjectModule extends Module {

    ProjectModule getParentModule();

    ProjectModule fetchParentModule();

    /*************************
     ***** Basic streams *****
     *************************/

    default ReusableStream<String> getChildrenModuleNames() {
        return ReusableStream.create(() -> {
            ReusableStream<String> childrenModuleNames;
            WebFxModuleFile webFxModuleFile = getWebFxModuleFile();
            if (webFxModuleFile != null && !webFxModuleFile.shouldTakeChildrenModuleNamesFromPomInstead()) {
                childrenModuleNames = webFxModuleFile.getChildrenModuleNames();
                if (webFxModuleFile.shouldSubdirectoriesChildrenModulesBeAdded())
                    childrenModuleNames = childrenModuleNames.concat(getSubdirectoriesChildrenModules());
            } else {
                MavenPomModuleFile mavenModuleFile = getMavenModuleFile();
                childrenModuleNames = mavenModuleFile == null ? ReusableStream.empty() : mavenModuleFile.getChildrenModuleNames();
            }
            return childrenModuleNames;
        });
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

    default ReusableStream<LibraryModule> getRequiredLibraryModules() { // Should be overridden to use a cache
        return ReusableStream.concat(
                getWebFxModuleFile().getRequiredWebFxLibraryModules(),
                getWebFxModuleFile().getRequiredThirdPartyLibraryModules()
        );
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

    default boolean hasAutoInjectionConditions() {
        return getWebFxModuleFile().hasAutoInjectionConditions();
    }

    default boolean isAggregate() {
        return getWebFxModuleFile().skipMavenPomUpdate() ?
                getMavenModuleFile().isAggregate()
                : getWebFxModuleFile().isAggregate();
    }

    default boolean isImplementingInterface() {
        return implementedInterfaces().count() > 0;
    }

    default ReusableStream<String> implementedInterfaces() {
        return getWebFxModuleFile().implementedInterfaces();
    }

    default ReusableStream<String> getProvidedJavaServiceImplementations(String javaService, boolean replaceDollarWithDot) {
        // Providers declared in the webfx module file
        ReusableStream<String> implementations = getWebFxModuleFile().providedServiceProviders()
                .filter(p -> p.getSpi().equals(javaService))
                .map(ServiceProvider::getImplementation);
        if (replaceDollarWithDot)
            implementations = implementations.map(s -> s.replace('$', '.'));
        return implementations;
    }

    default ReusableStream<String> getExportedJavaPackages() {
        ReusableStream<String> exportedPackages = getWebFxModuleFile().getExplicitExportedPackages();
        if (getWebFxModuleFile().areSourcePackagesAutomaticallyExported())
            exportedPackages = ReusableStream.concat(getJavaSourcePackages(), getJavaSourcePackagesMinusExcludedPackages()).distinct();
        return exportedPackages;
    }

    private ReusableStream<String> getJavaSourcePackagesMinusExcludedPackages() {
        ReusableStream<String> sourcePackages = getJavaSourcePackages();
        ReusableStream<String> excludedPackages = getWebFxModuleFile().getExcludedPackagesFromSourcePackages().cache();
        if (!excludedPackages.isEmpty())
            sourcePackages = sourcePackages.filter(p -> excludedPackages.noneMatch(p::equals));
        return sourcePackages;
    }


    RootModule getRootModule();

    boolean hasSourceDirectory();

    Path getSourceDirectory();

    boolean hasJavaSourceDirectory();

    Path getJavaSourceDirectory();

    ReusableStream<String> getJavaSourcePackages();

    ReusableStream<JavaFile> getJavaSourceFiles();

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

    default boolean usesJavaClass(String javaClass) {
        String packageName = JavaFile.getPackageNameFromJavaClass(javaClass);
        boolean excludeWebFxKit = packageName.startsWith("javafx.");
        if (excludeWebFxKit && getName().startsWith("webfx-kit-"))
            return false;
        return usesJavaPackage(packageName) && getJavaSourceFiles().anyMatch(jc -> jc.usesJavaClass(javaClass));
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

    ReusableStream<ModuleDependency> getDetectedByCodeAnalyzerSourceDependencies();

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

    private ReusableStream<ProjectModule> getRegisteredProjectModuleSearchScope(boolean resume) {
        return getProjectModuleSearchScope(resume ? getModuleRegistry().getProjectModuleRegistrationResumableStream() : getModuleRegistry().getProjectModuleRegistrationStream());
    }

    private ReusableStream<Module> getRegisteredModuleSearchScope(boolean resume) {
        return getModuleSearchScope(resume ? getModuleRegistry().getModuleRegistrationResumableStream() : getModuleRegistry().getModuleRegistrationStream());
    }

    private ReusableStream<ProjectModule> getDeclaredProjectModuleSearchScope(boolean resume) {
        return getProjectModuleSearchScope(resume ? getModuleRegistry().getProjectModuleDeclarationResume() : getModuleRegistry().getProjectModuleDeclarationStream());
    }

    private ReusableStream<Module> getDeclaredModuleSearchScope(boolean resume) {
        return getModuleSearchScope(resume ? getModuleRegistry().getModuleDeclarationResumableStream() : getModuleRegistry().getModuleDeclarationStream());
    }

    private ReusableStream<ProjectModule> getProjectModuleSearchScope(ReusableStream<ProjectModule> globalProjectModuleSearchScope) {
        return getThisAndChildrenModulesInDepth()
                .concat(globalProjectModuleSearchScope)
                .distinct();
    }

    private ReusableStream<Module> getModuleSearchScope(ReusableStream<Module> globalModuleSearchScope) {
        return getThisAndChildrenModulesInDepth().map(Module.class::cast)
                .concat(globalModuleSearchScope)
                .distinct();
    }

    default ReusableStream<ProjectModule> searchRegisteredProjectModules(Predicate<? super Module> predicate, boolean resume) {
        return getRegisteredProjectModuleSearchScope(resume).filter(predicate);
    }

    default ProjectModule searchRegisteredProjectModule(Predicate<? super Module> predicate, boolean resume) {
        return searchRegisteredProjectModules(predicate, resume).findFirst().orElse(null);
    }

    default ReusableStream<ProjectModule> searchDeclaredProjectModules(Predicate<? super Module> predicate, boolean resume) {
        return getDeclaredProjectModuleSearchScope(resume).filter(predicate);
    }

    default ProjectModule searchDeclaredProjectModule(Predicate<? super Module> predicate, boolean resume) {
        return searchDeclaredProjectModules(predicate, resume).findFirst().orElse(null);
    }

    default ReusableStream<Module> searchRegisteredModules(Predicate<? super Module> predicate, boolean resume) {
        return getRegisteredModuleSearchScope(resume).filter(predicate);
    }

    default Module searchRegisteredModule(Predicate<? super Module> predicate, boolean resume) {
        return searchRegisteredModules(predicate, resume).findFirst().orElse(null);
    }

    default ReusableStream<Module> searchDeclaredModules(Predicate<? super Module> predicate, boolean resume) {
        return getDeclaredModuleSearchScope(resume).filter(predicate);
    }

    default Module searchDeclaredModule(Predicate<? super Module> predicate, boolean resume) {
        return searchDeclaredModules(predicate, resume).findFirst().orElse(null);
    }

    default Module searchRegisteredModule(String name) {
        return searchRegisteredModule(name, false);
    }

    default Module searchRegisteredModule(String name, boolean silent) {
        // Trying first a quick get() which will work only if the module is already registered
        Module module = getModuleRegistry().getRegisteredModuleOrLibrary(name);
        // Otherwise, continuing polling the registration stream until we find it
        if (module == null)
            module = searchRegisteredModule(m -> m.getName().equals(name), true);
        if (module == null && !silent)
            throw new UnresolvedException("Unknown module " + name);
        return module;
    }

    default ProjectModule searchRegisteredProjectModule(String name) {
        return searchRegisteredProjectModule(name, false);
    }

    default ProjectModule searchRegisteredProjectModule(String name, boolean silent) {
        // Trying first a quick get() which will work only if the module is already registered
        ProjectModule module = getModuleRegistry().getRegisteredProjectModule(name);
        // Otherwise, continuing polling the registration stream until we find it
        if (module == null)
            module = searchRegisteredProjectModule(m -> m.getName().equals(name), true);
        if (module == null && !silent)
            throw new UnresolvedException("Unknown project module " + name);
        return module;
    }

    default ReusableStream<ProjectModule> searchRegisteredProjectModuleStartingWith(String name) {
        // searching along the whole registration stream (already registered + not yet registered) until we find it
        return searchRegisteredProjectModules(module -> module.getName().startsWith(name), false);
    }

    ReusableStream<ProjectModule> getDirectivesUsageCoverage();


    //// Static utility methods

    static ReusableStream<ProjectModule> filterProjectModules(ReusableStream<Module> modules) {
        return modules
                .filter(ProjectModule.class::isInstance)
                .map(ProjectModule.class::cast);
    }

    static boolean modulesUsesJavaPackage(ReusableStream<ProjectModule> modules, String javaPackage) {
        return modules.anyMatch(m -> m.usesJavaPackage(javaPackage));
    }

    static boolean modulesUsesJavaClass(ReusableStream<ProjectModule> modules, String javaClass) {
        return modules.anyMatch(m -> m.usesJavaClass(javaClass));
    }

    static ReusableStream<ProjectModule> filterDestinationProjectModules(ReusableStream<ModuleDependency> dependencies) {
        return filterProjectModules(mapDestinationModules(dependencies));
    }

    static ReusableStream<Module> mapDestinationModules(ReusableStream<ModuleDependency> dependencies) {
        return dependencies.map(ModuleDependency::getDestinationModule);
    }

}