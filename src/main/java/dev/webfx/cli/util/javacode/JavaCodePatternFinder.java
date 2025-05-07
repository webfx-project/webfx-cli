package dev.webfx.cli.util.javacode;

import dev.webfx.cli.util.stopwatch.StopWatch;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Bruno Salmon
 */
public class JavaCodePatternFinder implements Iterable<String> {

    public static final StopWatch JAVA_PARSING_STOPWATCH = StopWatch.createSystemNanoStopWatch();

    private final JavaCodePattern javaCodePattern;
    private final JavaCode javaCode;

    JavaCodePatternFinder(JavaCodePattern javaCodePattern, JavaCode javaCode) {
        this.javaCodePattern = javaCodePattern;
        this.javaCode = javaCode;
    }

    private String getTextCode() {
        return javaCode.getTextCode();
    }

    @Override
    public Iterator<String> iterator() {
        return new Iterator<>() {
            private final Matcher matcher;
            private final StringOrCommentFinder stringOrCommentFinder = new StringOrCommentFinder();
            {
                JAVA_PARSING_STOPWATCH.on();
                matcher = javaCodePattern.getPattern().matcher(getTextCode());
                JAVA_PARSING_STOPWATCH.off();
            }
            @Override
            public boolean hasNext() {
                JAVA_PARSING_STOPWATCH.on();
                boolean hasNext = false;
                while (matcher.find()) {
                    if (!isInsideStringOrComment(matcher.start(javaCodePattern.getMatchingGroup()))) {
                        hasNext = true;
                        break;
                    }
                }
                JAVA_PARSING_STOPWATCH.off();
                return hasNext;
            }

            @Override
            public String next() {
                JAVA_PARSING_STOPWATCH.on();
                String next = mapFoundGroup(matcher.group(javaCodePattern.getMatchingGroup()));
                JAVA_PARSING_STOPWATCH.off();
                return next;
            }

            private boolean isInsideStringOrComment(int index) {
                return stringOrCommentFinder.isInsideStringOrComment(index);
            }
        };
    }

    private class StringOrCommentFinder {
        // State constants
        private static final int STATE_CODE = 0;
        private static final int STATE_BLOCK_COMMENT = 1;
        private static final int STATE_INLINE_COMMENT = 2;
        private static final int STATE_STRING = 3;

        // State tracking
        private int currentState = STATE_CODE;
        private int currentPosition = 0;
        private final String textCode;

        private StringOrCommentFinder() {
            this.textCode = getTextCode();
        }

        private boolean isInsideStringOrComment(int index) {
            // If we need to scan forward, do so
            if (index >= currentPosition) {
                scanToPosition(index);
            } else {
                // If we need to scan from the beginning, reset state and do so
                currentState = STATE_CODE;
                currentPosition = 0;
                scanToPosition(index);
            }

            // Return true if we're inside a string or comment
            return currentState == STATE_BLOCK_COMMENT || 
                   currentState == STATE_INLINE_COMMENT || 
                   currentState == STATE_STRING;
        }

        private void scanToPosition(int targetPosition) {
            while (currentPosition < targetPosition && currentPosition < textCode.length()) {
                char c = textCode.charAt(currentPosition);

                switch (currentState) {
                    case STATE_CODE:
                        if (currentPosition + 1 < textCode.length()) {
                            // Check for block comment start
                            if (c == '/' && textCode.charAt(currentPosition + 1) == '*') {
                                currentState = STATE_BLOCK_COMMENT;
                                currentPosition += 2;
                                continue;
                            }
                            // Check for inline comment start
                            if (c == '/' && textCode.charAt(currentPosition + 1) == '/') {
                                currentState = STATE_INLINE_COMMENT;
                                currentPosition += 2;
                                continue;
                            }
                        }
                        // Check for string start
                        if (c == '"') {
                            currentState = STATE_STRING;
                            currentPosition++;
                            continue;
                        }
                        // Just regular code
                        currentPosition++;
                        break;

                    case STATE_BLOCK_COMMENT:
                        if (currentPosition + 1 < textCode.length() && 
                            c == '*' && textCode.charAt(currentPosition + 1) == '/') {
                            currentState = STATE_CODE;
                            currentPosition += 2;
                            continue;
                        }
                        currentPosition++;
                        break;

                    case STATE_INLINE_COMMENT:
                        if (c == '\n') {
                            currentState = STATE_CODE;
                        }
                        currentPosition++;
                        break;

                    case STATE_STRING:
                        // Handle escaped quotes
                        if (c == '\\' && currentPosition + 1 < textCode.length() && 
                            textCode.charAt(currentPosition + 1) == '"') {
                            currentPosition += 2;
                            continue;
                        }
                        // End of string
                        if (c == '"') {
                            currentState = STATE_CODE;
                        }
                        currentPosition++;
                        break;
                }
            }
        }
    }


    String mapFoundGroup(String group) {
        return group;
    }

    String resolveFullClassName(String className) {
        if (className.contains("."))
            return className;
        JAVA_PARSING_STOPWATCH.on();
        Iterator<String> classImportIterator = new JavaCodePatternFinder(new JavaCodePattern(Pattern.compile("^\\s*import\\s+([a-z][a-z_0-9]*(\\.[a-z][a-z_0-9]*)*\\." + className + "\\s*)\\s*;", Pattern.MULTILINE), 1), javaCode).iterator();
        String fullClassName = classImportIterator.hasNext() ? classImportIterator.next()
                : javaCodePackageName() + "." + className;
        JAVA_PARSING_STOPWATCH.off();
        return fullClassName;
    }

    String javaCodePackageName() {
        JAVA_PARSING_STOPWATCH.on();
        Iterator<String> packageIterator = new JavaCodePatternFinder(new JavaCodePattern(Pattern.compile("^\\s*package\\s+([a-z][a-z_0-9]*(\\.[a-z][a-z_0-9]*)*)\\s*;", Pattern.MULTILINE), 1), javaCode).iterator();
        String packageName = packageIterator.hasNext() ?
                packageIterator.next()
                : null;
        JAVA_PARSING_STOPWATCH.off();
        return packageName;
    }
}
