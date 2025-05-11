package dev.webfx.cli.commands;

import dev.webfx.cli.util.stopwatch.StopWatch;
import dev.webfx.cli.util.stopwatch.StopWatchGroup;

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
        //graalvm,
        callbacks,
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
        //graalvmStopWatch = StopWatch.createSystemNanoStopWatch(),
        callbacksStopWatch = StopWatch.createSystemNanoStopWatch(),
        metaStopWatch = StopWatch.createSystemNanoStopWatch(),
        mergePrepStopWatch = StopWatch.createSystemNanoStopWatch(),
        confMergeStopWatch = StopWatch.createSystemNanoStopWatch(),
        i18nMergeStopWatch = StopWatch.createSystemNanoStopWatch(),
        i18nJavaStopWatch = StopWatch.createSystemNanoStopWatch(),
        cssMergeStopWatch = StopWatch.createSystemNanoStopWatch();

    // Creating a group for merging stopwatches so that mergePrepStopWatch will automatically pause others while running
    private final StopWatchGroup mergeGroup = new StopWatchGroup(mergePrepStopWatch, confMergeStopWatch, i18nMergeStopWatch, cssMergeStopWatch);

    public int
        pomCount,
        moduleInfoCount,
        metaInfServicesCount,
        indexHtmlCount,
        gwtXmlCount,
        entryPointCount,
        embedResourceCount,
        //graalvmCount,
        callbacksCount,
        metaCount,
        confCount,
        i18nCount,
        i18nJavaCount,
        cssCount;

    private boolean areAllTasksSetToValue(boolean value) {
        return pom == value &&
               moduleInfo == value &&
               metaInfServices == value &&
               indexHtml == value &&
               gwtXml == value &&
               entryPoint == value &&
               embedResource == value &&
               //graalvm == value &&
               callbacks == value &&
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
            //graalvm =
            callbacks =
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
               //graalvmCount +
               callbacksCount +
               metaCount +
               confCount +
               i18nCount +
               cssCount;
    }
}
