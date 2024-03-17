package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;

/**
 * @author Bruno Salmon
 */
final class J2clEntryPointSourceGenerator {

    static void generateEntryPointSource(DevProjectModule module) {
        String template = ResourceTextFileReader.readTemplate("J2clEntryPoint.javat");
        StringBuilder sb = new StringBuilder();
        module.getMainJavaSourceRootAnalyzer().getExecutableProviders()
                .forEach(providers -> {
                    String spiClassName = providers.getSpiClassName();
                    ReusableStream<String> providerClassNames = providers.getProviderClassNames();
                    if (spiClassName.equals("dev.webfx.platform.resource.spi.impl.j2cl.J2clResourceBundle")) {
                        if (TextFileReaderWriter.fileExists(J2clEmbedResourcesBundleSourceGenerator.getJavaFilePath(module)))
                            providerClassNames = ReusableStream.concat(
                                    providerClassNames,
                                    ReusableStream.of(J2clEmbedResourcesBundleSourceGenerator.getProviderClassName(module))
                            );
                    }
                    if (!providerClassNames.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append("\n");
                        sb.append("        register(").append(spiClassName).append(".class");
                        providerClassNames.forEach(providerClassName -> {
                            sb.append(", ");
                            sb.append(getProviderConstructorReference(providerClassName));
                        });
                        sb.append(");");
                    }
                });
        template = template.replace("${registerServiceProvidersBody}", sb);
        sb.setLength(0);
        ProjectModule.filterProjectModules(module.getMainJavaSourceRootAnalyzer().getTransitiveModules())
                .flatMap(m -> m.getWebFxModuleFile().getArrayNewInstanceClasses())
                .distinct()
                .sorted()
                .forEach(className -> {
                    if (sb.length() > 0)
                        sb.append("\n");
                    sb.append("        RArray.register(").append(className).append(".class, ").append(className).append("[]::new);");
                });
        template = template.replace("${registerArrayConstructorsBody}", sb);
        if (sb.length() == 0)
            template = template.replace("import dev.webfx.platform.reflect.RArray;\n", "");

        TextFileReaderWriter.writeTextFileIfNewOrModified(
                template,
                module.getMainResourcesDirectory().resolve("dev/webfx/platform/boot/j2cl/entrypoint/J2clEntryPoint.java"));
    }

    private static String getProviderConstructorReference(String providerClassName) {
        return providerClassName.replace('$', '.') + "::new";
    }

}
