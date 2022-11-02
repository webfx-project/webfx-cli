package dev.webfx.cli.modulefiles;

import dev.webfx.cli.core.MavenUtil;
import dev.webfx.cli.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.cli.core.M2ProjectModule;
import dev.webfx.cli.modulefiles.abstr.PathBasedXmlModuleFileImpl;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class M2WebFxModuleFile extends PathBasedXmlModuleFileImpl implements WebFxModuleFile {

    private Boolean exported;

    public M2WebFxModuleFile(M2ProjectModule module) {
        super(module, getWebFxModuleFilePathAndDownloadIfMissing(module));
    }

    @Override
    public M2ProjectModule getProjectModule() {
        return (M2ProjectModule) super.getProjectModule();
    }

    @Override
    public void readFile() {
        super.readFile();
        // If the webfx.xml file contains an <export-snapshot/>, we use the project snapshot instead
        moduleElement = lookupExportedSnapshotProjectElement(getProjectModule());
        // We also set the exported flag, which will tell if this project comes from the export snapshot or not
        exported = moduleElement != null;
    }

    public boolean isExported() {
        if (exported == null)
            readFile();
        return exported;
    }

    @Override
    public boolean shouldTakeChildrenModuleNamesFromPomInstead() {
        return !isExported() // No, if the module is exported as the snapshot itself contains the effective children module names
                // No, if children modules names are explicitly listed in webfx.xml (<modules> section without <subdirectories-modules/> directive)
                && !(isAggregate() && !shouldSubdirectoriesChildrenModulesBeAdded())
                // No, if the module name ends with "-parent" as it's not meant to have children
                && !getProjectModule().getName().endsWith("-parent")
                // Yes, otherwise (ie no <modules/> section or with <subdirectories-modules/> directive)
                ;
    }

    public Element lookupExportedSnapshotProjectElement(M2ProjectModule module) {
        Node exportedProjectNode = lookupNode("/project/export-snapshot/project[@name='" + module.getName() + "']");
        if (exportedProjectNode instanceof Element)
            return (Element) exportedProjectNode;
        return null;
    }

    public String lookupExportedSnapshotFirstProjectName() {
        return XmlUtil.getAttributeValue(lookupNode("(/project/export-snapshot/project[@name])[1]"), "name");
    }

    public ReusableStream<String> modulesUsingJavaPackageFromExportSnapshot(Node javaPackageUsageNode) {
        return XmlUtil.nodeListToTextContentReusableStream(XmlUtil.lookupNodeList(javaPackageUsageNode, "module"));
    }

    public ReusableStream<String> javaPackagesFromExportSnapshotUsage() {
        return XmlUtil.nodeListToAttributeValueReusableStream(lookupNodeList("/project/export-snapshot/usages/java-package"), "name");
    }

    public ReusableStream<String> javaClassesFromExportSnapshotUsage() {
        return XmlUtil.nodeListToAttributeValueReusableStream(lookupNodeList("/project/export-snapshot/usages/java-class"), "name");
    }

    private static Path getWebFxModuleFilePathAndDownloadIfMissing(M2ProjectModule module) {
        MavenUtil.cleanM2ModuleSnapshotIfRequested(module);
        // Before returning the standard path (which points to the "-webfx.xml" file in this maven project repository),
        // we check if there is no existing parent with an export-snapshot that already includes all info about that
        // module, because it will be much quicker to use (no additional webfx.xml or sources to download).
        // If found, we return here the path to that parent -webfx.xml path instead, knowing that the readFile() method
        // will finally look up the correct node corresponding to that module inside that file.
        M2WebFxModuleFile exportSnapshotModuleFile = module.getWebFxModuleFileWithExportSnapshotContainingThisModule();
        if (exportSnapshotModuleFile != null)
            return exportSnapshotModuleFile.getModuleFilePath();
        // If not found, we return the standard path that points to the "-webfx.xml" file in the maven project repository
        Path path = module.getM2ArtifactSubPath("-webfx.xml");
        // And we download it at this point if it's not present, unless it is not expected (ex: third-party library)
        if (module.isWebFxModuleFileExpected() && !Files.exists(path))
            module.downloadArtifactClassifier("xml:webfx");
        return path;
    }
}
