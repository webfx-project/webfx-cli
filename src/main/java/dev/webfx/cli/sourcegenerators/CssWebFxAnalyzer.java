package dev.webfx.cli.sourcegenerators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified CSS validator for the v1 subset. Runs before translating webfx@ CSS to JavaFX.
 *
 * This is a lightweight analyzer (not a full CSS parser). It scans for declarations and checks
 * property names/values against the allowed subset. It is designed to be fast and deterministic,
 * and to provide actionable error messages. We can evolve it into a full parser later if needed.
 */
final class CssWebFxAnalyzer {

    private CssWebFxAnalyzer() {}

    enum CssValidationMode {
        ERROR, WARN, OFF;

        static CssValidationMode from(String s) {
            if (s == null) return ERROR;
            switch (s.toLowerCase(Locale.ROOT)) {
                case "off": return OFF;
                case "warn": return WARN;
                default: return ERROR;
            }
        }
    }

    static void validate(String css, CssValidationMode mode, String sourceName) {
        if (mode == CssValidationMode.OFF) return;
        List<Violation> violations = analyze(css, sourceName);
        if (!violations.isEmpty()) {
            if (mode == CssValidationMode.WARN) {
                for (Violation v : violations) {
                    System.err.println(v.format());
                }
            } else {
                // ERROR
                StringBuilder sb = new StringBuilder();
                sb.append("[Unified CSS v1] Found ").append(violations.size()).append(" unsupported rule(s) in ")
                  .append(sourceName).append('\n');
                int shown = 0;
                for (Violation v : violations) {
                    sb.append(" - ").append(v.format()).append('\n');
                    if (++shown >= 20) { // cap output
                        sb.append("   ... +").append(violations.size() - shown).append(" more\n");
                        break;
                    }
                }
                throw new IllegalArgumentException(sb.toString());
            }
        }
    }

    /**
     * Validate fxweb@ (JavaFX-authored) unified CSS files. Similar to {@link #validate(String, CssValidationMode, String)}
     * but with an allow-list geared toward JavaFX CSS property names (-fx-* family) plus custom properties.
     */
    static void validateFxWeb(String css, CssValidationMode mode, String sourceName) {
        if (mode == CssValidationMode.OFF) return;
        List<Violation> violations = analyzeFxWeb(css, sourceName);
        if (!violations.isEmpty()) {
            if (mode == CssValidationMode.WARN) {
                // Print the supported set once, then individual violations
                System.err.println("[Unified CSS v1] fxweb@ supported set: " + FXWEB_SUPPORTED_SET_MESSAGE);
                for (Violation v : violations) {
                    System.err.println(v.format());
                }
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("[Unified CSS v1] Found ").append(violations.size()).append(" unsupported rule(s) in ")
                  .append(sourceName).append('\n');
                // Print supported set once
                sb.append("   fxweb@ supported set: ").append(FXWEB_SUPPORTED_SET_MESSAGE).append('\n');
                int shown = 0;
                for (Violation v : violations) {
                    sb.append(" - ").append(v.format()).append('\n');
                    if (++shown >= 20) {
                        sb.append("   ... +").append(violations.size() - shown).append(" more\n");
                        break;
                    }
                }
                throw new IllegalArgumentException(sb.toString());
            }
        }
    }

    private static final Pattern DECL_PATTERN = Pattern.compile("(^|[;{\n\r])\\s*([a-zA-Z_-][a-zA-Z0-9_-]*)\\s*:(?!//)");

    private static final Set<String> ALLOWED_EXACT = new HashSet<>();
    private static final Set<String> ALLOWED_PREFIX = new HashSet<>();

    static {
        // Exact property names allowed
        addAllowedExact(
                "background", "background-color", "background-image", "background-repeat", "background-position", "background-size",
                "border", "border-style", "border-color", "border-width", "border-radius",
                "font", "font-family", "font-size", "font-weight", "font-style",
                "cursor", "color", "opacity", "text-align", "box-shadow",
                // SVG/Shape properties
                "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "fill",
                // Allow @font-face typical props in case a webfx file contains it
                "src", "unicode-range", "font-stretch", "font-style", "font-weight"
        );

        // Prefixes allowed
        addAllowedPrefix("-fx-");   // allow JavaFX-specific properties
        addAllowedPrefix("--");     // custom properties
        addAllowedPrefix("border-top-");
        addAllowedPrefix("border-right-");
        addAllowedPrefix("border-bottom-");
        addAllowedPrefix("border-left-");
    }

    private static void addAllowedExact(String... names) {
        for (String n : names) ALLOWED_EXACT.add(n);
    }

    private static void addAllowedPrefix(String... prefixes) {
        for (String p : prefixes) ALLOWED_PREFIX.add(p);
    }

