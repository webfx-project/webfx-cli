package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.DevJavaModuleInfoFile;
import dev.webfx.cli.modulefiles.M2WebFxModuleFile;
import dev.webfx.cli.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.cli.specific.SpecificClasses;
import dev.webfx.cli.specific.SpecificModules;
import dev.webfx.cli.specific.SpecificPackages;
import dev.webfx.cli.util.hashlist.HashList;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static dev.webfx.cli.core.ProjectModule.mapDestinationModules;

/**
 * @author Bruno Salmon
 */
public final class JavaSourceRootAnalyzer {

    /**
     * A path matcher for java files (filtering files with .java extension)
     */
    private final static PathMatcher JAVA_FILE_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.java");

    private final Supplier<Path> javaSourceRootPathSupplier;
    private final ProjectModuleImpl projectModule;

    /**
     * Returns all java source files present in this module (or empty if this is not a java source module).
     * Note: they are not captured in the export snapshot.
     */
    private final ReusableStream<JavaFile> javaSourceFilesCache =
        ReusableStream.create(() -> // Using deferred creation because we can't call these methods before the constructor is executed
                getJavaSourceRootPath() != null ? SplitFiles.uncheckedWalk(getJavaSourceRootPath()) : Spliterators.emptySpliterator())
            .filter(JAVA_FILE_MATCHER::matches)
            // Ignoring Gwt super sources (required for m2 sources artifacts only)
            .filter(path -> !path.startsWith("/super/"))
            // Ignoring module-info.java and package-info.java files
            .filter(path -> !path.getFileName().toString().endsWith("-info.java"))
            .map(path -> new JavaFile(path, getProjectModule()))
            .cache()
            .name("javaSourceFilesCache");

    /**
     * Returns all java source packages present in this module (or empty if this is not a java source module). These
     * packages are simply deduced from the java source files when present, or from the export snapshot otherwise.
     */
    private final ReusableStream<String> javaSourcePackagesCache =
        ReusableStream.create(() -> {
                ProjectModule projectModule = getProjectModule();
                if (projectModule.isAggregate())
                    return ReusableStream.empty();
                if (projectModule instanceof M2ProjectModule) {
                    M2WebFxModuleFile webFxModuleFile = ((M2ProjectModule) projectModule).getM2WebFxModuleFile();
                    if (webFxModuleFile.isExported())
                        return webFxModuleFile.javaSourcePackagesFromExportSnapshot();
                }
                return javaSourceFilesCache
                    .map(JavaFile::getPackageName)
                    .distinct();
            })
            .cache()
            .name("javaSourcePackagesCache");

    /**
     * Returns all java services directly used by this module, and that are required. Each service is returned as the
     * full name of the SPI class.
     */
    private final ReusableStream<String> usedRequiredJavaServicesCache =
        ReusableStream.create(() -> {
                ProjectModule projectModule = getProjectModule();
                ReusableStream<String> fromExportSnapshot = projectModule.getWebFxModuleFile().usedRequiredJavaServicesFromExportSnapshot().cache();
                if (projectModule instanceof M2ProjectModule || !fromExportSnapshot.isEmpty())
                    return fromExportSnapshot;
                return javaSourceFilesCache.flatMap(JavaFile::getUsedRequiredJavaServices);
            })
            .distinct()
            .cache()
            .name("usedRequiredJavaServicesCache");

    /**
     * Returns all java services directly used by this module, and that are optional. Each service is returned as the
     * full name of the SPI class.
     */
    private final ReusableStream<String> usedOptionalJavaServicesCache =
        ReusableStream.create(() -> {
                ProjectModule projectModule = getProjectModule();
                ReusableStream<String> fromExportSnapshot = projectModule.getWebFxModuleFile().usedOptionalJavaServicesFromExportSnapshot().cache();
                if (projectModule instanceof M2ProjectModule || !fromExportSnapshot.isEmpty())
                    return fromExportSnapshot;
                return javaSourceFilesCache.flatMap(JavaFile::getUsedOptionalJavaServices);
            })
            .distinct()
            .cache()
            .name("usedOptionalJavaServicesCache");

    /**
     * Returns all java services directly used by this module (both required and optional). Each service is returned as
     * the full name of the SPI class.
     */
    private final ReusableStream<String> usedJavaServicesCache =
        ReusableStream.concat(
            usedRequiredJavaServicesCache,
            usedOptionalJavaServicesCache
        ).name("usedJavaServicesCache");

    /**
     * Returns all java services declared in this module (they are the directly used java services that are also a
     * java class declared in this module)
     */
    private final ReusableStream<String> declaredJavaServicesCache =
        usedJavaServicesCache
            .filter(s -> javaSourceFilesCache.anyMatch(javaFile -> s.equals(javaFile.getClassName())))
            .cache()
            .name("declaredJavaServicesCache");


