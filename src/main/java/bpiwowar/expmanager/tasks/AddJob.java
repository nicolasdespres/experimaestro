package bpiwowar.expmanager.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeSet;

import org.apache.xmlrpc.client.XmlRpcClient;

import bpiwowar.argparser.Argument;
import bpiwowar.argparser.ArgumentClass;
import bpiwowar.argparser.ArgumentRegexp;
import bpiwowar.experiments.AbstractTask;
import bpiwowar.experiments.TaskDescription;
import bpiwowar.expmanager.tasks.config.XMLRPCClientConfig;
import bpiwowar.utils.GenericHelper;

/**
 * Add a job to the list of jobs to be executed - if the job has already been
 * executed, this only register the job
 * 
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */
@TaskDescription(name = "add-job", project = { "xpmanager" })
public class AddJob extends AbstractTask {
	@ArgumentClass(prefix = "xmlrpc", help = "Configuration file for the XML RPC call", required = true)
	XMLRPCClientConfig xmlrpcClientConfig;

	@Argument(name = "basename", help = "Basename for the resource", required = true)
	File basename;

	/**
	 * Parameters associated to this
	 */
	Map<String, String> parameters = GenericHelper.newTreeMap();

	@Argument(name = "param", required = false)
	@ArgumentRegexp("^([^=])=(.*)$")
	void addParameter(String name, String value) {
		parameters.put(name, value);
	}

	@Argument(name = "prority", help = "Priority (higher for more urgent tasks, 0 by default)")
	int priority = 0;

	@Argument(name = "depends", help = "List of jobs on which we depend")
	TreeSet<File> depends = GenericHelper.newTreeSet();

	@Argument(name = "lock-write", help = "Lock for read/write", required = false)
	TreeSet<File> writeLocks = GenericHelper.newTreeSet();

	@Argument(name = "lock-read", help = "Lock for read", required = false)
	TreeSet<File> readLocks = GenericHelper.newTreeSet();

	/**
	 * Remaining command line arguments
	 */
	private String[] command;

	@Argument(name = "data", required = false, help = "Produced data")
	void addData(File file) {

	}

	@Override
	public String[] processTrailingArguments(String[] args) throws Exception {
		this.command = args;
		return null;
	}

	public int execute() throws Throwable {
		// Read the arguments
		if (command == null || command.length == 0)
			throw new RuntimeException("There should be a command to run");

		// Contact the XML RPC server
		XmlRpcClient client = xmlrpcClientConfig.getClient();

		// Let's go
		ArrayList<Object> params = GenericHelper.newArrayList();
		params.add(basename.getAbsolutePath().toString());
		params.add(priority);
		params.add(command);
		params.add(System.getenv());
		params.add(System.getProperty("user.dir"));
		params.add(getFullPathArray(depends));
		params.add(getFullPathArray(readLocks));
		params.add(getFullPathArray(writeLocks));

		Boolean returns = (Boolean) client.execute("TaskManager.runCommand",
				params.toArray());

		return returns ? 0 : 1;
	}

	private Object getFullPathArray(TreeSet<File> set) {
		String[] array = new String[set.size()];
		int i = 0;
		for (File s : set)
			array[i++] = s.getAbsolutePath();
		return array;
	}

}