    static List<Violation> analyze(String css, String sourceName) {
        List<Violation> out = new ArrayList<>();

        // Mask out CSS block comments so validators ignore commented content while preserving positions
        String scanned = maskCssBlockComments(css);

        // Basic at-rule checks (we only allow @font-face in v1)
        if (containsInsensitive(scanned, "@media") || containsInsensitive(scanned, "@supports") || containsInsensitive(scanned, "@keyframes")) {
            out.add(v(scanned, 0, "At-rules '@media/@supports/@keyframes' are not supported in v1"));
        }

        // Value-based quick checks
        quickValueScan(scanned, out);

        // Opacity specific checks (range and units)
        scanOpacity(scanned, out);

        // Property whitelist scan
        Matcher m = DECL_PATTERN.matcher(scanned);
        int lastIndex = 0;
        LineCounter lc = new LineCounter(scanned);
        while (m.find()) {
            String prop = m.group(2).toLowerCase(Locale.ROOT);
            int propIndex = m.start(2);
            if (!isAllowedProperty(prop)) {
                int[] lcPos = lc.pos(propIndex);
                out.add(new Violation(lcPos[0], lcPos[1], "Property '" + prop + "' is not in the v1 subset"));
            }
            lastIndex = m.end();
        }

        return out;
    }

    // --- fxweb@ variant (JavaFX-authored unified CSS) ---

    private static final Set<String> FXWEB_ALLOWED_EXACT = new HashSet<>();
    private static final Set<String> FXWEB_ALLOWED_PREFIX = new HashSet<>();

    static {
        // fxweb@ scope v1.1: background, border, text fill, and font properties are supported.
        // Backgrounds
        FXWEB_ALLOWED_EXACT.add("-fx-background-color");
        FXWEB_ALLOWED_EXACT.add("-fx-background-image");
        FXWEB_ALLOWED_EXACT.add("-fx-background-repeat");
        FXWEB_ALLOWED_EXACT.add("-fx-background-position");
        FXWEB_ALLOWED_EXACT.add("-fx-background-size");
        FXWEB_ALLOWED_EXACT.add("-fx-background-radius");

        // Borders
        FXWEB_ALLOWED_EXACT.add("-fx-border-style");
        FXWEB_ALLOWED_EXACT.add("-fx-border-color");
        FXWEB_ALLOWED_EXACT.add("-fx-border-width");
        FXWEB_ALLOWED_EXACT.add("-fx-border-radius");

        // Text color
        FXWEB_ALLOWED_EXACT.add("-fx-text-fill");

        // Shape/Text fill (used for Text and SVG shapes)
        FXWEB_ALLOWED_EXACT.add("-fx-fill");

        // Shape stroke properties (SVG/Shape)
        FXWEB_ALLOWED_EXACT.add("-fx-stroke");
        FXWEB_ALLOWED_EXACT.add("-fx-stroke-width");
        // Support both hyphenated and compact variants for cap/join
        FXWEB_ALLOWED_EXACT.add("-fx-stroke-line-cap");
        FXWEB_ALLOWED_EXACT.add("-fx-stroke-linecap");
        FXWEB_ALLOWED_EXACT.add("-fx-stroke-line-join");
        FXWEB_ALLOWED_EXACT.add("-fx-stroke-linejoin");

        // Opacity
        FXWEB_ALLOWED_EXACT.add("-fx-opacity");

        // Text alignment
        FXWEB_ALLOWED_EXACT.add("-fx-text-alignment");

        // Effects
        FXWEB_ALLOWED_EXACT.add("-fx-effect");

        // Fonts
        FXWEB_ALLOWED_EXACT.add("-fx-font-family");
        FXWEB_ALLOWED_EXACT.add("-fx-font-size");
        FXWEB_ALLOWED_EXACT.add("-fx-font-weight");
        FXWEB_ALLOWED_EXACT.add("-fx-font-style");

        // Cursor
        FXWEB_ALLOWED_EXACT.add("-fx-cursor");

        // Allow typical @font-face properties in case they appear
        FXWEB_ALLOWED_EXACT.add("src");
        FXWEB_ALLOWED_EXACT.add("unicode-range");
        FXWEB_ALLOWED_EXACT.add("font-stretch");
        FXWEB_ALLOWED_EXACT.add("font-style");
        FXWEB_ALLOWED_EXACT.add("font-weight");

        // Prefix allowances
        // Keep per-side border longhands
        FXWEB_ALLOWED_PREFIX.add("-fx-border-top-");
        FXWEB_ALLOWED_PREFIX.add("-fx-border-right-");
        FXWEB_ALLOWED_PREFIX.add("-fx-border-bottom-");
        FXWEB_ALLOWED_PREFIX.add("-fx-border-left-");
        // We still do NOT allow generic -fx-* or custom properties (--) here to stay strict in v1.
    }