    /**
     * Returns all packages directly used in this module (or empty if this is not a java source module). These packages
     * are detected through a source code analysis of all java classes.
     * <p>
     * The packages of the SPIs are also added here in case they are not detected by the java source analyser. This can
     * happen if the provider extends a class instead of directly implementing the interface. Then the module-info.java
     * will report an error on the provider declaration because the module of the SPI won't be listed in the required modules.
     * TODO Remove this addition once the java source analyser will be able to find implicit java packages
     */
    private final ReusableStream<String> usedJavaPackagesCache =
        ReusableStream.create(() -> ReusableStream.concat(
                    javaSourceFilesCache.flatMap(JavaFile::getUsedJavaPackages),
                    getProjectModule().getWebFxModuleFile().getUndetectedUsedBySourcePackages(),
                    getProjectModule().getProvidedJavaServices().map(spi -> spi.substring(0, spi.lastIndexOf('.'))) // package of the SPI (ex: javafx.application if SPI = javafx.application.Application)
                )
            )
            .distinct()
            .cache()
            .name("usedJavaPackagesCache");


    /**
     * Returns all source module dependencies directly required by the source code of this module, and that could be
     * detected by the source code analyzer.
     */
    private final ReusableStream<ModuleDependency> detectedByCodeAnalyzerSourceDependenciesCache =
        ReusableStream.create(() -> {
            ProjectModuleImpl projectModule = getProjectModule();
            WebFxModuleFile webFxModuleFile = projectModule.getWebFxModuleFile();
            if (!webFxModuleFile.areUsedBySourceModulesDependenciesAutomaticallyAdded())
                return ReusableStream.empty();
            if (webFxModuleFile.hasDetectedUsedBySourceModulesFromExportSnapshot())
                return webFxModuleFile.detectedUsedBySourceModulesDependenciesFromExportSnapshot();
            if (webFxModuleFile.isAggregate())
                return ReusableStream.empty();
            return usedJavaPackagesCache
                .map(p -> projectModule.getRootModule().searchJavaPackageModule(p, projectModule))
                //.map(this::replaceEmulatedModuleWithNativeIfApplicable)
                .filter(module -> module != getProjectModule() && !module.getName().equals(projectModule.getName()))
                .map(m -> ModuleDependency.createSourceDependency(projectModule, m))
                .distinct()
                .cache();
        }).name("detectedByCodeAnalyzerSourceDependenciesCache");


    /**
     * Returns all source module dependencies directly required by the source code of this module (detected or not by
     * the source code analyzer).
     */
    private final ReusableStream<ModuleDependency> sourceDirectDependenciesCache =
        ReusableStream.create(() ->
            ReusableStream.concat(
                getProjectModule().explicitSourceDependenciesCache,
                detectedByCodeAnalyzerSourceDependenciesCache,
                getProjectModule().undetectedByCodeAnalyzerSourceDependenciesCache
            )).name("sourceDirectDependenciesCache");

    /**
     * Returns all the direct module dependencies without emulation and implicit providers modules (such as platform
     * provider modules). For executable modules, additional emulation modules may be required (ex: webfx-platform-
     * providers-gwt-emul-javatime) for the final compilation and execution, as well as implicit providers (ex: webfx-
     * platform-storage-gwt for a gwt application using webfx-platform-storage service module). These final missing
     * modules will be added later.
     */
    private final ReusableStream<ModuleDependency> directDependenciesWithoutEmulationAndImplicitProvidersCache =
        ReusableStream.create(() ->
            ReusableStream.concat(
                    sourceDirectDependenciesCache,
                    getProjectModule().resourceDirectDependenciesCache,
                    getProjectModule().applicationDependencyCache,
                    getProjectModule().pluginDirectDependenciesCache
                )
                .distinct()
                .cache()
        ).name("directDependenciesWithoutEmulationAndImplicitProvidersCache");

    /**
     * Returns all the transitive dependencies without emulation and implicit providers modules.
     */
    private final ReusableStream<ModuleDependency> transitiveDependenciesWithoutEmulationAndImplicitProvidersCache =
        directDependenciesWithoutEmulationAndImplicitProvidersCache
            .flatMap(ModuleDependency::collectThisAndTransitiveDependencies)
            .distinct()
            .cache()
            .name("transitiveDependenciesWithoutEmulationAndImplicitProvidersCache");

    /**
     * Returns the emulation modules required for this executable module (returns nothing if this module is not executable).
     */
    private final ReusableStream<ModuleDependency> executableEmulationDependenciesCaches =
        ReusableStream.create(this::collectExecutableEmulationModules)
            .map(m -> ModuleDependency.createEmulationDependency(getProjectModule(), m))
            .cache()
            .name("executableEmulationDependenciesCaches");


    /**
     * Returns the auto-injected modules required for this executable module (returns nothing if this module is not executable).
     */
    private final ReusableStream<ProjectModule> executableAutoInjectedModulesCaches =
        ReusableStream.create(this::collectExecutableAutoInjectedModules)
            .cache()
            .name("executableAutoInjectedModulesCaches");

    /**
     * Resolves and returns all implicit providers required by this executable module (returns nothing if this
     * module is not executable).
     */
    private final ReusableStream<Providers> executableImplicitProvidersCache =
        ReusableStream.create(this::collectExecutableProviders)
            .sorted()
            .cache()
            .name("executableImplicitProvidersCache");

