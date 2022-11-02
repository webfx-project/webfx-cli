package dev.webfx.cli.core;

import dev.webfx.cli.util.process.ProcessCall;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author Bruno Salmon
 */
public final class M2RootModule extends M2ProjectModule implements RootModule {

    private final ModuleRegistry moduleRegistry;
    private final LibraryModule libraryModule;

    public M2RootModule(LibraryModule libraryModule, ModuleRegistry moduleRegistry) {
        super(libraryModule, null);
        this.moduleRegistry = moduleRegistry;
        this.libraryModule = libraryModule;
    }

    @Override
    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    public LibraryModule getLibraryModule() {
        return libraryModule;
    }

    @Override
    public boolean isWebFxModuleFileExpected() {
        return libraryModule.isWebFx();
    }

    @Override
    public boolean isInlineWebFxParent() {
        return false; // Never called
    }

    @Override
    public void setInlineWebFxParent(boolean inlineWebFxParent) {
        // Never called
    }

    public ReusableStream<LibraryModule> getTransitiveLibraries() {
        return ReusableStream.create(() -> {
            // We invoke the Maven dependency tree, and analyse it, to get the transitive libraries
            List<LibraryModule> tree = new ArrayList<>();
            Path cachePath = MavenUtil.getMavenModuleWorkspace(this).resolve("dependency-tree.txt");

            Consumer<String> mavenDependencyTreeAnalyzer = new Consumer<>() {
                boolean treelogstart, treelogend;
                @Override
                public void accept(String line) {
                    if (line.startsWith("[INFO] ")) {
                        line = line.substring(7);
                        if (!treelogstart)
                            treelogstart = line.startsWith("--- maven-dependency-plugin");
                        else if (!treelogend) {
                            if (line.startsWith("-----"))
                                treelogend = true;
                            else if (!Character.isAlphabetic(line.charAt(0))) {
                                // Extracting the artifact
                                String artifact = line.substring(line.lastIndexOf(' ') + 1, line.lastIndexOf(':')); // artifact
                                tree.add(new LibraryModule(new ArtifactModule(artifact), M2RootModule.this));
                            }
                        }
                    }
                }
            };

            try {
                File cacheFile = cachePath.toFile();
                if (cacheFile.exists()) {
                    try (Stream<String> stream = Files.lines(cachePath)) {
                        stream.forEach(mavenDependencyTreeAnalyzer);
                    }
                } else {
                    BufferedWriter writer;
                    if (isSnapshotVersion())
                        writer = null;
                    else {
                        cacheFile.getParentFile().mkdirs();
                        writer = Files.newBufferedWriter(cachePath, StandardCharsets.UTF_8);
                    }
                    MavenUtil.invokeMavenGoalOnPomModule(this, "dependency:tree", new ProcessCall().setLogLineFilter(line -> {
                        if (line.isBlank() || line.startsWith("Progress"))
                            return false;
                        mavenDependencyTreeAnalyzer.accept(line);
                        if (writer != null)
                            try {
                                writer.write(line);
                                writer.write(System.getProperty("line.separator"));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        return true;
                    }));
                    if (writer != null) {
                        writer.flush();
                        writer.close();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return ReusableStream.fromIterable(tree);
        });
    }

}
