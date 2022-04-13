package dev.webfx.buildtool;

/**
 * @author Bruno Salmon
 */
public class BuildInfo {

    public final ProjectModuleImpl projectModule;
    public final boolean isForGwt;
    public final boolean isForTeaVm;
    public final boolean isForOpenJfx;
    public final boolean isForGluon;
    public final boolean isForVertx;
    public final boolean isExecutable;
    public final boolean isRegistry;

    public BuildInfo(ProjectModuleImpl projectModule) {
        this.projectModule = projectModule;
        isForGwt = projectModule.getTarget().isMonoPlatform(Platform.GWT);
        isForTeaVm = projectModule.getTarget().isMonoPlatform(Platform.TEAVM);
        isForVertx = projectModule.getTarget().isMonoPlatform(Platform.JRE) && projectModule.getTarget().hasTag(TargetTag.VERTX);
        isForGluon = projectModule.getTarget().isMonoPlatform(Platform.JRE) && projectModule.getTarget().hasTag(TargetTag.GLUON);
        isForOpenJfx = projectModule.getTarget().isMonoPlatform(Platform.JRE) && (projectModule.getTarget().hasTag(TargetTag.OPENJFX) || isForGluon);
        isExecutable = projectModule.isExecutable();
        isRegistry = projectModule.getName().contains("-registry-") || projectModule.getName().endsWith("-registry");
    }
}