    /**
     * Returns all direct dependencies without the implicit providers (but with emulation modules). This intermediate
     * step is required in case the emulation modules use additional services (which will be resolved in the next step).
     */
    private final ReusableStream<ModuleDependency> directDependenciesWithoutImplicitProvidersCache =
        ReusableStream.concat(
                directDependenciesWithoutEmulationAndImplicitProvidersCache,
                executableEmulationDependenciesCaches
            )
            .distinct()
            .cache()
            .name("directDependenciesWithoutImplicitProvidersCache");

    /**
     * Returns the transitive dependencies without the implicit providers.
     */
    private final ReusableStream<ModuleDependency> transitiveDependenciesWithoutImplicitProvidersCache =
        directDependenciesWithoutImplicitProvidersCache
            .flatMap(ModuleDependency::collectThisAndTransitiveDependencies)
            .filter(dep -> dep.getExecutableTargets().isEmpty() || getProjectModule().isExecutable() && dep.getExecutableTargets().stream().anyMatch(depTarget -> depTarget.gradeTargetMatch(getProjectModule().getTarget()) >= 0))
            .distinct()
            .cache()
            .name("transitiveDependenciesWithoutImplicitProvidersCache");

    /**
     * Returns the transitive project modules without the implicit providers.
     */
    private final ReusableStream<ProjectModule> transitiveProjectModulesWithoutImplicitProvidersCache =
        ProjectModule.filterDestinationProjectModules(transitiveDependenciesWithoutImplicitProvidersCache)
            .name("transitiveDependenciesWithoutImplicitProvidersCache");

    /**
     * Defines the project modules scope to use when searching required providers.
     */
    private final ReusableStream<ProjectModule> autoInjectedOrRequiredProvidersModulesSearchScopeCache =
        ReusableStream.concat(
                transitiveProjectModulesWithoutImplicitProvidersCache,
                ReusableStream.create(() -> ReusableStream.concat(
                        // Note: it's important to start from the WebFX root module and not the root
                        // module to make this work with aggregate repositories, otherwise the
                        // search scope will mix with other application repositories.
                        ReusableStream.of(getWebFxRootModule()),
                        getWebFxRootModule().getRequiredProvidersSearchScopeWithinWebFxLibraries()
                    ))
                    .flatMap(ProjectModule::getThisAndChildrenModulesInDepth)
                    .filter(ProjectModule::hasMainJavaSourceDirectory) // excludes aggregate modules in particular
                    // Excluding plugin modules from required providers search scope (it's still possible
                    // to have a required provider that is a plugin, but for this to happen, it must be
                    // explicitly included in the project).
                    .filter(pm -> !SpecificModules.isPluginModule(pm.getName()))
                    .filter(pm -> pm.isCompatibleWithTargetModule(getProjectModule()))
            )
            .distinct()
            .cache()
            .name("autoInjectedOrRequiredProvidersModulesSearchScopeCache");

    /**
     * Defines the project modules scope to use when searching for optional providers.
     */
    private final ReusableStream<ProjectModule> optionalProvidersModulesSearchScopeCache =
        ReusableStream.concat(
            transitiveProjectModulesWithoutImplicitProvidersCache,
            executableAutoInjectedModulesCaches
        ).cache().name("optionalProvidersModulesSearchScopeCache");

    /**
     * Defines the project modules scope to use when searching required providers.
     */
    private final ReusableStream<ProjectModule> requiredProvidersModulesSearchScopeCache =
        autoInjectedOrRequiredProvidersModulesSearchScopeCache;


    /**
     * Returns the additional dependencies needed to integrate the implicit providers into this executable module
     * (returns nothing if this module is not executable).
     */
    private final ReusableStream<ModuleDependency> executableImplicitProvidersDependenciesCache =
        executableImplicitProvidersCache
            .flatMap(Providers::getProviderModules)
            // Removing modules already in transitive dependencies (no need to repeat them)
            .filter(m -> m != getProjectModule() && transitiveDependenciesWithoutImplicitProvidersCache.noneMatch(dep -> dep.getDestinationModule() == m))
            .map(m -> ModuleDependency.createImplicitProviderDependency(getProjectModule(), m))
            .cache()
            .name("executableImplicitProvidersDependenciesCache");


    /**
     * Returns all the direct module dependencies (including the final emulation and implicit provider modules) but
     * without the final resolutions required for executable modules.
     */
    private final ReusableStream<ModuleDependency> directDependenciesWithoutFinalExecutableResolutionsCache =
        ReusableStream.concat(
                directDependenciesWithoutImplicitProvidersCache,
                executableImplicitProvidersDependenciesCache
            )
            .distinct()
            .cache()
            .name("directDependenciesWithoutFinalExecutableResolutionsCache");

    /**
     * Returns all the transitive module dependencies (including the final emulation and implicit provider modules) but
     * without the final resolutions required for executable modules.
     */
    private final ReusableStream<ModuleDependency> transitiveDependenciesWithoutFinalExecutableResolutionsCache =
        directDependenciesWithoutFinalExecutableResolutionsCache
            .flatMap(ModuleDependency::collectThisAndTransitiveDependencies)
            .distinct()
            .cache()
            .name("transitiveDependenciesWithoutFinalExecutableResolutionsCache");

