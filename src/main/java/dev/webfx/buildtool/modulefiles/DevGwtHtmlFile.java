package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.DevProjectModule;
import dev.webfx.buildtool.ProjectModule;
import dev.webfx.buildtool.modulefiles.abstr.DevModuleFileImpl;
import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.textfile.TextFileReaderWriter;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Node;

import java.util.Comparator;
import java.util.Scanner;

/**
 * @author Bruno Salmon
 */
public class DevGwtHtmlFile extends DevModuleFileImpl {

    public DevGwtHtmlFile(DevProjectModule module) {
        super(module, module.getResourcesDirectory().resolve("public/index.html"));
    }

    @Override
    public void writeFile() {
        StringBuilder headSb = new StringBuilder(), bodySb = new StringBuilder();
        ReusableStream<ProjectModule> transitiveProjectModules =
                ProjectModule.filterProjectModules(getProjectModule().getThisAndTransitiveModules()).distinct();
        ReusableStream.concat(
                transitiveProjectModules.map(m -> m.getWebFxModuleFile().getHtmlNode()),
                ReusableStream.of(XmlUtil.lookupNode(XmlUtil.parseXmlString("<html><body order=\"0\"><script type=\"text/javascript\" src=\"" + getModule().getName().replaceAll("-", "_") + ".nocache.js\" charset=\"utf-8\"/></body></html>"), "/html"))
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
