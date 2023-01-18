package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.abstr.XmlGavModuleFile;
import dev.webfx.lib.reusablestream.ReusableStream;

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


    private ProjectModule parentModule;
    private final RootModule rootModule;
    private Target target;
    private boolean checkReadGavFromModuleFiles;
    private boolean checkParentFromModuleFiles;

    public ProjectModuleImpl(String name, ProjectModule parentModule) {
        super(name);
        this.parentModule = parentModule;
        ProjectModule m = this;
        while (m != null && !(m instanceof RootModule))
            m = m.getParentModule();
        rootModule = (RootModule) m;
    }

    public ProjectModule fetchParentModule() {
        checkParentFromModuleFiles();
        return parentModule;
    }

    @Override
    public ProjectModule getParentModule() {
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
            applicationModule = getRootModule().getModuleRegistry().getRegisteredProjectModule(moduleName.substring(0, moduleName.lastIndexOf('-')));
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
            readGavFromModuleFile(
                    // Reading GAV from webfx.xml (and parents) unless webfx.xml doesn't exist or maven update is skipped
                    getWebFxModuleFile().fileExists() && !getWebFxModuleFile().skipMavenPomUpdate() ? getWebFxModuleFile()
                            // in that case, pom.xml is the reference to read the GAV for this module
                            : getMavenModuleFile()
            );
        }
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
            if (parentModule == null)
                lookupParentFromModuleFile(getWebFxModuleFile().fileExists() ? getWebFxModuleFile() : getMavenModuleFile());
        }
    }

    private void lookupParentFromModuleFile(XmlGavModuleFile gavModuleFile) {
        if (parentModule == null) {
            String lookupParentName = gavModuleFile.lookupParentName();
            String parentName = lookupParentName != null ? lookupParentName : "webfx-parent";
            if (!parentName.equals(getName()))
                parentModule = getRootModule().searchRegisteredProjectModule(parentName);
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

    ///// Java packages

    public ReusableStream<String> getProvidedJavaServices() {
        return providedJavaServicesCache;
    }

    ReusableStream<String> findRequiredServices() {
        return ProjectModule.filterDestinationProjectModules(getMainJavaSourceRootAnalyzer().getTransitiveDependencies())
                .flatMap(projectModule -> projectModule.getMainJavaSourceRootAnalyzer().getUsedJavaServices())
                .distinct()
                ;
    }

    ReusableStream<ProjectModule> findModulesProvidingRequiredService(TargetTag... serviceTags) {
        return findModulesProvidingRequiredService(new Target(serviceTags));
    }

    ReusableStream<ProjectModule> findModulesProvidingRequiredService(Target serviceTarget) {
        return findRequiredServices()
                .map(js -> getRootModule().findBestMatchModuleProvidingJavaService(js, serviceTarget))
                .distinct()
                ;
    }

    private ReusableStream<LibraryModule> requiredLibraryModulesCache;

    @Override
    public ReusableStream<LibraryModule> getRequiredLibraryModules() {
        if (requiredLibraryModulesCache == null)
            requiredLibraryModulesCache = ProjectModule.super.getRequiredLibraryModules().cache();
        return requiredLibraryModulesCache;
    }

    /******************************
     ***** Analyzing streams  *****
     ******************************/

    public ReusableStream<JavaFile> getJavaFilesDependingOn(String destinationModule) {
        return getMainJavaSourceRootAnalyzer().getSourceFiles()
                .filter(jf -> jf.getUsedJavaPackages().anyMatch(p -> destinationModule.equals(rootModule.searchJavaPackageModule(p, this).getName())))
                ;
    }

    public BuildInfo getBuildInfo() {
        return new BuildInfo(this);
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
