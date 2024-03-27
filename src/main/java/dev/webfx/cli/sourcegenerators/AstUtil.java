package dev.webfx.cli.sourcegenerators;

import dev.webfx.platform.ast.AST;
import dev.webfx.platform.ast.ReadOnlyAstObject;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;

/**
 * @author Bruno Salmon
 */
final class AstUtil {

    final static PathMatcher AST_FILE_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.{properties,json}");

    static boolean hasArray(ReadOnlyAstObject astObject) {
        for (Object key : astObject.keys()) {
            Object value = astObject.get(key.toString());
            if (AST.isArray(value))
                return true;
            if (AST.isObject(value) && hasArray((ReadOnlyAstObject) value))
                return true;
        }
        return false;
    }
}
