package dev.webfx.buildtool;

/**
 * @author Bruno Salmon
 */
public class ServiceProvider {

    private final String spi;
    private final String implementation;

    public ServiceProvider(String spi, String implementation) {
        this.spi = spi;
        this.implementation = implementation;
    }

    public String getSpi() {
        return spi;
    }

    public String getImplementation() {
        return implementation;
    }
}
