package xyz.ignite4inferneo;

final class FramePacerRegression {
    static void run() throws Exception {
        long[] time = {0}, waited = {0};
        FramePacer pacer = new FramePacer(33, () -> time[0], nanos -> {waited[0] += nanos; time[0] += nanos;});
        pacer.await(); check(time[0] == 33, "first frame paced");
        time[0] += 10; waited[0] = 0; pacer.await();
        check(waited[0] == 23 && time[0] == 66, "guest work deducted from wait");
        time[0] += 100; waited[0] = 0; pacer.await();
        check(waited[0] == 0, "late frame has no added wait");
        pacer.await(); check(waited[0] == 33, "overrun rebased without catch-up burst");
        long[] wrap = {Long.MAX_VALUE - 10};
        FramePacer wrapping = new FramePacer(33, () -> wrap[0], nanos -> wrap[0] += nanos);
        wrapping.await(); check(wrap[0] == Long.MIN_VALUE + 22, "monotonic counter wrap");
        FramePacer interrupted = new FramePacer(33, () -> 0, nanos -> {throw new InterruptedException();});
        try { interrupted.await(); throw new AssertionError("interrupt swallowed"); }
        catch (InterruptedException expected) { }
        System.out.println("PASS: display pacing accounts for work, rebases overruns, handles clock wrap and interruption");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
