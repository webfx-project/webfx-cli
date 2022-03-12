package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

/**
 * @author Bruno Salmon
 */
public abstract class ProjectModuleImpl extends ModuleImpl implements ProjectModule {

    /**
     * Returns the children project modules if any (only first level under this module).
     */
    protected final ReusableStream<ProjectModule> childrenModulesCache =
            getChildrenModuleNames()
                    .map(this::getOrCreateChildProjectModule)
                    .cache()
            ;

    /**
     * Returns the children project modules if any (all levels under this module).
     */
    protected final ReusableStream<ProjectModule> childrenModulesInDepthCache =
            childrenModulesCache
                    .flatMap(ProjectModule::getThisAndChildrenModulesInDepth)
            //.cache()
            ;

    /**
     * Returns all java services provided by this module (returns the list of files under META-INF/services).
     */
    protected final ReusableStream<String> providedJavaServicesCache =
            ReusableStream.create(() -> getWebFxModuleFile().providedServerProviders())
                    .map(ServiceProvider::getSpi)
                    .distinct()
                    .cache();


    /**
     * Returns all source module dependencies directly required by the source code of this module but that couldn't be
     * discovered by the source code analyzer (due to limitations of the current source code analyzer which is based on
     * regular expressions). These source module dependencies not discovered by the source code analyzer must be listed
     * in the webfx module file for now.
     */
    protected final ReusableStream<ModuleDependency> undiscoveredByCodeAnalyzerSourceDependenciesCache =
            ReusableStream.create(() -> getWebFxModuleFile().getUndiscoveredUsedBySourceModulesDependencies())
                    .cache();


    /**
     * Returns source module dependencies explicitly mentioned in webfx.xml, which can be used to set some special
     * attribute on those dependencies (ex: optional = "true").
     */
    protected final ReusableStream<ModuleDependency> explicitSourceDependenciesCache =
            ReusableStream.create(() -> getWebFxModuleFile().getExplicitSourceModulesDependencies())
                    .cache();

    /**
     * Returns all resource module dependencies directly required by the source code of this module (must be listed in
     * the webfx module file).
     */
    protected final ReusableStream<ModuleDependency> resourceDirectDependenciesCache =
            ReusableStream.create(() -> getWebFxModuleFile().getResourceModuleDependencies())
                    .cache();

    /**
     * Returns the application module to be executed in case this module is executable (otherwise returns nothing). For
     * now the application module is implicitly guessed from the executable module name (ex: if executable module is
     * my-app-javafx or my-app-gwt, then the application module is my-app).
     */
    // Modules
    protected final ReusableStream<ModuleDependency> applicationDependencyCache =
            ReusableStream.create(() -> {
                ProjectModule applicationModule = null;
                if (isExecutable()) {
                    String moduleName = getName();
                    applicationModule = getRootModule().searchProjectModuleWithinSearchScopeWithoutRegisteringLibrariesAndPackages(moduleName.substring(0, moduleName.lastIndexOf('-')), true);
                }
                return applicationModule != null ? ReusableStream.of(ModuleDependency.createApplicationDependency(this, applicationModule)) : ReusableStream.empty();
            });

    /**
     * Returns the plugin module dependencies to be directly added to this module (must be listed in the webfx module
     * file).
     */
    protected final ReusableStream<ModuleDependency> pluginDirectDependenciesCache =
            ReusableStream.create(() -> getWebFxModuleFile().getPluginModuleDependencies())
                    .cache();



    protected final ProjectModule parentModule;
    protected final RootModule rootModule;
    protected Target target;
    protected boolean checkedWebFxModuleFileGAV;

    public ProjectModuleImpl(String name, ProjectModule parentModule) {
        super(name);
        this.parentModule = parentModule;
        ProjectModule m = this;
        while (m != null && !(m instanceof RootModule))
            m = m.getParentModule();
        rootModule = (RootModule) m;
    }

    public ProjectModule getParentModule() {
        return parentModule;
    }

    public RootModule getRootModule() {
        return rootModule;
    }

    public Target getTarget() {
        if (target == null)
            target = new Target(this);
        return target;
    }

    private void checkMavenModuleFileGAV() {
        if (!checkedWebFxModuleFileGAV) {
            checkedWebFxModuleFileGAV = true;
            if (groupId == null) {
                groupId = getMavenModuleFile().getGroupId();
                if (groupId == null && parentModule == null)
                    groupId = getMavenModuleFile().getParentGroupId();
            }
            if (artifactId == null)
                artifactId = getMavenModuleFile().getArtifactId();
            if (version == null) {
                version = getMavenModuleFile().getVersion();
                if (version == null && parentModule == null)
                    version = getMavenModuleFile().getParentVersion();
            }
        }
    }

    @Override
    public String getGroupId() {
        checkMavenModuleFileGAV();
        return groupId != null || parentModule == null ? groupId : parentModule.getGroupId();
    }

    @Override
    public String getArtifactId() {
        checkMavenModuleFileGAV();
        return artifactId;
    }

    @Override
    public String getVersion() {
        checkMavenModuleFileGAV();
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

    ReusableStream<ProjectModule> findProjectModuleStartingWith(String name) {
        return getProjectModuleSearchScope()
                .filter(module -> module.getName().startsWith(name));
    }

    public ReusableStream<String> getProvidedJavaServices() {
        return providedJavaServicesCache;
    }

    ReusableStream<String> findRequiredServices() {
        return ProjectModule.filterDestinationProjectModules(getTransitiveDependencies())
                .flatMap(ProjectModule::getUsedJavaServices)
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

}
