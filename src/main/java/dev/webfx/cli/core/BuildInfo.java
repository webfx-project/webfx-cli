package dev.webfx.cli.core;

/**
 * @author Bruno Salmon
 */
public class BuildInfo {

    public final ProjectModuleImpl projectModule;
    public final boolean isForGwt;
    public final boolean isForJ2cl;
    public final boolean isForTeaVm;
    public final boolean isForWeb;
    public final boolean isForOpenJfx;
    public final boolean isForGluon;
    public final boolean isForVertx;
    public final boolean isExecutable;
    public final boolean isJ2clCompilable;
    public final boolean requiresJavafxEmul;

    public BuildInfo(ProjectModuleImpl projectModule) {
        this.projectModule = projectModule;
        Target target = projectModule.getTarget();
        isForJ2cl = target.isMonoPlatform(Platform.J2CL);
        isForGwt = target.isMonoPlatform(Platform.GWT);
        isForTeaVm = target.isMonoPlatform(Platform.TEAVM);
        isForWeb = isForGwt || isForJ2cl || isForTeaVm;
        isForVertx = target.isMonoPlatform(Platform.JRE) && target.hasTag(TargetTag.VERTX);
        isForOpenJfx = target.isMonoPlatform(Platform.JRE) && (target.hasTag(TargetTag.OPENJFX));
        isForGluon = target.isMonoPlatform(Platform.JRE) && target.hasTag(TargetTag.GLUON);
        isExecutable = projectModule.isExecutable();
        String moduleName = projectModule.getName();
        isJ2clCompilable = (isForJ2cl || target.isPlatformSupported(Platform.J2CL) || target.hasTag(TargetTag.J2CL) || target.hasTag(TargetTag.EMUL)) && !SpecificModules.isModulePartOfWebfxKitJavaFxGraphicsFatJ2cl(moduleName);
        requiresJavafxEmul = isForWeb || target.hasTag(TargetTag.GWT) || target.hasTag(TargetTag.J2CL)
                             || SpecificModules.isRegistryModule(moduleName)
                             || moduleName.equals(SpecificModules.WEBFX_KIT_JAVAFXBASE_EMUL) // required for correct artifactId in pom.xml
                             || moduleName.equals(SpecificModules.WEBFX_KIT_JAVAFXGRAPHICS_EMUL) // required javafx-base emulation
                             || moduleName.equals(SpecificModules.WEBFX_KIT_JAVAFXCONTROLS_EMUL)
                             || moduleName.equals(SpecificModules.WEBFX_KIT_JAVAFXMEDIA_EMUL)
                             || moduleName.equals(SpecificModules.WEBFX_KIT_JAVAFXWEB_EMUL)
                             || moduleName.equals(SpecificModules.WEBFX_KIT_JAVAFXWEB_ENGINEPEER);
    }
}