    /**
     * Returns the final list of all the direct module dependencies. There are 2 changes made in this last step.
     * 1) modules dependencies declared with an executable target (ex: my-module-if-java) are kept only if this module
     * is executable and compatible with this target (if not, these dependencies are removed). Also, these dependencies
     * (if kept) are moved into the direct dependencies of this executable module even if they were initially in the
     * transitive dependencies (ex: if my-module-if-java was a transitive dependency and this module is a java(fx) final
     * executable module, my-module-if-java will finally be a direct dependency of this module and removed from the
     * final transitive dependencies because they may not be designed for the java target only).
     * 2) interface module dependencies are resolved, which means replaced by concrete modules implementing these
     * interface modules. This resolution is made only if this module is executable (otherwise the interface module is
     * kept). For example, if my-app-css is an interface module and my-app-css-javafx & my-app-css-web are the concrete
     * modules, my-app-css will be replaced by my-app-css-javafx in a final javafx application and by my-app-css-web in
     * a final web application. For making this replacement work with the java module system, the concrete modules will
     * be declared using the same name as the interface module in their module-info.java (See {@link DevJavaModuleInfoFile} ).
     */
    private final ReusableStream<ModuleDependency> unfilteredDirectDependenciesCache =
        ReusableStream.concat(
                directDependenciesWithoutFinalExecutableResolutionsCache,
                // Moving transitive dependencies declared with an executable target to here (i.e., direct dependencies)
                transitiveDependenciesWithoutFinalExecutableResolutionsCache
                    .filter(dep -> !dep.getExecutableTargets().isEmpty())
            )
            .flatMap(this::resolveInterfaceDependencyIfExecutable) // Resolving interface modules
            .distinct()
            .cache()
            .name("unfilteredDirectDependenciesCache");

    private final ReusableStream<ModuleDependency> directDependenciesCache =
        unfilteredDirectDependenciesCache
            // Removing dependencies declared with an executable target if this module is not executable or with the incompatible target
            .filter(dep -> dep.getExecutableTargets().isEmpty() || getProjectModule().isExecutable() && dep.getExecutableTargets().stream().anyMatch(depTarget -> depTarget.gradeTargetMatch(getProjectModule().getTarget()) >= 0))
            .cache()
            .name("directDependenciesCache");


    /**
     * Returns the final list of all the transitive module dependencies. @See {@link JavaSourceRootAnalyzer#directDependenciesCache}
     * for an explanation of the changes made in this last step.
     */
    private final ReusableStream<ModuleDependency> transitiveDependenciesCache =
        transitiveDependenciesWithoutFinalExecutableResolutionsCache
            // Removing dependencies declared with an executable target if this module is not executable or with the incompatible target
            .filter(dep -> dep.getExecutableTargets().isEmpty() || getProjectModule().isExecutable() && dep.getExecutableTargets().stream().anyMatch(depTarget -> depTarget.gradeTargetMatch(getProjectModule().getTarget()) >= 0))
            .flatMap(this::resolveInterfaceDependencyIfExecutable) // Resolving interface modules
            .distinct()
            .cache()
            .name("transitiveDependenciesCache");


    public JavaSourceRootAnalyzer(Supplier<Path> javaSourceRootPathSupplier, ProjectModuleImpl projectModule) {
        this.javaSourceRootPathSupplier = javaSourceRootPathSupplier;
        this.projectModule = projectModule;
    }

    public Path getJavaSourceRootPath() {
        return javaSourceRootPathSupplier.get();
    }

    public ProjectModuleImpl getProjectModule() {
        return projectModule;
    }

    public ProjectModule getWebFxRootModule() {
        if (projectModule instanceof DevProjectModule devProjectModule)
            return devProjectModule.getWebFxRootModule();
        return projectModule.getRootModule();
    }

    /// // Java classes

    public ReusableStream<JavaFile> getSourceFiles() {
        return javaSourceFilesCache;
    }

    /// // Java packages

    public ReusableStream<String> getSourcePackages() {
        return javaSourcePackagesCache;
    }

    public boolean usesJavaPackage(String javaPackage) {
        // Special case for M2 projects where the info might be in webfx.xml <export-snapshot> and so, doesn't require to download the sources at this point
        if (projectModule instanceof M2ProjectModule) {
            Boolean m2UsesJavaPackage = ((M2ProjectModule) projectModule).tryEvaluateUsesJavaPackageWithoutDownloadingSources(javaPackage);
            // If the info has been found in <export-snapshot>, the result will be False or True
            if (m2UsesJavaPackage != null)
                return m2UsesJavaPackage;
            // Otherwise if the result is null, we need to continue with the general case which pulls the stream,
            // so this will cause the download of the sources by Maven (if not already downloaded)
        }
        return getUsedJavaPackages().anyMatch(javaPackage::equals);
    }

