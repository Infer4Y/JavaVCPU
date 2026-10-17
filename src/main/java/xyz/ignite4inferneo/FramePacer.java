package xyz.ignite4inferneo;

import java.util.function.LongSupplier;

/** Monotonic display pacing. Guest work consumes the frame budget; overruns never cause bursts. */
final class FramePacer {
    @FunctionalInterface interface Sleeper { void sleep(long nanos) throws InterruptedException; }
    private final long interval;
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private boolean started;
    private long deadline;

    FramePacer(long interval) {
        this(interval, System::nanoTime, nanos -> Thread.sleep(nanos / 1_000_000L, (int)(nanos % 1_000_000L)));
    }

    FramePacer(long interval, LongSupplier clock, Sleeper sleeper) {
        if (interval <= 0) throw new IllegalArgumentException("Positive frame interval required");
        this.interval = interval;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    void await() throws InterruptedException {
        long now = clock.getAsLong();
        if (!started) { deadline = now + interval; started = true; }
        long remaining;
        while ((remaining = deadline - now) > 0) {
            sleeper.sleep(remaining);
            now = clock.getAsLong();
        }
        // Rebase after missed deadlines rather than issuing rapid catch-up presentations.
        deadline = now + interval;
    }
}
