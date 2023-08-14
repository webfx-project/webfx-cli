package dev.webfx.cli.modulefiles;

/**
 * @author Bruno Salmon
 */
final class JavaDocBlock {

    static String interpretJavaDocBlock(String text, boolean remove) {
        final int openIndex = text.indexOf("{@javadoc");
        if (openIndex < 0)
            return text;
        int openCount = 1, searchStartIndex = openIndex + 1, closeIndex = 0;
        while (openCount > 0) {
            closeIndex = text.indexOf('}', searchStartIndex);
            if (closeIndex < 0) // Probably a malformed block, just return the text as is.
                return text;
            int nextOpenIndex = text.indexOf('{', searchStartIndex);
            if (nextOpenIndex > 0 && nextOpenIndex < closeIndex) {
                searchStartIndex = nextOpenIndex + 1;
                openCount++;
            } else {
                searchStartIndex = closeIndex + 1;
                openCount--;
            }
        }
        String before = text.substring(0, openIndex);
        String after = text.substring(closeIndex + 1);
        if (remove)
            text = before + after;
        else
           text = before + text.substring(openIndex + 9, closeIndex) + after;
        // Making a recursive call in case there are more {@javadoc}
        return interpretJavaDocBlock(text, remove);
    }

}