    public boolean usesJavaClass(String javaClass) {
        // Special case for M2 projects where the info might be in webfx.xml <export-snapshot> and so, doesn't require to download the sources at this point
        if (projectModule instanceof M2ProjectModule) {
            Boolean m2UsesJavaClass = ((M2ProjectModule) projectModule).tryEvaluateUsesJavaClassWithoutDownloadingSources(javaClass);
            // If the info has been found in <export-snapshot>, the result will be False or True
            if (m2UsesJavaClass != null)
                return m2UsesJavaClass;
            // Otherwise if the result is null, we need to continue with the general case which pulls the stream,
            // so this will cause the download of the sources by Maven (if not already downloaded)
        }
        String packageName = JavaFile.getPackageNameFromJavaClass(javaClass);
        boolean excludeWebFxKit = packageName.startsWith("javafx.");
        if (excludeWebFxKit && projectModule.getName().startsWith("webfx-kit-"))
            return false;
        return usesJavaPackage(packageName) && getSourceFiles().anyMatch(jc -> jc.usesJavaClass(javaClass));
    }

    public boolean requiresJavaBaseJ2clEmulation() {
        if (!projectModule.getBuildInfo().isJ2clCompilable)
            return false;
        if (SpecificModules.isModulePartOfWebfxKitJavaFxGraphicsFatJ2cl(projectModule.getName()))
            return false;
        return usesJavaPackage(SpecificPackages.JAVA_TEXT) ||
               usesJavaPackage(SpecificPackages.JAVA_LANG_REF) ||
               usesJavaPackage(SpecificPackages.JAVA_UTIL_REGEX) ||
               usesJavaPackage(SpecificPackages.NETSCAPE_JAVASCRIPT) ||
               usesJavaClass(SpecificClasses.JAVA_IO_EOF_EXCEPTION) ||
               usesJavaClass(SpecificClasses.JAVA_UTIL_SERVICE_LOADER) ||
               usesJavaClass(SpecificClasses.JAVA_UTIL_PROPERTIES);
    }

    public boolean requiresJavaTimeJ2clEmulation() {
        if (!projectModule.getBuildInfo().isJ2clCompilable)
            return false;
        if (SpecificModules.isModulePartOfWebfxKitJavaFxGraphicsFatJ2cl(projectModule.getName()))
            return false;
        return isThisOrTransitiveDependenciesUsingJavaTime();
    }

    public boolean isThisOrTransitiveDependenciesUsingJavaTime() {
        if (usesJavaPackage(SpecificPackages.JAVA_TIME))
            return true;
        return ProjectModule.filterDestinationProjectModules(transitiveDependenciesWithoutEmulationAndImplicitProvidersCache)
            .anyMatch(pm -> pm.getMainJavaSourceRootAnalyzer().usesJavaPackage(SpecificPackages.JAVA_TIME));
    }

    /// // Services

    public ReusableStream<String> getUsedRequiredJavaServices() {
        return usedRequiredJavaServicesCache;
    }

    public ReusableStream<String> getUsedOptionalJavaServices() {
        return usedOptionalJavaServicesCache;
    }

    public ReusableStream<String> getUsedJavaServices() {
        return usedJavaServicesCache;
    }

    public ReusableStream<String> getDeclaredJavaServices() {
        return declaredJavaServicesCache;
    }

    /******************************
     ***** Analyzing streams  *****
     ******************************/

    ///// Dependencies
    public ReusableStream<ModuleDependency> getDirectDependencies() {
        return directDependenciesCache;
    }

    public ReusableStream<ModuleDependency> getUnfilteredDirectDependencies() {
        return unfilteredDirectDependenciesCache;
    }

    public ReusableStream<ModuleDependency> getTransitiveDependencies() {
        return transitiveDependenciesCache;
    }

    public ReusableStream<ModuleDependency> getTransitiveDependenciesWithoutImplicitProviders() {
        return transitiveDependenciesWithoutImplicitProvidersCache;
    }

    public ReusableStream<ModuleDependency> getDetectedByCodeAnalyzerSourceDependencies() {
        return detectedByCodeAnalyzerSourceDependenciesCache;
    }

    public ReusableStream<ModuleDependency> getDirectDependenciesWithoutFinalExecutableResolutions() {
        return directDependenciesWithoutFinalExecutableResolutionsCache;
    }

    public ReusableStream<String> getUsedJavaPackages() {
        return usedJavaPackagesCache;
    }

    /// // Modules

    public ReusableStream<Module> getDirectModules() {
        return mapDestinationModules(getDirectDependencies());
    }

    public ReusableStream<Module> getThisAndDirectModules() {
        return ReusableStream.concat(
            ReusableStream.of(projectModule),
            getDirectModules()
        );
    }

    public boolean isDirectlyDependingOn(String moduleName) {
        return getDirectModules().anyMatch(m -> moduleName.equals(m.getName()));
    }

    public ReusableStream<Module> getTransitiveModules() {
        return mapDestinationModules(getTransitiveDependencies());
    }

    public ReusableStream<Module> getThisAndTransitiveModules() {
        return ReusableStream.concat(
            ReusableStream.of(projectModule),
            ProjectModule.mapDestinationModules(getTransitiveDependencies())
        );
    }

