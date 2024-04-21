package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.core.ModuleDependency;
import dev.webfx.cli.core.Target;
import dev.webfx.cli.core.TargetTag;
import dev.webfx.cli.util.xml.XmlDocumentApi;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public interface XmlModuleFile extends ModuleFile, XmlDocumentApi {

    default Element getXmlNode() {
        return getModuleElement();
    }

    default Element getModuleElement() {
        Document document = getDocument();
        return document == null ? null : document.getRootElement();
    }

    default boolean updateDocument(Document document) {
        XmlUtil.removeChildren(document);
        document.add(DocumentHelper.createElement("project"));
        return true;
    }

    default ReusableStream<ModuleDependency> lookupDependencies(String xPathExpression, ModuleDependency.Type type, String defaultScope) {
        return XmlUtil.nodeListToReusableStream(lookupElementList(xPathExpression), node ->
                new ModuleDependency(
                        getModule(),
                        getProjectModule().getRootModule().searchRegisteredModule(node.getText()),
                        type,
                        XmlUtil.getBooleanAttributeValue(node, "optional"),
                        XmlUtil.getBooleanAttributeValue(node, "transitive"),
                        coalesce(XmlUtil.getAttributeValue(node, "scope"), defaultScope),
                        XmlUtil.getAttributeValue(node, "classifier"),
                        getTargetAttributeValue(node, "executable-target")
                ));
    }

    private String coalesce(String s1, String s2) {
        return s1 != null ? s1 : s2;
    }

    private List<Target> getTargetAttributeValue(Element node, String attribute) {
        String stringValue = XmlUtil.getAttributeValue(node, attribute);
        if (stringValue == null)
            return Collections.emptyList();
        return Arrays.stream(stringValue.split(","))
                .map(token -> new Target(TargetTag.parseTags(stringValue, false)))
                .collect(Collectors.toUnmodifiableList());
    }

}
