package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public final class Providers implements Comparable<Providers> {
    private final String spiClassName;
    private final ReusableStream<ProjectModule> providerModules;
    private final ReusableStream<String> providerClassNames;
    private List<String> providerClassNamesList;

    public Providers(String spiClassName, ReusableStream<ProjectModule> providerModules) {
        this.spiClassName = spiClassName;
        this.providerModules = providerModules;
        this.providerClassNames = providerModules
                .flatMap(m -> m.getProvidedJavaServiceImplementations(spiClassName, true))
                .sorted();
    }

    public String getSpiClassName() {
        return spiClassName;
    }

    public ReusableStream<ProjectModule> getProviderModules() {
        return providerModules;
    }

    public ReusableStream<String> getProviderClassNames() {
        return providerClassNames;
    }

    List<String> getProviderClassNamesList() {
        if (providerClassNamesList == null)
            providerClassNamesList = providerClassNames.collect(Collectors.toList());
        return providerClassNamesList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Providers providers = (Providers) o;

        if (!spiClassName.equals(providers.spiClassName)) return false;
        return getProviderClassNamesList().equals(providers.getProviderClassNamesList());
    }

    @Override
    public int hashCode() {
        int result = spiClassName.hashCode();
        result = 31 * result + providerClassNames.collect(Collectors.toList()).hashCode();
        return result;
    }

    @Override
    public String toString() {
        return spiClassName + " -> " + providerClassNames.collect(Collectors.joining(", "));
    }

    @Override
    public int compareTo(Providers o) {
        return spiClassName.compareTo(o.spiClassName);
    }
}
