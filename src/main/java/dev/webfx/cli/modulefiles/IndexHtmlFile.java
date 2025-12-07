package dev.webfx.cli.modulefiles;

import dev.webfx.cli.core.*;
import dev.webfx.cli.modulefiles.abstr.DevModuleFileImpl;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.dom4j.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Scanner;

/**
 * @author Bruno Salmon
 */
public class IndexHtmlFile extends DevModuleFileImpl {

    private static final String MAIN_CSS_RELATIVE_PATH = "dev/webfx/kit/css/main.css";

    public IndexHtmlFile(DevProjectModule module) {
        super(module, module.getWebAppSourceDirectory().resolve("index.html"));
    }

    @Override
    public boolean writeFile() {
        StringBuilder headSb = new StringBuilder(), bodySb = new StringBuilder();
        DevProjectModule module = getModule();
        if (module.isPwa()) {
            headSb.append("""
                <meta name='apple-mobile-web-app-status-bar-style' content='black-translucent'>
                <link rel='manifest' href='./pwa-manifest.json'>
                <style>
                    :root {
                        --progress-circle-radius: 52px;
                        --progress-icon-size: 32px;
                    }
            
                    #pwa-progress-bar-container {
                        display: none;
                        position: fixed;
                        top: 50%;
                        left: 50%;
                        transform: translate(-50%, -50%);
                        width: calc(var(--progress-circle-radius) * 2 + 16px);
                        height: calc(var(--progress-circle-radius) * 2 + 16px);
                    }
            
                    .circular-progress {
                        position: relative;
                        width: calc(var(--progress-circle-radius) * 2 + 16px);
                        height: calc(var(--progress-circle-radius) * 2 + 16px);
                    }
            
                    .circular-progress > svg {
                        transform: rotate(-90deg);
                        width: calc(var(--progress-circle-radius) * 2 + 16px);
                        height: calc(var(--progress-circle-radius) * 2 + 16px);
                    }
            
                    .circular-progress-bg {
                        fill: none;
                        stroke: #e6e6e6;
                        stroke-width: 8;
                    }
            
                    .circular-progress-bar {
                        fill: none;
                        stroke: #007bff;
                        stroke-width: 8;
                        stroke-linecap: round;
                        transition: stroke-dashoffset 0.3s ease;
                    }
            
                    .progress-icon {
                        position: absolute;
                        top: 50%;
                        left: 50%;
                        transform: translate(-50%, -50%);
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
            
                    .pause-icon {
                        width: var(--progress-icon-size);
                        height: var(--progress-icon-size);
                        background-color: #007bff;
                        border-radius: calc(var(--progress-icon-size) * 0.125);
                    }
            
                    .play-icon {
                        width: calc(var(--progress-icon-size) * 1.4);
                        height: calc(var(--progress-icon-size) * 1.4);
                        background-image: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M5 2v20c0 .83.84 1.37 1.55.98l15-10c.68-.49.68-1.47 0-1.96l-15-10C5.84 .63 5 1.17 5 2z" stroke-linejoin="round" fill="%23007bff"/></svg>');
                        background-size: contain;
                        background-repeat: no-repeat;
                        background-position: center;
                    }
                </style>""".indent(8));
            bodySb.append("""
                <div id="pwa-progress-bar-container">
                    <div class="circular-progress">
                        <svg id="progress-svg">
                            <circle class="circular-progress-bg" id="progress-bg"></circle>
                            <circle id="pwa-progress-bar" class="circular-progress-bar"></circle>
                        </svg>
                        <div class="progress-icon">
                            <div id="progress-icon-inner" class="pause-icon"></div>
                        </div>
                    </div>
                </div>
               
                <script>
                    function loadGwtApp() {
                        console.log('Injecting application script...');
                        var script = document.createElement('script');
                        script.src = "./%APP_SCRIPT_SRC%";
                        document.body.appendChild(script);
                    }
                    if ('serviceWorker' in navigator) {
                        navigator.serviceWorker.register('pwa-service-worker.js');
                        var isCriticalDone = false; // Don't use `let` declaration otherwise it might be unaccessible in closures on older Safari/WebKit versions on iOS  
               
                        // Initialize circular progress bar using CSS variable
                        const radiusValue = getComputedStyle(document.documentElement).getPropertyValue('--progress-circle-radius').trim();
                        const radius = parseFloat(radiusValue);
                        const circumference = 2 * Math.PI * radius;
                        const center = radius + 8; // radius + half stroke width
               
                        const bgCircle = document.getElementById('progress-bg');
                        const bar = document.getElementById('pwa-progress-bar');
               
                        if (bgCircle) {
                            bgCircle.setAttribute('cx', center);
                            bgCircle.setAttribute('cy', center);
                            bgCircle.setAttribute('r', radius);
                        }
               
                        if (bar) {
                            bar.setAttribute('cx', center);
                            bar.setAttribute('cy', center);
                            bar.setAttribute('r', radius);
                            bar.style.strokeDasharray = circumference;
                            bar.style.strokeDashoffset = circumference;
                        }
               
                        function checkLaunch() {
                            if (isCriticalDone && navigator.serviceWorker.controller) {
                                loadGwtApp();
                            }
                        }
                        navigator.serviceWorker.addEventListener('message', event => {
                            if (event.data) {
                                if (event.data.type === 'loading_progress') {
                                    const percent = Math.round((event.data.current / event.data.total) * 100);
                                    const container = document.getElementById('pwa-progress-bar-container');
                                    const bar = document.getElementById('pwa-progress-bar');
                                    const icon = document.getElementById('progress-icon-inner');
                                    if (container && bar) {
                                        if (!event.data.completed && percent < 100) {
                                            container.style.display = 'block';
                                            bar.style.strokeDashoffset = circumference - (percent / 100) * circumference;
                                            if (icon) {
                                                icon.className = 'pause-icon';
                                            }
                                        }
                                        if (event.data.completed || percent >= 100) {
                                            bar.style.strokeDashoffset = 0;
                                            if (icon) {
                                                icon.className = 'play-icon';
                                            }
                                            setTimeout(() => container.style.display = 'none', 800);
                                        }
                                    }
                                    if (event.data.criticalCompleted && !isCriticalDone) {
                                        isCriticalDone = true;
                                        checkLaunch();
                                    }
                                } else if (event.data.type === 'status') {
                                    if (event.data.criticalCompleted && !isCriticalDone) {
                                        isCriticalDone = true;
                                        checkLaunch();
                                    }
                                }
                            }
                        });
                        if (navigator.serviceWorker.controller) {
                            navigator.serviceWorker.controller.postMessage({ type: 'check_status' });
                        } else {
                            navigator.serviceWorker.addEventListener('controllerchange', () => {
                                navigator.serviceWorker.controller.postMessage({ type: 'check_status' });
                            });
                        }
                    } else {
                        loadGwtApp();
                    }
                </script>
               """.replace("%APP_SCRIPT_SRC%", getApplicationJsScriptFileName(module)).indent(8));
        } else {
            bodySb.append("""
                <script>
                    async function unregisterPwaServiceWorker() {
                        if (navigator.serviceWorker) {
                            navigator.serviceWorker.getRegistrations()
                                .then(registrations => {
                                    registrations.forEach((registration, i) => registration.unregister());
                                    return null;
                                });
                            const cache = await caches.open("webfx-pwa-cache")
                            const keys = await cache.keys();
                            await Promise.all(keys.map(async (req) => {
                                await cache.delete(req);
                            }));
                        }
                    }
                    unregisterPwaServiceWorker();
                </script>
                """.indent(8));
        }
        ReusableStream<ProjectModule> transitiveProjectModules =
            ProjectModule.filterProjectModules(module.getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules()).distinct();
        // Fixing possible incomplete stream
        Workaround.fixTerminalReusableStream(transitiveProjectModules); // TODO: remove this once fixed
        Path mainCssPath = getModule().getWebAppSourceDirectory().resolve(MAIN_CSS_RELATIVE_PATH);
        boolean isMainCssPresent = Files.exists(mainCssPath);
        // Now the stream should be complete
        ReusableStream.concat(
                transitiveProjectModules.flatMap(m -> m.getWebFxModuleFile().getHtmlNodes()),
                // JS script (generated by GWT or J2CL)
                module.isPwa() ? null : ReusableStream.of(XmlUtil.lookupElement(XmlUtil.parseXmlString("<html><body order='0'><script type='text/javascript' charset='utf-8' src='./" + getApplicationJsScriptFileName(module) + "'/></body></html>").getRootElement(), "/html[1]")),
                // Main CSS (if present). We also add the maven build timestamp as a query parameter to force the browser to reload the CSS on each new build
                isMainCssPresent ? ReusableStream.of(XmlUtil.lookupElement(XmlUtil.parseXmlString("<html><head><link rel='stylesheet' href='./" + MAIN_CSS_RELATIVE_PATH + "?v=${maven.build.timestamp}'></link></head></html>").getRootElement(), "/html[1]")) : null
            )
            .filter(htmlNode -> checkNodeConditions(htmlNode, transitiveProjectModules))
            .flatMap(htmlNode -> htmlNode == null ? ReusableStream.empty() : XmlUtil.nodeListToReusableStream(htmlNode.content(), n -> n))
            .filter(Element.class::isInstance).map(Element.class::cast)
            .sorted(Comparator.comparingInt(IndexHtmlFile::getNodeOrder))
            .filter(headOrBodyNode -> checkNodeConditions(headOrBodyNode, transitiveProjectModules))
            .forEach(headOrBodyNode -> {
                String nodeName = headOrBodyNode.getName();
                StringBuilder sb = "head".equalsIgnoreCase(nodeName) ? headSb : "body".equalsIgnoreCase(nodeName) ? bodySb : null;
                if (sb != null) {
                    String xmlText = XmlUtil.formatHtmlText(headOrBodyNode);
                    // Removing the head or body tag
                    xmlText = xmlText.substring(xmlText.indexOf('>') + 1);
                    xmlText = xmlText.substring(0, xmlText.length() - 3 - nodeName.length());
                    xmlText = xmlText.replaceAll("<META http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">", "");
                    Scanner scanner = new Scanner(xmlText);
                    boolean firstEmptyLineReached = false;
                    int shift = 0, emptyLines = 0;
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        boolean blank = line.isBlank();
                        if (!blank || firstEmptyLineReached) {
                            if (!firstEmptyLineReached) {
                                shift = 8 - line.indexOf("<");
                                firstEmptyLineReached = true;
                            } else if (blank) {
                                emptyLines++;
                                continue;
                            } else {
                                sb.append("\n".repeat(emptyLines));
                                emptyLines = 0;
                            }
                            int firstChar = 0;
                            if (shift >= 0)
                                sb.append(" ".repeat(shift));
                            else {
                                firstChar = Math.min(-shift, line.length());
                                for (int i = 0; i < firstChar; i++)
                                    if (!Character.isWhitespace(line.charAt(i))) {
                                        firstChar = i;
                                        break;
                                    }
                            }
                            sb.append(line.substring(firstChar)).append('\n');
                        }
                    }
                }
            });
        if (module.getBuildInfo().isForTeaVm) {
            if (module.isWasmModule()) {
                bodySb.append("""
                    <script>
                        async function main() {
                            let teavm = await TeaVM.wasmGC.load("classes.wasm", {
                                stackDeobfuscator: {
                                    enabled: false // Can be set to true during development to get clear stack traces
                                }
                            });
                            teavm.exports.main([]);
                        }
                        main();
                    </script>
                    """.indent(8));
            } else
                bodySb.append("        <script>main()</script>");
        }
        String html = ResourceTextFileReader.readTemplate("index.html")
            .replace("${generatedHeadContent}", headSb)
            .replace("${generatedBodyContent}", bodySb);
        TextFileReaderWriter.writeTextFileIfNewOrModified(html, getModuleFilePath());
        // Also updating index.html in target if exists (so the user doesn't have to recompile the app for just a style change)
        Path targetIndexHtmlPath = module.getGwtExecutableFilePath();
        if (Files.exists(targetIndexHtmlPath))
            TextFileReaderWriter.writeTextFileIfNewOrModified(html, targetIndexHtmlPath);
        return true;
    }

