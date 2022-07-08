package dev.webfx.cli.util.textfile;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
final class TextFileOperation {

    final Path path;
    final String content; // null for delete, non-null for write

    public TextFileOperation(Path path, String content) {
        this.path = path;
        this.content = content;
    }
}
