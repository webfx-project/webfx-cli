package dev.webfx.cli.core;

import javax.lang.model.SourceVersion;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Java packages of compiled code (a classes directory or a jar), used by {@code webfx build --per-package}: listing
 * the packages, reading the dependencies between packages from the class files, sorting the packages so that the
 * dependencies come first, and writing one jar per package.
 */
public final class JavaPackages {

    /**
     * Name of the jar holding the files outside any package directory (META-INF, files at the root). A package name
     * can't contain a hyphen, so this jar never collides with the jar of a package.
     */
    public static final String ROOT_RESOURCES_JAR_NAME = "root-resources.jar";

    // Class names inside descriptors and signatures (ex: Lde/example/Foo; or Ljava/util/List<Lde/example/Foo;>;)
    private static final Pattern DESCRIPTOR_CLASS_NAME = Pattern.compile("L([A-Za-z_$][\\w$]*(?:/[A-Za-z_$][\\w$]*)+)[;<]");

    private JavaPackages() {
    }

    /**
     * @return package -> packages referenced by the class files of this package (including the package itself, as
     *         well as JDK and library packages)
     */
    public static Map<String, Set<String>> readPackageDependencies(Path classesDirectoryOrJar) throws IOException {
        Map<String, Set<String>> dependencies = new TreeMap<>();
        forEachFile(classesDirectoryOrJar, true, (name, in) -> {
            if (!isClassFile(name))
                return;
            String pkg = packageOfFile(name);
            if (pkg == null)
                return;
            Set<String> packageDependencies = dependencies.computeIfAbsent(pkg, k -> new TreeSet<>());
            for (String className : readReferencedClassNames(in)) {
                String dependency = packageOfInternalClassName(className);
                if (dependency != null)
                    packageDependencies.add(dependency);
            }
        });
        return dependencies;
    }

    /**
     * Groups the packages that depend on each other (strongly connected components) and sorts these groups so that
     * each group comes after all the groups it depends on. Dependencies to packages that are not keys of the map are
     * ignored.
     */
    public static List<List<String>> sortDependenciesFirst(Map<String, Set<String>> dependencies) {
        return new StronglyConnectedComponents(dependencies).compute();
    }

