package dev.webfx.tool.buildtool.modulefiles;

import dev.webfx.tool.buildtool.*;
import dev.webfx.tool.buildtool.Module;
import dev.webfx.tool.buildtool.modulefiles.abstr.DevXmlModuleFileImpl;
import dev.webfx.tool.buildtool.modulefiles.abstr.MavenPomModuleFile;
import dev.webfx.tool.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.tool.buildtool.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.Map;
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
        return aggregate != null ? aggregate : lookupNode("modules") != null;
    }

    @Override
    public boolean recreateOnUpdateAndWrite() {
        return !getProjectModule().getWebFxModuleFile().skipMavenPomUpdate();
    }

    @Override
    public Document createInitialDocument() {
        DevProjectModule projectModule = getProjectModule();
        boolean isRootModule = projectModule instanceof RootModule;
        BuildInfo buildInfo = projectModule.getBuildInfo();
        String templateFileName =
                isRootModule ? (((RootModule) projectModule).isInlineWebFxParent() ? "pom_root_inline.xml" : "pom_root.xml")
                        : isAggregate() ? "pom_aggregate.xml"
                        : !buildInfo.isExecutable ? "pom_not_executable.xml"
                        : buildInfo.isForGwt ? "pom_gwt_executable.xml"
                        : buildInfo.isForTeaVm ? "pom_teavm_executable.xml"
                        : buildInfo.isForGluon ? "pom_gluon_executable.xml"
                        : buildInfo.isForVertx ? "pom_vertx_executable.xml"
                        : "pom_javafx_executable.xml";
        String template = ResourceTextFileReader.readTemplate(templateFileName)
                .replace("${groupId}", ArtifactResolver.getGroupId(projectModule))
                .replace("${artifactId}", ArtifactResolver.getArtifactId(projectModule))
                .replace("${version}",    ArtifactResolver.getVersion(projectModule))
                ;
        if (!isRootModule) {
            ProjectModule parentModule = projectModule.fetchParentModule();
            template = template
                    .replace("${parent.groupId}", ArtifactResolver.getGroupId(parentModule))
                    .replace("${parent.artifactId}", ArtifactResolver.getArtifactId(parentModule))
                    .replace("${parent.version}", ArtifactResolver.getVersion(parentModule))
            ;
        }
        Document document = XmlUtil.parseXmlString(template);
        Element documentElement = document.getDocumentElement();
        Node webFxMavenPomProjectNode = projectModule.getWebFxModuleFile().getMavenManualNode();
        if (webFxMavenPomProjectNode != null) {
            XmlUtil.appendChildren(document.importNode(webFxMavenPomProjectNode, true), documentElement);
            XmlUtil.indentNode(documentElement, true);
        }
        return document;
    }

    @Override
    public boolean updateDocument(Document document) {
        DevProjectModule module = getProjectModule();
        if (module.isAggregate()) {
            appendElementWithTextContentIfNotAlreadyExists("packaging", "pom", true);
            if (module.getWebFxModuleFile().isAggregate())
                module.getChildrenModules().forEach(this::addModule);
            else
                return false;
        } else {
            if (!module.hasSourceDirectory()) // Ex: webfx-parent, webfx-stack-parent
                return false;
            Node dependenciesNode = lookupOrCreateNode("dependencies");
            XmlUtil.removeChildren(dependenciesNode);
            if (!recreateOnUpdateAndWrite()) {
                dependenciesNode.appendChild(document.createTextNode(" "));
                dependenciesNode.appendChild(document.createComment(" Dependencies managed by WebFX (DO NOT EDIT MANUALLY) "));
            }
            BuildInfo buildInfo = module.getBuildInfo();
            ReusableStream<ModuleDependency> dependencies = buildInfo.isForGwt && buildInfo.isExecutable ? module.getTransitiveDependencies() :
                    ReusableStream.concat(
                            module.getDirectDependencies(),
                            module.getTransitiveDependencies().filter(dep -> dep.getType() == ModuleDependency.Type.IMPLICIT_PROVIDER)
                    ).distinct();
            Set<String> gas = new HashSet<>(); // set of groupId:artifactId listed so far in the pom dependencies - used for duplicate removal below
            dependencies
                    .collect(Collectors.groupingBy(ModuleDependency::getDestinationModule)).entrySet()
                    .stream().sorted(Map.Entry.comparingByKey())
                    .forEach(moduleGroup -> {
                        Module destinationModule = moduleGroup.getKey();
                        String artifactId = ArtifactResolver.getArtifactId(destinationModule, buildInfo);
                        if (artifactId != null) {
                            String groupId = ArtifactResolver.getGroupId(destinationModule, buildInfo);
                            // Destination modules are already unique but maybe some are actually resolved to the same groupId:artifactId
                            String ga = groupId + ":" + artifactId;
                            if (!gas.contains(ga)) { // Checking uniqueness to avoid malformed pom
                                gas.add(ga);
                                Node groupNode = XmlUtil.appendElementWithTextContent(dependenciesNode, "/dependency/groupId", groupId, true, false);
                                Node dependencyNode = groupNode.getParentNode();
                                XmlUtil.appendElementWithTextContent(dependencyNode, "/artifactId", artifactId);
                                String version = ArtifactResolver.getVersion(destinationModule, buildInfo);
                                if (version != null)
                                    XmlUtil.appendElementWithTextContent(dependencyNode, "/version", version);
                                String type = ArtifactResolver.getType(destinationModule);
                                if (type != null)
                                    XmlUtil.appendElementWithTextContent(dependencyNode, "/type", type);
                                String scope = ArtifactResolver.getScope(moduleGroup, buildInfo);
                                String classifier = ArtifactResolver.getClassifier(moduleGroup, buildInfo);
                                // Adding scope if provided, except if scope="runtime" and classifier="sources" (this would prevent GWT to access the source)
                                if (scope != null && !("runtime".equals(scope) && "sources".equals(classifier)))
                                    XmlUtil.appendElementWithTextContent(dependencyNode, "/scope", scope);
                                if (classifier != null)
                                    XmlUtil.appendElementWithTextContent(dependencyNode, "/classifier", classifier);
                                if (moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
                                    XmlUtil.appendElementWithTextContent(dependencyNode, "/optional", "true");
                            }
                        }
                    });
            if (!gas.isEmpty() && dependenciesNode.getParentNode() == null)
                appendIndentNode(dependenciesNode, true);
        }
        String groupId = ArtifactResolver.getGroupId(module);
        String version = ArtifactResolver.getVersion(module);
        Module parentModule = module.fetchParentModule();
        String parentGroupId = ArtifactResolver.getGroupId(parentModule);
        String parentVersion = ArtifactResolver.getVersion(parentModule);
        if (version != null && !version.equals(parentVersion))
            prependElementWithTextContentIfNotAlreadyExists("version", version, true);
        prependElementWithTextContentIfNotAlreadyExists("artifactId", ArtifactResolver.getArtifactId(module), true);
        if (groupId != null && !groupId.equals(parentGroupId))
            prependElementWithTextContentIfNotAlreadyExists("groupId", groupId, true);
        if (parentModule != null && lookupNode("parent/artifactId") == null) {
            Node parentNode = lookupNode("parent");
            if (parentNode == null)
                parentNode = createAndPrependElement("parent", true);
            else
                XmlUtil.removeChildren(parentNode);
            XmlUtil.appendElementWithTextContent(parentNode, "groupId", parentGroupId);
            XmlUtil.appendElementWithTextContent(parentNode, "artifactId", ArtifactResolver.getArtifactId(parentModule));
            XmlUtil.appendElementWithTextContent(parentNode, "version", parentVersion);
        }
        Node modelVersionNode = lookupNode("modelVersion");
        if (modelVersionNode == null)
            prependElementWithTextContent("modelVersion", "4.0.0");
        return true;
    }

    public void addModule(Module module) {
        String artifactId = ArtifactResolver.getArtifactId(module);
        appendElementWithTextContentIfNotAlreadyExists("modules/module", artifactId, true, false);
    }

}
