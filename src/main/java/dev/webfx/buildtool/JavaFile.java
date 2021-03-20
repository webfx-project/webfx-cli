package dev.webfx.buildtool;

import dev.webfx.buildtool.util.javacode.JavaCode;
import dev.webfx.buildtool.util.javacode.OptionalJavaServicesFinder;
import dev.webfx.buildtool.util.javacode.JavaCodePackagesFinder;
import dev.webfx.buildtool.util.javacode.RequiredJavaServicesFinder;
import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class JavaFile {

    private final Path path;
    private final ProjectModule projectModule;
    private final JavaCode javaCode = new JavaCode(this::getPath);
    private String packageName;
    private String className;
    private final ReusableStream<String> usedJavaPackagesCache =
            ReusableStream.fromIterable(new JavaCodePackagesFinder(javaCode))
                    .distinct()
                    .cache();
    private final ReusableStream<String> usedRequiredJavaServicesCache =
            ReusableStream.fromIterable(new RequiredJavaServicesFinder(javaCode))
                    .distinct()
                    .cache();
    private final ReusableStream<String> usedOptionalJavaServicesCache =
            ReusableStream.fromIterable(new OptionalJavaServicesFinder(javaCode))
                    .distinct()
                    .cache();

    /***********************
     ***** Constructor *****
     ***********************/

    JavaFile(Path path, ProjectModule projectModule) {
        this.path = path;
        this.projectModule = projectModule;
    }


    JavaCode getJavaCode() {
        return javaCode;
    }

    /*************************
     ***** Basic getters *****
     *************************/


    Path getPath() {
        return path;
    }

    ProjectModule getProjectModule() {
        return projectModule;
    }

    String getPackageName() {
        if (packageName == null) {
            getClassName();
            int lastDotIndex = className.lastIndexOf('.');
            packageName = className.substring(0, lastDotIndex);
        }
        return packageName;
    }

    String getClassName() {
        if (className == null)
            className = path.toString().substring(projectModule.getJavaSourceDirectory().toString().length() + 1, path.toString().length() - 5).replaceAll("[/\\\\]", ".");
        return className;
    }


    /******************************
     ***** Analyzing streams  *****
     ******************************/

    ReusableStream<String> getUsedJavaPackages() {
        return usedJavaPackagesCache;
    }

    ReusableStream<String> getUsedRequiredJavaServices() {
        return usedRequiredJavaServicesCache;
    }

    ReusableStream<String> getUsedOptionalJavaServices() {
        return usedOptionalJavaServicesCache;
    }

    boolean usesJavaClass(String javaClass) {
        return getJavaCode().getTextCode().contains(javaClass);
    }


    /********************
     ***** Logging  *****
     ********************/

    @Override
    public String toString() {
        return getClassName();
    }

}
