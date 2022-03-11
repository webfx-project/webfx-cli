package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.LibraryModule;
import dev.webfx.buildtool.ProjectModule;
import dev.webfx.buildtool.util.xml.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public class ExportedWebFxModuleFile extends XmlModuleFile {

    public ExportedWebFxModuleFile(ProjectModule module) {
        super(module, false);
    }

    @Override
    Path getModuleFilePath() {
        return resolveFromModuleHomeDirectory("webfx-export.xml");
    }

    void updateDocument(Document document) {
        clearDocument(document);
        Element rootElement = document.createElement("exported-modules");
        document.appendChild(rootElement);
        getProjectModule().getThisAndChildrenModulesInDepth()
                .flatMap(ProjectModule::getDirectModules)
                .distinct()
                .stream()
                .collect(Collectors.groupingBy(ArtifactResolver::getGroupId))
                .forEach((groupId, gModules) -> {
                    Node groupIdNode = appendTextNode(rootElement, "/group/groupId", groupId);
                    Node groupNode = groupIdNode.getParentNode();
                    gModules.stream().collect(Collectors.groupingBy(ArtifactResolver::getSafeVersion))
                            .forEach((version, gvModules) -> {
                                if (!"null".equals(version))
                                    appendTextNode(groupNode, "/version", version);
                                gvModules.forEach(m -> {
                                    if (m instanceof ProjectModule) {
                                        // Getting the WebFx module file manager
                                        ProjectModule pm = (ProjectModule) m;
                                        WebFxModuleFile webfxModuleFile = pm.getWebFxModuleFile();
                                        // Creating a xml copy of the project element (which is the document element)
                                        Element projectElement = (Element) getDocument().importNode(webfxModuleFile.getOrCreateDocumentElement(), true);
                                        // Setting the module name as xml "name" attribute
                                        projectElement.setAttribute("name", m.getName());
                                        // Replacing the exported sources package with their actual computed values
                                        Node sourcePackagesNode = XmlUtil.lookupNode(projectElement, "exported-packages/source-packages");
                                        if (sourcePackagesNode != null) {
                                            Node parentNode = sourcePackagesNode.getParentNode();
                                            parentNode.removeChild(sourcePackagesNode);
                                            pm.getExportedJavaPackages().forEach(p -> appendTextNode(parentNode, "/package", p));
                                        }
                                        // Replacing the used by source modules with their actual computed values
                                        Node usedBySourceModulesNode = XmlUtil.lookupNode(projectElement, "dependencies/used-by-source-modules");
                                        if (usedBySourceModulesNode != null)
                                            pm.getDiscoveredByCodeAnalyzerSourceDependencies().forEach(d -> appendTextNode(usedBySourceModulesNode, "/source-module", d.getDestinationModule().getName()));
                                        // Appending the result into the group node
                                        groupNode.appendChild(projectElement);
                                    } else if (m instanceof LibraryModule) {
                                        groupNode.appendChild(getDocument().importNode(((LibraryModule) m).getModuleNode(), true));
                                    }
                                });
                            });
                });
    }

}
