package dev.webfx.cli.util.splitfiles;

import dev.webfx.cli.util.stopwatch.StopWatch;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * @author Bruno Salmon
 */
public class SplitFiles {

    public static final StopWatch FILE_TREE_WALKING_STOPWATCH = StopWatch.createSystemNanoStopWatch();

    public static Spliterator<Path> walk(Path start, FileVisitOption... options) throws IOException {
        return walk(start, Integer.MAX_VALUE, options);
    }

    public static Spliterator<Path> walk(Path start, int maxDepth, FileVisitOption... options) throws IOException {
        FILE_TREE_WALKING_STOPWATCH.on();
        FileTreeIterator iterator = new FileTreeIterator(start, maxDepth, options);
        try {
            return Spliterators.spliteratorUnknownSize(new Iterator<>() {
                private boolean closed;

                @Override
                public boolean hasNext() {
                    if (closed)
                        return false;
                    FILE_TREE_WALKING_STOPWATCH.on();
                    boolean hasNext = iterator.hasNext();
                    if (!hasNext) {
                        iterator.close();
                        closed = true;
                    }
                    FILE_TREE_WALKING_STOPWATCH.off();
                    return hasNext;
                }

                @Override
                public Path next() {
                    FILE_TREE_WALKING_STOPWATCH.on();
                    Path file = iterator.next().file();
                    FILE_TREE_WALKING_STOPWATCH.off();
                    return file;
                }
            }, Spliterator.DISTINCT);
        } catch (Error | RuntimeException e) {
            iterator.close();
            throw e;
        } finally {
            FILE_TREE_WALKING_STOPWATCH.off();
        }
    }

    public static Spliterator<Path> uncheckedWalk(Path start, FileVisitOption... options) throws RuntimeException {
        return uncheckedWalk(start, Integer.MAX_VALUE, options);
    }

    public static Spliterator<Path> uncheckedWalk(Path start, int maxDepth, FileVisitOption... options) throws RuntimeException {
        try {
            return walk(start, maxDepth, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String uncheckedReadTextFile(Path path) throws RuntimeException {
        try {
            return new String(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
