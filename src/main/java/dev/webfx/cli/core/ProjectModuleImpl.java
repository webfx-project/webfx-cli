package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.abstr.XmlGavModuleFile;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.util.List;
import java.util.Map;

/**
 * @author Bruno Salmon
 */
public abstract class ProjectModuleImpl extends ModuleImpl implements ProjectModule {

    private JavaSourceRootAnalyzer mainJavaJavaSourceRootAnalyzer, testJavaJavaSourceRootAnalyzer;

    /**
     * Returns the children project modules if any (only first level under this module).
     */
    private final ReusableStream<ProjectModule> childrenModulesCache =
            getChildrenModuleNames()
                    .map(this::getOrCreateChildProjectModule)
                    .cache();

    /**
     * Returns the children project modules if any (all levels under this module).
     */
    private final ReusableStream<ProjectModule> childrenModulesInDepthCache =
            childrenModulesCache
                    .flatMap(ProjectModule::getThisAndChildrenModulesInDepth)
            //.cache()
            ;

    /**
     * Returns all java services provided by this module (returns the list of files under META-INF/services).
     */
    private final ReusableStream<String> providedJavaServicesCache =
            ReusableStream.create(() -> getWebFxModuleFile().providedServiceProviders())
                    .map(ServiceProvider::getSpi)
                    .distinct()
                    .sorted()
                    .cache();


    /**
     * Returns all source module dependencies directly required by the source code of this module but that couldn't be
     * detected by the source code analyzer (due to limitations of the current source code analyzer which is based on
     * regular expressions). These source module dependencies not detected by the source code analyzer must be listed
     * in the webfx module file for now.
     */
    final ReusableStream<ModuleDependency> undetectedByCodeAnalyzerSourceDependenciesCache =
            ReusableStream.create(() -> getWebFxModuleFile().getUndetectedUsedBySourceModulesDependencies())
                    .cache();


    /**
     * Returns source module dependencies explicitly mentioned in webfx.xml, which can be used to set some special
     * attribute on those dependencies (ex: optional = "true").
     */
    final ReusableStream<ModuleDependency> explicitSourceDependenciesCache =
            ReusableStream.create(() -> getWebFxModuleFile().getExplicitSourceModulesDependencies())
                    .cache();

    /**
     * Returns all resource module dependencies directly required by the source code of this module (must be listed in
     * the webfx module file).
     */
    final ReusableStream<ModuleDependency> resourceDirectDependenciesCache =
            ReusableStream.create(() -> getWebFxModuleFile().getResourceModuleDependencies())
                    .cache();

    /**
     * Returns the application module to be executed in case this module is executable (otherwise returns nothing). For
     * now the application module is implicitly guessed from the executable module name (ex: if executable module is
     * my-app-javafx or my-app-gwt, then the application module is my-app).
     */
    // Modules
    final ReusableStream<ModuleDependency> applicationDependencyCache =
            ReusableStream.create(() -> {
                ProjectModule applicationModule = getApplicationModule();
                return applicationModule != null ? ReusableStream.of(ModuleDependency.createApplicationDependency(this, applicationModule)) : ReusableStream.empty();
            });

    /**
     * Returns the plugin module dependencies to be directly added to this module (must be listed in the webfx module
     * file).
     */
    final ReusableStream<ModuleDependency> pluginDirectDependenciesCache =
            ReusableStream.create(() -> getWebFxModuleFile().getPluginModuleDependencies())
                    .cache();


    private final ProjectModule parentDirectoryModule;
    private ProjectModule parentModule;
    private final RootModule rootModule;
    private Target target;
    private boolean checkReadGavFromModuleFiles;
    private boolean checkParentFromModuleFiles;

    public ProjectModuleImpl(String name, ProjectModule parentDirectoryModule) {
        super(name);
        this.parentDirectoryModule = parentDirectoryModule;
        ProjectModule m = this;
        while (m != null && !(m instanceof RootModule))
            m = m.getParentDirectoryModule();
        rootModule = (RootModule) m;
    }

    @Override
    public ProjectModule getParentDirectoryModule() {
        return parentDirectoryModule;
    }

    @Override
    public ProjectModule getParentModule() {
        checkParentFromModuleFiles();
        return parentModule;
    }

    public RootModule getRootModule() {
        return rootModule;
    }

    @Override
    public String getApplicationLabel() {
        String applicationLabel = ProjectModule.super.getApplicationLabel();
        if (applicationLabel == null) {
            ProjectModule applicationModule = getApplicationModule();
            if (applicationModule != null)
                applicationLabel = applicationModule.getApplicationLabel();
        }
        return applicationLabel;
    }

