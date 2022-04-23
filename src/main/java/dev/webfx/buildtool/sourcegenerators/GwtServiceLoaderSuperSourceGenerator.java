package dev.webfx.buildtool.sourcegenerators;

import dev.webfx.buildtool.DevProjectModule;
import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.nio.file.Files;

/**
 * @author Bruno Salmon
 */
final class GwtServiceLoaderSuperSourceGenerator {

    static void generateServiceLoaderSuperSource(DevProjectModule module) {
        //GwtFilesGenerator.logSection("Generating " + module.getName() + " module java.util.ServiceLoader.java super source for GWT");
        StringBuilder sb = new StringBuilder();
        module.getExecutableProviders()
                .forEach(providers -> {
                    String spiClassName = providers.getSpiClassName();
                    ReusableStream<String> providerClassNames = providers.getProviderClassNames();
                    if (spiClassName.equals("dev.webfx.platform.gwt.services.resource.spi.impl.GwtResourceBundle")) {
                        if (Files.exists(GwtEmbedResourcesBundleSourceGenerator.getJavaFilePath(module)))
                            providerClassNames = ReusableStream.concat(
                                    providerClassNames,
                                    ReusableStream.of(GwtEmbedResourcesBundleSourceGenerator.getProviderClassName(module))
                            );
                    }
                    sb.append("            case \"").append(spiClassName).append("\": return new ServiceLoader<S>(");
                    int initialLength = sb.length();
                    providerClassNames.forEach(providerClassName -> {
                        if (sb.length() > initialLength)
                            sb.append(", ");
                        sb.append(getProviderConstructorReference(providerClassName));
                    });
                    sb.append(");\n");
                });
        TextFileReaderWriter.writeTextFileIfNewOrModified(
                ResourceTextFileReader.readTemplate("ServiceLoader.java")
                        .replace("${generatedCasesCode}", sb),
                module.getResourcesDirectory().resolve("super/java/util/ServiceLoader.java"));
    }

    private static String getProviderConstructorReference(String providerClassName) {
        return providerClassName.replace('$', '.')
                + (providerClassName.equals("dev.webfx.platform.gwt.services.json.spi.impl.GwtJsonObject") ? "::create" : "::new");
    }

}
