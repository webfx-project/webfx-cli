package dev.webfx.buildtool;

/**
 * @author Bruno Salmon
 */
public class BuildInfo {

    public final DevProjectModule projectModule;
    public final boolean isForGwt;
    public final boolean isForOpenJfx;
    public final boolean isForGluon;
    public final boolean isExecutable;
    public final boolean isRegistry;

    public BuildInfo(DevProjectModule projectModule) {
        this.projectModule = projectModule;
        isForGwt = projectModule.getTarget().isMonoPlatform(Platform.GWT);
        isForGluon = projectModule.getTarget().isMonoPlatform(Platform.JRE) && projectModule.getTarget().hasTag(TargetTag.GLUON);
        isForOpenJfx = projectModule.getTarget().isMonoPlatform(Platform.JRE) && (projectModule.getTarget().hasTag(TargetTag.OPENJFX) || isForGluon);
        isExecutable = projectModule.isExecutable();
        isRegistry = projectModule.getName().contains("-registry-") || projectModule.getName().endsWith("-registry");
    }
}
