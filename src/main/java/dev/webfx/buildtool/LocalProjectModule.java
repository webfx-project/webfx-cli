package dev.webfx.buildtool;

import dev.webfx.buildtool.modulefiles.*;
import dev.webfx.buildtool.util.splitfiles.SplitFiles;
import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public class LocalProjectModule extends ProjectModuleImpl {

    /**
     * A path matcher for java files (filtering files with .java extension)
     */
    private final static PathMatcher JAVA_FILE_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.java");


    /**
     * Returns all java files declared in this module (or empty if this is not a java source module).
     */
    private final ReusableStream<JavaFile> declaredJavaFilesCache =
            ReusableStream.create(() -> // Using deferred creation because we can't call these methods before the constructor is executed
                            hasJavaSourceDirectory() ? SplitFiles.uncheckedWalk(getJavaSourceDirectory()) : Spliterators.emptySpliterator())
                    .filter(JAVA_FILE_MATCHER::matches)
                    .filter(path -> !path.getFileName().toString().endsWith("-info.java")) // Ignoring module-info.java and package-info.java files
                    .map(path -> new JavaFile(path, this))
                    .cache();

    /**
     * Returns all packages declared in this module (or empty if this is not a java source module). These packages are
     * simply deduced from the declared java classes.
     */
    private final ReusableStream<String> declaredJavaPackagesCache =
            declaredJavaFilesCache
                    .map(JavaFile::getPackageName)
                    .distinct();

    /**
     * Returns all java services directly used by this module and that are required. Each service is returned as the
     * full name of the SPI class.
     */
    private final ReusableStream<String> usedRequiredJavaServicesCache =
            declaredJavaFilesCache
                    .flatMap(JavaFile::getUsedRequiredJavaServices)
                    .distinct()
                    .cache();

    /**
     * Returns all java services directly used by this module and that are optional. Each service is returned as the
     * full name of the SPI class.
     */
    private final ReusableStream<String> usedOptionalJavaServicesCache =
            declaredJavaFilesCache
                    .flatMap(JavaFile::getUsedOptionalJavaServices)
                    .distinct()
                    .cache();

    /**
     * Returns all java services directly used by this module (both required and optional). Each service is returned as
     * the full name of the SPI class.
     */
    private final ReusableStream<String> usedJavaServicesCache =
            ReusableStream.concat(
                    usedRequiredJavaServicesCache,
                    usedOptionalJavaServicesCache
            );

    /**
     * Returns all java services declared in this module (they are the directly used java services that are also a
     * java class declared in this module)
     */
    private final ReusableStream<String> declaredJavaServicesCache =
            usedJavaServicesCache
                    .filter(s -> declaredJavaFilesCache.anyMatch(jc -> s.equals(jc.getClassName())))
                    .cache();


    /**
     * Returns all packages directly used in this module (or empty if this is not a java source module). These packages
     * are discovered through a source code analyze of all java classes.
     * The packages of the SPIs are also added here in case they are not discovered by the java source analyser. This can
     * happen if the provider extends a class instead of directly implementing the interface. Then the module-info.java
     * will report an error on the provider declaration because the module of the SPI won't be listed in the required modules.
     * TODO Remove this addition once the java source analyser will be able to find implicit java packages
     */
    private final ReusableStream<String> usedJavaPackagesCache =
            ReusableStream.concat(
                            declaredJavaFilesCache.flatMap(JavaFile::getUsedJavaPackages),
                            providedJavaServicesCache.map(spi -> spi.substring(0, spi.lastIndexOf('.'))) // package of the SPI (ex: javafx.application if SPI = javafx.application.Application)
                    )
                    .distinct()
                    .cache();


    /**
     * Returns all source module dependencies directly required by the source code of this module and that could be
     * discovered by the source code analyzer.
     */
    private final ReusableStream<ModuleDependency> discoveredByCodeAnalyzerSourceDependenciesCache =
            ReusableStream.create(() -> !getWebFxModuleFile().areUsedBySourceModulesDependenciesAutomaticallyAdded() ? ReusableStream.empty() :
                    usedJavaPackagesCache
                            .map(p -> getRootModule().getJavaPackageModule(p, this))
                            //.map(this::replaceEmulatedModuleWithNativeIfApplicable)
                            .filter(module -> module != this && !module.getName().equals(getName()))
                            .distinct()
                            .map(m -> ModuleDependency.createSourceDependency(this, m))
                            .distinct()
                            .cache());


    /**
     * Returns all source module dependencies directly required by the source code of this module (discovered or not by
     * the source code analyzer).
     */
    private final ReusableStream<ModuleDependency> sourceDirectDependenciesCache =
            ReusableStream.concat(
                    explicitSourceDependenciesCache,
                    discoveredByCodeAnalyzerSourceDependenciesCache,
                    undiscoveredByCodeAnalyzerSourceDependenciesCache
            );

    /**
     * Returns all the direct module dependencies without emulation and implicit providers modules (such as platform
     * provider modules). For executable modules, additional emulation modules may be required (ex: webfx-platform-
     * providers-gwt-emul-javatime) for the final compilation and execution, as well as implicit providers (ex: webfx-
     * platform-storage-gwt for a gwt application using webfx-platform-storage service module). This final missing
     * modules will be added later.
     */
    private final ReusableStream<ModuleDependency> directDependenciesWithoutEmulationAndImplicitProvidersCache =
            ReusableStream.concat(
                            sourceDirectDependenciesCache,
                            resourceDirectDependenciesCache,
                            applicationDependencyCache,
                            pluginDirectDependenciesCache
                    )
                    .distinct()
                    .cache();

    /**
     * Returns all the transitive dependencies without emulation and implicit providers modules.
     */
    private final ReusableStream<ModuleDependency> transitiveDependenciesWithoutEmulationAndImplicitProvidersCache =
            directDependenciesWithoutEmulationAndImplicitProvidersCache
                    .flatMap(ModuleDependency::collectThisAndTransitiveDependencies)
                    .distinct()
                    .cache();


    /**
     * Returns the emulation modules required for this executable module (returns nothing if this module is not executable).
     */
    protected final ReusableStream<ModuleDependency> executableEmulationDependenciesCaches =
            ReusableStream.create(this::collectExecutableEmulationModules)
                    .map(m -> ModuleDependency.createEmulationDependency(this, m))
                    .cache();

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
                    .cache();

    /**
     * Returns the transitive dependencies without the implicit providers.
     */
    private final ReusableStream<ModuleDependency> transitiveDependenciesWithoutImplicitProvidersCache =
            directDependenciesWithoutImplicitProvidersCache
                    .flatMap(ModuleDependency::collectThisAndTransitiveDependencies)
                    .distinct()
                    .cache();

    /**
     * Returns the transitive project modules without the implicit providers.
     */
    private final ReusableStream<ProjectModule> transitiveProjectModulesWithoutImplicitProvidersCache =
            ProjectModule.filterDestinationProjectModules(transitiveDependenciesWithoutImplicitProvidersCache);

    /**
     * Defines the project modules scope to use when searching required providers.
     */
    private final ReusableStream<ProjectModule> automaticOrRequiredProvidersModulesSearchScopeCache =
            ReusableStream.concat(
                            transitiveProjectModulesWithoutImplicitProvidersCache,
                            ReusableStream.create(() -> ReusableStream.concat(
                                                    ReusableStream.of(
                                                            getRootModule(),
                                                            getRootModule().searchProjectModuleWithinSearchScopeWithoutRegisteringLibrariesAndPackages("webfx-platform"),
                                                            getRootModule().searchProjectModuleWithinSearchScopeWithoutRegisteringLibrariesAndPackages("webfx-kit")
                                                    ),
                                                    getRootModule().findProjectModuleStartingWith("webfx-stack"),
                                                    getRootModule().findProjectModuleStartingWith("webfx-extras"),
                                                    getRootModule().findProjectModuleStartingWith("webfx-framework")
                                            )
                                    )
                                    .flatMap(ProjectModule::getThisAndChildrenModulesInDepth)
                                    .filter(m -> m.isCompatibleWithTargetModule(this))
                    )
                    .distinct()
                    .cache();
    /**
     * Returns the automatic modules required for this executable module (returns nothing if this module is not executable).
     */
    private final ReusableStream<ProjectModule> executableAutomaticModulesCaches =
            ReusableStream.create(this::collectExecutableAutomaticModules)
                    .cache();

    /**
     * Defines the project modules scope to use when searching optional providers.
     */
    private final ReusableStream<ProjectModule> optionalProvidersModulesSearchScopeCache =
            ReusableStream.concat(
                    transitiveProjectModulesWithoutImplicitProvidersCache,
                    executableAutomaticModulesCaches
            ).cache();

    /**
     * Defines the project modules scope to use when searching required providers.
     */
    private final ReusableStream<ProjectModule> requiredProvidersModulesSearchScopeCache =
            automaticOrRequiredProvidersModulesSearchScopeCache;

    /**
     * Resolves and returns all implicit providers required by this executable module (returns nothing if this
     * module is not executable).
     */
    private final ReusableStream<Providers> executableImplicitProvidersCache =
            ReusableStream.create(this::collectExecutableProviders)
                    .cache();

    /**
     * Returns the additional dependencies needed to integrate the implicit providers into this executable module
     * (returns nothing if this module is not executable).
     */
    private final ReusableStream<ModuleDependency> executableImplicitProvidersDependenciesCache =
            executableImplicitProvidersCache
                    .flatMap(Providers::getProviderModules)
                    // Removing modules already in transitive dependencies (no need to repeat them)
                    .filter(m -> m != this && transitiveDependenciesWithoutImplicitProvidersCache.noneMatch(dep -> dep.getDestinationModule() == m))
                    .map(m -> ModuleDependency.createImplicitProviderDependency(this, m))
                    .cache();

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
                    .cache();

    /**
     * Returns all the transitive module dependencies (including the final emulation and implicit provider modules) but
     * without the final resolutions required for executable modules.
     */
    private final ReusableStream<ModuleDependency> transitiveDependenciesWithoutFinalExecutableResolutionsCache =
            directDependenciesWithoutFinalExecutableResolutionsCache
                    .flatMap(ModuleDependency::collectThisAndTransitiveDependencies)
                    .distinct()
                    .cache();

    /**
     * Returns the final list of all the direct module dependencies. There are 2 changes made in this last step.
     * 1) modules dependencies declared with an executable target (ex: my-module-if-java) are kept only if this module
     * is executable and compatible with this target (if not, these dependencies are removed). Also these dependencies
     * (if kept) are moved into the direct dependencies of this executable module even if they where initially in the
     * transitive dependencies (ex: if my-module-if-java was a transitive dependency and this module is a java(fx) final
     * executable module, my-module-if-java will finally be a direct dependency of this module and removed from the
     * final transitive dependencies because they may not be designed for the java target only).
     * 2) interface module dependencies are resolved which means replaced by concrete modules implementing these
     * interface modules. This resolution is made only if this module is executable (otherwise the interface module is
     * kept). For example, if my-app-css is an interface module and my-app-css-javafx & my-app-css-web are the concrete
     * modules, my-app-css will be replaced by my-app-css-javafx in a final javafx application and by my-app-css-web in
     * a final web application). For making this replacement work with the java module system, the concrete modules will
     * be declared using the same name as the interface module in their module-info.java (See {@link LocalJavaModuleInfoFile} ).
     */
    private final ReusableStream<ModuleDependency> directDependenciesCache =
            ReusableStream.concat(
                            directDependenciesWithoutFinalExecutableResolutionsCache,
                            // Moving transitive dependencies declared with an executable target to here (ie direct dependencies)
                            transitiveDependenciesWithoutFinalExecutableResolutionsCache
                                    .filter(dep -> dep.getExecutableTarget() != null)
                    )
                    // Removing dependencies declared with an executable target if this module is not executable or with incompatible target
                    .filter(dep -> dep.getExecutableTarget() == null || isExecutable() && dep.getExecutableTarget().gradeTargetMatch(getTarget()) >= 0)
                    .flatMap(this::resolveInterfaceDependencyIfExecutable) // Resolving interface modules
                    .distinct()
                    .cache();

    /**
     * Returns the final list of all the transitive module dependencies. @See {@link LocalProjectModule#directDependenciesCache}
     * for an explanation of the changes made in this last step.
     */
    private final ReusableStream<ModuleDependency> transitiveDependenciesCache =
            transitiveDependenciesWithoutFinalExecutableResolutionsCache
                    .filter(dep -> dep.getExecutableTarget() == null) // Removing dependencies declared with an executable target (because moved to direct dependencies)
                    .flatMap(this::resolveInterfaceDependencyIfExecutable) // Resolving interface modules
                    .distinct()
                    .cache();


    private final Path homeDirectory;
    private Boolean hasSourceDirectory;
    private Boolean hasJavaSourceDirectory;
    private LocalJavaModuleInfoFile javaModuleInfoFile;
    private LocalWebFxModuleFile webFxModuleFile;
    private LocalMavenPomModuleFile mavenPomModuleFile;

    private LocalGwtModuleFile gwtModuleFile;
    private GwtHtmlFile gwtHtmlFile;
    private ExportedWebFxModuleFile exportedWebFxModuleFile;

    /************************
     ***** Constructors *****
     ************************/

    public LocalProjectModule(Path homeDirectory, ProjectModule parentModule) {
        super(homeDirectory.toAbsolutePath().getFileName().toString(), parentModule);
        this.homeDirectory = homeDirectory;
    }

    /*************************
     ***** Basic getters *****
     *************************/

    public LocalWebFxModuleFile getWebFxModuleFile() {
        if (webFxModuleFile == null)
            webFxModuleFile = new LocalWebFxModuleFile(this);
        return webFxModuleFile;
    }

    public LocalMavenPomModuleFile getMavenModuleFile() {
        if (mavenPomModuleFile == null)
            mavenPomModuleFile = new LocalMavenPomModuleFile(this);
        return mavenPomModuleFile;
    }


    public Path getHomeDirectory() {
        return homeDirectory;
    }

    public Path getSourceDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src") : null;
    }

    public Path getJavaSourceDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/main/java/") : null;
    }

    public Path getResourcesDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/main/resources/") : null;
    }

    private Path getMetaInfJavaServicesDirectory() {
        return getResourcesDirectory().resolve("META-INF/services/");
    }

    public LocalGwtModuleFile getGwtModuleFile() {
        if (gwtModuleFile == null)
            gwtModuleFile = new LocalGwtModuleFile(this);
        return gwtModuleFile;
    }

    public GwtHtmlFile getGwtHtmlFile() {
        if (gwtHtmlFile == null)
            gwtHtmlFile = new GwtHtmlFile(this);
        return gwtHtmlFile;
    }

    public ExportedWebFxModuleFile getExportedWebFxModuleFile() {
        if (exportedWebFxModuleFile == null)
            exportedWebFxModuleFile = new ExportedWebFxModuleFile(this);
        return exportedWebFxModuleFile;
    }

    public LocalProjectModule getOrCreateChildProjectModule(String name) {
        return getOrCreateLocalProjectModule(homeDirectory.resolve(name).normalize(), this);
    }

    LocalProjectModule getOrCreateLocalProjectModule(Path homeDirectory, LocalProjectModule parentModule) {
        return getModuleRegistry().getOrCreateLocalProjectModule(homeDirectory, parentModule);
    }

    public boolean hasSourceDirectory() {
        if (hasSourceDirectory == null)
            hasSourceDirectory = pathExists(getSourceDirectory());
        return hasSourceDirectory;
    }

    private static boolean pathExists(Path path) {
        return path != null && Files.exists(path);
    }

    public boolean hasJavaSourceDirectory() {
        if (hasJavaSourceDirectory == null)
            hasJavaSourceDirectory = hasSourceDirectory() && pathExists(getJavaSourceDirectory());
        return hasJavaSourceDirectory;
    }

    public LocalJavaModuleInfoFile getJavaModuleFile() {
        if (javaModuleInfoFile == null)
            javaModuleInfoFile = new LocalJavaModuleInfoFile(this);
        return javaModuleInfoFile;
    }

    public LocalRootModule getRootModule() {
        return (LocalRootModule) super.getRootModule();
    }


    boolean isModuleUnderRootHomeDirectory(Module module) {
        Path homeDirectory = module instanceof LocalProjectModule ? ((LocalProjectModule) module).getHomeDirectory() : null;
        return homeDirectory != null && homeDirectory.startsWith(getRootModule().getHomeDirectory());
    }

    public BuildInfo getBuildInfo() {
        return new BuildInfo(this);
    }

    ///// Java classes

    public ReusableStream<JavaFile> getDeclaredJavaFiles() {
        return declaredJavaFilesCache;
    }

    ///// Java packages

    public ReusableStream<String> getDeclaredJavaPackages() {
        return declaredJavaPackagesCache;
    }

    ///// Services


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

    @Override
    public ReusableStream<ModuleDependency> getDirectDependencies() {
        return directDependenciesCache;
    }

    @Override
    public ReusableStream<ModuleDependency> getTransitiveDependencies() {
        return transitiveDependenciesCache;
    }

    @Override
    public ReusableStream<ModuleDependency> getTransitiveDependenciesWithoutImplicitProviders() {
        return transitiveDependenciesWithoutImplicitProvidersCache;
    }

    @Override
    public ReusableStream<ModuleDependency> getDiscoveredByCodeAnalyzerSourceDependencies() {
        return discoveredByCodeAnalyzerSourceDependenciesCache;
    }

    @Override
    public ReusableStream<ModuleDependency> getDirectDependenciesWithoutFinalExecutableResolutions() {
        return directDependenciesWithoutFinalExecutableResolutionsCache;
    }

    @Override
    public ReusableStream<String> getUsedJavaPackages() {
        return usedJavaPackagesCache;
    }

    public ReusableStream<JavaFile> getJavaFilesDependingOn(String destinationModule) {
        return getDeclaredJavaFiles()
                .filter(jf -> jf.getUsedJavaPackages().anyMatch(p -> destinationModule.equals(rootModule.getJavaPackageModule(p, this).getName())))
                ;
    }


    ///// Modules

    private ReusableStream<ProjectModule> collectExecutableAutomaticModules() {
        if (isExecutable())
            return automaticOrRequiredProvidersModulesSearchScopeCache
                    .filter(ProjectModule::isAutomatic)
                    .filter(am -> am.getWebFxModuleFile().getPackagesAutoCondition().allMatch(p -> usesJavaPackage(p) || transitiveProjectModulesWithoutImplicitProvidersCache.anyMatch(tm -> tm.usesJavaPackage(p))))
                    ;
        return ReusableStream.empty();
    }

    private ReusableStream<ProjectModule> getRequiredProvidersModulesSearchScope() {
        return requiredProvidersModulesSearchScopeCache;
    }

    private ReusableStream<ProjectModule> getOptionalProvidersModulesSearchScope() {
        return optionalProvidersModulesSearchScopeCache;
    }

    @Override
    public ReusableStream<Providers> getExecutableProviders() {
        return executableImplicitProvidersCache;
    }

    private ReusableStream<Providers> collectExecutableProviders() {
        return collectExecutableModuleProviders(this, this);
    }

    private static ReusableStream<Providers> collectExecutableModuleProviders(LocalProjectModule executableModule, LocalProjectModule collectingModule) {
        if (!executableModule.isExecutable())
            return ReusableStream.empty();
        Set<ProjectModule> allProviderModules = new HashSet<>();
        Set<String> allSpiClassNames = new HashSet<>();
        return ReusableStream.concat(
                // Collecting single/required SPI providers
                executableModule.collectProviders(collectingModule, true, allProviderModules, allSpiClassNames),
                // Collecting multiple/optional SPI providers
                executableModule.collectProviders(collectingModule, false, allProviderModules, allSpiClassNames),
                // Collecting subsequent single/required SPI providers
                ReusableStream.create(() -> new HashSet<>(executableModule == collectingModule ? allProviderModules : Collections.emptySet()).spliterator())
                        .flatMap(m -> collectingModule.collectProviders(m, true, allProviderModules, allSpiClassNames)),
                // Collecting subsequent multiple/optional SPI providers
                ReusableStream.create(() -> new HashSet<>(executableModule == collectingModule ? allProviderModules : Collections.emptySet()).spliterator())
                        .flatMap(m -> collectingModule.collectProviders(m, false, allProviderModules, allSpiClassNames))
        );
    }

    private ReusableStream<Providers> collectProviders(ProjectModule module, boolean single, Set<ProjectModule> allProviderModules, Set<String> allSpiClassNames) {
        ReusableStream<ProjectModule> searchScope = single ?
                getRequiredProvidersModulesSearchScope() :
                /* Adding provider modules in the optional scope because they may also provide optional , ex: webfx-platform-shared-gwt also declares a module initializer   */
                ReusableStream.concat(getOptionalProvidersModulesSearchScope(), ReusableStream.create(allProviderModules::spliterator)).distinct();
        ReusableStream<Module> stackWithoutImplicitProviders = ReusableStream.concat(ReusableStream.of(module), ProjectModule.mapDestinationModules(module.getTransitiveDependenciesWithoutImplicitProviders()));
        return ProjectModule.filterProjectModules(stackWithoutImplicitProviders)
                .flatMap(m -> single ? m.getUsedRequiredJavaServices() : m.getUsedOptionalJavaServices())
                .map(spiClassName -> {
                    if (allSpiClassNames.contains(spiClassName))
                        return null; // returning null to avoid rework
                    allSpiClassNames.add(spiClassName);
                    Providers providers = new Providers(spiClassName, RootModule.findModulesProvidingJavaService(searchScope, spiClassName, getTarget(), single));
                    providers.getProviderModules().collect(Collectors.toCollection(() -> allProviderModules));
                    if (providers.getProviderClassNames().count() == 0)
                        Logger.verbose("No provider found for " + spiClassName + " among " + searchScope.map(ProjectModule::getName).stream().sorted().collect(Collectors.toList()));
                    return providers;
                })
                .filter(Objects::nonNull) // Removing nulls
                ;
    }

    private ReusableStream<ModuleDependency> resolveInterfaceDependencyIfExecutable(ModuleDependency dependency) {
        if (isExecutable() && dependency.getDestinationModule() instanceof ProjectModule) {
            ProjectModule module = (ProjectModule) dependency.getDestinationModule();
            if (module.isInterface()) {
                ReusableStream<ProjectModule> searchScope = getRequiredProvidersModulesSearchScope();
                ProjectModule concreteModule = searchScope
                        .filter(m -> m.implementsModule(module))
                        .filter(m -> isCompatibleWithTargetModule(this))
                        .max(Comparator.comparingInt(m -> m.gradeTargetMatch(getTarget())))
                        .orElse(null);
                if (concreteModule != null) {
                    // Creating the dependency to this concrete module and adding transitive dependencies
                    ReusableStream<ModuleDependency> concreteModuleDependencies = ModuleDependency.createImplicitProviderDependency(this, concreteModule)
                            .collectThisAndTransitiveDependencies();
                    // In case these dependencies have a SPI, collecting the providers and adding their associated implicit dependencies
                    // Ex: interface = [webfx-extras-visual-controls-]grid-registry, concrete = [...]-grid-registry-spi, provider = [...]-grid-peers-javafx
                    // TODO: See if we can move this up to the generic steps when building dependencies
                    if (concreteModule instanceof LocalProjectModule) // Added to solve cast problem, is it OK?
                        concreteModuleDependencies = ReusableStream.concat(
                                concreteModuleDependencies,
                                collectExecutableModuleProviders(this, (LocalProjectModule) concreteModule)
                                        .flatMap(Providers::getProviderModules)
                                        //.filter(m -> transitiveDependenciesWithoutImplicitProvidersCache.noneMatch(dep -> dep.getDestinationModule() == m)) // Removing modules already in transitive dependencies (no need to repeat them)
                                        .map(m -> ModuleDependency.createImplicitProviderDependency(this, m))
                        );
                    return concreteModuleDependencies
                            .filter(dep -> !(dep.getDestinationModule() instanceof ProjectModule && ((ProjectModule) dep.getDestinationModule()).isInterface()))
                            .distinct()
                            ;
                }
                String message = "No concrete module found for interface module " + module + " in executable module " + this + " among " + searchScope.map(ProjectModule::getName).stream().sorted().collect(Collectors.toList());
                if (isModuleUnderRootHomeDirectory(this))
                    Logger.warning(message);
                else
                    Logger.verbose(message);
            }
        }
        return ReusableStream.of(dependency);
    }

    public static ReusableStream<LocalProjectModule> filterLocalProjectModules(ReusableStream<Module> modules) {
        return modules
                .filter(m -> m instanceof LocalProjectModule)
                .map(m -> (LocalProjectModule) m);
    }

    private ReusableStream<Module> collectExecutableEmulationModules() {
        if (isExecutable(Platform.GWT))
            return ReusableStream.of(
                    getRootModule().findModule("webfx-kit-gwt"),
                    getRootModule().findModule("webfx-platform-gwt-emul-javabase"),
                    getRootModule().findModule("gwt-time")
            );
        if (isExecutable(Platform.JRE)) {
            if (getTarget().hasTag(TargetTag.OPENJFX) || getTarget().hasTag(TargetTag.GLUON)) {
                boolean usesMedia = ProjectModule.mapDestinationModules(transitiveDependenciesWithoutEmulationAndImplicitProvidersCache).anyMatch(m -> m.getName().contains("webfx-kit-javafxmedia-emul"));
                return usesMedia ? ReusableStream.of(
                        getRootModule().findModule("webfx-kit-openjfx"),
                        getRootModule().findModule("webfx-kit-javafxmedia-emul"),
                        getRootModule().findModule("webfx-platform-java-boot-impl")
                ) : ReusableStream.of(
                        getRootModule().findModule("webfx-kit-openjfx"),
                        getRootModule().findModule("webfx-platform-java-boot-impl")
                );
            }
            return ProjectModule.mapDestinationModules(transitiveDependenciesWithoutEmulationAndImplicitProvidersCache)
                    .filter(RootModule::isJavaFxEmulModule);
        }
        return ReusableStream.empty();
    }

}