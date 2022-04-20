package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.*;
import dev.webfx.buildtool.modulefiles.abstr.DevXmlModuleFileImpl;
import dev.webfx.buildtool.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.buildtool.util.xml.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public final class DevWebFxModuleFile extends DevXmlModuleFileImpl implements WebFxModuleFile {

    private final static String EXPORT_SNAPSHOT_TAG = "export-snapshot";

    public DevWebFxModuleFile(DevProjectModule module) {
        super(module, module.getHomeDirectory().resolve("webfx.xml"));
    }

    @Override
    public boolean updateDocument(Document document) {
        Node exportNode = lookupNode(EXPORT_SNAPSHOT_TAG);
        if (!generatesExportSnapshot()) {
            if (exportNode != null)
                XmlUtil.removeNode(exportNode);
            return exportNode != null;
        }
        if (exportNode != null)
            XmlUtil.removeChildren(exportNode);
        else
            appendIndentNode(exportNode = document.createElement(EXPORT_SNAPSHOT_TAG), true);
        exportNode.appendChild(document.createComment(" Node managed by WebFX (DO NOT EDIT MANUALLY) "));
        final Node finalExportNode = exportNode;
        getProjectModule().getThisAndChildrenModulesInDepth()
                .forEach(m -> exportChildModule(m, finalExportNode));
        return true;
    }

    private void exportChildModule(ProjectModule childModule, Node exportNode) {
        Document childDocument = childModule.getWebFxModuleFile().getDocument();
        if (childDocument != null) {
            Document document = exportNode.getOwnerDocument();
            // Duplicating the xml element, so it can be copied into <export-snapshot/>
            Element childProjectElement = (Element) document.importNode(childDocument.getDocumentElement(), true);
            // Making the project name explicit (so the import knows what module we are talking about)
            childProjectElement.setAttribute("name", childModule.getName());
            // Replacing the <source-packages/> directive with the effective source packages (so the import doesn't need to download the sources)
            Node sourcePackagesNode = XmlUtil.lookupNode(childProjectElement, "exported-packages/source-packages");
            if (sourcePackagesNode != null) {
                childModule.getExportedJavaPackages().forEach(p -> XmlUtil.appendTextElement(sourcePackagesNode.getParentNode(), "package", p));
                XmlUtil.removeNode(sourcePackagesNode);
            }
            // Replacing the <modules/> section with the effective modules (so the import doesn't need to download the pom)
            Node modulesNode = XmlUtil.lookupNode(childProjectElement, "modules");
            if (modulesNode != null) {
                XmlUtil.removeChildren(modulesNode);
                childModule.getChildrenModules().forEach(m -> XmlUtil.appendTextElement(modulesNode, "module", m.getName()));
            }
            // Replacing the <used-by-source-modules/> directive with the detected source modules (so the import doesn't need to download the sources)
            Node usedBySourceModulesNode = XmlUtil.lookupNode(childProjectElement, "dependencies/used-by-source-modules");
            if (usedBySourceModulesNode != null) {
                childModule.getDetectedByCodeAnalyzerSourceDependencies()
                        .map(ModuleDependency::getDestinationModule)
                        .map(Module::getName)
                        .sorted()
                        .forEach(m -> XmlUtil.appendTextElement(usedBySourceModulesNode, "module", m));
            }
            // Trying to export the packages for the third-party libraries (so the import doesn't need to download their sources)
            DevProjectModule projectModule = getProjectModule();
            new ExportedWebFxModuleFile(projectModule, childProjectElement)
                    .getRequiredThirdPartyLibraryModules()
                    .filter(LibraryModule::shouldBeDownloadedInM2)
                    // Also excluding the snapshots because the exported packages may change in the future
                    .filter(libraryModule -> !libraryModule.getVersion().contains("SNAPSHOT"))
                    .forEach(libraryModule -> {
                        ProjectModule libraryProjectModule = projectModule.searchRegisteredProjectModule(libraryModule.getName(), true);
                        if (libraryProjectModule != null)
                            libraryProjectModule.getDeclaredJavaPackages()
                                    .forEach(p -> XmlUtil.appendTextNodeIfNotAlreadyExists(libraryModule.getXmlNode(), "exported-packages/package", p));
                    });
            XmlUtil.appendIndentNode(childProjectElement, exportNode, true);
        }
    }
}
