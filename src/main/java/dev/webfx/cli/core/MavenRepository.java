package dev.webfx.cli.core;

/**
 * @author Bruno Salmon
 */
public class MavenRepository {

    private final String id;
    private final String url;
    private final boolean snapshot;

    public MavenRepository(String id, String url, boolean snapshot) {
        this.id = id;
        this.url = url;
        this.snapshot = snapshot;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public boolean isSnapshot() {
        return snapshot;
    }
}
