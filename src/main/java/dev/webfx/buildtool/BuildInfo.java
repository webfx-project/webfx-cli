package dev.webfx.buildtool;

/**
 * @author Bruno Salmon
 */
public class BuildInfo {

    public final ProjectModule projectModule;
    public final boolean isForGwt;
    public final boolean isForJavaFx;
    public final boolean isForGluon;
    public final boolean isExecutable;
    public final boolean isRegistry;

    public BuildInfo(ProjectModule projectModule) {
        this.projectModule = projectModule;
        isForGwt = projectModule.getTarget().isMonoPlatform(Platform.GWT);
        isForGluon = projectModule.getTarget().isMonoPlatform(Platform.JRE) && projectModule.getTarget().hasTag(TargetTag.GLUON);
        isForJavaFx = projectModule.getTarget().isMonoPlatform(Platform.JRE) && (projectModule.getTarget().hasTag(TargetTag.JAVAFX) || isForGluon);
        isExecutable = projectModule.isExecutable();
        isRegistry = projectModule.getName().contains("-registry-") || projectModule.getName().endsWith("-registry");
    }
}
