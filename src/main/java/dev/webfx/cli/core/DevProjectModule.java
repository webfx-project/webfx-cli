package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.*;
import dev.webfx.cli.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.cli.modulefiles.abstr.WebFxModuleFileCache;
import dev.webfx.cli.util.hashlist.HashList;
import dev.webfx.cli.util.sort.TopologicalSort;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;

import javax.lang.model.SourceVersion;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public class DevProjectModule extends ProjectModuleImpl {

    private final Path homeDirectory;
    private Boolean hasSourceDirectory, hasMainJavaSourceDirectory, hasMainResourcesDirectory, hasMainWebFxDirectory, hasTestJavaSourceDirectory;
    private DevJavaModuleInfoFile mainJavaModuleInfoFile;
    private WebFxModuleFile webFxModuleFile;
    private DevMavenPomModuleFile mavenPomModuleFile;
    private DevGwtModuleFile gwtModuleFile;
    private DevGwtJ2clHtmlFile gwtJ2clHtmlFile;
    // The webfx root module of the application repository which may be different from the root module in case of
    // aggregate modules with different submodules (each submodule is a different app repo with its own root webfx module).
    private DevProjectModule webFxRootModule;

    private final ReusableStream<String> fileResourcePackagesCache =
            ReusableStream.create(() -> ReusableStream.create(() -> // Using deferred creation because we can't call these methods before the constructor is executed
                            hasMainResourcesDirectory() ? SplitFiles.uncheckedWalk(getMainResourcesDirectory()) : Spliterators.emptySpliterator())
                    // We want to filter directories that are not empty. To do that by walking through files and getting their parent directory
                    .filter(path -> {
                        if (Files.isDirectory(path))
                            return false;
                        // We also ignore hidden files
                        try {
                            if (Files.isHidden(path))
                                return false;
                        } catch (IOException e) {
                            return false;
                        }
                        return true;
                    })
                    .map(Path::getParent)
                    // We remove duplicates (because the directory was repeated by the number of files in it)
                    .distinct()
                    // We get the relative path from the resource directory
                    .map(path -> getMainResourcesDirectory().relativize(path))
                    // We transform the path into a package name
                    .map(path -> path.toString().replace('/', '.')))
                    // We ignore those not following the Java package name convention (this includes META-INF)
                    .filter(pkg -> SourceVersion.isName(pkg) && !pkg.contains("$"))
                    .cache()
                    .name("resourcePackagesCache");


    /************************
     ***** Constructors *****
     ************************/

    public DevProjectModule(Path homeDirectory, ProjectModule parentModule) {
        // The module name is the name of the home directory, unless a different name is specified in webfx.xml
        super(homeDirectory.toAbsolutePath().getFileName().toString(), parentModule);
        this.homeDirectory = homeDirectory;
        // When specified, the module name specified in webfx.xml is finally preferred over the name of the home directory
        String webFxName = getWebFxModuleFile().getName();
        if (webFxName != null)
            this.name = webFxName;
    }

    /*************************
     ***** Basic getters *****
     *************************/

    public DevProjectModule getWebFxRootModule() {
        if (webFxRootModule == null) {
            ProjectModule pm = getWebFxModuleFile().fileExists() ? this : null;
            while (pm != null) {
                // We can detect the webfx root module through its break in the modules hierarchy: its parent module
                // (ex: webfx-parent) is different from the parent directory module (ex: aggregate module).
                ProjectModule parentDirectoryModule = pm.getParentDirectoryModule();
                if (parentDirectoryModule != pm.getParentModule())
                    break;
                pm = parentDirectoryModule;
            }
            webFxRootModule = (DevProjectModule) pm;
        }
        return webFxRootModule;
    }

    public WebFxModuleFile getWebFxModuleFile() {
        if (webFxModuleFile == null)
            webFxModuleFile = new WebFxModuleFileCache(new DevWebFxModuleFile(this));
        return webFxModuleFile;
    }

    public DevMavenPomModuleFile getMavenModuleFile() {
        if (mavenPomModuleFile == null)
            mavenPomModuleFile = new DevMavenPomModuleFile(this);
        return mavenPomModuleFile;
    }

    public Path getHomeDirectory() {
        return homeDirectory;
    }

    public Path getSourceDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src") : null;
    }

    @Override
    public Path getMainJavaSourceDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/main/java/") : null;
    }

    @Override
    public Path getMainWebFxSourceDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/main/webfx/") : null;
    }

    @Override
    public Path getTestJavaSourceDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/test/java/") : null;
    }

    @Override
    public Path getMainResourcesDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/main/resources/") : null;
    }

    public Path getMetaInfJavaServicesDirectory() {
        return getMainResourcesDirectory().resolve("META-INF/services/");
    }

    public DevGwtModuleFile getGwtModuleFile() {
        if (gwtModuleFile == null)
            gwtModuleFile = new DevGwtModuleFile(this);
        return gwtModuleFile;
    }

    public DevGwtJ2clHtmlFile getGwtJ2clHtmlFile() {
        if (gwtJ2clHtmlFile == null)
            gwtJ2clHtmlFile = new DevGwtJ2clHtmlFile(this);
        return gwtJ2clHtmlFile;
    }

    public DevProjectModule getOrCreateChildProjectModule(String name) {
        return getOrCreateDevProjectModule(homeDirectory.resolve(name).normalize(), this);
    }

    DevProjectModule getOrCreateDevProjectModule(Path homeDirectory, DevProjectModule parentModule) {
        return getModuleRegistry().getOrCreateDevProjectModule(homeDirectory, parentModule);
    }

    public boolean hasSourceDirectory() {
        if (hasSourceDirectory == null)
            hasSourceDirectory = pathExists(getSourceDirectory());
        return hasSourceDirectory;
    }

    private static boolean pathExists(Path path) {
        return path != null && Files.exists(path);
    }

    @Override
    public boolean hasMainJavaSourceDirectory() {
        if (hasMainJavaSourceDirectory == null)
            hasMainJavaSourceDirectory = hasSourceDirectory() && pathExists(getMainJavaSourceDirectory());
        return hasMainJavaSourceDirectory;
    }

    @Override
    public boolean hasMainResourcesDirectory() {
        if (hasMainResourcesDirectory == null)
            hasMainResourcesDirectory = hasSourceDirectory() && pathExists(getMainResourcesDirectory());
        return hasMainResourcesDirectory;
    }

    @Override
    public boolean hasMainWebFxSourceDirectory() {
        if (hasMainWebFxDirectory == null)
            hasMainWebFxDirectory = hasSourceDirectory() && pathExists(getMainWebFxSourceDirectory());
        return hasMainWebFxDirectory;
    }

    @Override
    public ReusableStream<String> getFileResourcePackages() {
        return fileResourcePackagesCache;
    }

    public boolean hasTestJavaSourceDirectory() {
        if (hasTestJavaSourceDirectory == null)
            hasTestJavaSourceDirectory = hasSourceDirectory() && pathExists(getTestJavaSourceDirectory());
        return hasTestJavaSourceDirectory;
    }

    public DevJavaModuleInfoFile getMainJavaModuleFile() {
        if (mainJavaModuleInfoFile == null)
            mainJavaModuleInfoFile = new DevJavaModuleInfoFile(this);
        return mainJavaModuleInfoFile;
    }

    @Override
    public ReusableStream<String> getSubdirectoriesChildrenModules() {
        return ReusableStream.create(() -> SplitFiles.uncheckedWalk(getHomeDirectory(), 1))
                .filter(Files::isDirectory)
                .filter(path -> !path.equals(getHomeDirectory()))
                .filter(path -> Files.exists(path.resolve("webfx.xml")) || Files.exists(path.resolve("pom.xml")))
                .map(path -> path.toFile().getName())
                .sorted(Module::compareModuleNames)
                .cache();
    }

    public DevRootModule getRootModule() {
        return (DevRootModule) super.getRootModule();
    }


    boolean isModuleUnderRootHomeDirectory(Module module) {
        Path homeDirectory = module instanceof DevProjectModule ? ((DevProjectModule) module).getHomeDirectory() : null;
        return homeDirectory != null && homeDirectory.startsWith(getRootModule().getHomeDirectory());
    }

    public Path getGwtExecutableFilePath() {
        return getHomeDirectory().resolve("target").resolve(getName() + "-" + getVersion() + "/" + getName().replace('-', '_') + "/index.html");
    }

    private LinkedHashMap<String, Path> moduleWebFxPathsAsc;
    private LinkedHashMap<String, Path> moduleWebFxPathsDesc;

    public LinkedHashMap<String, Path> collectThisAndTransitiveWebFXPaths(boolean canUseCache, boolean asc) {
        if (asc && moduleWebFxPathsAsc != null)
            return moduleWebFxPathsAsc;
        if (!asc && moduleWebFxPathsDesc != null)
            return moduleWebFxPathsDesc;

        LinkedHashMap<String, Path> moduleWebFxPaths = new LinkedHashMap<>();

        // Reading the cache file (if allowed)
        String moduleCacheName = getHomeDirectory().toAbsolutePath().toString().replace('/', '~') + "-transitive-webfx-" + (asc ? "asc" : "desc") + ".txt";
        Path moduleCacheFile = WebFXHiddenFolder.getCacheFolder().resolve(moduleCacheName);
        boolean cacheRead = false;
        if (canUseCache && Files.exists(moduleCacheFile)) {
            try (Scanner scanner = new Scanner(moduleCacheFile)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    int p = line.indexOf(':');
                    String moduleName = line.substring(0, p);
                    String webfxPathUri = line.substring(p + 1);
                    // URI stored in cache so that we can retrieve the path even if inside jar or zip files
                    moduleWebFxPaths.put(moduleName, Paths.get(new URI(webfxPathUri)));
                }
                cacheRead = true;
            } catch (Exception ignored) {
            }
        }
        if (!cacheRead) {
            // Creating the dependency graph of the transitive project modules (i.e. list of dependencies for each module)
            Map<ProjectModule, List<ProjectModule>> modulesDependencyGraph = getProjectModulesDependencyGraph(true);

            // We sort these transitive modules in the requested order (ascending = most independent modules first,
            // ending with the executable module - descending = reverse order).
            List<ProjectModule> sortedModules =
                    asc ? TopologicalSort.sortAsc (modulesDependencyGraph) :
                          TopologicalSort.sortDesc(modulesDependencyGraph);

            // Reducing the modules to only WebFX project modules with a webfx source directory
            sortedModules = sortedModules.stream()
                    .filter(ProjectModule::hasMainWebFxSourceDirectory)
                    .collect(Collectors.toList());

            // The previous sort is not good enough, because it doesn't take in consideration that the modules may come
            // from different repositories that also have (non-circular) dependencies between them, and modules coming
            // from repository B that depends on repository A should always be listed second (if ascending), i.e.
            // modules B listed after modules A. This is important, especially if there is an implicit dependency not
            // known by WebFX, such as overriding in module B (typically application code) some CSS rules declared in
            // module A (typically webfx-A, webfx-lib-A, or webfx-extras-A, etc...). These rules from B needs to be
            // merged after the rules from A in the final CSS file, isn't it? But if these rules B are declared in a
            // separate module B (such as a global CSS module for the application), there is no explicit code dependency
            // between module B and module A, and the result of the previous sort may list module B first (because it
            // looks like an independent module). We can resolve that wrong order by understanding that repository B
            // depends (in general) on repository A, and therefore all modules B should be listed after modules A.
            // In order to achieve that second sort, we first build the repository dependency graph from the modules
            // dependency graph:
            Map<ProjectModule, List<ProjectModule>> repositoriesDependencyGraph = new HashMap<>();
            modulesDependencyGraph.forEach((module, moduleDeps) -> {
                ProjectModule repoModule = getRepositoryModule(module);
                List<ProjectModule> repoDeps = repositoriesDependencyGraph.computeIfAbsent(repoModule, k -> new HashList<>()); // HashList prevents duplicates
                moduleDeps.stream().map(DevProjectModule::getRepositoryModule).forEach(repoDeps::add);
            });

            // Then we sort these repositories in the same requested order
            List<ProjectModule> sortedRepositoryModules =
                    asc ? TopologicalSort.sortAsc (repositoriesDependencyGraph) :
                          TopologicalSort.sortDesc(repositoriesDependencyGraph);

            // Finally we execute that second sorting pass, which will group modules by repository (but modules inside
            // the same repository keep the same order coming from the first sorting pass).
            sortedModules.sort((pm1, pm2) -> {
                ProjectModule rm1 = getRepositoryModule(pm1);
                ProjectModule rm2 = getRepositoryModule(pm2);
                int index1 = sortedRepositoryModules.indexOf(rm1);
                int index2 = sortedRepositoryModules.indexOf(rm2);
                return Integer.compare(index1, index2);
            });

            // Having the modules now correctly sorted, we finally collect the webfx paths for each module in that order
            sortedModules.forEach(pm -> moduleWebFxPaths.put(pm.getName(), pm.getMainWebFxSourceDirectory()));

            StringBuilder sb = new StringBuilder();
            moduleWebFxPaths.forEach((moduleName, webfxPath) -> {
                if (sb.length() > 0)
                    sb.append("\n");
                // URI stored in cache so that we can retrieve the path even if inside jar or zip files
                sb.append(moduleName).append(':').append(webfxPath.toUri());
            });
            TextFileReaderWriter.writeTextFile(sb.toString(), moduleCacheFile, true, true);
        }

        if (asc)
            moduleWebFxPathsAsc = moduleWebFxPaths;
        else
            moduleWebFxPathsDesc = moduleWebFxPaths;

        return moduleWebFxPaths;
    }

    private static ProjectModule getRepositoryModule(ProjectModule pm) {
        if (pm instanceof DevProjectModule)
            return ((DevProjectModule) pm).getWebFxRootModule();
        return pm.getRootModule();
    }

}