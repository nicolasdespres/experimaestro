package sf.net.experimaestro.utils.log;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggerFactory;

import sf.net.experimaestro.utils.io.LoggerPrintStream;

public final class Logger extends org.apache.log4j.Logger {

	static public class Factory implements LoggerFactory {
		public org.apache.log4j.Logger makeNewLoggerInstance(String name) {
			return new Logger(name);
		}

	}

	private static Factory myFactory = new Factory();

	public Logger(String name) {
		super(name);
	}

	public static Level toLevel(String name, Level level) {
		return Level.toLevel(name, Level.INFO);
	}

	public void trace(String format, Object... values) {
		if (repository.isDisabled(Level.TRACE_INT))
			return;

		if (isEnabledFor(Level.TRACE))
			forcedLog(FQCN, Level.TRACE, String.format(format, values), null);
	}

	public void debug(String format, Object... values) {
		if (repository.isDisabled(Level.DEBUG_INT))
			return;
		if (isEnabledFor(Level.DEBUG))
			forcedLog(FQCN, Level.DEBUG, String.format(format, values), null);
	}

	public void info(String format, Object... values) {
		if (repository.isDisabled(Level.INFO_INT))
			return;
		if (isEnabledFor(Level.INFO))
			forcedLog(FQCN, Level.INFO, String.format(format, values), null);
	}

	public void warn(String format, Object... values) {
		if (repository.isDisabled(Level.WARN_INT))
			return;
		if (isEnabledFor(Level.WARN))
			forcedLog(FQCN, Level.WARN, String.format(format, values), null);
	}

	public void error(String format, Object... values) {
		if (repository.isDisabled(Level.ERROR_INT))
			return;

		if (isEnabledFor(Level.ERROR))
			forcedLog(FQCN, Level.ERROR, String.format(format, values), null);
	}

	/**
	 * This method overrides {@link Logger#getLogger} by supplying its own
	 * factory type as a parameter.
	 */
	public static Logger getLogger(String name) {
		return (Logger) org.apache.log4j.Logger.getLogger(name, myFactory);
	}

	/**
	 * Shorthand for <code>getLogger(clazz.getName())</code>.
	 * 
	 * @param clazz
	 *            The name of <code>clazz</code> will be used as the name of the
	 *            logger to retrieve. See {@link #getLogger(String)} for more
	 *            detailed information.
	 */
	static public Logger getLogger(Class<?> clazz) {
		return (Logger) getLogger(clazz.getName());
	}

	public static Logger getLogger() {
		return (Logger) getLogger(new Throwable().getStackTrace()[1]
				.getClassName());
	}

	private static final String FQCN = Logger.class.getName();

	/**
	 * Print an exception with its stack tracke
	 * 
	 * @param l
	 *            The level
	 * @param e
	 *            The exception
	 */
	public void printException(Level l, Throwable e) {
		if (repository.isDisabled(l.getSyslogEquivalent()))
			return;

		if (isEnabledFor(l)) {
			forcedLog(FQCN, Level.ERROR, e.toString(), null);
			LoggerPrintStream out = new LoggerPrintStream(this, l);
			e.printStackTrace(out);
			out.flush();
		}
	}

	public void log(Level level, String format, Object... values) {
		if (repository.isDisabled(level.toInt()))
			return;
		if (isEnabledFor(level))
			forcedLog(FQCN, level, String.format(format, values), null);
	}

}