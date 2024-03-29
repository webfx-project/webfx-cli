package dev.webfx.cli.modulefiles;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.modulefiles.abstr.DevModuleFileImpl;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.w3c.dom.Node;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Scanner;

/**
 * @author Bruno Salmon
 */
public class DevGwtHtmlFile extends DevModuleFileImpl {

    private static final String MAIN_CSS_RELATIVE_PATH = "dev/webfx/kit/css/main.css";
    public DevGwtHtmlFile(DevProjectModule module) {
        super(module, module.getMainResourcesDirectory().resolve("public/index.html"));
    }

    @Override
    public void writeFile() {
        StringBuilder headSb = new StringBuilder(), bodySb = new StringBuilder();
        ReusableStream<ProjectModule> transitiveProjectModules =
                ProjectModule.filterProjectModules(getProjectModule().getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules()).distinct();
        // Calling a terminal operation - here count() - otherwise the next stream may not provide a complete list
        // (although it's ended with a terminal operation) for any strange reason.
        // TODO Investigate why and provide a better fix
        transitiveProjectModules.count();
        Path mainCssPath = getModule().getMainResourcesDirectory().resolve("public").resolve(MAIN_CSS_RELATIVE_PATH);
        boolean isMainCssPresent = Files.exists(mainCssPath);
        // Now the stream should be complete
        ReusableStream.concat(
                transitiveProjectModules.flatMap(m -> m.getWebFxModuleFile().getHtmlNodes()),
                ReusableStream.of(XmlUtil.lookupNode(XmlUtil.parseXmlString("<html><body order='0'><script type='text/javascript' src='" + getGeneratedJsFileName() + "' charset='utf-8'/></body></html>"), "/html")),
                isMainCssPresent ? ReusableStream.of(XmlUtil.lookupNode(XmlUtil.parseXmlString("<html><head><link rel='stylesheet' href='" + MAIN_CSS_RELATIVE_PATH + "'></link></head></html>"), "/html")) : null
        )
                .filter(htmlNode -> checkNodeConditions(htmlNode, transitiveProjectModules))
                .flatMap(htmlNode -> htmlNode == null ? ReusableStream.empty() : XmlUtil.nodeListToReusableStream(htmlNode.getChildNodes(), n -> n))
                .sorted(Comparator.comparingInt(DevGwtHtmlFile::getNodeOrder))
                .filter(headOrBodyNode -> checkNodeConditions(headOrBodyNode, transitiveProjectModules))
                .forEach(headOrBodyNode -> {
                    String nodeName = headOrBodyNode.getNodeName();
                    StringBuilder sb = "head".equalsIgnoreCase(nodeName) ? headSb : "body".equalsIgnoreCase(nodeName) ? bodySb : null;
                    if (sb != null) {
                        String xmlText = XmlUtil.formatHtmlText(headOrBodyNode);
                        // Removing the head or body tag
                        xmlText = xmlText.substring(xmlText.indexOf('>') + 1);
                        xmlText = xmlText.substring(0, xmlText.length() - 3 - nodeName.length());
                        xmlText = xmlText.replaceAll("<META http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">", "");
                        Scanner scanner = new Scanner(xmlText);
                        boolean firstEmptyLineReached = false;
                        int shift = 0, emptyLines = 0;
                        while (scanner.hasNextLine()) {
                            String line = scanner.nextLine();
                            boolean blank = line.isBlank();
                            if (!blank || firstEmptyLineReached) {
                                if (!firstEmptyLineReached) {
                                    shift = 8 - line.indexOf("<");
                                    firstEmptyLineReached = true;
                                } else if (blank) {
                                    emptyLines++;
                                    continue;
                                } else {
                                    sb.append("\n".repeat(emptyLines));
                                    emptyLines = 0;
                                }
                                int firstChar = 0;
                                if (shift >= 0)
                                    sb.append(" ".repeat(shift));
                                else {
                                    firstChar = Math.min(-shift, line.length());
                                    for (int i = 0; i < firstChar; i++)
                                        if (!Character.isWhitespace(line.charAt(i))) {
                                            firstChar = i;
                                            break;
                                        }
                                }
                                sb.append(line.substring(firstChar)).append('\n');
                            }
                        }
                    }
                });
        String text = ResourceTextFileReader.readTemplate("index.html")
                .replace("${generatedHeadContent}", headSb)
                .replace("${generatedBodyContent}", bodySb);
        TextFileReaderWriter.writeTextFileIfNewOrModified(text, getModuleFilePath());
        // Also updating index.html in target if exists (so user don't have to recompile the app for just a style change)
        Path targetIndexHtmlPath = getProjectModule().getGwtExecutableFilePath();
        if (Files.exists(targetIndexHtmlPath))
            TextFileReaderWriter.writeTextFileIfNewOrModified(text, targetIndexHtmlPath);
    }

    private String getGeneratedJsFileName() {
        if (getModule().getBuildInfo().isForJ2cl)
            return getModule().getName() + ".js";
        // GWT
        return getModule().getName().replaceAll("-", "_") + ".nocache.js";
    }

    private static boolean checkNodeConditions(Node headOrBodyNode, ReusableStream<ProjectModule> transitiveProjectModules) {
        String ifModulePropertyTrue = XmlUtil.getAttributeValue(headOrBodyNode, "if-module-property-true");
        if (ifModulePropertyTrue != null && transitiveProjectModules.noneMatch(m -> m.getWebFxModuleFile().getModuleProperties().anyMatch(p -> p.getPropertyName().equals(ifModulePropertyTrue) && "true".equalsIgnoreCase(p.getPropertyValue()))))
            return false;
        String ifUsesJavaPackage = XmlUtil.getAttributeValue(headOrBodyNode, "if-uses-java-package");
        if (ifUsesJavaPackage != null && !ProjectModule.modulesUsesJavaPackage(transitiveProjectModules, ifUsesJavaPackage))
            return false;
        String ifUsesJavaClass = XmlUtil.getAttributeValue(headOrBodyNode, "if-uses-java-class");
        if (ifUsesJavaClass != null && !ProjectModule.modulesUsesJavaClass(transitiveProjectModules, ifUsesJavaClass))
            return false;
        return true;
    }

    private static int getNodeOrder(Node node) {
        String order = XmlUtil.getAttributeValue(node, "order");
        return order == null ? 1 : Integer.parseInt(order);
    }
}
