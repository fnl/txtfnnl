package txtfnnl.subprocess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.ClosedByInterruptException;

import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

/**
 * A threaded logger logging everything received on an input stream.
 * 
 * @author Florian Leitner
 */
class ReadlineLogger extends Thread {
    /** Input stream of log messages. */
    InputStream stream;
    /** The logger instance. */
    Logger logger;
    /** The level for logging the messages. */
    Level level;
    /**
     * If <code>true</code>, the readline loop is canceled after the next iteration.
     */
    boolean stop;

    /**
     * Create a new threaded logger for an input stream.
     * 
     * @param in the input stream to observe
     * @param logger instance for the contents on the stream
     * @param level to log the incoming data at
     */
    ReadlineLogger(InputStream in, Logger logger, Level level) {
        stream = in;
        this.logger = logger;
        this.level = level;
        stop = false;
    }

    /**
     * Create a new threaded logger for an input stream. Logs incoming messages at INFO level.
     * 
     * @param in the input stream to observe
     * @param a logger instance for the contents on the stream
     */
    ReadlineLogger(InputStream in, Logger logger) {
        this(in, logger, Level.INFO);
    }

    /**
     * Log additional (non-streamed) messages to the underlying logger.
     * 
     * @param level for the message
     * @param message to log
     * @param params for the message
     */
    public void log(@SuppressWarnings("hiding") Level level, String message, Object... params) {
        logger.log(level, message, params);
    }

    /**
     * Log additional (non-streamed) messages to the underlying logger.
     * 
     * @param level for the message
     * @param message to log
     */
    public void log(@SuppressWarnings("hiding") Level level, String message) {
        logger.log(level, message);
    }

    /**
     * Log additional (non-streamed) messages to the underlying logger.
     * 
     * @param message to log
     * @param params for the message
     */
    public void log(String message, Object... params) {
        this.log(level, message, params);
    }

    /**
     * Log additional (non-streamed) messages to the underlying logger.
     * 
     * @param message to log
     */
    public void log(String message) {
        this.log(level, message);
    }

    /** Stop the readline loop an close the input stream. */
    void halt() throws IOException {
        stop = true;
        stream.close();
    }

    @Override
    public void run() {
        try {
            final InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
            final BufferedReader buffer = new BufferedReader(reader);
            String line = null;
            while ((line = buffer.readLine()) != null) {
                logger.log(level, line.trim());
                if (stop) {
                    break;
                }
            }
        } catch (final ClosedByInterruptException e) {
            logger.log(Level.FINE, "input stream was closed");
        } catch (final IOException e) {
            logger.log(Level.WARNING, "threaded logger died: " + e.getMessage());
        }
    }
}