    public ReusableStream<ProjectModule> getThisOrChildrenModulesInDepthDirectlyDependingOn(String moduleArtifactId) {
        return projectModule.getThisAndChildrenModulesInDepth()
            .filter(module -> module.getMainJavaSourceRootAnalyzer().isDirectlyDependingOn(moduleArtifactId))
            ;
    }

    private ReusableStream<ProjectModule> collectExecutableAutoInjectedModules() {
        if (projectModule.isExecutable())
            return autoInjectedOrRequiredProvidersModulesSearchScopeCache
                .filter(ProjectModule::hasAutoInjectionConditions)
                .filter(am -> am.getWebFxModuleFile().getUsesPackagesAutoInjectionConditions().allMatch(p -> usesJavaPackage(p) || transitiveProjectModulesWithoutImplicitProvidersCache.anyMatch(tm -> tm.getMainJavaSourceRootAnalyzer().usesJavaPackage(p))))
                ;
        return ReusableStream.empty();
    }

    private ReusableStream<ProjectModule> getRequiredProvidersModulesSearchScope() {
        return requiredProvidersModulesSearchScopeCache;
    }

    private ReusableStream<ProjectModule> getOptionalProvidersModulesSearchScope() {
        return optionalProvidersModulesSearchScopeCache;
    }

    public ReusableStream<Providers> getExecutableProviders() {
        return executableImplicitProvidersCache;
    }

    /**
     * If the module is executable, this method returns the list of all providers required for its execution.
     *
     * @return a stream of all providers (empty for non-executable modules)
     */
    private ReusableStream<Providers> collectExecutableProviders() {
        return collectExecutableModuleProviders(this, this);
    }

    /**
     * This is the static implementation of collectExecutableModuleProviders() that takes 2 arguments:
     *
     * @param executableSourceRoot the source root analyzer of the executable module
     * @param collectingSourceRoot the source root analyzer of the module we are collecting the provider for
     * @return a stream of all providers (empty for non-executable modules)
     */
    private static ReusableStream<Providers> collectExecutableModuleProviders(JavaSourceRootAnalyzer executableSourceRoot, JavaSourceRootAnalyzer collectingSourceRoot) {
        // Returning an empty stream if the module is not executable
        ProjectModuleImpl executableModule = executableSourceRoot.getProjectModule();
        if (!executableModule.isExecutable())
            return ReusableStream.empty();

        ProjectModuleImpl collectingModule = collectingSourceRoot.getProjectModule();
        List<ProjectModule> walkingModules = new HashList<>();
        walkingModules.add(collectingModule);
        ReusableStream<ProjectModule> transitiveModulesWithInterfacesResolved =
            ProjectModule.filterProjectModules(
                mapDestinationModules(
                    collectingSourceRoot.getTransitiveDependenciesWithoutImplicitProviders()
                        // Note: the previous stream may contain interface modules, so we resolve them here because the
                        // implementing modules may also declare additional providers
                        .flatMap(collectingSourceRoot::resolveInterfaceDependency)
                ));
        // Fixing possible incompleteness of the list, causing missing optional services in pom.xml.
        // Ex: webfx update -p -M kbs-backoffice-application-openjfx
        if (executableSourceRoot == collectingSourceRoot)
            Workaround.fixTerminalReusableStream(transitiveModulesWithInterfacesResolved); // TODO: remove this once fixed
        // Now the stream should be complete
        walkingModules.addAll(transitiveModulesWithInterfacesResolved.collect(Collectors.toList()));
        List<String/* SPI */> requiredServices = new HashList<>();
        ReusableStream<ProjectModule> requiredSearchScope = executableSourceRoot.getRequiredProvidersModulesSearchScope();
        List<String/* SPI */> optionalServices = new HashList<>();
        ReusableStream<ProjectModule> optionalSearchScope = executableSourceRoot.getOptionalProvidersModulesSearchScope();
        Map<String/* SPI */, List<ProjectModule>> providerModules = new HashMap<>();

        int walkingIndex = 0;

        while (walkingIndex < walkingModules.size()) {

            // Collecting the required and optional services (SPI) used by this module and transitive dependencies
            for (; walkingIndex < walkingModules.size(); walkingIndex++) {
                ProjectModule projectModule = walkingModules.get(walkingIndex);
                JavaSourceRootAnalyzer projectAnalyzer = projectModule.getMainJavaSourceRootAnalyzer();
                List<String> projectRequiredJavaServices = projectAnalyzer.getUsedRequiredJavaServices().collect(Collectors.toList());
                requiredServices.addAll(projectRequiredJavaServices);
                List<String> projectOptionalJavaServices = projectAnalyzer.getUsedOptionalJavaServices().collect(Collectors.toList());
                optionalServices.addAll(projectOptionalJavaServices);
            }

            // Resolving the required services (finding the most relevant modules that implement these SPIs - only one module per SPI)
            for (Iterator<String> it = requiredServices.iterator(); it.hasNext(); ) {
                String spi = it.next();
                if (providerModules.get(spi) != null) // already resolved
                    it.remove(); // We remove this service from requiredServices, so this list contains only unresolved services
                else {
                    ReusableStream<ProjectModule> searchScope = ReusableStream.concat(ReusableStream.fromIterable(walkingModules), requiredSearchScope).distinct();
                    ReusableStream<ProjectModule> requiredModules = RootModule.findModulesProvidingJavaService(searchScope, spi, executableModule, true);
                    requiredModules.findFirst().ifPresent(requiredModule -> {
                        providerModules.put(spi, Collections.singletonList(requiredModule)); // singleton list because there only 1 instance for required services
                        if (collectingSourceRoot == executableSourceRoot) {
                            // Adding the module implementing the service to the walking modules (for later research on the next loop)
                            walkingModules.add(requiredModule);
                            // Also adding all its transitive dependencies
                            List<ProjectModule> requiredModuleTransitiveDependencies =
                                ProjectModule.filterProjectModules(mapDestinationModules(
                                        requiredModule.getMainJavaSourceRootAnalyzer()
                                            .getTransitiveDependenciesWithoutImplicitProviders()))
                                    .collect(Collectors.toList());
                            walkingModules.addAll(requiredModuleTransitiveDependencies);
                        }
                        it.remove(); // We remove this service because it is now resolved
                    });
                }
            }

            // Resolving the optional services (finding all modules that implement these SPIs)
            optionalServices.forEach(spi -> {
                List<ProjectModule> optionalModules = providerModules.get(spi);
                if (optionalModules == null)
                    providerModules.put(spi, optionalModules = new HashList<>(RootModule.findModulesProvidingJavaService(optionalSearchScope, spi, collectingModule, false).collect(Collectors.toList())));
                List<ProjectModule> additionalOptionalModules = RootModule.findModulesProvidingJavaService(ReusableStream.fromIterable(walkingModules), spi, collectingModule, false).collect(Collectors.toList());
                optionalModules.addAll(additionalOptionalModules);
                if (collectingSourceRoot == executableSourceRoot)
                    walkingModules.addAll(additionalOptionalModules);
            });

            // We don't collect later providers when the collecting source root differs from the executable source
            // root - which happens when this method is called from resolveInterfaceDependencyIfExecutable().
            if (collectingSourceRoot != executableSourceRoot)
                break;

            // Otherwise, we loop for collecting the possible later providers (because the modules we just added
            // for the providers may themselves require additional providers)
        }

        if (collectingSourceRoot == executableSourceRoot)
            requiredServices.forEach(spi -> Logger.verbose("[" + executableModule.getName() + "] No provider found for " + spi + " among " + requiredSearchScope.map(ProjectModule::getName).sorted().collect(Collectors.toList())));

        return ReusableStream.fromIterable(providerModules.entrySet())
            .map(entry -> new Providers(entry.getKey(), ReusableStream.fromIterable(entry.getValue())));
    }

