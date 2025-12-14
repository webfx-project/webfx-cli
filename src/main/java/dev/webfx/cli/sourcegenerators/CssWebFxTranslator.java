package dev.webfx.cli.sourcegenerators;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal v1 translator from unified web-style CSS (webfx@) to JavaFX CSS.
 *
 * Scope: backgrounds (color/image/repeat/position/size), borders (style/color/width, border-radius),
 * and custom properties/var() rewriting.
 *
 * Note: This is a conservative text-based translator intended as a first step. It avoids complex
 * CSS parsing and covers the agreed subset. Unsupported constructs emit inline comment warnings.
 */
final class CssWebFxTranslator {

    private CssWebFxTranslator() {}

    static String translateUnifiedWebToJavaFx(String css) {
        String translated = css;

        // 1) Rewrite custom property declarations: --name: value;  ->  -name: value;
        // Only when used as a property name (start of declaration or after ; or {)
        translated = replacePropertyNames(translated, "--", "-");

        // 2) Replace var(--name) and var(--name, fallback) with -name
        translated = replaceVarFunctions(translated);

        // 2b) Translate selector tags: fx-<node> → <JavaFXType> (e.g., fx-text → Text)
        // Do this before property name/value mappings to avoid interfering with declarations
        translated = translateFxTagSelectors(translated);

        // 3) Map property names to JavaFX equivalents (longhands)
        translated = mapPropertyName(translated, "background-color", "-fx-background-color");
        translated = mapPropertyName(translated, "background-image", "-fx-background-image");
        translated = mapPropertyName(translated, "background-repeat", "-fx-background-repeat");
        translated = mapPropertyName(translated, "background-position", "-fx-background-position");
        translated = mapPropertyName(translated, "background-size", "-fx-background-size");
        translated = mapPropertyName(translated, "border-style", "-fx-border-style");
        translated = mapPropertyName(translated, "border-color", "-fx-border-color");
        translated = mapPropertyName(translated, "border-width", "-fx-border-width");
        translated = mapPropertyName(translated, "border-radius", "-fx-border-radius");

        // 3b) Fonts v1: map font and color properties
        translated = mapPropertyName(translated, "font-family", "-fx-font-family");
        translated = mapPropertyName(translated, "font-size", "-fx-font-size");
        translated = mapPropertyName(translated, "font-weight", "-fx-font-weight");
        translated = mapPropertyName(translated, "font-style", "-fx-font-style");
        // Map color to text fill for convenience per approval
        translated = mapPropertyName(translated, "color", "-fx-text-fill");
        // Text alignment support: web text-align -> JavaFX -fx-text-alignment
        translated = mapPropertyName(translated, "text-align", "-fx-text-alignment");
        // Opacity support: web opacity -> JavaFX -fx-opacity
        translated = mapPropertyName(translated, "opacity", "-fx-opacity");

        // 3c) SVG/Shape properties: stroke and fill
        translated = mapPropertyName(translated, "stroke", "-fx-stroke");
        translated = mapPropertyName(translated, "stroke-width", "-fx-stroke-width");
        translated = mapPropertyName(translated, "stroke-linecap", "-fx-stroke-line-cap");
        translated = mapPropertyName(translated, "stroke-linejoin", "-fx-stroke-line-join");
        translated = mapPropertyName(translated, "fill", "-fx-fill");

        // 4) Background repeat shorthands repeat-x / repeat-y mapping
        translated = translated.replaceAll("(?i)(?<=:|,)\\s*repeat-x(?![a-z-])", " repeat no-repeat");
        translated = translated.replaceAll("(?i)(?<=:|,)\\s*repeat-y(?![a-z-])", " no-repeat repeat");

        // 5) linear-gradient(to direction, ...) → linear-gradient(angle, ...)
        translated = mapLinearGradientDirections(translated);

        // 6) Expand supported shorthand: border: <width> <style> <color>
        translated = expandBorderShorthand(translated);

        // 6b) Expand per-side shorthand: border-{top|right|bottom|left}: <width> <style> <color>
        translated = expandPerSideBorderShorthand(translated);

        // 6c) Map per-side longhands: border-*-width/style/color → -fx-border-*
        translated = mapPerSideBorderLonghands(translated);

        // 6d) Fonts v1: expand font shorthand
        translated = expandFontShorthand(translated);

        // 6e) Fonts v1: normalize font values (sizes and weights, style oblique)
        translated = normalizeFontValues(translated);

        // 6ea) Backgrounds v1: expand background shorthand (full v1 subset with layers)
        translated = expandBackgroundShorthandFull(translated);

        // 6f) Cursor support: map property and normalize values
        translated = mapPropertyName(translated, "cursor", "-fx-cursor");
        translated = normalizeCursorValues(translated);

        // 6g) Box-shadow support: translate to -fx-effect dropshadow
        translated = translateBoxShadow(translated);

        // 6h) Border-radius: automatically set background-radius to same value
        translated = duplicateBorderRadiusToBackgroundRadius(translated);

        // 7) Warn on unsupported shorthands left (background only for now) — keep after expansions
        translated = warnUnsupportedShorthand(translated, "background");

        // 8) Warn on per-side border properties (any leftover not handled)
        translated = warnUnsupportedPerSideBorders(translated);

        return translated;
    }

    // --- Selector mapping fx-<tag> → JavaFX type selectors ---

