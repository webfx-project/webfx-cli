package dev.webfx.buildtool;

import dev.webfx.buildtool.util.javacode.JavaCode;
import dev.webfx.buildtool.util.javacode.OptionalJavaServicesFinder;
import dev.webfx.buildtool.util.javacode.JavaCodePackagesFinder;
import dev.webfx.buildtool.util.javacode.RequiredJavaServicesFinder;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class JavaFile implements Comparable<JavaFile> {

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


    public JavaCode getJavaCode() {
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
            int lastDotIndex = getClassName().lastIndexOf('.');
            packageName = className.substring(0, lastDotIndex);
        }
        return packageName;
    }

    public String getClassName() {
        if (className == null)
            className = projectModule.getJavaSourceDirectory().relativize(path).toString().replace(".java", "").replaceAll("[/\\\\]", ".");
        return className;
    }


    /******************************
     ***** Analyzing streams  *****
     ******************************/

    public ReusableStream<String> getUsedJavaPackages() {
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
        return getPath().toString();
    }

    @Override
    public int compareTo(JavaFile o) {
        return path.compareTo(o.path);
    }

    private static String lastJavaClass;
    private static String lastPackageName;

    static String getPackageNameFromJavaClass(String javaClass) {
        if (javaClass == lastJavaClass)
            return lastPackageName;
        lastJavaClass = javaClass;
        int lastDotIndex = javaClass.lastIndexOf('.');
        return lastPackageName = javaClass.substring(0, lastDotIndex);
    }
}
