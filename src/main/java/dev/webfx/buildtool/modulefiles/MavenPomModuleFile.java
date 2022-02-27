package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.*;
import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public final class MavenPomModuleFile extends XmlModuleFile {

    private Boolean aggregate;

    public MavenPomModuleFile(ProjectModule module) {
        super(module, true);
    }

    @Override
    Path getModuleFilePath() {
        return resolveFromModuleHomeDirectory("pom.xml");
    }

    public String getGroupId() {
        Node node = lookupNode("/project/groupId");
        return node == null ? null : node.getTextContent();
    }

    public String getArtifactId() {
        Node node = lookupNode("/project/artifactId");
        return node == null ? null : node.getTextContent();
    }

    public String getVersion() {
        Node node = lookupNode("/project/version");
        return node == null ? null : node.getTextContent();
    }

    public String getParentGroupId() {
        Node node = lookupNode("/project/parent/groupId");
        return node == null ? null : node.getTextContent();
    }

    public String getParentVersion() {
        Node node = lookupNode("/project/parent/version");
        return node == null ? null : node.getTextContent();
    }

    public void setAggregate(boolean aggregate) {
        this.aggregate = aggregate;
    }

    public boolean isAggregate() {
        return aggregate != null ? aggregate : lookupNode("/project/modules") != null;
    }

    public ReusableStream<Path> getChildrenModules() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("/project/modules//module"), node -> resolveFromModuleHomeDirectory(node.getTextContent()));
    }

    @Override
    Document createInitialDocument() {
        ProjectModule projectModule = getProjectModule();
        boolean isRootModule = projectModule instanceof RootModule;
        BuildInfo buildInfo = projectModule.getBuildInfo();
        String templateFileName = isRootModule ? (((RootModule) projectModule).isInlineWebfxParent() ? "pom_root_inline.xml" : "pom_root.xml") : isAggregate() ? "pom_aggregate.xml" :  !buildInfo.isExecutable ? "pom_not_executable.xml" : buildInfo.isForGwt ? "pom_gwt_executable.xml" : buildInfo.isForGluon ? "pom_gluon_executable.xml" : "pom_javafx_executable.xml";
        String template = ResourceTextFileReader.readTemplate(templateFileName)
                .replace("${groupId}",    ArtifactResolver.getGroupId(projectModule))
                .replace("${artifactId}", ArtifactResolver.getArtifactId(projectModule))
                .replace("${version}",    ArtifactResolver.getVersion(projectModule))
                ;
        if (!isRootModule) {
            ProjectModule parentModule = projectModule.getParentModule();
            template = template
                    .replace("${parent.groupId}",    ArtifactResolver.getGroupId(parentModule))
                    .replace("${parent.artifactId}", ArtifactResolver.getArtifactId(parentModule))
                    .replace("${parent.version}",    ArtifactResolver.getVersion(parentModule))
            ;
        }
        return XmlUtil.parseXmlString(template);
    }

    @Override
    void updateDocument(Document document) {
        if (isAggregate())
            return;
        Node dependenciesNode = lookupOrCreateNode("/project/dependencies");
        clearNodeChildren(dependenciesNode);
        dependenciesNode.appendChild(document.createTextNode(" "));
        dependenciesNode.appendChild(document.createComment(" Dependencies managed by WebFX (DO NOT EDIT MANUALLY) "));
        dependenciesNode.appendChild(document.createTextNode("\n\n    "));
        ProjectModule module = getProjectModule();
        BuildInfo buildInfo = module.getBuildInfo();
        ReusableStream<ModuleDependency> dependencies = buildInfo.isForGwt && buildInfo.isExecutable ? module.getTransitiveDependencies() :
                ReusableStream.concat(
                        module.getDirectDependencies(),
                        module.getTransitiveDependencies().filter(dep -> dep.getType() == ModuleDependency.Type.IMPLICIT_PROVIDER)
                ).distinct();
        Set<String> gas = new HashSet<>(); // set of groupId:artifactId listed so far in the pom dependencies - used for duplicate removal below
        dependencies
                .stream().collect(Collectors.groupingBy(ModuleDependency::getDestinationModule)).entrySet()
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
                            Node groupNode = appendTextNode(dependenciesNode, "/dependency/groupId", groupId);
                            Node dependencyNode = groupNode.getParentNode();
                            appendTextNode(dependencyNode, "/artifactId", artifactId);
                            String version = ArtifactResolver.getVersion(destinationModule, buildInfo);
                            if (version != null)
                                appendTextNode(dependencyNode, "/version", version);
                            String type = ArtifactResolver.getType(destinationModule);
                            if (type != null)
                                appendTextNode(dependencyNode, "/type", type);
                            String scope = ArtifactResolver.getScope(moduleGroup, buildInfo);
                            if (scope != null)
                                appendTextNode(dependencyNode, "/scope", scope);
                            String classifier = ArtifactResolver.getClassifier(moduleGroup, buildInfo);
                            if (classifier != null)
                                appendTextNode(dependencyNode, "/classifier", classifier);
                            if (moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
                                appendTextNode(dependencyNode, "/optional", "true");
                            dependenciesNode.appendChild(document.createTextNode("\n    "));
                        }
                    }
                });
    }

    public void addModule(Module module) {
        String artifactId = ArtifactResolver.getArtifactId(module);
        appendTextNodeIfNotAlreadyExists("/project/modules/module", artifactId);
    }
}