    private static String translateFxTagSelectors(String css) {
        StringBuilder out = new StringBuilder(css.length());
        int i = 0;
        while (i < css.length()) {
            int braceOpen = css.indexOf('{', i);
            if (braceOpen == -1) { // no more blocks
                out.append(css, i, css.length());
                break;
            }
            // Copy up to selector start
            int selectorStart = i;
            int selectorEnd = braceOpen; // exclusive
            String selector = css.substring(selectorStart, selectorEnd);
            String translatedSelector = translateFxTagsInSelector(selector);
            out.append(translatedSelector);

            // Now copy the block content until matching '}' (not a full parser; assume well-formed)
            int braceClose = findMatchingBrace(css, braceOpen);
            if (braceClose == -1) { // malformed; just append rest
                out.append(css.substring(braceOpen));
                break;
            }
            out.append(css, braceOpen, braceClose + 1);
            i = braceClose + 1;
        }
        return out.toString();
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

    private static String translateFxTagsInSelector(String selector) {
        // Also map :root (web pseudo-class) to .root (JavaFX style class) in selector headers only
        // Do a conservative, case-insensitive replacement when :root appears as a whole token
        selector = selector.replaceAll("(?i)(?<![\\w-]):root(?![\\w-])", ".root");

        // Map .pseudo-selected (web class) to :selected (JavaFX pseudo-class)
        // WebFX uses .pseudo-selected because web CSS doesn't have a :selected pseudo-class
        selector = selector.replaceAll("(?i)\\.pseudo-selected(?![\\w-])", ":selected");

        StringBuilder sb = new StringBuilder(selector.length());
        int n = selector.length();
        for (int i = 0; i < n; ) {
            char ch = selector.charAt(i);
            // Quick check for potential token start 'f' of 'fx-'
            if (ch == 'f' || ch == 'F') {
                // Verify previous char is a type boundary (start, whitespace, or combinator , > + ~ ( ) )
                char prev = (i == 0) ? '\0' : selector.charAt(i - 1);
                if (isTypeBoundaryPrev(prev) && startsWithIgnoreCase(selector, i, "fx-")) {
                    int j = i + 3; // after 'fx-'
                    int nameStart = j;
                    while (j < n) {
                        char cj = selector.charAt(j);
                        if (isIdentChar(cj)) j++; else break;
                    }
                    if (nameStart < j) {
                        // Ensure following char is a boundary (end, whitespace, combinator, comma, ., #, :, [, ) )
                        char next = (j < n) ? selector.charAt(j) : '\0';
                        if (isTypeBoundaryNext(next)) {
                            String simple = selector.substring(nameStart, j).toLowerCase();
                            String mapped = mapFxTagToJavaFxType(simple);
                            sb.append(mapped);
                            i = j;
                            continue;
                        }
                    }
                }
            }
            // Default: copy char and advance
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }

    private static boolean isTypeBoundaryPrev(char c) {
        return c == '\0' || Character.isWhitespace(c) || c == ',' || c == '>' || c == '+' || c == '~' || c == '(' || c == '{' || c == '}';
    }

    private static boolean isTypeBoundaryNext(char c) {
        return c == '\0' || Character.isWhitespace(c) || c == ',' || c == '>' || c == '+' || c == '~' || c == '.' || c == '#' || c == ':' || c == '[' || c == ')' || c == '{' || c == '}';
    }

    private static boolean startsWithIgnoreCase(String s, int offset, String prefix) {
        int len = prefix.length();
        if (offset + len > s.length()) return false;
        for (int k = 0; k < len; k++) {
            char a = Character.toLowerCase(s.charAt(offset + k));
            char b = Character.toLowerCase(prefix.charAt(k));
            if (a != b) return false;
        }
        return true;
    }

    private static boolean isIdentChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }

    private static String mapFxTagToJavaFxType(String simpleLower) {
        // Known mappings for main JavaFX nodes
        String mapped = MAIN_JAVAFX_NODES.get(simpleLower);
        if (mapped != null) return mapped;
        // Fallback: capitalize first letter (Borderpane→Borderpane). Better than nothing.
        if (simpleLower.isEmpty()) return "";
        return Character.toUpperCase(simpleLower.charAt(0)) + simpleLower.substring(1);
    }

    private static final java.util.Map<String, String> MAIN_JAVAFX_NODES = new java.util.HashMap<>();

    static {
        // Controls
        MAIN_JAVAFX_NODES.put("label", "Label");
        MAIN_JAVAFX_NODES.put("text", "Text");
        MAIN_JAVAFX_NODES.put("button", "Button");
        MAIN_JAVAFX_NODES.put("togglebutton", "ToggleButton");
        MAIN_JAVAFX_NODES.put("hyperlink", "Hyperlink");
        MAIN_JAVAFX_NODES.put("textfield", "TextField");
        MAIN_JAVAFX_NODES.put("passwordfield", "PasswordField");
        MAIN_JAVAFX_NODES.put("textarea", "TextArea");
        MAIN_JAVAFX_NODES.put("checkbox", "CheckBox");
        MAIN_JAVAFX_NODES.put("radiobutton", "RadioButton");
        MAIN_JAVAFX_NODES.put("progressbar", "ProgressBar");
        MAIN_JAVAFX_NODES.put("progressindicator", "ProgressIndicator");
        MAIN_JAVAFX_NODES.put("slider", "Slider");
        MAIN_JAVAFX_NODES.put("separator", "Separator");
        MAIN_JAVAFX_NODES.put("tooltip", "Tooltip");
        MAIN_JAVAFX_NODES.put("contextmenu", "ContextMenu");
        MAIN_JAVAFX_NODES.put("listview", "ListView");
        MAIN_JAVAFX_NODES.put("tableview", "TableView");
        MAIN_JAVAFX_NODES.put("treeview", "TreeView");
        MAIN_JAVAFX_NODES.put("treetableview", "TreeTableView");
        MAIN_JAVAFX_NODES.put("pagination", "Pagination");
        MAIN_JAVAFX_NODES.put("choicebox", "ChoiceBox");
        MAIN_JAVAFX_NODES.put("combobox", "ComboBox");
        MAIN_JAVAFX_NODES.put("datepicker", "DatePicker");
        MAIN_JAVAFX_NODES.put("colorpicker", "ColorPicker");
        MAIN_JAVAFX_NODES.put("spinner", "Spinner");
        MAIN_JAVAFX_NODES.put("mediaview", "MediaView");
        MAIN_JAVAFX_NODES.put("textflow", "TextFlow");
        MAIN_JAVAFX_NODES.put("tabpane", "TabPane");
        MAIN_JAVAFX_NODES.put("scrollpane", "ScrollPane");
        MAIN_JAVAFX_NODES.put("splitpane", "SplitPane");

        // Layouts & shapes
        MAIN_JAVAFX_NODES.put("group", "Group");
        MAIN_JAVAFX_NODES.put("pane", "Pane");
        MAIN_JAVAFX_NODES.put("region", "Region");
        MAIN_JAVAFX_NODES.put("anchorpane", "AnchorPane");
        MAIN_JAVAFX_NODES.put("borderpane", "BorderPane");
        MAIN_JAVAFX_NODES.put("stackpane", "StackPane");
        MAIN_JAVAFX_NODES.put("gridpane", "GridPane");
        MAIN_JAVAFX_NODES.put("flowpane", "FlowPane");
        MAIN_JAVAFX_NODES.put("hbox", "HBox");
        MAIN_JAVAFX_NODES.put("vbox", "VBox");

        // Media/graphics/web
        MAIN_JAVAFX_NODES.put("imageview", "ImageView");
        MAIN_JAVAFX_NODES.put("webview", "WebView");
        MAIN_JAVAFX_NODES.put("canvas", "Canvas");
        MAIN_JAVAFX_NODES.put("svgpath", "SVGPath");
    }

    private static String replacePropertyNames(String css, String fromPrefix, String toPrefix) {
        // Matches: [start or { or ;][optional whitespace] --ident :
        Pattern p = Pattern.compile("([;{\\n\\r])\\s*(" + Pattern.quote(fromPrefix) + ")([a-zA-Z0-9_-]+)\\s*:");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(1) + " " + toPrefix + m.group(3) + ":");
        }
        m.appendTail(sb);
        // Also handle beginning of file/selectors case: line start
        Pattern p2 = Pattern.compile("(^)\\s*(" + Pattern.quote(fromPrefix) + ")([a-zA-Z0-9_-]+)\\s*:", Pattern.MULTILINE);
        Matcher m2 = p2.matcher(sb.toString());
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            m2.appendReplacement(sb2, toPrefix + m2.group(3) + ":");
        }
        m2.appendTail(sb2);
        return sb2.toString();
    }

    private static String replaceVarFunctions(String css) {
        // var(--name) or var(--name, fallback) → -name
        // We ignore fallback at v1; future versions could inline when variable undefined.
        Pattern p = Pattern.compile("var\\(\\s*--([a-zA-Z0-9_-]+)\\s*(?:,\\s*[^)]+)?\\)");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, '-' + m.group(1));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String mapPropertyName(String css, String from, String to) {
        // Replace property names in declarations (case-insensitive, preserve spacing around colon)
        Pattern p = Pattern.compile("(?i)(^|[;{\\n\\r])\\s*(" + Pattern.quote(from) + ")\\s*:");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(1) + " " + to + ":");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String mapLinearGradientDirections(String css) {
        // Map common "to ..." forms to angles in degrees (CSS deg where 0deg points up in JavaFX).
        // Web: to bottom(↓) → 180deg, to top(↑) → 0deg, to right(→) → 90deg, to left(←) → 270deg
        // Diagonals: to bottom right → 135deg, bottom left → 225deg, top left → 315deg, top right → 45deg
        css = css.replaceAll("(?i)linear-gradient\\(\\s*to\\s+bottom", "linear-gradient(180deg");
        css = css.replaceAll("(?i)linear-gradient\\(\\s*to\\s+top", "linear-gradient(0deg");
        css = css.replaceAll("(?i)linear-gradient\\(\\s*to\\s+right", "linear-gradient(90deg");
        css = css.replaceAll("(?i)linear-gradient\\(\\s*to\\s+left", "linear-gradient(270deg");
        // Diagonals (handle 2-word directions first to avoid partial replacements)
        css = css.replaceAll("(?i)linear-gradient\\(\\s*to\\s+bottom\\s+right", "linear-gradient(135deg");
        css = css.replaceAll("(?i)linear-gradient\\(\\s*to\\s+bottom\\s+left", "linear-gradient(225deg");
        css = css.replaceAll("(?i)linear-gradient\\(\\s*to\\s+top\\s+left", "linear-gradient(315deg");
        css = css.replaceAll("(?i)linear-gradient\\(\\s*to\\s+top\\s+right", "linear-gradient(45deg");
        return css;
    }

    // --- Fonts v1 helpers ---

    private static String expandFontShorthand(String css) {
        // Matches a 'font:' declaration capturing value up to ; or }
        Pattern p = Pattern.compile("(?i)(^|[;{\n\r])(\\t|[ ]*)font\\s*:\\s*([^;{}]+)(;?)");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String indent = m.group(2);
            String value = m.group(3).trim();

            FontComponents fc = parseFontShorthand(value);
            if (fc == null) {
                String replacement = prefix + indent + "/* [webfx translator] WARNING: couldn't parse 'font' shorthand, leaving as-is */\n"
                        + indent + "font: " + value + (m.group(4).isEmpty() ? "" : ";");
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                continue;
            }

            StringBuilder repl = new StringBuilder();
            repl.append(prefix);
            if (fc.style != null) repl.append(indent).append("-fx-font-style: ").append(fc.style).append(";\n");
            if (fc.weight != null) repl.append(indent).append("-fx-font-weight: ").append(fc.weight).append(";\n");
            if (fc.size != null) repl.append(indent).append("-fx-font-size: ").append(stripPx(fc.size)).append(";\n");
            if (fc.family != null) repl.append(indent).append("-fx-font-family: ").append(fc.family).append(";");
            // line-height: warn if provided and not normal
            if (fc.lineHeightWarning) {
                repl.insert(0, prefix + indent + "/* [webfx translator] WARNING: 'font' shorthand line-height not supported; ignored */\n");
            }
            // Trim trailing newline if last statement ended with it and nothing followed
            String out = repl.toString();
            m.appendReplacement(sb, Matcher.quoteReplacement(out));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // --- Backgrounds v1: full shorthand expansion with multi-layer support ---

    private static String expandBackgroundShorthandFull(String css) {
        // Supports: background: [<color> || <image> || <position> [ / <size> ] || <repeat>]{1,} (comma-separated layers)
        // v1 limits: no attachment, no origin/clip from shorthand; size does not accept cover/contain (caught by analyzer)
        Pattern p = Pattern.compile("(?i)(^|[;{\n\r])(\t|[ ]*)background\s*:\n?\s*([^;{}]+)(;?)");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String indent = m.group(2);
            String value = m.group(3).trim();

            java.util.List<String> layerStrings = splitTopLevelComma(value);
            java.util.List<BackgroundLayer> layers = new java.util.ArrayList<>();
            boolean parseOk = true;
            for (String ls : layerStrings) {
                BackgroundLayer bl = parseBackgroundLayer(ls.trim());
                if (bl == null) { parseOk = false; break; }
                layers.add(bl);
            }

            if (!parseOk || layers.isEmpty()) {
                // Fallback: leave as-is with a warning comment
                String replacement = prefix + indent + "/* [webfx translator] WARNING: couldn't fully parse 'background' shorthand; leaving as-is */\n"
                        + indent + "background: " + value + (m.group(4).isEmpty() ? "" : ";");
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                continue;
            }

            // Build parallel lists
            StringBuilder img = new StringBuilder();
            StringBuilder pos = new StringBuilder();
            StringBuilder siz = new StringBuilder();
            StringBuilder rep = new StringBuilder();
            StringBuilder col = new StringBuilder();

            boolean anyImg=false, anyPos=false, anySize=false, anyRep=false, anyCol=false;

            for (int i = 0; i < layers.size(); i++) {
                BackgroundLayer bl = layers.get(i);
                if (bl.image != null) { if (anyImg) img.append(", "); img.append(bl.image); anyImg = true; }
                if (bl.hasPosition()) { if (anyPos) pos.append(", "); pos.append(bl.positionX).append(' ').append(bl.positionY); anyPos = true; }
                if (bl.hasSize()) { if (anySize) siz.append(", "); siz.append(bl.sizeW).append(' ').append(bl.sizeH); anySize = true; }
                if (bl.hasRepeat()) { if (anyRep) rep.append(", "); rep.append(bl.repeatX).append(' ').append(bl.repeatY); anyRep = true; }
                if (bl.color != null) { if (anyCol) col.append(", "); col.append(bl.color); anyCol = true; }
            }

            StringBuilder repl = new StringBuilder();
            repl.append(prefix);
            if (anyImg) repl.append(indent).append("-fx-background-image: ").append(img).append(";\n");
            if (anyRep) repl.append(indent).append("-fx-background-repeat: ").append(rep).append(";\n");
            if (anyPos) repl.append(indent).append("-fx-background-position: ").append(pos).append(";\n");
            if (anySize) repl.append(indent).append("-fx-background-size: ").append(siz).append(";\n");
            if (anyCol) repl.append(indent).append("-fx-background-color: ").append(col).append(";");
            // Remove trailing newline if present without following content
            String out = repl.toString();
            m.appendReplacement(sb, Matcher.quoteReplacement(out));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static class BackgroundLayer {
        String image; // url(...) | linear-gradient(...) | radial-gradient(...) | none → omitted
        String positionX; String positionY; // e.g., left|center|right or <length|%>
        String sizeW; String sizeH; // auto|<length|%>
        String repeatX; String repeatY; // repeat|no-repeat|space|round
        String color; // hex/rgb(a)/hsl(a)/identifier/-var

        boolean hasPosition() { return positionX != null && positionY != null; }
        boolean hasSize() { return sizeW != null && sizeH != null; }
        boolean hasRepeat() { return repeatX != null && repeatY != null; }
    }

    private static BackgroundLayer parseBackgroundLayer(String s) {
        BackgroundLayer bl = new BackgroundLayer();

        // Extract image functions first (url/linear/radial) – keep parentheses groups intact.
        Matcher mUrl = Pattern.compile("(?i)url\\s*\\([^)]*\\)").matcher(s);
        Matcher mLin = Pattern.compile("(?i)linear-gradient\\s*\\([^)]*\\)").matcher(s);
        Matcher mRad = Pattern.compile("(?i)radial-gradient\\s*\\([^)]*\\)").matcher(s);
        int imgStart = -1, imgEnd = -1; String imgVal = null;
        if (mUrl.find()) { imgStart = mUrl.start(); imgEnd = mUrl.end(); imgVal = s.substring(imgStart, imgEnd); }
        else if (mLin.find()) { imgStart = mLin.start(); imgEnd = mLin.end(); imgVal = s.substring(imgStart, imgEnd); }
        else if (mRad.find()) { imgStart = mRad.start(); imgEnd = mRad.end(); imgVal = s.substring(imgStart, imgEnd); }

        String remainder = s;
        if (imgVal != null) {
            bl.image = imgVal.trim();
            remainder = (s.substring(0, imgStart) + " " + s.substring(imgEnd)).trim();
        } else if (s.toLowerCase().contains(" none" ) || s.equalsIgnoreCase("none")) {
            // Explicit none – no image; strip token
            remainder = s.replaceAll("(?i)\\bnone\\b", " ").trim();
        }

        // Normalize repeated whitespace
        remainder = remainder.replaceAll("\\s+", " ").trim();

        // Extract repeat tokens
        String repX=null, repY=null;
        Matcher mRep = Pattern.compile("(?i)\\b(repeat-x|repeat-y|repeat|no-repeat|space|round)\\b").matcher(remainder);
        java.util.List<String> reps = new java.util.ArrayList<>();
        while (mRep.find()) reps.add(mRep.group(1).toLowerCase());
        if (!reps.isEmpty()) {
            // Remove repeats from remainder
            remainder = remainder.replaceAll("(?i)\\b(repeat-x|repeat-y|repeat|no-repeat|space|round)\\b", " ").replaceAll("\\s+", " ").trim();
            if (reps.size() == 1) {
                String r = reps.get(0);
                String[] pair = mapRepeatToken(r);
                repX = pair[0]; repY = pair[1];
            } else {
                // Take first two meaningful tokens; map repeat-x/y if present
                String[] a = mapRepeatToken(reps.get(0));
                String[] b = mapRepeatToken(reps.get(1));
                repX = a[0]; repY = b[1];
            }
        }
        bl.repeatX = repX; bl.repeatY = repY;

        // Split position/size on '/'
        String posPart = remainder;
        String sizePart = null;
        int slash = indexOfTopLevelSlashOutsideParens(remainder);
        if (slash >= 0) {
            posPart = remainder.substring(0, slash).trim();
            sizePart = remainder.substring(slash + 1).trim();
        }

        // Extract color (take last color-like token outside parentheses from posPart if any; if not there, from sizePart)
        String color = extractTrailingColorToken(posPart);
        if (color != null) {
            bl.color = color;
            posPart = trimTrailingToken(posPart, color).trim();
        } else if (sizePart != null) {
            color = extractTrailingColorToken(sizePart);
            if (color != null) {
                bl.color = color;
                sizePart = trimTrailingToken(sizePart, color).trim();
            }
        }

        // Parse position
        if (!posPart.isEmpty()) {
            String[] xy = parseBackgroundPosition(posPart);
            if (xy != null) { bl.positionX = xy[0]; bl.positionY = xy[1]; }
        }
        // Parse size
        if (sizePart != null && !sizePart.isEmpty()) {
            String[] wh = parseBackgroundSize(sizePart);
            if (wh != null) { bl.sizeW = wh[0]; bl.sizeH = wh[1]; }
        }

        // Defaults: if position present only one value like 'center', normalize in parser. If none, leave nulls (JavaFX will default).
        return bl;
    }

    private static int indexOfTopLevelSlashOutsideParens(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if (c == '/' && depth == 0) return i;
        }
        return -1;
    }

    private static String[] mapRepeatToken(String r) {
        switch (r) {
            case "repeat-x": return new String[]{"repeat", "no-repeat"};
            case "repeat-y": return new String[]{"no-repeat", "repeat"};
            default: return new String[]{r, r};
        }
    }

    private static String extractTrailingColorToken(String s) {
        // Remove parentheses content to avoid picking colors inside gradients
        String masked = s.replaceAll("\\([^)]*\\)", " ");
        String[] parts = masked.trim().split("\\s+");
        if (parts.length == 0) return null;
        String last = parts[parts.length - 1];
        if (looksLikeOnlyColor(last)) return last;
        return null;
    }

    private static String trimTrailingToken(String s, String token) {
        int idx = s.lastIndexOf(token);
        if (idx >= 0) return (s.substring(0, idx) + s.substring(idx + token.length())).trim();
        return s;
    }

    private static String[] parseBackgroundPosition(String s) {
        if (s.isEmpty()) return null;
        String[] parts = s.trim().split("\\s+");
        // Normalize common keywords
        if (parts.length == 1) {
            String p = normalizePosToken(parts[0]);
            if (p.equals("center")) return new String[]{"center", "center"};
            if (p.equals("left") || p.equals("right")) return new String[]{p, "center"};
            if (p.equals("top") || p.equals("bottom")) return new String[]{"center", p};
            return new String[]{p, "center"}; // length/percentage only: treat as x with y center
        }
        if (parts.length >= 2) {
            String x = normalizePosToken(parts[0]);
            String y = normalizePosToken(parts[1]);
            return new String[]{x, y};
        }
        return null;
    }

    private static String normalizePosToken(String t) {
        String tl = t.toLowerCase();
        switch (tl) {
            case "left": case "center": case "right": case "top": case "bottom": return tl;
            default: return t; // lengths/percentages left as-is
        }
    }

    private static String[] parseBackgroundSize(String s) {
        String[] parts = s.trim().split("\\s+");
        if (parts.length == 1) {
            String w = parts[0];
            return new String[]{w, "auto"};
        }
        if (parts.length >= 2) {
            return new String[]{parts[0], parts[1]};
        }
        return null;
    }

    private static boolean looksLikeOnlyColor(String s) {
        String sl = s.toLowerCase();
        // Disqualify if tokens that imply image/position/repeat/size exist
        if (sl.contains("url(") || sl.contains("gradient(") || sl.contains("repeat") || sl.contains("/")
                || sl.contains(" left") || sl.contains(" right") || sl.contains(" top") || sl.contains(" bottom")
                || sl.matches(".*[0-9].*(px|em|rem|%).*") ) {
            // crude guard: if there are obvious size/position tokens, not color-only
            // Note: this may false-positive in rare cases; acceptable for v1.
            return false;
        }
        // Accept hex
        if (sl.matches("#([0-9a-f]{3}|[0-9a-f]{4}|[0-9a-f]{6}|[0-9a-f]{8})")) return true;
        // rgb/rgba/hsl/hsla
        if (sl.startsWith("rgb(") || sl.startsWith("rgba(") || sl.startsWith("hsl(") || sl.startsWith("hsla(")) return true;
        // variable (already mapped: var(--x) → -x) or custom/known color identifier
        if (sl.startsWith("-")) return true; // -my-color or -fx-* variable
        if (sl.matches("[a-zA-Z][a-zA-Z0-9_-]*")) return true; // named colors like white, aliceblue
        return false;
    }

    private static java.util.List<String> splitTopLevelComma(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int depthParen = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') { depthParen++; cur.append(c); }
            else if (c == ')') { depthParen = Math.max(0, depthParen - 1); cur.append(c); }
            else if (c == ',' && depthParen == 0) { out.add(cur.toString()); cur.setLength(0); }
            else { cur.append(c); }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static class FontComponents {
        String style; // normal|italic (oblique mapped later)
        String weight; // normal|bold or numeric mapped later
        String size; // may be px or number
        String family; // full family list
        boolean lineHeightWarning;
    }

    private static FontComponents parseFontShorthand(String value) {
        // Heuristic parser: split value, find first token that looks like a size (\d or . with unit or px),
        // tokens before size may include style and weight (order-agnostic), everything after size (optionally '/line-height') is family
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int paren = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') { paren++; current.append(c); }
            else if (c == ')') { paren = Math.max(0, paren - 1); current.append(c); }
            else if (Character.isWhitespace(c) && paren == 0) {
                if (current.length() > 0) { tokens.add(current.toString()); current.setLength(0); }
            } else { current.append(c); }
        }
        if (current.length() > 0) tokens.add(current.toString());

        if (tokens.isEmpty()) return null;

        FontComponents fc = new FontComponents();
        int i = 0;
        // Pre-size tokens: detect style/weight; ignore variant/stretch for v1
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if (looksLikeFontSize(t)) break;
            String tl = t.toLowerCase();
            if (tl.equals("normal") || tl.equals("italic") || tl.equals("oblique")) {
                fc.style = t; // oblique will be normalized later
                i++;
                continue;
            }
            if (tl.equals("bold") || tl.equals("bolder") || tl.equals("lighter") || tl.matches("[1-9]00")) {
                fc.weight = t;
                i++;
                continue;
            }
            // Unknown pre-size token; give up (keep original)
            return null;
        }
        if (i >= tokens.size()) return null;

        // Size token
        fc.size = tokens.get(i++);

        // Optional line-height
        if (i < tokens.size() && tokens.get(i).startsWith("/")) {
            String lh = tokens.get(i++).substring(1).trim();
            if (!lh.isEmpty() && !lh.equalsIgnoreCase("normal")) {
                fc.lineHeightWarning = true;
            }
        }

        // Remaining tokens form the family; reconstruct with spaces
        if (i < tokens.size()) {
            StringBuilder fam = new StringBuilder();
            for (int k = i; k < tokens.size(); k++) {
                if (k > i) fam.append(' ');
                fam.append(tokens.get(k));
            }
            fc.family = fam.toString();
        }

        return fc;
    }

    private static boolean looksLikeFontSize(String token) {
        String t = token.toLowerCase();
        return t.matches("[0-9]*\\.?[0-9]+px") || t.matches("[0-9]*\\.?[0-9]+") || t.matches("[0-9]*\\.?[0-9]+(em|rem)");
    }

    private static String stripPx(String size) {
        String s = size.trim();
        if (s.toLowerCase().endsWith("px")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static String normalizeFontValues(String css) {
        String out = css;
        // font-size: convert px to unitless for JavaFX
        out = out.replaceAll("(?i)(^|[;{\n\r])\\s*(-fx-font-size)\\s*:\\s*([0-9]*\\.?[0-9]+)px\\s*;", "$1 $2: $3;");
        // font-style: oblique -> italic
        out = out.replaceAll("(?i)(^|[;{\n\r])\\s*(-fx-font-style)\\s*:\\s*oblique\\s*;", "$1 $2: italic;");
        // font-weight: numeric mapping
        Pattern p = Pattern.compile("(?i)(^|[;{\n\r])\\s*(-fx-font-weight)\\s*:\\s*([^;]+);", Pattern.MULTILINE);
        Matcher m = p.matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String prop = m.group(2);
            String val = m.group(3).trim().toLowerCase();
            String mapped = mapWeight(val);
            m.appendReplacement(sb, Matcher.quoteReplacement(prefix + " " + prop + ": " + mapped + ";"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String mapWeight(String val) {
        if ("bold".equals(val) || "bolder".equals(val)) return "bold";
        if ("normal".equals(val) || "lighter".equals(val)) return "normal";
        try {
            int n = Integer.parseInt(val);
            if (n <= 300) return "normal";
            return "bold"; // 400+ → bold (conservative)
        } catch (NumberFormatException e) {
            return val; // leave as-is
        }
    }

    // --- Cursor helpers ---

    private static String normalizeCursorValues(String css) {
        String out = css;
        // If url(...) is used, prepend a warning comment (JavaFX -fx-cursor doesn't support url)
        Pattern urlPat = Pattern.compile("(?i)(^|[;{\\n\\r])\\s*(-fx-cursor)\\s*:\\s*url\\(.*?\\)\\s*;", Pattern.MULTILINE);
        Matcher um = urlPat.matcher(out);
        StringBuffer usb = new StringBuffer();
        while (um.find()) {
            String prefix = um.group(1);
            String prop = um.group(2);
            String replacement = prefix + " /* [webfx translator] WARNING: '-fx-cursor: url(...)' not supported by JavaFX CSS; ignored */\n " + prop + ": default;";
            um.appendReplacement(usb, Matcher.quoteReplacement(replacement));
        }
        um.appendTail(usb);
        out = usb.toString();

        // Map web cursor keywords to JavaFX equivalents
        // pointer -> hand
        out = out.replaceAll("(?i)(^|[;{\\n\\r])\\s*(-fx-cursor)\\s*:\\s*pointer\\s*;", "$1 $2: hand;");
        // grab/grabbing -> open-hand/closed-hand
        out = out.replaceAll("(?i)(^|[;{\\n\\r])\\s*(-fx-cursor)\\s*:\\s*grab\\s*;", "$1 $2: open-hand;");
        out = out.replaceAll("(?i)(^|[;{\\n\\r])\\s*(-fx-cursor)\\s*:\\s*grabbing\\s*;", "$1 $2: closed-hand;");
        // ew-resize -> h-resize, ns-resize -> v-resize
        out = out.replaceAll("(?i)(^|[;{\\n\\r])\\s*(-fx-cursor)\\s*:\\s*ew-resize\\s*;", "$1 $2: h-resize;");
        out = out.replaceAll("(?i)(^|[;{\\n\\r])\\s*(-fx-cursor)\\s*:\\s*ns-resize\\s*;", "$1 $2: v-resize;");
        // no-drop and not-allowed -> disappear
        out = out.replaceAll("(?i)(^|[;{\\n\\r])\\s*(-fx-cursor)\\s*:\\s*(no-drop|not-allowed)\\s*;", "$1 $2: disappear;");
        // Pass-through of other standard names already match JavaFX (e.g., default, text, move, e-resize, ne-resize, etc.)

        return out;
    }

    private static String warnUnsupportedShorthand(String css, String shorthand) {
        // Add a comment ahead of unsupported shorthand usage to help users migrate
        Pattern p = Pattern.compile("(?i)(^|[;{\\n\\r])\\s*" + Pattern.quote(shorthand) + "\\s*:");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            m.appendReplacement(sb, prefix + " /* [webfx translator] WARNING: '" + shorthand + ":' shorthand not converted in v1. Use longhands. */\n" + shorthand + ":");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String expandBorderShorthand(String css) {
        // Matches a 'border:' declaration value up to the next ';' or '}'
        Pattern p = Pattern.compile("(?i)(^|[;{\n\r])([ \t]*)border\s*:\\s*([^;{}]+)(;?)");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String indent = m.group(2);
            String value = m.group(3).trim();

            BorderComponents bc = parseBorderComponents(value);

            // If nothing could be parsed, keep original with a warning
            if (bc.isEmpty()) {
                String original = "border: " + value + (m.group(4).isEmpty() ? "" : ";");
                String replacement = prefix + indent + "/* [webfx translator] WARNING: couldn't parse border shorthand, leaving as-is */\n"
                        + indent + original;
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                continue;
            }

            StringBuilder repl = new StringBuilder();
            repl.append(prefix);
            // Emit width before style to help IDE grouping; order not important for CSS
            if (bc.width != null) {
                repl.append(indent).append("-fx-border-width: ").append(bc.width).append(";").append('\n');
            }
            if (bc.style != null) {
                repl.append(indent).append("-fx-border-style: ").append(bc.style).append(";").append('\n');
            }
            if (bc.color != null) {
                repl.append(indent).append("-fx-border-color: ").append(bc.color).append(";");
            } else {
                // Trim trailing newline if present
                if (repl.length() > 0 && repl.charAt(repl.length() - 1) == '\n') {
                    repl.setLength(repl.length() - 1);
                }
            }

            m.appendReplacement(sb, Matcher.quoteReplacement(repl.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static final class BorderComponents {
        String width;
        String style;
        String color;
        boolean isEmpty() { return width == null && style == null && color == null; }
    }

    private static BorderComponents parseBorderComponents(String value) {
        BorderComponents bc = new BorderComponents();
        String[] tokens = splitCssTokens(value);

        // Recognized styles
        Pattern widthPattern = Pattern.compile("(?i)^(?:0|[0-9]*\\.?[0-9]+(?:px|pt|pc|in|cm|mm|em|rem|ex|ch|vh|vw|vmin|vmax)?)$");
        // Color recognizers
        Pattern hexColor = Pattern.compile("(?i)^#(?:[0-9a-f]{3}|[0-9a-f]{4}|[0-9a-f]{6}|[0-9a-f]{8})$");
        Pattern funcColor = Pattern.compile("(?i)^(?:rgb|rgba|hsl|hsla)\\(.*\\)$");
        Pattern varColor = Pattern.compile("^-[-a-zA-Z0-9_]+$"); // after var() replacement variables look like -foo

        for (String t : tokens) {
            String token = t.trim();
            if (token.isEmpty()) continue;

            // Style
            if (bc.style == null && token.equalsIgnoreCase("none")) {
                bc.style = "none";
                if (bc.width == null) bc.width = "0";
                continue;
            }
            if (bc.style == null && (token.equalsIgnoreCase("solid") || token.equalsIgnoreCase("dotted") || token.equalsIgnoreCase("dashed"))) {
                bc.style = token.toLowerCase();
                continue;
            }

            // Width keyword mapping
            if (bc.width == null) {
                if (token.equalsIgnoreCase("thin")) { bc.width = "1px"; continue; }
                if (token.equalsIgnoreCase("medium")) { bc.width = "3px"; continue; }
                if (token.equalsIgnoreCase("thick")) { bc.width = "5px"; continue; }
            }

            // Width numeric
            if (bc.width == null && widthPattern.matcher(token).matches()) {
                bc.width = token;
                // If width is 0 and style unspecified, ensure style none
                if (("0".equals(token) || token.toLowerCase().startsWith("0px")) && bc.style == null) {
                    bc.style = "none";
                }
                continue;
            }

            // Color
            if (bc.color == null && (hexColor.matcher(token).matches() || funcColor.matcher(token).matches() || varColor.matcher(token).matches() || isNamedColor(token))) {
                bc.color = token;
                continue;
            }
        }

        // Defaults when partially specified and not explicitly none
        if (!"none".equalsIgnoreCase(bc.style)) {
            if (bc.style == null && (bc.width != null || bc.color != null)) bc.style = "solid";
            if (bc.width == null && (bc.style != null || bc.color != null)) bc.width = "1px";
        }

        return bc;
    }

    private static boolean isNamedColor(String token) {
        // A light heuristic: a single alphabetic identifier that is not a recognized style keyword
        if (!token.matches("(?i)^[a-z]+$")) return false;
        String tl = token.toLowerCase();
        return !(tl.equals("solid") || tl.equals("dotted") || tl.equals("dashed") || tl.equals("none"));
    }

    private static String[] splitCssTokens(String value) {
        // Split by whitespace, but keep functions like rgb(... ...), also handle nested parentheses
        StringBuilder current = new StringBuilder();
        java.util.List<String> tokens = new java.util.ArrayList<>();
        int paren = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                paren++;
                current.append(c);
            } else if (c == ')') {
                paren = Math.max(0, paren - 1);
                current.append(c);
            } else if (Character.isWhitespace(c) && paren == 0) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens.toArray(new String[0]);
    }

    private static String warnUnsupportedPerSideBorders(String css) {
        String[] perSide = {
                "border-top-width", "border-right-width", "border-bottom-width", "border-left-width",
                "border-top-style", "border-right-style", "border-bottom-style", "border-left-style",
                "border-top-color", "border-right-color", "border-bottom-color", "border-left-color"
        };
        for (String prop : perSide) {
            Pattern p = Pattern.compile("(?i)(^|[;{\\n\\r])\\s*" + Pattern.quote(prop) + "\\s*:");
            Matcher m = p.matcher(css);
            StringBuffer sb = new StringBuffer();
            boolean found = false;
            while (m.find()) {
                found = true;
                String prefix = m.group(1);
                m.appendReplacement(sb, prefix + " /* [webfx translator] WARNING: per-side border properties not converted in v1. Prefer 'border-*' longhands with 1–4 values. */\n" + prop + ":");
            }
            m.appendTail(sb);
            if (found) css = sb.toString();
        }
        return css;
    }

    // --- New: per-side support ---

    private static String expandPerSideBorderShorthand(String css) {
        // Matches: border-top|right|bottom|left: <value>
        Pattern p = Pattern.compile("(?i)(^|[;{\n\r])(\t|[ ]*)border-(top|right|bottom|left)\\s*:\\s*([^;{}]+)(;?)");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String indent = m.group(2);
            String side = m.group(3).toLowerCase();
            String value = m.group(4).trim();

            BorderComponents bc = parseBorderComponents(value);
            if (bc.isEmpty()) {
                String original = "border-" + side + ": " + value + (m.group(5).isEmpty() ? "" : ";");
                String replacement = prefix + indent + "/* [webfx translator] WARNING: couldn't parse per-side border shorthand, leaving as-is */\n"
                        + indent + original;
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                continue;
            }

            // Build 4-value lists for width/style/color
            int idx = sideIndex(side);
            String[] widths = {"0", "0", "0", "0"};
            String[] styles = {"none", "none", "none", "none"};
            String[] colors = {"transparent", "transparent", "transparent", "transparent"};

            // Defaults when partially specified and not none
            if (!"none".equalsIgnoreCase(bc.style)) {
                if (bc.style == null && (bc.width != null || bc.color != null)) bc.style = "solid";
                if (bc.width == null && (bc.style != null || bc.color != null)) bc.width = "1px";
            }

            if (bc.width != null) widths[idx] = bc.width;
            if (bc.style != null) styles[idx] = bc.style;
            // Only set color if present and style not none
            if (bc.color != null && (bc.style == null || !"none".equalsIgnoreCase(bc.style))) colors[idx] = bc.color;

            StringBuilder repl = new StringBuilder();
            repl.append(prefix);
            repl.append(indent).append("-fx-border-width: ").append(String.join(" ", widths)).append(";\n");
            repl.append(indent).append("-fx-border-style: ").append(String.join(" ", styles)).append(";\n");
            repl.append(indent).append("-fx-border-color: ").append(String.join(" ", colors)).append(";");

            m.appendReplacement(sb, Matcher.quoteReplacement(repl.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String mapPerSideBorderLonghands(String css) {
        // Handle width/style/color individually
        Pattern p = Pattern.compile("(?i)(^|[;{\n\r])(\t|[ ]*)border-(top|right|bottom|left)-(width|style|color)\\s*:\\s*([^;{}]+)(;?)");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String indent = m.group(2);
            String side = m.group(3).toLowerCase();
            String kind = m.group(4).toLowerCase();
            String value = m.group(5).trim();

            int idx = sideIndex(side);
            String prop;
            String[] vals;
            switch (kind) {
                case "width":
                    prop = "-fx-border-width";
                    vals = new String[]{"0", "0", "0", "0"};
                    vals[idx] = value;
                    break;
                case "style":
                    prop = "-fx-border-style";
                    vals = new String[]{"none", "none", "none", "none"};
                    vals[idx] = value;
                    break;
                case "color":
                    prop = "-fx-border-color";
                    vals = new String[]{"transparent", "transparent", "transparent", "transparent"};
                    vals[idx] = value;
                    break;
                default:
                    // Should not happen
                    m.appendReplacement(sb, m.group(0));
                    continue;
            }

            String replacement = prefix + indent + prop + ": " + String.join(" ", vals) + ";";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static int sideIndex(String side) {
        switch (side) {
            case "top": return 0;
            case "right": return 1;
            case "bottom": return 2;
            case "left": return 3;
            default: return 0;
        }
    }

    /**
     * Transforms unified web CSS for web output by:
     * 1. Moving border properties to :before pseudo-element (single-host strategy)
     * 2. Translating 'color' property to '--fx-text-fill' custom property
     *
     * @param css The unified web CSS content
     * @return Transformed CSS with border properties moved to :before selectors and color translated
     */
    static String transformUnifiedWebForWebOutput(String css) {
        StringBuilder out = new StringBuilder(css.length() * 2); // Estimate larger output due to duplication
        int i = 0;

        while (i < css.length()) {
            int braceOpen = css.indexOf('{', i);
            if (braceOpen == -1) {
                out.append(css, i, css.length());
                break;
            }

            // Extract selector
            String selector = css.substring(i, braceOpen);
            int braceClose = findMatchingBrace(css, braceOpen);
            if (braceClose == -1) {
                out.append(css.substring(i));
                break;
            }

            // Extract rule block content
            String ruleContent = css.substring(braceOpen + 1, braceClose);

            // Transform properties for web output
            String transformedContent = transformWebProperties(ruleContent);

            // Split properties into border and non-border
            BorderAndNonBorderProperties split = splitBorderProperties(transformedContent);

            // Write the rule
            if (!split.nonBorderProperties.isEmpty()) {
                // Write original selector with non-border properties
                out.append(selector).append(" {\n");
                out.append(split.nonBorderProperties);
                out.append("}\n");
            }

            if (!split.borderProperties.isEmpty()) {
                // Write :before selector with border properties
                String beforeSelector = createBeforeSelector(selector.trim());
                out.append(beforeSelector).append(" {\n");
                out.append(split.borderProperties);
                out.append("}\n");
            }

            i = braceClose + 1;
        }

        return out.toString();
    }

    /**
     * Transforms unified JavaFX-authored CSS (fxweb@) for web output by:
     * 1. Mapping JavaFX property names to WebFX custom properties:
     *    -fx-background-* → --fx-background-*
     *    -fx-border-*     → --fx-border-*
     * 2. Translating JavaFX root selector to web root:
     *    .root → :root (in selector headers)
     *
     * No other transformations are performed in this initial version.
     */
    static String transformFxWebForWebOutput(String css) {
        StringBuilder out = new StringBuilder(css.length());
        int i = 0;
        while (i < css.length()) {
            int braceOpen = css.indexOf('{', i);
            if (braceOpen == -1) {
                out.append(css, i, css.length());
                break;
            }

            String selector = css.substring(i, braceOpen);
            int braceClose = findMatchingBrace(css, braceOpen);
            if (braceClose == -1) {
                out.append(css.substring(i));
                break;
            }

            // Map .root to :root in selector headers (conservative, token-based)
            String translatedSelector = selector.replaceAll("(?i)(?<![\\w-])\\.root(?![\\w-])", ":root");
            // Map JavaFX pseudo-class :selected to a web-compatible helper class .pseudo-selected
            // (Web CSS lacks a native :selected pseudo-class on generic elements)
            // Note: allow replacement even when preceded by a class or type (e.g., .foo:selected)
            translatedSelector = translatedSelector.replaceAll("(?i):selected(?![\\w-])", ".pseudo-selected");

            // Extract declarations block
            String ruleContent = css.substring(braceOpen + 1, braceClose);

            // Map -fx-background-* and -fx-border-* property names to --fx-*
            String transformedContent = ruleContent;
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-background-", "$1$2--fx-background-");
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-border-", "$1$2--fx-border-");

            // Map text color: -fx-text-fill -> --fx-text-fill
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-text-fill", "$1$2--fx-text-fill");

            // Map text alignment: -fx-text-alignment -> text-align
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-text-alignment", "$1$2text-align");

            // Map shape/text fill color: -fx-fill -> --fx-fill
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-fill", "$1$2--fx-fill");

            // Map shape stroke properties from JavaFX to WebFX custom properties
            // -fx-stroke -> --fx-stroke (only when used as a full property name, i.e., followed by ':')
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-stroke\\s*:", "$1$2--fx-stroke:");
            // -fx-stroke-width -> --fx-stroke-width
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-stroke-width", "$1$2--fx-stroke-width");
            // Support both JavaFX canonical hyphenation and compact variant for line-cap/line-join
            // -fx-stroke-line-cap or -fx-stroke-linecap -> --fx-stroke-linecap
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-stroke-line-?cap", "$1$2--fx-stroke-linecap");
            // -fx-stroke-line-join or -fx-stroke-linejoin -> --fx-stroke-linejoin
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-stroke-line-?join", "$1$2--fx-stroke-linejoin");

            // Map opacity: -fx-opacity -> opacity
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-opacity", "$1$2opacity");

            // Map font properties to standard web CSS
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-font-family", "$1$2font-family");
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-font-size", "$1$2font-size");
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-font-weight", "$1$2font-weight");
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-font-style", "$1$2font-style");

            // Support JavaFX-authored custom property declarations (single-dash) by converting
            // them to standard web custom properties (double-dash) in the web output.
            // Example: -kbs-primary-color: #00A3FF;  ->  --kbs-primary-color: #00A3FF;
            // Safeguards: do not touch -fx-* properties and do not alter already valid --* names.
            transformedContent = transformedContent.replaceAll(
                    "(?i)(^|[;\\{\\n\\r])(\\s*)-(?!-)(?!fx-)([a-zA-Z][a-zA-Z0-9_-]*)\\s*:",
                    "$1$2--$3:");

            // Normalize unitless font-size numbers to px for web CSS
            transformedContent = transformedContent.replaceAll(
                    "(?i)(^|[;\\{\\n\\r])\\s*(font-size)\\s*:\\s*([0-9]*\\.?[0-9]+)\\s*;",
                    "$1 $2: $3px;");

            // Cursor: map -fx-cursor -> cursor and translate JavaFX keywords to web keywords
            // Property name
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])(\\s*)-fx-cursor", "$1$2cursor");
            // Values mapping based on HtmlSvgNodePeer.toCssCursor()
            // hand -> pointer
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])\\s*(cursor)\\s*:\\s*hand\\s*;", "$1 $2: pointer;");
            // open-hand -> grab
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])\\s*(cursor)\\s*:\\s*open-hand\\s*;", "$1 $2: grab;");
            // closed-hand -> grabbing
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])\\s*(cursor)\\s*:\\s*closed-hand\\s*;", "$1 $2: grabbing;");
            // h-resize -> ew-resize
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])\\s*(cursor)\\s*:\\s*h-resize\\s*;", "$1 $2: ew-resize;");
            // v-resize -> ns-resize
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])\\s*(cursor)\\s*:\\s*v-resize\\s*;", "$1 $2: ns-resize;");
            // disappear -> no-drop
            transformedContent = transformedContent.replaceAll("(?i)(^|[;\\{\\n\\r])\\s*(cursor)\\s*:\\s*disappear\\s*;", "$1 $2: no-drop;");

            // Normalize unitless numbers to px for properties where the web expects a length unit
            // Example: -fx-border-radius: 4  ->  --fx-border-radius: 4px
            transformedContent = normalizeFxWebLengthValuesForWeb(transformedContent);

            // Rewrite single-dash JavaFX custom variables used as values to CSS var(--...) syntax
            // Example: --fx-border-color: -kbs-border-color; -> --fx-border-color: var(--kbs-border-color);
            transformedContent = rewriteSingleDashVariablesInValues(transformedContent);

            // Map JavaFX drop shadow effect to web box-shadow
            transformedContent = translateFxEffectDropShadowToBoxShadow(transformedContent);

            out.append(translatedSelector).append("{");
            out.append(transformedContent);
            out.append("}");
            i = braceClose + 1;
        }
        return out.toString();
    }

    // --- fxweb@ helpers for web output ---

    /**
     * Translates JavaFX '-fx-effect: dropshadow(...)' declarations to web 'box-shadow: ...' declarations.
     * Supported form (JavaFX CSS): dropshadow( blurType , color , radius , spread , offsetX , offsetY )
     * - blurType and spread are accepted but ignored for mapping (web doesn't need them for a basic shadow)
     * - radius, offsetX, offsetY: unitless numbers are converted to 'px'
     * - color: kept as-is (supports hex, rgb[a], var(...))
     */
    private static String translateFxEffectDropShadowToBoxShadow(String declarations) {
        if (declarations == null || declarations.isEmpty()) return declarations;

        String s = declarations;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int propStart = indexOfIgnoreCase(s, "-fx-effect", i);
            if (propStart < 0) {
                out.append(s, i, s.length());
                break;
            }

            // Append text before the property
            out.append(s, i, propStart);

            // Find ':' after -fx-effect
            int colon = s.indexOf(':', propStart);
            if (colon < 0) {
                // Malformed, append rest and stop
                out.append(s.substring(propStart));
                break;
            }

            // Append the part up to the ':' normalized as a single space before name
            out.append(s, propStart, colon + 1); // keep original up to ':' for warning fallback cases

            // From after ':' skip spaces
            int vStart = colon + 1;
            while (vStart < s.length() && Character.isWhitespace(s.charAt(vStart))) vStart++;

            // Expect dropshadow(
            if (!regionMatchesIgnoreCase(s, vStart, "dropshadow(")) {
                // Not a dropshadow(), leave original segment untouched and continue after this declaration end
                int end = findEndOfDeclaration(s, colon + 1);
                out.append(s, colon + 1, end);
                i = end;
                continue;
            }

            int argsStart = vStart + "dropshadow(".length();
            int argsEnd = findMatchingParen(s, argsStart - 1); // pass index of '(' just before argsStart
            if (argsEnd < 0) {
                // Unbalanced, keep original to end of declaration
                int end = findEndOfDeclaration(s, colon + 1);
                out.append(s, colon + 1, end);
                i = end;
                continue;
            }

            String args = s.substring(argsStart, argsEnd);
            DropShadowArgs dsa = parseJavaFxDropShadowArgs(args);
            if (dsa == null) {
                // Can't parse: keep original and add warning just before it
                out.append(" /* [webfx translator] WARNING: couldn't parse -fx-effect dropshadow(), leaving as-is */\n ");
                int end = findEndOfDeclaration(s, argsEnd + 1);
                out.append(s, colon + 1, end);
                i = end;
                continue;
            }

            // Build replacement box-shadow
            String ox = appendPxIfUnitless(dsa.offsetX);
            String oy = appendPxIfUnitless(dsa.offsetY);
            String blur = appendPxIfUnitless(dsa.radius);
            String color = dsa.color == null ? "rgba(0,0,0,0.25)" : dsa.color.trim();

            // Replace entire declaration value up to optional trailing semicolon
            int declEnd = findEndOfDeclaration(s, argsEnd + 1);
            // Remove what we appended up to ':' and replace the whole value by box-shadow
            // We already appended up to ':' at the beginning; now overwrite that by writing the final property instead
            // To keep the output clean, backtrack to just before the property name we appended
            int rollbackTo = out.length() - (colon + 1 - propStart);
            if (rollbackTo >= 0) out.setLength(rollbackTo);

            out.append(" box-shadow: ").append(ox).append(' ').append(oy).append(' ').append(blur).append(' ').append(color).append(';');
            i = declEnd;
        }
        return out.toString();
    }

    private static int indexOfIgnoreCase(String s, String needle, int from) {
        int nlen = needle.length();
        for (int i = Math.max(0, from); i + nlen <= s.length(); i++) {
            if (s.regionMatches(true, i, needle, 0, nlen)) return i;
        }
        return -1;
    }

    private static boolean regionMatchesIgnoreCase(String s, int offset, String needle) {
        if (offset < 0 || offset + needle.length() > s.length()) return false;
        return s.regionMatches(true, offset, needle, 0, needle.length());
    }

    private static int findEndOfDeclaration(String s, int from) {
        // Return index just after the declaration value including optional semicolon
        int i = from;
        int depth = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if (c == ';' && depth == 0) return i + 1;
            else if (c == '}' && depth == 0) return i; // block end, semicolon may be omitted
            i++;
        }
        return i;
    }

    private static int findMatchingParen(String s, int openParenIndex) {
        // openParenIndex is the index of '('; returns index of matching ')' or -1
        if (openParenIndex < 0 || openParenIndex >= s.length() || s.charAt(openParenIndex) != '(') return -1;
        int depth = 0;
        for (int i = openParenIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String appendPxIfUnitless(String token) {
        if (token == null) return null;
        String t = token.trim();
        if (t.isEmpty()) return t;
        // If already has a unit or percentage, keep
        if (t.matches("(?i).*[a-z%].*")) return t;
        // If it's a number (possibly negative/decimal), append px
        if (t.matches("^-?[0-9]*\\.?[0-9]+$")) return t + "px";
        return t; // fallback
    }

    private static final class DropShadowArgs {
        String blurType; // ignored in mapping
        String color;
        String radius;
        String spread;  // ignored in mapping
        String offsetX;
        String offsetY;
    }

    private static DropShadowArgs parseJavaFxDropShadowArgs(String insideParens) {
        // Split on top-level commas (ignore commas inside parentheses like rgba(...))
        List<String> parts = splitArgsRespectingParens(insideParens);
        if (parts.isEmpty()) return null;

        DropShadowArgs dsa = new DropShadowArgs();
        // JavaFX order: blurType, color, radius, spread, offsetX, offsetY (all optional after blurType)
        // We will assign by position conservatively if present
        int n = parts.size();
        // Trim all
        for (int i = 0; i < n; i++) parts.set(i, parts.get(i).trim());
        try {
            if (n >= 1) dsa.blurType = parts.get(0);
            if (n >= 2) dsa.color = parts.get(1);
            if (n >= 3) dsa.radius = parts.get(2);
            if (n >= 4) dsa.spread = parts.get(3);
            if (n >= 5) dsa.offsetX = parts.get(4);
            if (n >= 6) dsa.offsetY = parts.get(5);
            // Fallbacks: if offsets missing, default to 0,0
            if (dsa.offsetX == null) dsa.offsetX = "0";
            if (dsa.offsetY == null) dsa.offsetY = "0";
            // If radius missing, default to 0 (no blur)
            if (dsa.radius == null) dsa.radius = "0";
            return dsa;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> splitArgsRespectingParens(String s) {
        List<String> out = new java.util.ArrayList<>();
        if (s == null) return out;
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (start <= s.length()) out.add(s.substring(start));
        return out;
    }

    /**
     * In JavaFX CSS, bare numbers are often interpreted as px (e.g., -fx-border-radius: 4).
     * Browsers, however, require explicit units once these values are used in CSS variable substitutions.
     * This method appends "px" to any unitless numeric tokens for a selected set of properties
     * that represent lengths in the web output:
     *  - --fx-border-width
     *  - --fx-border-<side>-width
     *  - --fx-border-radius
     *  - --fx-background-radius
     * It preserves numbers that already carry a unit and supports multi-value syntaxes
     * separated by spaces, commas, or slashes (for elliptical radii).
     */
    private static String normalizeFxWebLengthValuesForWeb(String declarations) {
        // Pattern to find the targeted properties and capture their values up to the next ';' or '}'
        Pattern p = Pattern.compile(
                "(?is)(^|[;{\\n\\r])\\s*(--fx-border-width|--fx-border-(?:top|right|bottom|left)-width|--fx-border-radius|--fx-background-radius|--fx-stroke-width)\\s*:\\s*([^;}]*)"
        );
        Matcher m = p.matcher(declarations);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String prop = m.group(2);
            String value = m.group(3);
            String normalized = appendPxToUnitlessNumbers(value);
            String replacement = prefix + " " + prop + ": " + normalized;
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Appends "px" to any unitless numeric token in the given value string.
     * Tokens may be separated by whitespace, commas, or slashes.
     * Examples:
     *  - "4" -> "4px"
     *  - "4 8" -> "4px 8px"
     *  - "10/20" -> "10px/20px"
     *  - "1.5em, 12" -> "1.5em, 12px"
     */
    private static String appendPxToUnitlessNumbers(String value) {
        if (value == null || value.isEmpty()) return value;
        // We can't use variable-length lookbehind in Java for (^|separator), so capture it and re-insert on replacement.
        // IMPORTANT: Ensure the number is followed by a boundary (end, whitespace, comma, slash, ')' or '}', or ';')
        // to avoid partial matches inside tokens like '16px' (which previously produced '1px6px').
        Pattern num = Pattern.compile("(?i)(^|[\\s,\\/])([0-9]*\\.?[0-9]+)(?=$|[\\s,\\/;\\)\\}])");
        Matcher m = num.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String sep = m.group(1);
            String number = m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(sep + number + "px"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Rewrites tokens that look like JavaFX-style custom variables (single dash, e.g. -kbs-primary-color)
     * to standard web CSS custom property usage with var(--...): var(--kbs-primary-color).
     * This operates only on values (right-hand side of declarations), preserving property names.
     */
    private static String rewriteSingleDashVariablesInValues(String declarations) {
        if (declarations == null || declarations.isEmpty()) return declarations;

        // Match a declaration and capture its property and value (up to next ';' or '}')
        Pattern decl = Pattern.compile("(?is)(^|[;{\\n\\r])\\s*([a-zA-Z_-][a-zA-Z0-9_-]*)\\s*:\\s*([^;}]*)");
        Matcher dm = decl.matcher(declarations);
        StringBuffer out = new StringBuffer();
        while (dm.find()) {
            String prefix = dm.group(1);
            String prop = dm.group(2); // not used here, but kept for clarity
            String value = dm.group(3);

            // Replace occurrences of a single-dash custom name token with var(--name)
            // Preceded by start/whitespace/comma/slash/open-paren to avoid touching mid-ident parts
            Pattern singleDashVar = Pattern.compile("(^|[\\s,:/\\(])-(?!-)([a-zA-Z][a-zA-Z0-9-]*)");
            Matcher vm = singleDashVar.matcher(value);
            StringBuffer vb = new StringBuffer();
            while (vm.find()) {
                String sep = vm.group(1);
                String name = vm.group(2);
                vm.appendReplacement(vb, Matcher.quoteReplacement(sep + "var(--" + name + ")"));
            }
            vm.appendTail(vb);

            dm.appendReplacement(out, Matcher.quoteReplacement(prefix + " " + prop + ": " + vb.toString()));
        }
        dm.appendTail(out);

        return out.toString();
    }

    private static String transformWebProperties(String ruleContent) {
        String transformed = ruleContent;

        // Translate 'color:' property to '--fx-text-fill:' for web output
        // This is needed because WebFX uses 'color: var(--fx-text-fill);' as default rule
        transformed = transformed.replaceAll("(?i)(^|[;\n\r])\\s*color\\s*:", "$1    --fx-text-fill:");

        // Duplicate border-radius to --fx-background-radius for web output
        // Web developers expect border-radius to clip the background, but WebFX needs explicit background-radius
        Pattern p = Pattern.compile("(?i)(^|[;\n\r])(\\s*)border-radius\\s*:\\s*([^;{}]+)(;?)");
        Matcher m = p.matcher(transformed);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String indent = m.group(2);
            String value = m.group(3).trim();
            String semicolon = m.group(4);

            // Keep the original border-radius and add --fx-background-radius
            String replacement = prefix + indent + "border-radius: " + value + semicolon + "\n"
                              + indent + "--fx-background-radius: " + value + ";";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        return sb.toString();
    }

    private static class BorderAndNonBorderProperties {
        String borderProperties = "";
        String nonBorderProperties = "";
    }

    private static BorderAndNonBorderProperties splitBorderProperties(String ruleContent) {
        BorderAndNonBorderProperties result = new BorderAndNonBorderProperties();
        StringBuilder borderProps = new StringBuilder();
        StringBuilder nonBorderProps = new StringBuilder();

        // Simple line-by-line parsing
        String[] lines = ruleContent.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Check if this is a border property
            if (isBorderProperty(trimmed)) {
                borderProps.append("    ").append(trimmed).append("\n");
            } else {
                nonBorderProps.append("    ").append(trimmed).append("\n");
            }
        }

        result.borderProperties = borderProps.toString();
        result.nonBorderProperties = nonBorderProps.toString();
        return result;
    }

    private static boolean isBorderProperty(String propertyLine) {
        String lower = propertyLine.toLowerCase().trim();
        return lower.startsWith("border:") ||
               lower.startsWith("border-width:") ||
               lower.startsWith("border-style:") ||
               lower.startsWith("border-color:") ||
               lower.startsWith("border-radius:") ||
               lower.startsWith("border-top:") ||
               lower.startsWith("border-right:") ||
               lower.startsWith("border-bottom:") ||
               lower.startsWith("border-left:") ||
               lower.startsWith("border-top-width:") ||
               lower.startsWith("border-right-width:") ||
               lower.startsWith("border-bottom-width:") ||
               lower.startsWith("border-left-width:") ||
               lower.startsWith("border-top-style:") ||
               lower.startsWith("border-right-style:") ||
               lower.startsWith("border-bottom-style:") ||
               lower.startsWith("border-left-style:") ||
               lower.startsWith("border-top-color:") ||
               lower.startsWith("border-right-color:") ||
               lower.startsWith("border-bottom-color:") ||
               lower.startsWith("border-left-color:") ||
               lower.startsWith("border-top-left-radius:") ||
               lower.startsWith("border-top-right-radius:") ||
               lower.startsWith("border-bottom-left-radius:") ||
               lower.startsWith("border-bottom-right-radius:");
    }

    private static String createBeforeSelector(String selector) {
        // Handle multiple selectors separated by commas
        if (selector.contains(",")) {
            String[] parts = selector.split(",");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) result.append(", ");
                result.append(addBeforeToPart(parts[i].trim()));
            }
            return result.toString();
        }
        return addBeforeToPart(selector);
    }

    private static String addBeforeToPart(String selectorPart) {
        // :before pseudo-element must come AFTER pseudo-classes but BEFORE pseudo-elements
        // Examples:
        //   .foo -> .foo:before
        //   .foo:hover -> .foo:hover:before
        //   .foo:hover:focus -> .foo:hover:focus:before
        //   .foo::after -> not supported (can't have both :before and ::after)

        // Find all pseudo-classes and pseudo-elements
        int firstPseudoElement = findFirstPseudoElement(selectorPart);

        if (firstPseudoElement != -1) {
            // There's already a pseudo-element (::before, ::after, etc.)
            // Insert :before before the pseudo-element
            return selectorPart.substring(0, firstPseudoElement) + ":before" + selectorPart.substring(firstPseudoElement);
        } else {
            // No pseudo-element, just append :before at the end
            return selectorPart + ":before";
        }
    }

    private static int findFirstPseudoElement(String selector) {
        // Find first occurrence of :: (pseudo-element)
        int doubleColon = selector.indexOf("::");
        if (doubleColon != -1) {
            return doubleColon;
        }

        // Also check for single-colon pseudo-elements (legacy syntax)
        // :before, :after, :first-letter, :first-line
        String[] pseudoElements = {":before", ":after", ":first-letter", ":first-line"};
        int minIndex = -1;
        for (String pe : pseudoElements) {
            int idx = selector.indexOf(pe);
            if (idx != -1 && (minIndex == -1 || idx < minIndex)) {
                minIndex = idx;
            }
        }
        return minIndex;
    }

    // --- Border-radius duplication ---

    private static String duplicateBorderRadiusToBackgroundRadius(String css) {
        // When border-radius is set, automatically add -fx-background-radius with the same value
        // This matches web developer expectations where border-radius affects the background clipping
        Pattern p = Pattern.compile("(?i)(^|[;{\\n\\r])(\\t|[ ]*)-fx-border-radius\\s*:\\s*([^;{}]+)(;?)");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String prefix = m.group(1);
            String indent = m.group(2);
            String value = m.group(3).trim();
            String semicolon = m.group(4);

            // Keep the original border-radius and add background-radius
            String replacement = prefix + indent + "-fx-border-radius: " + value + semicolon + "\n"
                              + indent + "-fx-background-radius: " + value + ";";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        m.appendTail(sb);
        return sb.toString();
    }

    // --- Box-shadow translation ---

    private static String translateBoxShadow(String css) {
        // Translate box-shadow to -fx-effect: dropshadow(...)
        // Web: box-shadow: h-offset v-offset blur spread color [inset];
        // JavaFX: -fx-effect: dropshadow(blur-type, color, radius, spread, x-offset, y-offset);

        Pattern p = Pattern.compile("(?i)(^|[;{\\n\\r])(\\t|[ ]*)box-shadow\\s*:\\s*([^;{}]+)(;?)");
        Matcher m = p.matcher(css);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String prefix = m.group(1);
            String indent = m.group(2);
            String value = m.group(3).trim();

            // Parse the box-shadow value
            BoxShadowComponents shadow = parseBoxShadow(value);

            if (shadow == null || shadow.inset) {
                // Can't translate or inset not supported
                String warning = shadow != null && shadow.inset
                    ? "/* [webfx translator] WARNING: inset box-shadow not supported in JavaFX; ignored */\n"
                    : "/* [webfx translator] WARNING: couldn't parse box-shadow, leaving as-is */\n";
                String replacement = prefix + indent + warning + indent + "box-shadow: " + value + (m.group(4).isEmpty() ? "" : ";");
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                continue;
            }

            // Build JavaFX dropshadow
            // dropshadow(blur-type, color, radius, spread, x-offset, y-offset)
            // Note: JavaFX spread is 0-1 (normalized), web spread is absolute length
            // For simplicity in v1, we'll use spread 0 (sharp) or calculate normalized spread
            String blurType = "gaussian";
            String color = shadow.color != null ? shadow.color : "rgba(0, 0, 0, 0.5)";
            String radius = shadow.blur != null ? stripPxAndDivideByTwo(shadow.blur) : "0";
            String spread = "0"; // Web spread doesn't map directly; use 0 for now
            String xOffset = shadow.offsetX != null ? stripPx(shadow.offsetX) : "0";
            String yOffset = shadow.offsetY != null ? stripPx(shadow.offsetY) : "0";

            String fxEffect = String.format("dropshadow(%s, %s, %s, %s, %s, %s)",
                blurType, color, radius, spread, xOffset, yOffset);

            String replacement = prefix + indent + "-fx-effect: " + fxEffect + ";";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        m.appendTail(sb);
        return sb.toString();
    }

    private static class BoxShadowComponents {
        String offsetX;
        String offsetY;
        String blur;
        String spread;
        String color;
        boolean inset;
    }

    private static BoxShadowComponents parseBoxShadow(String value) {
        // Simple parser for: [inset] h-offset v-offset [blur] [spread] [color]
        // Only supports single shadow (no comma-separated list) in v1

        if (value.contains(",")) {
            // Multiple shadows not supported in v1
            return null;
        }

        BoxShadowComponents shadow = new BoxShadowComponents();
        String[] tokens = splitCssTokens(value);

        if (tokens.length < 2) return null; // Need at least h-offset and v-offset

        int i = 0;

        // Check for inset
        if (tokens[i].equalsIgnoreCase("inset")) {
            shadow.inset = true;
            i++;
            if (i >= tokens.length) return null;
        }

        // Next 2-4 tokens are lengths (offset-x, offset-y, blur, spread)
        java.util.List<String> lengths = new java.util.ArrayList<>();
        while (i < tokens.length && looksLikeLength(tokens[i])) {
            lengths.add(tokens[i]);
            i++;
        }

        if (lengths.size() < 2) return null; // Need at least x and y offsets

        shadow.offsetX = lengths.get(0);
        shadow.offsetY = lengths.get(1);
        if (lengths.size() > 2) shadow.blur = lengths.get(2);
        if (lengths.size() > 3) shadow.spread = lengths.get(3);

        // Remaining tokens should be color
        if (i < tokens.length) {
            StringBuilder colorSb = new StringBuilder();
            for (int j = i; j < tokens.length; j++) {
                if (tokens[j].equalsIgnoreCase("inset")) {
                    shadow.inset = true;
                    continue;
                }
                if (j > i) colorSb.append(' ');
                colorSb.append(tokens[j]);
            }
            if (colorSb.length() > 0) {
                shadow.color = colorSb.toString();
            }
        }

        return shadow;
    }

    private static boolean looksLikeLength(String token) {
        String t = token.toLowerCase().trim();
        // Match 0 or numbers with units (px, em, rem, etc.) or just numbers
        return t.matches("^-?[0-9]*\\.?[0-9]+(px|pt|em|rem|ex|ch|vh|vw|vmin|vmax|cm|mm|in|pc)?$");
    }

    private static String stripPxAndDivideByTwo(String length) {
        // JavaFX radius is roughly half the blur radius in web
        String stripped = stripPx(length);
        try {
            double value = Double.parseDouble(stripped);
            return String.valueOf(value / 2);
        } catch (NumberFormatException e) {
            return stripped;
        }
    }
}
