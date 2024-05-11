package dev.webfx.cli.util.stopwatch;

import java.util.function.Supplier;

/**
 * @author Bruno Salmon
 */
public final class StopWatch {

    private final Supplier<Long> systemTimeGetter;
    private int runCount;
    private boolean running;
    private boolean stopped;
    private long systemTimeAtStart;
    private long stopWatchElapsedTimeAtPause;
    private long stopWatchCumulativePauseDuration;

    public StopWatch(Supplier<Long> systemTimeGetter) {
        this.systemTimeGetter = systemTimeGetter;
    }

    public static StopWatch createSystemNanoStopWatch() {
        return new StopWatch(System::nanoTime);
    }

    public static StopWatch createSystemMillisStopWatch() {
        return new StopWatch(System::currentTimeMillis);
    }

    public long getSystemTime() {
        return systemTimeGetter.get();
    }

    public long getSystemTimeAtStart() {
        return systemTimeAtStart;
    }

    public long getSystemElapsedTime() {
        return getSystemTime() - getSystemTimeAtStart();
    }

    public long getStopWatchElapsedTime() {
        return isRunning() ? getSystemElapsedTime() - stopWatchCumulativePauseDuration : stopWatchElapsedTimeAtPause;
    }

    public int getRunCount() {
        return runCount;
    }

    public void incRunCount() {
        runCount++;
    }

    public void decRunCount() {
        runCount--;
    }

    public void reset() {
        runCount = 0;
        running = stopped = false;
        systemTimeAtStart = stopWatchElapsedTimeAtPause = stopWatchCumulativePauseDuration = 0;
    }

    public boolean isStarted() {
        return runCount > 0;
    }

    public boolean isStopped() {
        return stopped;
    }

    private void setRunning(boolean running) {
        if (running != this.running) {
            this.running = running;
            if (running)
                incRunCount();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return isStarted() && !isRunning();
    }

    public void start() {
        startAt(0);
    }

    public void startAt(long initialElapsedTime) {
        if (isStarted() && !isStopped())
            throw new IllegalStateException("StopWatch already started, and not stopped. Alternatively use restart(), or safe on() instead.");
        reset();
        systemTimeAtStart = getSystemTime() - initialElapsedTime;
        setRunning(true);
    }

    public void stop() {
        if (!isStarted())
            throw new IllegalStateException("StopWatch not started. Alternatively use safe off() instead.");
        if (isRunning())
            pause();
        stopped = true;
    }

    public void restart() {
        stop();
        start();
    }

    public void pause() {
        if (!isRunning())
            throw new IllegalStateException("StopWatch is not running. Alternatively use safe off() instead.");
        stopWatchElapsedTimeAtPause = getStopWatchElapsedTime();
        setRunning(false);
    }

    public void resume() {
        if (!isPaused())
            throw new IllegalStateException("StopWatch is not paused. Alternatively use safe on() instead.");
        setRunning(true);
        stopWatchCumulativePauseDuration += getStopWatchElapsedTime() - stopWatchElapsedTimeAtPause;
    }

    // Safe transition methods (never throw exceptions)

    public void on() {
        if (isStopped())
            reset();
        if (!isStarted())
            start();
        else if (isPaused())
            resume();
    }

    public void off() {
        if (isRunning())
            pause();
    }

    public void toggle() {
        if (isRunning())
            off();
        else
            on();
    }

}

