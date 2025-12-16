package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.util.textfile.TextFileReaderWriter;

import java.nio.file.Path;

/**
 * Utility to remove unsupported declarations from JavaFX-authored unified CSS (fxweb@) files
 * according to the v1 supported subset defined in {@link CssWebFxAnalyzer}.
 *
 * Rules kept:
 *  - -fx-background-*
 *  - -fx-border-* (including per-side)
 *  - -fx-text-fill
 *  - -fx-text-alignment
 *  - -fx-fill
 *  - -fx-stroke, -fx-stroke-width, -fx-stroke-line-cap/linecap, -fx-stroke-line-join/linejoin
 *  - -fx-opacity
 *  - -fx-effect
 *  - -fx-font-{family|size|weight|style}
 *  - -fx-cursor
 *  - custom variables declared with a single dash (e.g., -brand) or with -- (web custom properties)
 *
 * Everything else is removed, while preserving comments and overall formatting as much as reasonable.
 */
public final class CssFxWebCleaner {

    private CssFxWebCleaner() {}

    public static void cleanFxWebFileInPlace(Path cssPath) {
        String css = TextFileReaderWriter.readInputTextFile(cssPath);
        String cleaned = cleanFxWebCss(css);
        if (!cleaned.equals(css)) {
            TextFileReaderWriter.writeTextFileIfNewOrModified(cleaned, cssPath);
        }
    }

    public static String cleanFxWebCss(String css) {
        if (css == null || css.isEmpty()) return css;
        StringBuilder out = new StringBuilder(css.length());
        int i = 0;
        while (i < css.length()) {
            int braceOpen = css.indexOf('{', i);
            if (braceOpen == -1) {
                out.append(css, i, css.length());
                break;
            }
            // selector header (kept untouched)
            out.append(css, i, braceOpen + 1);

            int braceClose = findMatchingBrace(css, braceOpen);
            if (braceClose == -1) { // malformed, append rest
                out.append(css.substring(braceOpen + 1));
                break;
            }

            String block = css.substring(braceOpen + 1, braceClose);
            String filtered = filterBlock(block);
            out.append(filtered);
            out.append('}');
            i = braceClose + 1;
        }
        return out.toString();
    }

    private static String filterBlock(String block) {
        StringBuilder out = new StringBuilder(block.length());
        int i = 0;
        while (i < block.length()) {
            // Copy whitespace and comments verbatim until we hit a declaration start
            int declStart = indexOfDeclarationStart(block, i);
            if (declStart < 0) {
                out.append(block, i, block.length());
                break;
            }
            // copy leading content
            out.append(block, i, declStart);

            // Parse property name
            int nameStart = skipSpaces(block, declStart);
            int nameEnd = nameStart;
            while (nameEnd < block.length()) {
                char c = block.charAt(nameEnd);
                if (c == ':' || Character.isWhitespace(c)) break;
                nameEnd++;
            }
            String prop = block.substring(nameStart, nameEnd);

            // Find ':'
            int colon = block.indexOf(':', nameEnd);
            if (colon < 0) {
                // malformed, append rest and stop
                out.append(block.substring(declStart));
                break;
            }

            // Find end of declaration considering parentheses
            int declEnd = findEndOfDeclaration(block, colon + 1);
            String fullDecl = block.substring(declStart, declEnd);

            if (isSupportedFxWebPropertyOrCustom(prop)) {
                out.append(fullDecl);
            }
            // else: drop it (do not append)

            i = declEnd;
        }
        return out.toString();
    }

    private static boolean isSupportedFxWebPropertyOrCustom(String prop) {
        if (prop == null) return false;
        String p = prop.trim();
        if (p.isEmpty()) return false;
        // If it starts with -fx- but is NOT a known JavaFX property, treat it as a custom var and keep it
        if (p.startsWith("-fx-") && !CssWebFxAnalyzer.isKnownJavaFxProperty(p)) return true;
        // Keep JavaFX custom variables: single dash but not -fx- (e.g., -brand)
        if (p.startsWith("-") && !p.startsWith("--") && !p.startsWith("-fx-")) return true;
        // Keep standard web custom vars as well (in case they appear in fxweb@ sources)
        if (p.startsWith("--")) return true;
        // Defer to analyzer allow-list for -fx-* properties
        return CssWebFxAnalyzer.isFxWebPropertySupported(p);
    }

    private static int indexOfDeclarationStart(String s, int from) {
        int i = from;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                if (end == -1) return -1; // unclosed, treat as end
                i = end + 2; // skip comment
                continue;
            }
            // Start of a declaration (property name begins with letter, dash or underscore)
            if (Character.isLetter(c) || c == '-' || c == '_') return i;
            // Otherwise, copy as-is (e.g., stray semicolons) and continue
            return i; // let filterBlock handle malformed; keeps robustness
        }
        return -1;
    }

    private static int skipSpaces(String s, int i) {
        int n = s.length();
        while (i < n && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static int findEndOfDeclaration(String s, int from) {
        int i = from;
        int depth = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if (c == ';' && depth == 0) return i + 1;
            i++;
        }
        return i; // end of block
    }

    private static int findMatchingBrace(String s, int openIndex) {
        int depth = 0;
        for (int j = openIndex; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return j;
            }
        }
        return -1;
    }
}