    static List<Violation> analyzeFxWeb(String css, String sourceName) {
        List<Violation> out = new ArrayList<>();

        String scanned = maskCssBlockComments(css);

        // At-rules: allow @font-face only
        if (containsInsensitive(scanned, "@media") || containsInsensitive(scanned, "@supports") || containsInsensitive(scanned, "@keyframes")) {
            out.add(v(scanned, 0, "At-rules '@media/@supports/@keyframes' are not supported in v1"));
        }

        // Value checks that are independent of property naming
        quickValueScan(scanned, out);
        scanOpacity(scanned, out);

        // Property name scanning
        Matcher m = DECL_PATTERN.matcher(scanned);
        while (m.find()) {
            String prop = m.group(2);
            if (!isAllowedPropertyFxWeb(prop)) {
                int[] lc = new LineCounter(scanned).pos(m.start(2));
                // Keep the per-violation message concise; the fxweb@ context and supported-set
                // are printed once by the caller (validateFxWeb).
                out.add(new Violation(lc[0], lc[1], "Unsupported property '" + prop + "'"));
            }
        }

        return out;
    }

    // Single shared description printed once per validation run (not per-violation)
    private static final String FXWEB_SUPPORTED_SET_MESSAGE = "-fx-background-*, -fx-border-* (including per-side), -fx-text-fill, -fx-text-alignment, -fx-fill, -fx-stroke, -fx-stroke-width, -fx-stroke-line-cap/linecap, -fx-stroke-line-join/linejoin, -fx-opacity, -fx-effect (dropshadow), -fx-font-{family|size|weight|style}, -fx-cursor, and JavaFX custom properties declared with a single dash (e.g., -your-var).";

    private static boolean isAllowedPropertyFxWeb(String prop) {
        String p = prop.toLowerCase(Locale.ROOT);
        if (FXWEB_ALLOWED_EXACT.contains(p)) return true;
        for (String pref : FXWEB_ALLOWED_PREFIX) {
            if (p.startsWith(pref)) return true;
        }
        // Allow JavaFX-style custom properties declared with a single dash (but exclude -fx-)
        if (p.startsWith("-") && !p.startsWith("--") && !p.startsWith("-fx-")) {
            // Must look like an identifier after the leading dash
            if (p.length() >= 2 && Character.isLetter(p.charAt(1))) return true;
        }
        // Also allow standard web custom properties (`--name`) in fxweb@, to be permissive
        if (p.startsWith("--") && p.length() >= 3 && Character.isLetter(p.charAt(2))) return true;
        return false;
    }

    // Replace all characters inside /* ... */ comments with spaces, preserving newlines,
    // so that indexes and line/column positions remain stable for error reporting.
    private static String maskCssBlockComments(String css) {
        StringBuilder sb = new StringBuilder(css);
        int i = 0;
        while (i < sb.length()) {
            int start = indexOf(sb, "/*", i);
            if (start < 0) break;
            int end = indexOf(sb, "*/", start + 2);
            if (end < 0) end = sb.length(); // unterminated; mask to end
            // Mask from start to (end+2 or end)
            int maskEnd = Math.min(end + 2, sb.length());
            for (int k = start; k < maskEnd; k++) {
                char c = sb.charAt(k);
                if (c != '\n' && c != '\r') sb.setCharAt(k, ' ');
            }
            i = maskEnd;
        }
        return sb.toString();
    }