    /**
     * Writes one jar per package of the classes directory (with the classes and resources of the package), plus
     * {@link #ROOT_RESOURCES_JAR_NAME} for the files outside any package directory (META-INF, root files). The
     * module-info.class is left out, because the package jars are meant for the classpath. The jars of a previous
     * call are deleted first.
     *
     * @return the written jars
     */
    public static List<Path> writePackageJars(Path classesDirectory, Path outputDirectory) throws IOException {
        Map<String, List<String>> filesByJar = new TreeMap<>();
        forEachFile(classesDirectory, false, (name, in) -> {
            if (name.equals("module-info.class") || name.equals("META-INF/MANIFEST.MF"))
                return;
            String pkg = name.startsWith("META-INF/") ? null : packageOfFile(name);
            filesByJar.computeIfAbsent(pkg == null ? ROOT_RESOURCES_JAR_NAME : pkg + ".jar", k -> new ArrayList<>()).add(name);
        });
        if (Files.isDirectory(outputDirectory)) {
            try (Stream<Path> previousFiles = Files.list(outputDirectory)) {
                for (Path previousJar : previousFiles.filter(p -> p.getFileName().toString().endsWith(".jar")).toList())
                    Files.delete(previousJar);
            }
        }
        Files.createDirectories(outputDirectory);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("Created-By"), "WebFX CLI (webfx build --per-package)");
        List<Path> jars = new ArrayList<>();
        for (Map.Entry<String, List<String>> jarFiles : filesByJar.entrySet()) {
            Path jar = outputDirectory.resolve(jarFiles.getKey());
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
                Set<String> directories = new HashSet<>();
                for (String name : jarFiles.getValue()) {
                    // Directory entries (some resource lookups need them)
                    for (int i = name.indexOf('/'); i > 0; i = name.indexOf('/', i + 1)) {
                        String directory = name.substring(0, i + 1);
                        if (directories.add(directory)) {
                            out.putNextEntry(new JarEntry(directory));
                            out.closeEntry();
                        }
                    }
                    out.putNextEntry(new JarEntry(name));
                    Files.copy(classesDirectory.resolve(name), out);
                    out.closeEntry();
                }
            }
            jars.add(jar);
        }
        return jars;
    }

    /**
     * @return the package of a file (ex: de/example/Foo.class -> de.example), or null if the file is not in a
     *         package directory (root files, META-INF or other directory names that are not valid package names)
     */
    public static String packageOfFile(String fileName) {
        int slash = fileName.lastIndexOf('/');
        if (slash <= 0)
            return null;
        String pkg = fileName.substring(0, slash).replace('/', '.');
        return SourceVersion.isName(pkg) ? pkg : null;
    }

    /**
     * @return the package of a class name (ex: de.example.Foo -> de.example), or null for the default package
     */
    public static String packageOfClassName(String className) {
        int dot = className.lastIndexOf('.');
        return dot <= 0 ? null : className.substring(0, dot);
    }

    private static String packageOfInternalClassName(String internalClassName) {
        int slash = internalClassName.lastIndexOf('/');
        return slash <= 0 ? null : internalClassName.substring(0, slash).replace('/', '.');
    }

    private static boolean isClassFile(String fileName) {
        // module-info.class has no package, and META-INF/versions/... (multi-release jars) is ignored
        return fileName.endsWith(".class") && !fileName.equals("module-info.class") && !fileName.startsWith("META-INF/");
    }

    /**
     * Reads the class names (internal form, ex: de/example/Foo) referenced from the constant pool of a class file:
     * the class entries, and the class names inside descriptors and signatures (fields, methods, generics).
     */
    static Set<String> readReferencedClassNames(InputStream in) throws IOException {
        Set<String> classNames = new HashSet<>();
        DataInputStream data = new DataInputStream(new BufferedInputStream(in));
        if (data.readInt() != 0xCAFEBABE)
            return classNames;
        data.readUnsignedShort(); // minor version
        data.readUnsignedShort(); // major version
        int count = data.readUnsignedShort();
        String[] utf8 = new String[count];
        List<Integer> classNameIndexes = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            int tag = data.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = data.readUTF(); // Utf8
                case 7 -> classNameIndexes.add(data.readUnsignedShort()); // Class
                case 8, 16, 19, 20 -> data.skipNBytes(2); // String, MethodType, Module, Package
                case 15 -> data.skipNBytes(3); // MethodHandle
                case 3, 4, 9, 10, 11, 12, 17, 18 -> data.skipNBytes(4); // Integer, Float, Fieldref, Methodref, InterfaceMethodref, NameAndType, Dynamic, InvokeDynamic
                case 5, 6 -> { // Long, Double (they take two constant pool entries)
                    data.skipNBytes(8);
                    i++;
                }
                default -> throw new IOException("Unexpected constant pool tag " + tag);
            }
        }
        for (int index : classNameIndexes) {
            String name = utf8[index];
            if (name != null && !name.startsWith("[")) // array classes are covered by the descriptor scan below
                classNames.add(name);
        }
        for (String s : utf8) {
            if (s != null && s.indexOf('L') >= 0 && s.indexOf('/') > 0) {
                Matcher matcher = DESCRIPTOR_CLASS_NAME.matcher(s);
                while (matcher.find())
                    classNames.add(matcher.group(1));
            }
        }
        return classNames;
    }

    private interface FileConsumer {
        void accept(String fileName, InputStream in) throws IOException;
    }

    /**
     * Calls the consumer for each file of the directory or jar, with its name relative to the root ('/' separated),
     * and its content if requested (otherwise the input stream is null).
     */
    private static void forEachFile(Path classesDirectoryOrJar, boolean withContent, FileConsumer consumer) throws IOException {
        if (Files.isDirectory(classesDirectoryOrJar)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(classesDirectoryOrJar)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path file : files) {
                String name = classesDirectoryOrJar.relativize(file).toString().replace(File.separatorChar, '/');
                if (!withContent)
                    consumer.accept(name, null);
                else
                    try (InputStream in = Files.newInputStream(file)) {
                        consumer.accept(name, in);
                    }
            }
        } else if (Files.isRegularFile(classesDirectoryOrJar)) {
            try (ZipFile zip = new ZipFile(classesDirectoryOrJar.toFile())) {
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    if (entry.isDirectory())
                        continue;
                    if (!withContent)
                        consumer.accept(entry.getName(), null);
                    else
                        try (InputStream in = zip.getInputStream(entry)) {
                            consumer.accept(entry.getName(), in);
                        }
                }
            }
        }
    }

    /**
     * Tarjan's algorithm. It completes a component only after all the components reachable from it (i.e. its
     * dependencies), so the components come out in the "dependencies first" order.
     */
    private static final class StronglyConnectedComponents {
        private final Map<String, Set<String>> dependencies;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<List<String>> components = new ArrayList<>();
        private int counter;

        StronglyConnectedComponents(Map<String, Set<String>> dependencies) {
            this.dependencies = dependencies;
        }

        List<List<String>> compute() {
            for (String node : new TreeSet<>(dependencies.keySet()))
                if (!index.containsKey(node))
                    visit(node);
            return components;
        }

        private void visit(String node) {
            index.put(node, counter);
            lowLink.put(node, counter);
            counter++;
            stack.push(node);
            onStack.add(node);
            for (String dependency : dependencies.get(node)) {
                if (dependency.equals(node) || !dependencies.containsKey(dependency))
                    continue;
                if (!index.containsKey(dependency)) {
                    visit(dependency);
                    lowLink.put(node, Math.min(lowLink.get(node), lowLink.get(dependency)));
                } else if (onStack.contains(dependency))
                    lowLink.put(node, Math.min(lowLink.get(node), index.get(dependency)));
            }
            if (lowLink.get(node).equals(index.get(node))) {
                List<String> component = new ArrayList<>();
                String member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (!member.equals(node));
                Collections.sort(component);
                components.add(component);
            }
        }
    }
}
