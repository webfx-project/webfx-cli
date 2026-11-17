package dev.webfx.cli.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Analyzes the bytecode of a third-party jar (typically a library declared in webfx.xml with its exported packages,
 * but whose source code is not available to the CLI) to find which Java classes and packages it uses. This allows the
 * CLI to evaluate directives such as if-uses-java-class or if-uses-java-package also for code that only exists as
 * bytecode (ex: an application whose UI is provided by a jar).
 * <p>
 * It also reads the Java module name of the jar (from module-info.class or the Automatic-Module-Name manifest entry).
 */
public final class JarBytecodeAnalyzer {

    private static final Map<Path, JarBytecodeAnalyzer> CACHE = new ConcurrentHashMap<>();

    // Matches class references inside descriptors and generic signatures, ex: Ljavafx/scene/control/ScrollPane;
    private static final Pattern DESCRIPTOR_CLASS_PATTERN = Pattern.compile("L([A-Za-z_$][A-Za-z0-9_$]*(?:/[A-Za-z_$][A-Za-z0-9_$]*)+)[;<]");

    private final Path jarPath;
    private Set<String> usedJavaClasses;
    private Set<String> usedJavaPackages;
    private String javaModuleName;
    private boolean javaModuleNameRead;

    private JarBytecodeAnalyzer(Path jarPath) {
        this.jarPath = jarPath;
    }

    /**
     * Returns the analyzer for this jar, or null if the jar doesn't exist.
     */
    public static JarBytecodeAnalyzer of(Path jarPath) {
        if (jarPath == null || !Files.isRegularFile(jarPath))
            return null;
        return CACHE.computeIfAbsent(jarPath.toAbsolutePath().normalize(), JarBytecodeAnalyzer::new);
    }

    public Path getJarPath() {
        return jarPath;
    }

    /**
     * Returns the fully qualified names (dot-separated, nested classes reduced to their top-level class) of all
     * classes referenced by the bytecode of this jar, excluding the classes declared in the jar itself.
     */
    public synchronized Set<String> getUsedJavaClasses() {
        if (usedJavaClasses == null)
            analyze();
        return usedJavaClasses;
    }

    /**
     * Returns the packages of all classes referenced by the bytecode of this jar, excluding the packages declared in
     * the jar itself.
     */
    public synchronized Set<String> getUsedJavaPackages() {
        if (usedJavaPackages == null)
            analyze();
        return usedJavaPackages;
    }

    public boolean usesJavaClass(String javaClass) {
        return getUsedJavaClasses().contains(javaClass);
    }

    public boolean usesJavaPackage(String javaPackage) {
        return getUsedJavaPackages().contains(javaPackage);
    }

