package dev.webfx.cli.core;

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
    public final boolean requiresJavafxEmul;

    public BuildInfo(ProjectModuleImpl projectModule) {
        this.projectModule = projectModule;
        isForGwt = projectModule.getTarget().isMonoPlatform(Platform.GWT);
        isForTeaVm = projectModule.getTarget().isMonoPlatform(Platform.TEAVM);
        isForVertx = projectModule.getTarget().isMonoPlatform(Platform.JRE) && projectModule.getTarget().hasTag(TargetTag.VERTX);
        isForOpenJfx = projectModule.getTarget().isMonoPlatform(Platform.JRE) && (projectModule.getTarget().hasTag(TargetTag.OPENJFX));
        isForGluon = projectModule.getTarget().isMonoPlatform(Platform.JRE) && projectModule.getTarget().hasTag(TargetTag.GLUON);
        isExecutable = projectModule.isExecutable();
        requiresJavafxEmul = isForGwt || SpecificModules.isRegistryModule(projectModule.getName())
                    || projectModule.getName().equals(SpecificModules.WEBFX_KIT_JAVAFXWEB_ENGINEPEER);
    }
}
