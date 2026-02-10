package dev.webfx.cli.commands;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.Logger;
import dev.webfx.cli.core.MavenUtil;
import dev.webfx.cli.exceptions.CliException;
import dev.webfx.cli.util.os.OperatingSystem;
import dev.webfx.cli.util.process.ProcessCall;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringTokenizer;

/**
 * @author Bruno Salmon
 */
@Command(name = "run", description = "Run a WebFX application.")
public final class Run extends CommonSubcommand implements Runnable {

    @CommandLine.Option(names = {"-g", "--gwt"}, description = "Runs the GWT app")
    private boolean gwt;

    @CommandLine.Option(names = {"--j2cl"}, description = "Runs the J2CL app")
    private boolean j2cl;

    @CommandLine.Option(names = {"-t", "--teavm"}, description = "Runs the TeaVM app")
    private boolean teavm;

    @CommandLine.Option(names = {"-j", "--javascript"}, description = "Runs the TeaVM JavaScript app")
    private boolean javascript;

    @CommandLine.Option(names = {"-w", "--wasm"}, description = "Runs the TeaVM Wasm app")
    private boolean wasm;

    @CommandLine.Option(names = {"-f", "--openjfx-fatjar"}, description = "Runs the OpenJFX fat jar")
    private boolean fatjar;

    @CommandLine.Option(names = {"-k", "--openjfx-desktop"}, description = "Runs the OpenJFX desktop app")
    private boolean openJfxDesktop;

    @CommandLine.Option(names = {"-d", "--gluon-desktop"}, description = "Runs the Gluon native desktop app")
    private boolean gluonDesktop;

    @CommandLine.Option(names = {"-m", "--gluon-mobile"}, description = "Same as --gluon-android on Linux, --gluon-ios on macOS")
    private boolean mobile;

    @CommandLine.Option(names = {"-a", "--gluon-android"}, description = "Runs the Gluon native Android app")
    private boolean android;

    @CommandLine.Option(names = {"-i", "--gluon-ios"}, description = "Runs the Gluon native iOS app")
    private boolean ios;

    @CommandLine.Option(names= {"-l", "--locate"}, description = "Just prints the location of the expected executable file (no run)")
    boolean locate;

    @CommandLine.Option(names= {"-s", "--show"}, description = "Just shows the executable file in the file browser (no run)")
    boolean show;

    @CommandLine.Option(names= {"-b", "--build"}, description = "(Re)build the application before running it")
    boolean build;

    @CommandLine.Option(names= {"-c", "--clean"}, description = "Clean the target folder before the build")
    boolean clean;

    @CommandLine.Option(names= {"--AppImage"}, description = "Takes the AppImage as executable (Linux)")
    boolean appImage;

    @CommandLine.Option(names= {"--deb"}, description = "Takes the deb package as executable (Linux)")
    boolean deb;

    @CommandLine.Option(names= {"--rpm"}, description = "Takes the rpm package as executable (Linux)")
    boolean rpm;

    @CommandLine.Option(names= {"-p", "--port"}, description = "Port of the web server.")
    int port;

    @CommandLine.Option(names= {"--file"}, description = "Runs the webapp via file:// rather than http://")
    boolean file;

    @Override
    public void run() {
        if (mobile) {
            if (OperatingSystem.isMacOs())
                ios = true;
            else
                android = true;
        }
        execute(new BuildRunCommon(clean, build, true, gwt, j2cl, teavm, javascript, wasm, fatjar, openJfxDesktop, gluonDesktop, android, ios, locate, show, appImage, deb, rpm, port, file), getWorkspace());
    }

    static void execute(BuildRunCommon brc, CommandWorkspace workspace) {
        if (brc.build)
            Build.execute(brc, workspace); // Build will call executeNoBuild() at the end of the build
        else
            executeNoBuild(brc, workspace);
    }

    static void executeNoBuild(BuildRunCommon brc, CommandWorkspace workspace) {
        DevProjectModule executableModule = brc.findExecutableModule(workspace);
        if (executableModule != null) // null with --locate or --show
            brc.getExecutableFilePath(executableModule).forEach(path -> executeFile(path, brc.port, brc.file));
    }