    /**
     * Returns the Java module name declared by this jar (module-info.class or Automatic-Module-Name), or null if the
     * jar doesn't declare any.
     */
    public synchronized String getJavaModuleName() {
        if (!javaModuleNameRead) {
            javaModuleNameRead = true;
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                ZipEntry moduleInfo = jar.getEntry("module-info.class");
                if (moduleInfo != null) {
                    try (InputStream is = jar.getInputStream(moduleInfo)) {
                        javaModuleName = readModuleNameFromModuleInfo(is);
                    }
                }
                if (javaModuleName == null) {
                    Manifest manifest = jar.getManifest();
                    if (manifest != null) {
                        String automaticName = manifest.getMainAttributes().getValue("Automatic-Module-Name");
                        if (automaticName != null && !automaticName.isBlank())
                            javaModuleName = automaticName.trim();
                    }
                }
            } catch (IOException e) {
                Logger.warning("Couldn't read the Java module name of " + jarPath + ": " + e.getMessage());
            }
        }
        return javaModuleName;
    }

    private void analyze() {
        Set<String> referencedClasses = new HashSet<>();
        Set<String> declaredClasses = new HashSet<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/") || name.endsWith("module-info.class"))
                    continue;
                declaredClasses.add(topLevelClassName(name.substring(0, name.length() - ".class".length())));
                try (InputStream is = jar.getInputStream(entry)) {
                    collectReferencedClasses(is, referencedClasses);
                } catch (IOException | RuntimeException e) {
                    Logger.warning("Couldn't analyze " + name + " in " + jarPath + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Logger.warning("Couldn't analyze the bytecode of " + jarPath + ": " + e.getMessage());
        }
        Set<String> declaredPackages = new HashSet<>();
        declaredClasses.forEach(c -> declaredPackages.add(packageOf(c)));
        Set<String> classes = new TreeSet<>();
        Set<String> packages = new TreeSet<>();
        for (String internalName : referencedClasses) {
            String className = topLevelClassName(internalName).replace('/', '.');
            String packageName = packageOf(className);
            if (packageName.isEmpty() || declaredPackages.contains(packageName))
                continue;
            classes.add(className);
            packages.add(packageName);
        }
        usedJavaClasses = Collections.unmodifiableSet(classes);
        usedJavaPackages = Collections.unmodifiableSet(packages);
    }

    private static String topLevelClassName(String internalName) {
        int dollarIndex = internalName.indexOf('$', internalName.lastIndexOf('/') + 1);
        return dollarIndex > 0 ? internalName.substring(0, dollarIndex) : internalName;
    }

    private static String packageOf(String className) {
        int lastDot = Math.max(className.lastIndexOf('.'), className.lastIndexOf('/'));
        return lastDot < 0 ? "" : className.substring(0, lastDot).replace('/', '.');
    }

    /**
     * Reads the constant pool of a class file and collects all referenced classes (internal names). Class constants
     * give the directly referenced classes, and UTF8 constants give the classes appearing in field and method
     * descriptors or generic signatures (ex: a method parameter type that is never instantiated).
     */
    private static void collectReferencedClasses(InputStream classStream, Set<String> referencedClasses) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(classStream));
        if (in.readInt() != 0xCAFEBABE)
            return;
        in.readUnsignedShort(); // minor version
        in.readUnsignedShort(); // major version
        int constantPoolCount = in.readUnsignedShort();
        String[] utf8 = new String[constantPoolCount];
        List<Integer> classNameIndexes = new ArrayList<>();
        for (int i = 1; i < constantPoolCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF(); // Utf8
                case 7 -> classNameIndexes.add(in.readUnsignedShort()); // Class
                case 8, 16, 19, 20 -> in.readUnsignedShort(); // String, MethodType, Module, Package
                case 3, 4 -> in.readInt(); // Integer, Float
                case 5, 6 -> { in.readLong(); i++; } // Long, Double (take two slots)
                case 9, 10, 11, 12, 17, 18 -> in.readInt(); // Fieldref, Methodref, InterfaceMethodref, NameAndType, Dynamic, InvokeDynamic
                case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); } // MethodHandle
                default -> throw new IOException("Unknown constant pool tag " + tag);
            }
        }
        for (int index : classNameIndexes) {
            String name = utf8[index];
            if (name == null)
                continue;
            if (name.startsWith("[")) // array type, ex: [Ljavafx/scene/Node;
                collectDescriptorClasses(name, referencedClasses);
            else
                referencedClasses.add(name);
        }
        for (String s : utf8) {
            if (s != null && s.indexOf('L') >= 0 && s.indexOf(';') > 0)
                collectDescriptorClasses(s, referencedClasses);
        }
    }

    private static void collectDescriptorClasses(String descriptor, Set<String> referencedClasses) {
        Matcher matcher = DESCRIPTOR_CLASS_PATTERN.matcher(descriptor);
        while (matcher.find())
            referencedClasses.add(matcher.group(1));
    }

    /**
     * Reads the module name from a module-info.class file: the Module attribute references a CONSTANT_Module entry,
     * whose name is a UTF8 constant.
     */
    private static String readModuleNameFromModuleInfo(InputStream classStream) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(classStream));
        if (in.readInt() != 0xCAFEBABE)
            return null;
        in.readUnsignedShort(); // minor version
        in.readUnsignedShort(); // major version
        int constantPoolCount = in.readUnsignedShort();
        String[] utf8 = new String[constantPoolCount];
        int[] moduleNameIndexes = new int[constantPoolCount]; // CONSTANT_Module index -> UTF8 name index
        for (int i = 1; i < constantPoolCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();
                case 19 -> moduleNameIndexes[i] = in.readUnsignedShort();
                case 7, 8, 16, 20 -> in.readUnsignedShort();
                case 3, 4 -> in.readInt();
                case 5, 6 -> { in.readLong(); i++; }
                case 9, 10, 11, 12, 17, 18 -> in.readInt();
                case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }
                default -> throw new IOException("Unknown constant pool tag " + tag);
            }
        }
        in.readUnsignedShort(); // access flags
        in.readUnsignedShort(); // this_class
        in.readUnsignedShort(); // super_class
        int interfacesCount = in.readUnsignedShort();
        for (int i = 0; i < interfacesCount; i++)
            in.readUnsignedShort();
        int fieldsCount = in.readUnsignedShort();
        for (int i = 0; i < fieldsCount; i++)
            skipMember(in);
        int methodsCount = in.readUnsignedShort();
        for (int i = 0; i < methodsCount; i++)
            skipMember(in);
        int attributesCount = in.readUnsignedShort();
        for (int i = 0; i < attributesCount; i++) {
            int nameIndex = in.readUnsignedShort();
            int length = in.readInt();
            if ("Module".equals(utf8[nameIndex])) {
                int moduleIndex = in.readUnsignedShort();
                int moduleNameIndex = moduleIndex < moduleNameIndexes.length ? moduleNameIndexes[moduleIndex] : 0;
                return moduleNameIndex > 0 ? utf8[moduleNameIndex] : null;
            }
            in.skipNBytes(length);
        }
        return null;
    }
    private static void skipMember(DataInputStream in) throws IOException {
        in.readUnsignedShort(); // access flags
        in.readUnsignedShort(); // name
        in.readUnsignedShort(); // descriptor
        int attributesCount = in.readUnsignedShort();
        for (int i = 0; i < attributesCount; i++) {
            in.readUnsignedShort();
            in.skipNBytes(in.readInt());
        }
    }
}
