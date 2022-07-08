package dev.webfx.tool.cli.util.javacode;

import java.util.regex.Pattern;

/**
 * @author Bruno Salmon
 */
public final class RequiredJavaServicesFinder extends JavaCodePatternFinder {

    private static final JavaCodePattern SERVICE_PATTERN =
            new JavaCodePattern(Pattern.compile("SingleServiceProvider\\s*\\.\\s*getProvider\\s*\\(\\s*([a-z_0-9A-Z.]+)\\.class"), 1);

    public RequiredJavaServicesFinder(JavaCode javaCode) {
        super(SERVICE_PATTERN, javaCode);
    }

    @Override
    String mapFoundGroup(String group) {
        return resolveFullClassName(group);
    }
}
