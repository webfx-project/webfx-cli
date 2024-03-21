package dev.webfx.cli.util.textfile;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
final class TextFileOperation {

    final Path path;
    final String content; // null for delete, non-null for write

    final boolean silent;

    public TextFileOperation(Path path, String content, boolean silent) {
        this.path = path;
        this.content = content;
        this.silent = silent;
    }
}