    ReusableStream<ModuleDependency> resolveInterfaceDependencyIfExecutable(ModuleDependency dependency) {
        if (projectModule.isExecutable())
            return resolveInterfaceDependency(dependency);
        return ReusableStream.of(dependency);
    }

    ReusableStream<ModuleDependency> resolveInterfaceDependency(ModuleDependency dependency) {
        if (dependency.getDestinationModule() instanceof ProjectModule module && module.isInterface()) {
            ReusableStream<ProjectModule> searchScope = getRequiredProvidersModulesSearchScope();
            ProjectModule concreteModule = searchScope
                .filter(m -> m.implementsModule(module))
                .filter(m -> m.isCompatibleWithTargetModule(projectModule))
                // Also if the module has auto-injection conditions, we check that these conditions are true
                // Ex: webfx-platform-conf-zero-impl is the ZeroConf implementation only when the Conf API is used
                // TODO: fix issue because executableAutoInjectedModulesCaches may not be completed at this stage => second pass resolution?
                .filter(m -> !m.hasAutoInjectionConditions() || projectModule.getMainJavaSourceRootAnalyzer().executableAutoInjectedModulesCaches.anyMatch(autoModule -> autoModule == m))
                .max(Comparator.comparingInt(m -> m.gradeTargetMatch(projectModule.getTarget())))
                .orElse(null);
            // If the search was fruitless, the remaining possibility is that the module interface implements itself
            // (i.e., it provides a default implementation itself).
            if (concreteModule == null && module.implementsItself() && module.isCompatibleWithTargetModule(projectModule))
                concreteModule = module;
            if (concreteModule != null) {
                // Creating the dependency to this concrete module and adding transitive dependencies
                ReusableStream<ModuleDependency> concreteModuleDependencies = ModuleDependency.createImplicitProviderDependency(projectModule, concreteModule)
                    .collectThisAndTransitiveDependencies();
                // In case these dependencies have a SPI, collecting the providers and adding their associated implicit dependencies
                // Ex: interface = [webfx-extras-visual-]grid-registry, concrete = [...]-grid-registry-spi, provider = [...]-grid-peers-javafx
                // TODO: See if we can move this up to the generic steps when building dependencies
                return ReusableStream.concat(
                        concreteModuleDependencies,
                        collectExecutableModuleProviders(this, concreteModule.getMainJavaSourceRootAnalyzer())
                            .flatMap(Providers::getProviderModules)
                            //.filter(m -> transitiveDependenciesWithoutImplicitProvidersCache.noneMatch(dep -> dep.getDestinationModule() == m)) // Removing modules already in transitive dependencies (no need to repeat them)
                            .map(m -> ModuleDependency.createImplicitProviderDependency(projectModule, m))
                    )
                    // Resolving possible further interface dependencies coming from the transitive dependencies or implicit providers
                    .flatMap(dep -> dep.getDestinationModule() == module ? ReusableStream.empty() /* because already done */ : resolveInterfaceDependency(dep))
                    .distinct();
            }
            String message = "No concrete module found for interface module " + module + " in executable module " + projectModule + " among " + searchScope.map(ProjectModule::getName).sorted().collect(Collectors.toList());
            Logger.warning(message);
        }
        return ReusableStream.of(dependency);
    }

