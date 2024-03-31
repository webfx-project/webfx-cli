package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;

/**
 * @author Bruno Salmon
 */
final class GwtJ2clEntryPointSourceGenerator {

    static void generateJ2clEntryPointSource(DevProjectModule module) {
        generateGwtJ2clEntryPointSource(module, true);
    }

    static void generateGwtEntryPointSource(DevProjectModule module) {
        generateGwtJ2clEntryPointSource(module, false);
    }

    private static void generateGwtJ2clEntryPointSource(DevProjectModule module, boolean j2cl) {
        String templateName = j2cl ? "J2clEntryPoint.javat" : "GwtEntryPoint.javat";
        String javaFilePath = j2cl ? "dev/webfx/platform/boot/j2cl/J2clEntryPoint.java" : "super/dev/webfx/platform/boot/gwt/GwtEntryPoint.java";
        String bundleSpiClassName = j2cl ? "dev.webfx.platform.resource.spi.impl.j2cl.J2clResourceBundle" : "dev.webfx.platform.resource.spi.impl.gwt.GwtResourceBundle";
        String template = ResourceTextFileReader.readTemplate(templateName);
        StringBuilder sb = new StringBuilder();
        module.getMainJavaSourceRootAnalyzer().getExecutableProviders()
                .forEach(providers -> {
                    String spiClassName = providers.getSpiClassName();
                    ReusableStream<String> providerClassNames = providers.getProviderClassNames();
                    if (spiClassName.equals(bundleSpiClassName)) {
                        if (TextFileReaderWriter.fileExists(j2cl ? J2clEmbedResourcesBundleSourceGenerator.getJavaFilePath(module) : GwtEmbedResourcesBundleSourceGenerator.getJavaFilePath(module)))
                            providerClassNames = ReusableStream.concat(
                                    providerClassNames,
                                    ReusableStream.of(j2cl ? J2clEmbedResourcesBundleSourceGenerator.getProviderClassName() : GwtEmbedResourcesBundleSourceGenerator.getProviderClassName())
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
                module.getMainResourcesDirectory().resolve(javaFilePath));
    }

    private static String getProviderConstructorReference(String providerClassName) {
        return providerClassName.replace('$', '.')
               + (providerClassName.endsWith(".GwtJsonObject") ? "::create" : "::new");
    }

}