    private static void executeFile(Path executableFilePath, int port, boolean file) {
        try {
            String fileName = executableFilePath.getFileName().toString();
            String pathName = executableFilePath.toString();
            if (!Files.exists(executableFilePath))
                Logger.log("Can't execute nonexistent file " + ProcessCall.toShellLogCommandToken(executableFilePath));
            else if (fileName.endsWith(".jar"))
                ProcessCall.executeCommandTokens("java", "-jar", pathName);
            else if (fileName.endsWith(".html") && !file) {
                runWebAppWithBuiltInWebServer(executableFilePath, port);
            } else if (fileName.endsWith(".apk") || fileName.endsWith(".ipa")) {
                boolean android = fileName.endsWith(".apk");
                Path gluonModulePath = executableFilePath.getParent();
                while (gluonModulePath != null && !Files.exists(gluonModulePath.resolve("pom.xml")))
                    gluonModulePath = gluonModulePath.getParent();
                if (gluonModulePath != null)
                    MavenUtil.invokeMavenGoal("-P gluon-" + (android ? "android" : "ios") + " gluonfx:install gluonfx:nativerun"
                            , new ProcessCall().setWorkingDirectory(gluonModulePath));
            } else if (fileName.endsWith(".deb")) {
                int exitCode = ProcessCall.executeCommandTokens("sudo", "apt", "install", pathName);
                if (exitCode == 0 && fileName.contains("_")) {
                    String commandName = fileName.substring(0, fileName.lastIndexOf('_'));
                    Logger.log("\nIn addition to the desktop icon, you can now type '" + commandName + "' in the terminal to launch the application.\nUse 'sudo apt remove " + commandName.toLowerCase() + "' to uninstall the application.");
                }
            } else { // Everything else should be an executable file that we can call directly
                Desktop.getDesktop().open(executableFilePath.toFile()); // Works cross-platform
            }
        } catch (Exception e) {
            throw new CliException(e.getMessage());
        }
    }

    private static void runWebAppWithBuiltInWebServer(Path webappHtmlPath, int port) throws Exception {
        // We use ServerSocket instead of HttpServer because HttpServer strictly validates the request URI.
        // If the URI contains special characters like { or } (which can happen with Maven placeholders in query strings),
        // HttpServer rejects the request with a 400 Bad Request error before it even reaches our handler.
        // ServerSocket allows us to read the raw request and handle such URIs manually.
        boolean portSpecified = port != 0;
        if (!portSpecified)
            port = 8080;
        ServerSocket serverSocket;
        int count = 0;
        while (true) {
            try {
                serverSocket = new ServerSocket(port); // We don't use try-with-resources here because we want to keep the socket open
                break;
            } catch (java.net.BindException e) {
                if (portSpecified || ++count > 100)
                    throw new CliException("Port " + port + " is busy" + (portSpecified ? "" : " (tried 100 ports before giving up)"));
                port++;
            }
        }
        ServerSocket finalServerSocket = serverSocket;
        int finalPort = port;
        String htmlFileName = webappHtmlPath.getFileName().toString();
        Path directory = webappHtmlPath.getParent();
        new Thread(() -> {
            while (true) {
                try {
                    Socket socket = finalServerSocket.accept();
                    new Thread(() -> { // Each request is processed in a separate thread
                        try (socket) {
                            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            String line = in.readLine();
                            if (line == null) return;
                            StringTokenizer st = new StringTokenizer(line);
                            if (!st.hasMoreElements()) return;
                            st.nextToken(); // Method (ignored - only GET is supported)
                            if (!st.hasMoreElements()) return;
                            String path = st.nextToken();

                            // Stripping query string or hash if any
                            int qp = path.indexOf('?');
                            if (qp != -1) path = path.substring(0, qp);
                            int hp = path.indexOf('#');
                            if (hp != -1) path = path.substring(0, hp);

                            if ("/".equals(path))
                                path = "/" + htmlFileName;
                            // Decoding the path (ex: %20 -> space) to resolve the file correctly
                            try {
                                path = new URI(null, null, path, null).getPath();
                            } catch (Exception ignored) { }
                            Path file = directory.resolve(path.substring(1));
                            // Probing the MIME type
                            String contentType = Files.probeContentType(file);
                            // Fallback for .wasm in case the OS doesn't know about it
                            if (contentType == null && path.endsWith(".wasm")) {
                                contentType = "application/wasm";
                            }

                            OutputStream out = socket.getOutputStream();
                            if (Files.exists(file) && !Files.isDirectory(file)) {
                                byte[] bytes = Files.readAllBytes(file);
                                out.write("HTTP/1.1 200 OK\r\n".getBytes());
                                if (contentType != null)
                                    out.write(("Content-Type: " + contentType + "\r\n").getBytes());
                                out.write(("Content-Length: " + bytes.length + "\r\n").getBytes());
                                out.write("\r\n".getBytes());
                                out.write(bytes);
                            } else {
                                out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
                            }
                            out.flush();
                        } catch (Exception ignored) { }
                    }).start();
                } catch (Exception e) {
                    if (finalServerSocket.isClosed())
                        break;
                }
            }
        }).start();
        String url = "http://localhost:" + finalPort;
        Logger.log("Serving " + webappHtmlPath.getParent().getFileName() + " on " + url + " (Ctrl+C to stop)");
        Desktop.getDesktop().browse(new URI(url));
        Thread.currentThread().join(); // Wait forever (until Ctrl+C)
    }

}