    public String getApplicationId() {
        String applicationId = ProjectModule.super.getApplicationId();
        if (applicationId == null) {
            ProjectModule applicationModule = getApplicationModule();
            if (applicationModule != null)
                applicationId = applicationModule.getApplicationId();
        }
        return applicationId;
    }

    public ProjectModule getApplicationModule() {
        ProjectModule applicationModule = null;
        if (isExecutable()) {
            String moduleName = getName();
            applicationModule = getModuleRegistry().getRegisteredProjectModule(moduleName.substring(0, moduleName.lastIndexOf('-')));
        }
        return applicationModule;
    }

    public Target getTarget() {
        if (target == null)
            target = new Target(this);
        return target;
    }

    private void checkReadGavFromModuleFiles() {
        if (!checkReadGavFromModuleFiles) {
            checkReadGavFromModuleFiles = true;
            readGavFromModuleFile(getMostRelevantGavModuleFile());
        }
    }

    private XmlGavModuleFile getMostRelevantGavModuleFile() {
        // Reading GAV from webfx.xml (and parents) unless webfx.xml doesn't exist or maven update is skipped
        return getWebFxModuleFile().fileExists() && !getWebFxModuleFile().skipMavenPomUpdate() ? getWebFxModuleFile()
                // in that case, pom.xml is the reference to read the GAV for this module
                : getMavenModuleFile();
    }

    private void readGavFromModuleFile(XmlGavModuleFile gavModuleFile) {
        if (groupId == null) {
            groupId = gavModuleFile.getGroupId();
            if (groupId == null && parentModule == null) {
                groupId = gavModuleFile.lookupParentGroupId();
                if (groupId == null)
                    checkParentFromModuleFiles();
            }
        }
        if (artifactId == null)
            artifactId = gavModuleFile.getArtifactId();
        if (version == null) {
            version = gavModuleFile.getVersion();
            if (version == null && parentModule == null)
                version = gavModuleFile.lookupParentVersion();
        }
    }

    private void checkParentFromModuleFiles() {
        if (!checkParentFromModuleFiles) {
            checkParentFromModuleFiles = true;
            if (parentModule == null) {
                if (this instanceof M2ProjectModule)
                    parentModule = parentDirectoryModule;
                else {
                    XmlGavModuleFile gavModuleFile = getMostRelevantGavModuleFile();
                    String lookupParentName = gavModuleFile.lookupParentName(); // TODO: Always null with pom.xml?
                    // If no parent is specified, we take the parent directory module as the parent (if exists)
                    if (lookupParentName == null && parentDirectoryModule != null)
                        parentModule = parentDirectoryModule;
                    else { // Otherwise
                        // If no parent is specified (and no parent directory module), we take "webfx-parent" by default
                        if (lookupParentName == null)
                            lookupParentName = "webfx-parent";
                        // Then we search the module from its name
                        if (!lookupParentName.equals(getName())) // parent shouldn't be this module
                            parentModule = getRootModule().searchRegisteredProjectModule(lookupParentName);
                    }
                }
            }
        }
    }

    @Override
    public String getGroupId() {
        if (groupId == null)
            checkReadGavFromModuleFiles();
        return groupId != null || parentModule == null ? groupId : parentModule.getGroupId();
    }

    @Override
    public String getArtifactId() {
        if (artifactId == null)
            checkReadGavFromModuleFiles();
        return artifactId;
    }

    @Override
    public String getVersion() {
        if (version == null)
            checkReadGavFromModuleFiles();
        return version != null || parentModule == null ? version : parentModule.getVersion();
    }

    public ReusableStream<ProjectModule> getChildrenModules() {
        return childrenModulesCache;
    }

    public ReusableStream<ProjectModule> getThisAndChildrenModules() {
        return ReusableStream.concat(ReusableStream.of(this), getChildrenModules());
    }

    public ReusableStream<ProjectModule> getChildrenModulesInDepth() {
        return childrenModulesInDepthCache;
    }

    ///// Java classes

    @Override
    public JavaSourceRootAnalyzer getMainJavaSourceRootAnalyzer() {
        if (mainJavaJavaSourceRootAnalyzer == null)
            mainJavaJavaSourceRootAnalyzer = new JavaSourceRootAnalyzer(() -> hasMainJavaSourceDirectory() ? getMainJavaSourceDirectory() : null, this);
        return mainJavaJavaSourceRootAnalyzer;
    }

    @Override
    public JavaSourceRootAnalyzer getTestJavaSourceRootAnalyzer() {
        if (testJavaJavaSourceRootAnalyzer == null)
            testJavaJavaSourceRootAnalyzer = new JavaSourceRootAnalyzer(() -> hasTestJavaSourceDirectory() ? getTestJavaSourceDirectory() : null, this);
        return testJavaJavaSourceRootAnalyzer;
    }

