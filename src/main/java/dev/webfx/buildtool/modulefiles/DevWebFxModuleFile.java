package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.*;
import dev.webfx.buildtool.modulefiles.abstr.DevXmlModuleFileImpl;
import dev.webfx.buildtool.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.buildtool.util.xml.XmlUtil;
import org.w3c.dom.*;

/**
 * @author Bruno Salmon
 */
public final class DevWebFxModuleFile extends DevXmlModuleFileImpl implements WebFxModuleFile {

    private final static String EXPORT_SNAPSHOT_TAG = "export-snapshot";
    private final static String EXPORT_SECTION_COMMENT = "\n" +
            "\n" +
            "     ******************************************************************************************************************* \n" +
            "     ******************************* Section managed by WebFX (DO NOT EDIT MANUALLY) *********************************** \n" +
            "     ******************************************************************************************************************* \n" +
            "\n" +
            "     <export-snapshot> allows a much faster import of this WebFX library into another project. It's a self-contained\n" +
            "     image of this and children modules. All information required for the import of this library is present in this\n" +
            "     single file. The export snapshot is optional, and a WebFX library that doesn't generate it can still be imported\n" +
            "     into another project, but WebFX will then need to download all individual webfx.xml files for every children\n" +
            "     modules, together with their pom and sources. Knowing that each download requires a maven invocation that takes\n" +
            "     at least 3s (sometimes 10s or more), the export snapshot brings a significant performance improvement in the\n" +
            "     import process.\n" +
            "\n" +
            "     ";


    public DevWebFxModuleFile(DevProjectModule module) {
        super(module, module.getHomeDirectory().resolve("webfx.xml"));
    }

    @Override
    public boolean updateDocument(Document document) {
        Node exportNode = lookupNode(EXPORT_SNAPSHOT_TAG);
        boolean exportNodeWasPresent = exportNode != null;
        if (exportNode != null) {
            XmlUtil.removeChildren(exportNode);
            removeNodeAndPreviousBlankText(exportNode, true);
            XmlUtil.removeNode(exportNode);
        } else
            exportNode = document.createElement(EXPORT_SNAPSHOT_TAG);
        if (!generatesExportSnapshot())
            return exportNodeWasPresent;
        final Node finalExportNode = exportNode;
        getProjectModule().getThisAndChildrenModulesInDepth()
                .forEach(m -> exportChildModule(m, finalExportNode));
        appendIndentNode(document.createComment(EXPORT_SECTION_COMMENT), true);
        appendIndentNode(exportNode, true);
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
            // Removing tags that are not necessary for the import: <update-options>, <maven-pom-manual>
            String[] unnecessaryTags = {"update-options", "maven-pom-manual"};
            for (String tag : unnecessaryTags)
                removeNodeAndPreviousBlankText(XmlUtil.lookupNode(childProjectElement, tag), true);
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
                            libraryProjectModule.getJavaSourcePackages()
                                    .forEach(p -> XmlUtil.appendTextNodeIfNotAlreadyExists(libraryModule.getXmlNode(), "exported-packages/package", p, true));
                    });
            // Adding a snapshot of the source packages (must be listed in executable GWT modules),
            // and also to be able to evaluate the <source-packages/> directive without having to download the sources
            childModule.getJavaSourcePackages().forEach(p -> XmlUtil.appendTextNodeIfNotAlreadyExists(childProjectElement, "source-packages/package", p, true));
            // Adding a snapshot of the used required java services
            childModule.getUsedRequiredJavaServices().forEach(js -> XmlUtil.appendTextNodeIfNotAlreadyExists(childProjectElement, "used-services/required-service", js, true));
            // Adding a snapshot of the used optional java services
            childModule.getUsedOptionalJavaServices().forEach(js -> XmlUtil.appendTextNodeIfNotAlreadyExists(childProjectElement, "used-services/optional-service", js, true));
            XmlUtil.appendIndentNode(childProjectElement, exportNode, true);
        }
    }

    private static void removeNodeAndPreviousBlankText(Node node, boolean removeComments) {
        if (node != null)
            while (true) {
                Node previousSibling = node.getPreviousSibling();
                if (previousSibling instanceof Text && previousSibling.getTextContent().isBlank()
                        || removeComments && previousSibling instanceof Comment)
                    XmlUtil.removeNode(previousSibling);
                else {
                    XmlUtil.removeNode(node);
                    break;
                }
            }
    }
}
