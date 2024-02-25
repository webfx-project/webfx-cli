package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;

/**
 * @author Bruno Salmon
 */
final class J2clEntryPointSourceGenerator {

    static void generateEntryPointSource(DevProjectModule module) {
        StringBuilder sb = new StringBuilder();
        module.getMainJavaSourceRootAnalyzer().getExecutableProviders()
                .forEach(providers -> {
                    if (sb.length() > 0)
                        sb.append("\n");
                    String spiClassName = providers.getSpiClassName();
                    ReusableStream<String> providerClassNames = providers.getProviderClassNames();
                    if (spiClassName.equals("dev.webfx.platform.resource.spi.impl.j2cl.J2clResourceBundle")) {
                        if (TextFileReaderWriter.fileExists(J2clEmbedResourcesBundleSourceGenerator.getJavaFilePath(module)))
                            providerClassNames = ReusableStream.concat(
                                    providerClassNames,
                                    ReusableStream.of(J2clEmbedResourcesBundleSourceGenerator.getProviderClassName(module))
                            );
                    }
                    sb.append("        register(").append(spiClassName).append(".class");
                    providerClassNames.forEach(providerClassName -> {
                        sb.append(", ");
                        sb.append(getProviderConstructorReference(providerClassName));
                    });
                    sb.append(");");
                });
        TextFileReaderWriter.writeTextFileIfNewOrModified(
                ResourceTextFileReader.readTemplate("J2clEntryPoint.javat")
                        .replace("${generatedRegisterCode}", sb),
                module.getMainResourcesDirectory().resolve("dev/webfx/platform/boot/j2cl/entrypoint/J2clEntryPoint.java"));
    }

    private static String getProviderConstructorReference(String providerClassName) {
        return providerClassName.replace('$', '.')
                + (providerClassName.endsWith(".GwtJsonObject") ? "::create" : "::new");
    }

}
