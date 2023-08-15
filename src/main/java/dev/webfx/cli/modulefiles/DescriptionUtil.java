package dev.webfx.cli.modulefiles;

/**
 * @author Bruno Salmon
 */
final class DescriptionUtil {

    static String interpretJavaDocBlock(String description, boolean remove) {
        final int openIndex = description.indexOf("{@javadoc");
        if (openIndex < 0)
            return description;
        int openCount = 1, searchStartIndex = openIndex + 1, closeIndex = 0;
        while (openCount > 0) {
            closeIndex = description.indexOf('}', searchStartIndex);
            if (closeIndex < 0) // Probably a malformed block, just return the description as is.
                return description;
            int nextOpenIndex = description.indexOf('{', searchStartIndex);
            if (nextOpenIndex > 0 && nextOpenIndex < closeIndex) {
                searchStartIndex = nextOpenIndex + 1;
                openCount++;
            } else {
                searchStartIndex = closeIndex + 1;
                openCount--;
            }
        }
        String before = description.substring(0, openIndex);
        String after = description.substring(closeIndex + 1);
        if (remove)
            description = before + after;
        else
           description = before + description.substring(openIndex + 9, closeIndex) + after;
        // Making a recursive call in case there are more {@javadoc}
        return interpretJavaDocBlock(description, remove);
    }

    static String escapeHtml(String description) {
        // TODO: This is not a complete solution. It only escapes some html characters.
        return description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String escapeXml(String description) {
        return escapeHtml(description);
    }

}
