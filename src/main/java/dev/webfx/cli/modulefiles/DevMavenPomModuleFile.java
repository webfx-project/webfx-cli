package dev.webfx.cli.modulefiles;

import dev.webfx.cli.core.Module;
import dev.webfx.cli.core.*;
import dev.webfx.cli.modulefiles.abstr.DevXmlModuleFileImpl;
import dev.webfx.cli.modulefiles.abstr.GavApi;
import dev.webfx.cli.modulefiles.abstr.MavenPomModuleFile;
import dev.webfx.cli.specific.SpecificModules;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public final class DevMavenPomModuleFile extends DevXmlModuleFileImpl implements MavenPomModuleFile {

    private Boolean aggregate;

    public DevMavenPomModuleFile(DevProjectModule module) {
        super(module, module.getHomeDirectory().resolve("pom.xml"));
    }

    public void setAggregate(boolean aggregate) {
        this.aggregate = aggregate;
    }

    public boolean isAggregate() {
        return aggregate != null ? aggregate : lookupNode("modules[1]") != null;
    }

    @Override
    public boolean recreateOnUpdateAndWrite() {
        return !getProjectModule().getWebFxModuleFile().skipMavenPomUpdate();
    }

    @Override
    public Document createInitialDocument() {
        DevProjectModule projectModule = getProjectModule();
        // The project is a root module from the Maven point of view if it's a root module (from WebFX point of view) or
        // if the parent module is not the parent directory module (ex: kbs3-modality-fork is a root module for Maven,
        // even when placed under the kbs3 directory and listed in webfx.xml)
        String templateFileName = getTemplateFileName(projectModule);
        String template = ResourceTextFileReader.readTemplate(templateFileName)
            .replace("${groupId}", ArtifactResolver.getGroupId(projectModule))
            .replace("${artifactId}", getArtifactId(projectModule))
            .replace("${version}", getVersion(projectModule))
            .replace("${application.name}", getApplicationName(projectModule))
            .replace("${application.displayName}", getApplicationDisplayName(projectModule))
            .replace("${webfx-repository-id}", WebFxMavenRepository.ID)
            .replace("${webfx-repository-url}", WebFxMavenRepository.URL)
            .replace("${webfx-repository-snapshot}", String.valueOf(WebFxMavenRepository.SNAPSHOT))
            .replace("${webfx-repository-release}", String.valueOf(WebFxMavenRepository.RELEASE));
        // For the executable Gluon pom, we need to add some extra configuration required by the GluonFX plugin:
        if (template.contains("${plugin.gluonfx.configuration}")) {
            // 1) <attachList> => lists all the Gluon attach modules used by the application:
            String gluonConfig = "<attachList>\n" +
                                 projectModule.getMainJavaSourceRootAnalyzer().getTransitiveModules()
                                     .filter(m -> "com.gluonhq.attach".equals(m.getGroupId()))
                                     .map(GavApi::getArtifactId)
                                     .distinct()
                                     .stream().sorted()
                                     .map(a -> "<list>" + a + "</list>")
                                     .collect(Collectors.joining("\n"))
                                 + "</attachList>";
            // 2) <resourcesList> => lists all resource files potentially used by the application
            gluonConfig += "\n<resourcesList>\n"
                           + ProjectModule.filterProjectModules(projectModule.getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules())
                               .flatMap(ProjectModule::getResourcePackages)
                               .distinct()
                               .stream().sorted()
                               .map(p -> "<list>" + p.replace('.', '/') + "/[^/]+$</list>")
                               .collect(Collectors.joining("\n"))
                           + "</resourcesList>";
            // 3) application identifier
            String applicationId = projectModule.getApplicationId();
            if (applicationId != null)
                gluonConfig += "\n<appIdentifier>" + applicationId + "</appIdentifier>";
            // 4) <releaseConfiguration> => application label
            String applicationLabel = projectModule.getApplicationLabel();
            if (applicationLabel != null)
                gluonConfig += "\n<releaseConfiguration>\n"
                               + "<!-- macOS/iOS -->\n"
                               + "<bundleName>" + applicationLabel + "</bundleName>\n"
                               + "<!-- Android -->\n"
                               + "<appLabel>" + applicationLabel + "</appLabel>\n"
                               + "</releaseConfiguration>";
            template = template.replace("${plugin.gluonfx.configuration}", gluonConfig);
        }
        // J2CL resources
        if (template.contains("${resourceArtifactItems}")) {
            String artifactItems = ProjectModule.filterProjectModules(projectModule.getMainJavaSourceRootAnalyzer().getTransitiveModules()) // Not getThisAndTransitiveModules() because for teavm modules, this module is a war and the unpack goal fails with them
                .map(pm -> {
                    ReusableStream<String> resourcePackages = pm.getNonEmbedResourcePackages();
                    if (resourcePackages.isEmpty())
                        return null;
                    return "<artifactItem>\n" +
                           "<groupId>" + ArtifactResolver.getGroupId(pm) + "</groupId>\n" +
                           "<artifactId>" + ArtifactResolver.getArtifactId(pm) + "</artifactId>\n" +
                           "<version>" + ArtifactResolver.getVersion(pm) + "</version>\n" +
                           "<includes>" + resourcePackages.map(p -> p.replace('.', '/') + "/").collect(Collectors.joining(" , ")) + "</includes>\n" +
                           "</artifactItem>";
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
            if (artifactItems.isEmpty())
                artifactItems = "<skip>true</skip>"; // We skip the resource plugin, otherwise it will fail if no artifact items are provided
            else
                artifactItems = "<artifactItems>\n" + artifactItems + "</artifactItems>";
            template = template.replace("${resourceArtifactItems}", artifactItems);
        }
        Document document = XmlUtil.parseXmlString(template);
        Element documentElement = document.getRootElement();
        Element webFxMavenPomProjectNode = projectModule.getWebFxModuleFile().getMavenManualNode();
        if (webFxMavenPomProjectNode != null) {
            XmlUtil.appendChildren(webFxMavenPomProjectNode, documentElement);
            XmlUtil.indentNode(documentElement, true);
        }
        return document;
    }

    private static String getTemplateFileName(DevProjectModule projectModule) {
        boolean isMavenRootModule = projectModule instanceof RootModule
                                    || projectModule.getParentModule() != projectModule.getParentDirectoryModule();
        BuildInfo buildInfo = projectModule.getBuildInfo();
        return isMavenRootModule ? "pom_root.xml" // (((RootModule) projectModule).isInlineWebFxParent() ? "pom_root_inline.xml" : "pom_root.xml")
            : projectModule.isAggregate() ? "pom_aggregate.xml"
            : !buildInfo.isExecutable ? "pom_not_executable.xml"
            : buildInfo.isForGwt ? "pom_gwt_executable.xml"
            : buildInfo.isForJ2cl ? "pom_j2cl_executable.xml"
            : buildInfo.isForTeaVm ? "pom_teavm_executable.xml"
            : buildInfo.isForGluon ? "pom_gluon_executable.xml"
            : buildInfo.isForVertx ? "pom_vertx_executable.xml"
            : "pom_openjfx_executable.xml";
    }

    public static String getArtifactId(Module module) {
        String artifactId = ArtifactResolver.getArtifactId(module);
        if (artifactId == null)
            artifactId = module.getArtifactId();
        if (artifactId == null)
            artifactId = module.getName();
        return artifactId;
    }

    public static String getVersion(Module module) {
        String version = ArtifactResolver.getVersion(module);
        if (version == null)
            version = module.getVersion();
        if (version == null)
            version = "0.1.0-SNAPSHOT";
        return version;
    }

    public static String getApplicationName(Module module) {
        return getApplicationDisplayName(module).replace(" ", "");
    }

    public static String getApplicationDisplayName(Module module) {
        String name = module.getName();
        if (name.contains("-application"))
            name = name.substring(0, name.indexOf("-application"));
        name = name.replace('-', ' ').replace("webfx", "WebFX");
        String[] tokens = name.split(" ");
        for (int i = 0; i < tokens.length; i++)
            tokens[i] = Character.toUpperCase(tokens[i].charAt(0)) + tokens[i].substring(1);
        return String.join(" ", tokens);
    }

    @Override
    public boolean updateDocument(Document document) {
        DevProjectModule module = getProjectModule();
        if (module.getWebFxModuleFile().skipMavenPomUpdate())
            return false;
        if (module.isAggregate()) {
            appendElementWithTextContentIfNotAlreadyExists("packaging", "pom", true);
            if (module.getWebFxModuleFile().isAggregate())
                module.getChildrenModules().forEach(this::addModule);
            else // means that it's just a pom.xml without webfx.xml
                return false;
        } else {
            /* Commented to make it work with webfx-cli-fat (which has no source directory, but we still want to generate
               the pom.xml). Regarding webfx-parent and webfx-stack-parent, they have a <skip-maven-pom/> option anyway.
            if (!module.hasSourceDirectory()) // Ex: webfx-parent, webfx-stack-parent
                return false;
            */
            Element dependenciesNode = lookupOrCreateNode("dependencies");
            XmlUtil.removeChildren(dependenciesNode);
            Set<String> gas = new HashSet<>(); // set of groupId:artifactId listed so far in the pom dependencies - used for duplicate removal below
            // Always running the main java source root dependencies even if there is no main java source directory (ex: gwt modules)
            addSourceRootDependencies(module.getMainJavaSourceRootAnalyzer(), false, dependenciesNode, gas);
            // Running the test java source root dependencies only if there is a test java source directory
            if (module.hasTestJavaSourceDirectory())
                addSourceRootDependencies(module.getTestJavaSourceRootAnalyzer(), true, dependenciesNode, gas);
            if (!gas.isEmpty() && dependenciesNode.getParent() == null)
                appendIndentNode(dependenciesNode, true);
        }
        ReusableStream<MavenRepository> mavenRepositories = module.mavenRepositories();
        if (mavenRepositories.count() > 0) { // For now, we keep the possible <repositories/> section in pom.xml for retro-compatibility
            Element repositoriesNode = createOrEmptySectionNode("repositories");
            String deployRepositoryId = module.getWebFxModuleFile().getDeployRepositoryId();
            Element distributionManagementNode = deployRepositoryId == null ? null : createOrEmptySectionNode("distributionManagement");
            mavenRepositories.forEach(mr -> {
                if (deployRepositoryId == null || !deployRepositoryId.equals(mr.getId())) {
                    Element repositoryElement = XmlUtil.createAndAppendElement(repositoriesNode, "repository");
                    XmlUtil.appendElementWithTextContent(repositoryElement, "id", mr.getId());
                    XmlUtil.appendElementWithTextContent(repositoryElement, "url", mr.getUrl());
                    XmlUtil.appendElementWithTextContent(repositoryElement, "releases/enabled", String.valueOf(!mr.isSnapshot()));
                    XmlUtil.appendElementWithTextContent(repositoryElement, "snapshots/enabled", String.valueOf(mr.isSnapshot()));
                } else {
                    Element repositoryElement = XmlUtil.createAndAppendElement(distributionManagementNode, mr.isSnapshot() ? "snapshotRepository" : "repository");
                    XmlUtil.appendElementWithTextContent(repositoryElement, "id", mr.getId());
                    XmlUtil.appendElementWithTextContent(repositoryElement, "url", mr.getUrl());
                }
            });
        }
        String description = module.getWebFxModuleFile().getDescription();
        if (description != null) {
            // Removing blocks intended for Javadoc only
            description = DescriptionUtil.interpretJavaDocBlock(description, true);
            // Not sure why, but we don't need to escape special characters (ex: "&") and the generated XML is correct (ex: "&amp;")
            prependElementWithTextContentIfNotAlreadyExists("description", description, true);
        }
        // Getting the GAV for this module
        String groupId = ArtifactResolver.getGroupId(module);
        String artifactId = getArtifactId(module);
        String version = ArtifactResolver.getVersion(module);
        // Getting the GAV for the parent module
        Module parentModule = module instanceof DevRootModule && !module.getMavenModuleFile().fileExists() ? null // This happens on the first pom.xml creation with "webfx init" => no parent yet
            : module.getParentModule(); // Otherwise, we fetch the parent module (this may invoke mvn)
        String parentGroupId = parentModule == null ? null : ArtifactResolver.getGroupId(parentModule);
        String parentVersion = parentModule == null ? null : ArtifactResolver.getVersion(parentModule);
        if (version != null && !version.equals(parentVersion))
            prependElementWithTextContentIfNotAlreadyExists("version", version);
        prependElementWithTextContentIfNotAlreadyExists("artifactId", artifactId);
        if (groupId != null && !groupId.equals(parentGroupId))
            prependElementWithTextContentIfNotAlreadyExists("groupId", groupId);
        // Adding the <parent/> section in pom.xml (when parentModule is not null)
        if (parentModule != null) {
            Element parentNode = createOrEmptySectionNode("parent");
            XmlUtil.appendElementWithTextContent(parentNode, "groupId", parentGroupId);
            XmlUtil.appendElementWithTextContent(parentNode, "artifactId", ArtifactResolver.getArtifactId(parentModule));
            XmlUtil.appendElementWithTextContent(parentNode, "version", parentVersion);
            // Adding <relativePath/> when the parent module is not the parent directory module
            if (parentModule != module.getParentDirectoryModule())
                XmlUtil.createAndAppendElement(parentNode, "relativePath");
        }
        Node modelVersionNode = lookupNode("modelVersion[1]");
        if (modelVersionNode == null)
            prependElementWithTextContent("modelVersion", "4.0.0");
        return true;
    }

    @Override
    public String getXmlContent() {
        String xmlContent = super.getXmlContent();
        return xmlContent.replace("<project>", """
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">""");
    }

    private void addSourceRootDependencies(JavaSourceRootAnalyzer javaSourceRootAnalyzer, boolean test, Element dependenciesNode, Set<String> gas) {
        ProjectModuleImpl projectModule = javaSourceRootAnalyzer.getProjectModule();
        BuildInfo buildInfo = projectModule.getBuildInfo();
        try (BuildInfoThreadLocal ignoredClosable = BuildInfoThreadLocal.open(buildInfo)) { // To pass this buildInfo to ArtifactResolver when sorting the dependencies via Module.compareTo()
            // We need to get all dependencies of the module to populate <dependencies> in the pom.xml
            ReusableStream<ModuleDependency> dependencies =
                // For a GWT executable module, we need to list all transitive dependencies (to pull all the source code to compile by GWT)
                buildInfo.isForGwt && buildInfo.isExecutable ? javaSourceRootAnalyzer.getTransitiveDependencies() :
                    // For other modules, we just need the direct dependencies, but also the implicit providers
                    ReusableStream.concat(
                            javaSourceRootAnalyzer.getDirectDependencies(),
                            javaSourceRootAnalyzer.getTransitiveDependencies()
                                .filter(dep -> dep.getType() == ModuleDependency.Type.IMPLICIT_PROVIDER),
                            // Also, adding optional runtime dependency to java base/time J2CL (if used) to make J2CL compilation successful with Vertispan plugin
                            // (ignoring executable modules as this case is managed by JavaSourceRootAnalyzer.collectExecutableEmulationModules())
                            !buildInfo.isExecutable && javaSourceRootAnalyzer.requiresJavaBaseJ2clEmulation() ? ReusableStream.of(ModuleDependency.createVertispanJ2clEmulationDependency(projectModule, projectModule.getRootModule().searchRegisteredModule(SpecificModules.WEBFX_PLATFORM_JAVABASE_EMUL_J2CL), projectModule.getTarget().hasTag(TargetTag.J2CL))) : null,
                            !buildInfo.isExecutable && javaSourceRootAnalyzer.requiresJavaTimeJ2clEmulation() ? ReusableStream.of(ModuleDependency.createVertispanJ2clEmulationDependency(projectModule, projectModule.getRootModule().searchRegisteredModule(SpecificModules.WEBFX_PLATFORM_JAVATIME_EMUL_J2CL), projectModule.getTarget().hasTag(TargetTag.J2CL))) : null
                        )
                        .filter(Objects::nonNull)
                        .distinct();
            // Once we have these dependencies, we process them to populate the <dependencies> node
            dependencies
                // The <dependencies> node actually lists the destination modules, which are the most important
                // objects to extract here, but to know if each module is optional or not (<optional>true</optional>),
                // we need to check if any dependency is optional. To prepare this job, we group the dependencies by
                // destination module. As a result, we will get a map: key = destination module (most important) ->
                // value = list of all dependencies having that module as destination module (used for optional).
                .collect(Collectors.groupingBy(ModuleDependency::getDestinationModule)).entrySet()
                // We sort the modules, so they don't appear in a random order in the pom (sorted mainly by artifactId - see Module.compareTo() for more info)
                .stream().sorted(Map.Entry.comparingByKey()) // Module.compareTo() calls ArtifactResolver.getArtifactId() which will find the buildInfo via BuildInfoThreadLocal
                .forEach(moduleGroup -> { // Map.Entry
                    Module destinationModule = moduleGroup.getKey();
                    if (destinationModule instanceof M2RootModule) {
                        Module bomModule = ((M2RootModule) destinationModule).getLibraryModule().getRootModule();
                        if (bomModule != null)
                            destinationModule = bomModule;
                    }
                    String artifactId = ArtifactResolver.getArtifactId(destinationModule, buildInfo);
                    // When ArtifactResolver returns null, it means that the module doesn't need to be listed
                    if (artifactId != null) {
                        String groupId = ArtifactResolver.getGroupId(destinationModule, buildInfo);
                        // The same with null groupId (ex: scram-client declared as a library in webfx.xml but with no
                        // GAV => `requires com.congress.scram.client;` can be added in module-info.java but without
                        // the need to include that library in pom.xml - as Vert.x already includes it by default).
                        if (groupId == null)
                            return;
                        // Destination modules are already unique, but maybe some are actually resolved to the same groupId:artifactId
                        String ga = groupId + ":" + artifactId;
                        if (!gas.contains(ga)) { // Checking uniqueness to avoid malformed pom
                            gas.add(ga);
                            String scope = test ? "test" : ArtifactResolver.getScope(moduleGroup, buildInfo);
                            if ("aggregate".equals(scope))
                                return;
                            Node groupNode = XmlUtil.appendElementWithTextContent(dependenciesNode, "/dependency/groupId", groupId, true, false);
                            Element dependencyElement = groupNode.getParent();
                            XmlUtil.appendElementWithTextContent(dependencyElement, "/artifactId", artifactId);
                            String version = ArtifactResolver.getVersion(destinationModule, buildInfo);
                            if (version != null)
                                XmlUtil.appendElementWithTextContent(dependencyElement, "/version", version);
                            String type = ArtifactResolver.getType(destinationModule);
                            if (type != null)
                                XmlUtil.appendElementWithTextContent(dependencyElement, "/type", type);
                            String classifier = ArtifactResolver.getClassifier(moduleGroup, buildInfo);
                            // Adding scope if provided, except if scope="runtime" and classifier="sources" (this would prevent GWT to access the source)
                            if (scope != null && !("runtime".equals(scope) && "sources".equals(classifier)))
                                XmlUtil.appendElementWithTextContent(dependencyElement, "/scope", scope);
                            if (classifier != null)
                                XmlUtil.appendElementWithTextContent(dependencyElement, "/classifier", classifier);
                            if (moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
                                XmlUtil.appendElementWithTextContent(dependencyElement, "/optional", "true");
                            // If the module is a library with an <exclusions> element, we copy that element in the pom
                            if (destinationModule instanceof LibraryModule libraryModule) {
                                Element exclusionsNode = XmlUtil.lookupElement(libraryModule.getXmlNode(), "exclusions[1]");
                                if (exclusionsNode != null) {
                                    dependencyElement.add(XmlUtil.copyElement(exclusionsNode, getDocument()));
                                }
                            }
                        }
                    }
                });
        }
    }

    public void addModule(ProjectModule module) {
        String mavenModuleName = module.getHomeDirectory().getFileName().toString();
        appendElementWithTextContentIfNotAlreadyExists("modules/module", mavenModuleName, true, false);
    }

    private Element createOrEmptySectionNode(String xpath) {
        Element sectionNode = lookupElement(xpath);
        if (sectionNode == null)
            sectionNode = createAndPrependElement(xpath, true);
        else
            XmlUtil.removeChildren(sectionNode);
        return sectionNode;
    }

}
