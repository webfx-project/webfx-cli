package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.TargetTag;
import dev.webfx.cli.exceptions.CliException;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.cli.util.stopwatch.StopWatch;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Bruno Salmon
 */
public final class CssFilesGenerator {

    private final static PathMatcher CSS_FILE_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.css");
    private final static Map<Path, String> CSS_CACHE = new HashMap<>(); // We assume the CLI exits after the update command, so no need to clear that cache

    public static int generateExecutableModuleCssResourceFiles(DevProjectModule module, boolean canUseCache, StopWatch mergePrepStopWatch) {
        boolean isWebExecutable = module.getBuildInfo().isForWeb;
        boolean isJavaFxExecutable = !isWebExecutable && module.isExecutable() && (module.getTarget().hasTag(TargetTag.OPENJFX) || module.getTarget().hasTag(TargetTag.GLUON));
        if (!isWebExecutable && !isJavaFxExecutable)
            return 0;

        String cssTag = isWebExecutable ? "web@" : "javafx@";

        Path mergedCssSourceFolder = (isWebExecutable ? module.getWebAppSourceDirectory() : module.getMainResourcesDirectory()).resolve("dev/webfx/kit/css");

        Map<Path /* relative path to the merged CSS file */, StringBuilder /* content concatenation */> cssMerges = new HashMap<>();

        Map<String, Path> moduleWebFxPaths = module.collectThisAndTransitiveWebFXPaths(canUseCache, true, mergePrepStopWatch);

        moduleWebFxPaths.forEach((moduleName, webfxPath) -> {
            Path webfxCssDirectory = webfxPath.resolve("css");
            if (Files.isDirectory(webfxCssDirectory)) {
                ReusableStream.create(() -> SplitFiles.uncheckedWalk(webfxCssDirectory))
                        .filter(CSS_FILE_MATCHER::matches)
                        .forEach(path -> {
                            Path relativeCssPath = webfxCssDirectory.relativize(path);
                            String fileName = relativeCssPath.getFileName().toString();
                            boolean isWebFxUnified = fileName.contains("webfx@");
                            boolean useThisFile = fileName.contains(cssTag) || isWebFxUnified; // include unified CSS in both targets
                            if (useThisFile) {
                                // Cache only the RAW file content so it can be transformed per-target on each use
                                String rawFileContent = CSS_CACHE.get(path);
                                if (rawFileContent == null) {
                                    rawFileContent = TextFileReaderWriter.readInputTextFile(path);
                                    CSS_CACHE.put(path, rawFileContent);
                                }
                                // Build target-specific content with header, and apply translation based on target
                                String cssContent = "\n/*===== " + relativeCssPath + " from " + moduleName + " =====*/\n\n" + rawFileContent;
                                // If this is a unified webfx@ CSS, validate and transform for the target platform
                                if (isWebFxUnified) {
                                    // Validation: enforce the v1 subset before translation
                                    try {
                                        CssWebFxAnalyzer.CssValidationMode mode = CssWebFxAnalyzer.CssValidationMode.from(
                                                System.getProperty("webfx.css.validation", "error"));
                                        // Validate raw content (without the per-file header)
                                        CssWebFxAnalyzer.validate(rawFileContent, mode, relativeCssPath + " from " + moduleName);
                                    } catch (RuntimeException e) {
                                        // Re-throw with file header context for better UX
                                        throw new CliException(e.getMessage());
                                    }
                                    // Apply platform-specific transformation
                                    if (isWebExecutable) {
                                        // For web: move border properties to :before pseudo-element (single-host strategy)
                                        cssContent = "\n/*===== " + relativeCssPath + " from " + moduleName + " =====*/\n\n"
                                                   + CssWebFxTranslator.transformUnifiedWebForWebOutput(rawFileContent);
                                    } else {
                                        // For JavaFX: translate web CSS syntax to JavaFX CSS syntax
                                        cssContent = CssWebFxTranslator.translateUnifiedWebToJavaFx(cssContent);
                                    }
                                }
                                if (fileName.contains("@")) {
                                    fileName = fileName.substring(fileName.lastIndexOf('@') + 1);
                                    Path parent = relativeCssPath.getParent();
                                    relativeCssPath = parent == null ? Path.of(fileName) : parent.resolve(fileName);
                                }
                                StringBuilder sb = cssMerges.computeIfAbsent(relativeCssPath, k -> new StringBuilder());
                                sb.append(cssContent);
                            }
                        });
            }
        });

        Path mergedCssTargetFolder = isWebExecutable ? module.getGwtExecutableFilePath().getParent().resolve("dev/webfx/kit/css") : null;

        // Writing down all css files other than main.css (main.css will be merged directly in index.html)
        for (Map.Entry<Path, StringBuilder> entry : cssMerges.entrySet()) {
            Path path = entry.getKey();
            String cssContent = entry.getValue().toString();
            if (isJavaFxExecutable) {
                StringBuilder fontFaces = new StringBuilder();
                while (true) {
                    int p1 = cssContent.indexOf("@font-face");
                    if (p1 == -1)
                        break;
                    int p2 = cssContent.indexOf('}', p1);
                    if (p2 == -1)
                        break;
                    while (p2 < cssContent.length() - 1 && Character.isWhitespace(cssContent.charAt(p2 + 1)))
                        p2++;
                    fontFaces.append(cssContent, p1, p2 + 1);
                    cssContent = cssContent.substring(0, p1) + cssContent.substring(p2 + 1);
                }
                if (!fontFaces.isEmpty()) {
                    cssContent = "/* @font-face rules listed first, otherwise they are ignored (OpenJFX bug) */\n\n" + fontFaces + cssContent;
                }
            }
            TextFileReaderWriter.writeTextFileIfNewOrModified(cssContent, mergedCssSourceFolder.resolve(path));
            // In addition, we update the target css file if it exists
            if (mergedCssTargetFolder != null) {
                Path targetCssPath = mergedCssTargetFolder.resolve(path);
                if (Files.exists(targetCssPath))
                    TextFileReaderWriter.writeTextFileIfNewOrModified(cssContent, targetCssPath);
            }
        }

        return cssMerges.size();
    }

}