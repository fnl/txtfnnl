package txtfnnl.subprocess;

/**
 * A thread that destroys a {@link ReadlineRuntime} process after a given number of seconds unless
 * the method {@link doNotKill} is called before that time has passed.
 * 
 * @author Florian Leitner
 */
public class RuntimeKiller extends Thread {
    private final ReadlineRuntime<?> target;
    private final int seconds;
    private boolean kill;

    public RuntimeKiller(ReadlineRuntime<?> target, int destroyAfterSeconds) {
        this.target = target;
        seconds = destroyAfterSeconds;
        kill = true;
    }

    public void doNotKill() {
        kill = false;
    }

    public void run() {
        try {
            Thread.sleep(1000L * seconds);
        } catch (final InterruptedException ignore) {}
        if (kill) {
            try {
                target.stop();
            } catch (final Throwable t) {}
        }
    }
}