    private Map<ProjectModule, List<ProjectModule>> dependencyGraphWithImplicitProvidersCache;
    private Map<ProjectModule, List<ProjectModule>> dependencyGraphWithoutImplicitProvidersCache;

    public Map<ProjectModule, List<ProjectModule>> getProjectModulesDependencyGraph(boolean withImplicitProviders) {
        // Returning the cache value if present
        if (withImplicitProviders && dependencyGraphWithImplicitProvidersCache != null)
            return dependencyGraphWithImplicitProvidersCache;
        if (!withImplicitProviders && dependencyGraphWithoutImplicitProvidersCache != null)
            return dependencyGraphWithoutImplicitProvidersCache;

        // Getting the requested dependencies (with or without implicit providers)
        ReusableStream<ModuleDependency> dependencies = withImplicitProviders ?
                getMainJavaSourceRootAnalyzer().getTransitiveDependencies() :
                getMainJavaSourceRootAnalyzer().getTransitiveDependenciesWithoutImplicitProviders();

        // Computing the dependency graph of these dependencies
        Map<ProjectModule, List<ProjectModule>> dependencyGraph = ModuleDependency.createProjectModulesDependencyGraph(dependencies);

        // Caching that dependency graph for possible next calls
        if (withImplicitProviders)
            dependencyGraphWithImplicitProvidersCache = dependencyGraph;
        else
            dependencyGraphWithoutImplicitProvidersCache = dependencyGraph;

        return dependencyGraph;
    }

    ///// Java packages

    public ReusableStream<String> getProvidedJavaServices() {
        return providedJavaServicesCache;
    }

    private ReusableStream<LibraryModule> requiredLibraryModulesCache;
    private ReusableStream<ProjectModule> transitiveWebFxLibraryProjectModulesCache;

    @Override
    public ReusableStream<LibraryModule> getRequiredLibraryModules() {
        if (requiredLibraryModulesCache == null)
            requiredLibraryModulesCache = ProjectModule.super.getRequiredLibraryModules().cache();
        return requiredLibraryModulesCache;
    }

    @Override
    public ReusableStream<ProjectModule> getRequiredProvidersSearchScopeWithinWebFxLibraries() {
        if (transitiveWebFxLibraryProjectModulesCache == null) {
            transitiveWebFxLibraryProjectModulesCache = ProjectModule.super.getRequiredProvidersSearchScopeWithinWebFxLibraries().distinct().cache();
            /*StringBuilder sb = new StringBuilder(">>> " + this + ": ");
            transitiveWebFxLibraryProjectModulesCache.forEach(m -> sb.append(m).append(' '));
            System.out.println(sb);*/
        }
        return transitiveWebFxLibraryProjectModulesCache;
    }

    /******************************
     ***** Analyzing streams  *****
     ******************************/

    public ReusableStream<JavaFile> getJavaFilesDependingOn(String destinationModule) {
        return getMainJavaSourceRootAnalyzer().getSourceFiles()
                .filter(jf -> jf.getUsedJavaPackages().anyMatch(p -> destinationModule.equals(rootModule.searchJavaPackageModule(p, this).getName())))
                ;
    }

    private BuildInfo buildInfo;

    public BuildInfo getBuildInfo() {
        if (buildInfo == null)
            buildInfo = new BuildInfo(this);
        return buildInfo;
    }

    private ReusableStream<ProjectModule> exportSnapshotUsageCoverage;
    @Override
    public ReusableStream<ProjectModule> getDirectivesUsageCoverage() {
        if (exportSnapshotUsageCoverage == null)
            exportSnapshotUsageCoverage = getDirectivesUsageCoverage(this).distinct().cache();
        return exportSnapshotUsageCoverage;
    }

    private static ReusableStream<ProjectModule> getDirectivesUsageCoverage(ProjectModule projectModule) {
        ReusableStream<ProjectModule> projectWithoutLibrariesCoverage = projectModule
                .getThisAndChildrenModulesInDepth()
                .flatMap(projectModule1 -> projectModule1.getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules()) // Normally doesn't require to access sources...
                .filter(ProjectModule.class::isInstance).map(ProjectModule.class::cast)
                .distinct();
        ReusableStream<ProjectModule> librariesCoverage = projectWithoutLibrariesCoverage
                .flatMap(ProjectModule::getRequiredLibraryModules)
                .distinct()
                .map(l -> projectModule.searchRegisteredModule(l.getName(), true))
                .filter(ProjectModule.class::isInstance).map(ProjectModule.class::cast)
                .flatMap(ProjectModule::getDirectivesUsageCoverage);
        return ReusableStream.concat(
                projectWithoutLibrariesCoverage,
                librariesCoverage
        );
    }

}
