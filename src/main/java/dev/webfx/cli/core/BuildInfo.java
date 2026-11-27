package dev.webfx.cli.core;

import dev.webfx.cli.exceptions.CliException;
import dev.webfx.cli.specific.SpecificModules;

/**
 * @author Bruno Salmon
 */
public class BuildInfo {

    public final ProjectModuleImpl projectModule;
    public final boolean isForJ2cl;
    public final boolean isForTeaVm;
    public final boolean isForElemental2;
    public final boolean isForWeb;
    public final boolean isForOpenJfx;
    public final boolean isForNative;
    public final boolean isForVertx;
    public final boolean isExecutable;
    public final boolean isJ2clCompilable;
    public final boolean requiresJavafxEmul;

    public BuildInfo(ProjectModuleImpl projectModule) {
        this.projectModule = projectModule;
        Target target = projectModule.getTarget();
        isExecutable = projectModule.isExecutable();
        // GWT executables are not supported anymore (the GWT platform remains known only to recognise the GWT-specific
        // library modules of WebFX, such as webfx-platform-javabase-emul-gwt, so that no other target picks them).
        if (isExecutable && target.isMonoPlatform(Platform.GWT))
            throw new CliException("GWT is not supported anymore: please remove the GWT executable module " + projectModule.getName()
                                   + " (and its entry in the parent pom.xml). Web applications are built with TeaVM (webfx build --teavm).");
        isForJ2cl = target.isMonoPlatform(Platform.J2CL);
        isForTeaVm = target.isMonoPlatform(Platform.TEAVM);
        isForElemental2 = target.hasTag(TargetTag.ELEMENTAL2);
        isForWeb = target.isMonoPlatform(Platform.GWT) /* GWT-specific library modules */ || isForJ2cl || isForTeaVm || isForElemental2;
        isForVertx = target.isMonoPlatform(Platform.JRE) && target.hasTag(TargetTag.VERTX);
        isForOpenJfx = target.isMonoPlatform(Platform.JRE) && (target.hasTag(TargetTag.OPENJFX));
        isForNative = target.isMonoPlatform(Platform.JRE) && target.hasTag(TargetTag.NATIVE);
        String moduleName = projectModule.getName();
        isJ2clCompilable = (isForJ2cl || target.isPlatformSupported(Platform.J2CL) || target.hasTag(TargetTag.J2CL) || target.hasTag(TargetTag.EMUL)) && !SpecificModules.isModulePartOfWebfxKitJavaFxGraphicsFatJ2cl(moduleName);
        // Note: target.hasTag(TargetTag.GWT) is still checked for the GWT-specific library modules of WebFX
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
