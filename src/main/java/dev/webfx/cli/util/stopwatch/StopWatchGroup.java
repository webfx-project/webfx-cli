package dev.webfx.cli.util.stopwatch;

import java.util.Arrays;

/**
 * @author Bruno Salmon
 */
public class StopWatchGroup {

    private StopWatch runningStopWatch;
    private StopWatch pausedStopWatch;
    private boolean internalSyncing;

    public StopWatchGroup() {
    }

    public StopWatchGroup(StopWatch... stopWatches) {
        Arrays.stream(stopWatches).forEach(stopWatch -> stopWatch.setGroup(this));
    }

    void updateStopWatchRunningState(StopWatch stopWatch, boolean running) {
        if (internalSyncing)
            return;
        internalSyncing = true;
        if (running && runningStopWatch != stopWatch) {
            if (runningStopWatch != null) {
                runningStopWatch.off();
                pausedStopWatch = runningStopWatch;
            }
            runningStopWatch = stopWatch;
        }
        if (!running && runningStopWatch == stopWatch) {
            if (pausedStopWatch != null) {
                pausedStopWatch.on();
                runningStopWatch = pausedStopWatch;
            } else
                runningStopWatch = null;
            pausedStopWatch = null;
        }
        internalSyncing = false;
    }

}