    private ReusableStream<Module> collectExecutableEmulationModules() {
        RootModule rootModule = projectModule.getRootModule();
        if (projectModule.isExecutable(Platform.J2CL)) {
            boolean isUsingJavaNio = ProjectModule.mapDestinationModules(transitiveDependenciesWithoutEmulationAndImplicitProvidersCache)
                .anyMatch(pm -> pm.getName().equals(SpecificModules.JAVA_NIO_EMUL));
            return ReusableStream.of(
                rootModule.searchRegisteredModule(SpecificModules.J2CL_ANNOTATIONS),
                rootModule.searchRegisteredModule(SpecificModules.J2CL_PROCESSORS),
                rootModule.searchRegisteredModule(SpecificModules.WEBFX_KIT_JAVAFXGRAPHICS_FAT_J2CL),
                rootModule.searchRegisteredModule(SpecificModules.WEBFX_PLATFORM_JAVABASE_EMUL_J2CL),
                requiresJavaTimeJ2clEmulation() ? rootModule.searchRegisteredModule(SpecificModules.WEBFX_PLATFORM_JAVATIME_EMUL_J2CL) : null,
                isUsingJavaNio ? rootModule.searchRegisteredModule(SpecificModules.JAVA_NIO_EMUL) : null
            ).filter(Objects::nonNull);
        }
        if (projectModule.isExecutable(Platform.GWT)) {
            boolean isUsingJavaTime = isThisOrTransitiveDependenciesUsingJavaTime();
            boolean requiresTimezoneData =
                ProjectModule.filterDestinationProjectModules(transitiveDependenciesWithoutEmulationAndImplicitProvidersCache)
                    .anyMatch(ProjectModule::requiresTimeZoneData);
            return ReusableStream.of(
                rootModule.searchRegisteredModule(SpecificModules.WEBFX_KIT_JAVAFXGRAPHICS_ELEMENTAL2),
                rootModule.searchRegisteredModule(SpecificModules.WEBFX_PLATFORM_JAVABASE_EMUL_GWT),
                isUsingJavaTime ? rootModule.searchRegisteredModule(SpecificModules.GWT_TIME) : null,
                !requiresTimezoneData ? null : rootModule.searchRegisteredModule(SpecificModules.ORG_JRESEARCH_GWT_TIME_TZDB)
            ).filter(Objects::nonNull);
        }
        if (projectModule.isExecutable(Platform.JRE)) {
            boolean isForOpenJFX = projectModule.getTarget().hasTag(TargetTag.OPENJFX);
            boolean isForGluon = projectModule.getTarget().hasTag(TargetTag.GLUON);
            if (!isForOpenJFX && !isForGluon)
                return mapDestinationModules(transitiveDependenciesWithoutEmulationAndImplicitProvidersCache)
                    .filter(m -> SpecificModules.isJavafxEmulModule(m.getName()));
            boolean usesMedia = mapDestinationModules(transitiveDependenciesWithoutEmulationAndImplicitProvidersCache).anyMatch(m -> SpecificModules.isJavaFxMediaModule(m.getName()));
            boolean usesWeb = mapDestinationModules(transitiveDependenciesWithoutEmulationAndImplicitProvidersCache).anyMatch(m -> SpecificModules.isJavaFxWebModule(m.getName()));
            boolean usesFxml = mapDestinationModules(transitiveDependenciesWithoutEmulationAndImplicitProvidersCache).anyMatch(m -> SpecificModules.isJavaFxFxmlModule(m.getName()));
            return ReusableStream.of(
                rootModule.searchRegisteredModule(SpecificModules.WEBFX_PLATFORM_BOOT_JAVA),
                rootModule.searchRegisteredModule(SpecificModules.WEBFX_KIT_JAVAFXGRAPHICS_OPENJFX),
                !usesMedia ? null : rootModule.searchRegisteredModule(isForGluon ? SpecificModules.WEBFX_KIT_JAVAFXMEDIA_GLUON : SpecificModules.WEBFX_KIT_JAVAFXMEDIA_EMUL),
                !usesWeb ? null : rootModule.searchRegisteredModule(SpecificModules.WEBFX_KIT_JAVAFXWEB_EMUL),
                !usesFxml ? null : rootModule.searchRegisteredModule(SpecificModules.WEBFX_KIT_JAVAFXFXML_EMUL)
            ).filter(Objects::nonNull);
        }
        return ReusableStream.empty();
    }

}
