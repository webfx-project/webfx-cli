package dev.webfx.cli.commands;

import dev.webfx.cli.util.stopwatch.StopWatch;

/**
 * @author Bruno Salmon
 */
public final class UpdateTasks {

    public boolean
            pom,
            moduleInfo,
            metaInfServices,
            indexHtml,
            gwtXml,
            entryPoint,
            embedResource,
            graalvm,
            meta,
            conf,
            i18n,
            css;

    public final StopWatch
            pomStopWatch = StopWatch.createSystemNanoStopWatch(),
            moduleInfoStopWatch = StopWatch.createSystemNanoStopWatch(),
            metaInfServicesStopWatch = StopWatch.createSystemNanoStopWatch(),
            indexHtmlStopWatch = StopWatch.createSystemNanoStopWatch(),
            gwtXmlStopWatch = StopWatch.createSystemNanoStopWatch(),
            entryPointStopWatch = StopWatch.createSystemNanoStopWatch(),
            embedResourceStopWatch = StopWatch.createSystemNanoStopWatch(),
            graalvmStopWatch = StopWatch.createSystemNanoStopWatch(),
            metaStopWatch = StopWatch.createSystemNanoStopWatch(),
            confStopWatch = StopWatch.createSystemNanoStopWatch(),
            i18nStopWatch = StopWatch.createSystemNanoStopWatch(),
            cssStopWatch = StopWatch.createSystemNanoStopWatch();

    public int
            pomCount,
            moduleInfoCount,
            metaInfServicesCount,
            indexHtmlCount,
            gwtXmlCount,
            entryPointCount,
            embedResourceCount,
            graalvmCount,
            metaCount,
            confCount,
            i18nCount,
            cssCount;

    private boolean areAllTasksSetToValue(boolean value) {
        return pom == value &&
               moduleInfo == value &&
               metaInfServices == value &&
               indexHtml == value &&
               gwtXml == value &&
               entryPoint == value &&
               embedResource == value &&
               graalvm == value &&
               meta == value &&
               conf == value &&
               i18n == value &&
               css == value;

    }

    void enableAllTasksIfUnset() {
        if (areAllTasksSetToValue(false)) {
            pom =
            moduleInfo =
            metaInfServices =
            indexHtml =
            gwtXml =
            entryPoint =
            embedResource =
            graalvm =
            meta =
            conf =
            i18n =
            css = true;
        }
    }

    boolean areAllTasksEnabled() {
        return areAllTasksSetToValue(true);
    }

    int totalCount() {
        return pomCount +
                moduleInfoCount +
                metaInfServicesCount +
                indexHtmlCount +
                gwtXmlCount +
                entryPointCount +
                embedResourceCount +
                graalvmCount +
                metaCount +
                confCount +
                i18nCount +
                cssCount;
    }
}