    private static int indexOf(StringBuilder sb, String needle, int from) {
        int nLen = needle.length();
        outer:
        for (int i = Math.max(0, from); i + nLen <= sb.length(); i++) {
            for (int j = 0; j < nLen; j++) {
                if (sb.charAt(i + j) != needle.charAt(j)) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static void quickValueScan(String css, List<Violation> out) {
        // background-size: cover/contain
        Pattern pCover = Pattern.compile("(?i)background-size\\s*:\\s*[^;{}]*\\b(cover|contain)\\b");
        addMatches(css, pCover, out, "background-size: cover/contain is not supported in v1");

        // background shorthand containing / cover|contain for size
        Pattern pBgShCover = Pattern.compile("(?i)(^|[;{\n\r])\\s*background\\s*:[^;{}]*/\\s*\\b(cover|contain)\\b");
        addMatches(css, pBgShCover, out, "background shorthand with background-size cover/contain is not supported in v1");

        // repeating gradients
        Pattern pRepGrad = Pattern.compile("(?i)repeating-(linear|radial)-gradient\\s*\\(");
        addMatches(css, pRepGrad, out, "repeating gradients are not supported in v1");

        // border-image
        Pattern pBorderImage = Pattern.compile("(?i)\\bborder-image\\b");
        addMatches(css, pBorderImage, out, "border-image is not supported in v1");

        // outline
        Pattern pOutline = Pattern.compile("(?i)(^|[;{\n\r])\\s*outline\\s*:");
        addMatches(css, pOutline, out, "outline is not supported; use border-* instead");

        // cursor url(...)
        Pattern pCursorUrl = Pattern.compile("(?i)cursor\\s*:\\s*url\\s*\\(");
        addMatches(css, pCursorUrl, out, "cursor: url(...) is not supported for JavaFX");

        // font-variant
        Pattern pFontVariant = Pattern.compile("(?i)(^|[;{\n\r])\\s*font-variant\\s*:");
        addMatches(css, pFontVariant, out, "font-variant is not supported in v1");

        // font shorthand non-normal line-height (font: ... / <lh>)
        Pattern pFontSh = Pattern.compile("(?i)(^|[;{\n\r])\\s*font\\s*:[^;{}]*/\\s*(?!normal)[^;{}]+");
        addMatches(css, pFontSh, out, "font shorthand with non-normal line-height is not supported in v1");
    }

    private static void scanOpacity(String css, List<Violation> out) {
        // Find opacity declarations and validate values
        Pattern pOpacity = Pattern.compile("(?i)(^|[;{\n\r])\\s*opacity\\s*:\\s*([^;{}]+)");
        Matcher m = pOpacity.matcher(css);
        LineCounter lc = new LineCounter(css);
        while (m.find()) {
            String raw = m.group(2).trim();
            // Remove !important if present
            raw = raw.replaceAll("(?i)\\s*!important\\s*$", "").trim();
            // Allow variables
            if (raw.startsWith("var(")) continue;
            // Percentage not supported for opacity in v1
            if (raw.endsWith("%") || raw.contains("%")) {
                int[] pos = lc.pos(m.start(2));
                out.add(new Violation(pos[0], pos[1], "opacity with percentage is not supported; use a number between 0 and 1"));
                continue;
            }
            // Try to parse a plain number
            try {
                double d = Double.parseDouble(raw);
                if (d < 0 || d > 1) {
                    int[] pos = lc.pos(m.start(2));
                    out.add(new Violation(pos[0], pos[1], "opacity value out of range [0..1]: " + raw));
                }
            } catch (NumberFormatException ignore) {
                // If it's not a number nor var(), flag as unsupported token
                int[] pos = lc.pos(m.start(2));
                out.add(new Violation(pos[0], pos[1], "opacity value not recognized; use 0..1 or var(--x)"));
            }
        }
    }

    private static void addMatches(String css, Pattern p, List<Violation> out, String message) {
        Matcher m = p.matcher(css);
        LineCounter lc = new LineCounter(css);
        while (m.find()) {
            int[] pos = lc.pos(m.start());
            out.add(new Violation(pos[0], pos[1], message));
        }
    }

    private static boolean isAllowedProperty(String prop) {
        if (ALLOWED_EXACT.contains(prop)) return true;
        for (String pref : ALLOWED_PREFIX) {
            if (prop.startsWith(pref)) return true;
        }
        // Background/border/font umbrella prefixes already covered; ensure specific background-* types only
        if (prop.startsWith("background-")) {
            return prop.equals("background-color") || prop.equals("background-image") || prop.equals("background-repeat")
                    || prop.equals("background-position") || prop.equals("background-size") || prop.equals("background-radius");
        }
        if (prop.startsWith("border-")) return true; // includes per-side longhands & shorthands
        if (prop.startsWith("font-")) return true;
        return false;
    }

    private static boolean containsInsensitive(String s, String needle) {
        return s.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static Violation v(String css, int index, String message) {
        LineCounter lc = new LineCounter(css);
        int[] pos = lc.pos(index);
        return new Violation(pos[0], pos[1], message);
    }

    private static final class LineCounter {
        private final int[] lineStarts; // index of first char of each line

        LineCounter(String s) {
            List<Integer> starts = new ArrayList<>();
            starts.add(0);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\n') {
                    starts.add(i + 1);
                }
            }
            lineStarts = new int[starts.size()];
            for (int i = 0; i < starts.size(); i++) lineStarts[i] = starts.get(i);
        }

        int[] pos(int index) {
            int line = 1;
            for (int i = 0; i < lineStarts.length; i++) {
                if (index >= lineStarts[i]) line = i + 1; else break;
            }
            int col = index - lineStarts[line - 1] + 1;
            return new int[]{line, col};
        }
    }

    private static final class Violation {
        final int line;
        final int col;
        final String message;

        Violation(int line, int col, String message) {
            this.line = line;
            this.col = col;
            this.message = message;
        }

        String format() {
            return "line " + line + ":" + col + ": " + message;
        }
    }
}
