package txtfnnl.uima.wrappers;

/**
 * A thread that destroys another process after a given number of seconds
 * unless the method {@link doNotKill} is called before that.
 * 
 * @author Florian Leitner
 */
public class RuntimeKiller extends Thread {

	private final ReadlineRuntime<?> target;
	private final int seconds;
	private boolean kill = true;

	public RuntimeKiller(ReadlineRuntime<?> target, int destroyAfterSeconds) {
		this.target = target;
		this.seconds = destroyAfterSeconds;
		this.kill = true;
	}

	public void doNotKill() {
		kill = false;
	}

	public void run() {
		try {
			Thread.sleep(1000L * seconds);
		} catch (InterruptedException ignore) {}

		if (kill) {
			try {
				target.stop();
			} catch (Throwable t) {}
		}
	}
}