    public static String getApplicationJsScriptFileName(DevProjectModule module) {
        BuildInfo buildInfo = module.getBuildInfo();
        if (buildInfo.isForTeaVm)
            return module.isWasmModule() ? "classes.wasm-runtime.js" : "classes.js";
        if (buildInfo.isForJ2cl)
            return module.getName() + ".js";
        // GWT
        return module.getName().replaceAll("-", "_") + ".nocache.js";
    }

        private static boolean checkNodeConditions(Element headOrBodyNode, ReusableStream<ProjectModule> transitiveProjectModules) {
        String ifModulePropertyTrue = XmlUtil.getAttributeValue(headOrBodyNode, "if-module-property-true");
        if (ifModulePropertyTrue != null && transitiveProjectModules.noneMatch(m -> m.getWebFxModuleFile().getModuleProperties().anyMatch(p -> p.getPropertyName().equals(ifModulePropertyTrue) && "true".equalsIgnoreCase(p.getPropertyValue()))))
            return false;
        String ifUsesJavaPackage = XmlUtil.getAttributeValue(headOrBodyNode, "if-uses-java-package");
        if (ifUsesJavaPackage != null && !ProjectModule.modulesUsesJavaPackage(transitiveProjectModules, ifUsesJavaPackage))
            return false;
        String ifUsesJavaClass = XmlUtil.getAttributeValue(headOrBodyNode, "if-uses-java-class");
        if (ifUsesJavaClass != null && !ProjectModule.modulesUsesJavaClass(transitiveProjectModules, ifUsesJavaClass))
            return false;
        return true;
    }

    private static int getNodeOrder(Element node) {
        String order = XmlUtil.getAttributeValue(node, "order");
        return order == null ? 1 : Integer.parseInt(order);
    }
}
