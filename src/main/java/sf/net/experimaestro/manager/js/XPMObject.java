/*
 *
 *  This file is part of experimaestro.
 *  Copyright (c) 2011 B. Piwowarski <benjamin@bpiwowar.net>
 *
 *  experimaestro is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  experimaestro is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with experimaestro.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package sf.net.experimaestro.manager.js;

import com.sleepycat.je.DatabaseException;
import com.sun.org.apache.xerces.internal.impl.xs.XSLoaderImpl;
import com.sun.org.apache.xerces.internal.xs.XSModel;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.ls.LSInput;
import sf.net.experimaestro.connectors.Connector;
import sf.net.experimaestro.exceptions.ExperimaestroException;
import sf.net.experimaestro.manager.*;
import sf.net.experimaestro.plan.ParseException;
import sf.net.experimaestro.plan.PlanParser;
import sf.net.experimaestro.scheduler.*;
import sf.net.experimaestro.utils.JSUtils;
import sf.net.experimaestro.utils.Output;
import sf.net.experimaestro.utils.XMLUtils;
import sf.net.experimaestro.utils.log.Logger;

import javax.xml.xpath.*;
import java.io.*;
import java.lang.Process;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.lang.String.format;

/**
 * This class contains both utility static methods and functions that can be
 * called from javascript
 * 
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */
/**
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 *
 */
public class XPMObject {

	final static private Logger LOGGER = Logger.getLogger();

	/**
	 * The experiment repository
	 */
	private final Repository repository;

	/**
	 * Our scope (global among javascripts)
	 */
	private final Scriptable scope;

	/**
	 * The context (local)
	 */
	private Context context;

    /**
     * The task scheduler
     */
	private final Scheduler scheduler;

    /**
     * The environment
     */
	private final Map<String, String> environment;

    /**
     * The connector for default inclusion
     */
    Locator currentScript;

	private static ThreadLocal<ArrayList<String>> log = new ThreadLocal<ArrayList<String>>() {
		protected synchronized ArrayList<String> initialValue() {
			return new ArrayList<String>();
		}
	};


    /**
     * Initialise a new XPM object
     * @param currentScript The path to the current script
     * @param cx
     * @param environment
     * @param scope
     * @param repository
     * @param scheduler
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     */
    public XPMObject(Locator currentScript, Context cx, Map<String, String> environment,
			Scriptable scope, Repository repository, Scheduler scheduler)
			throws IllegalAccessException, InstantiationException,
			InvocationTargetException, SecurityException, NoSuchMethodException {
        LOGGER.info("Current script is %s", currentScript);
        this.currentScript = currentScript;
		this.context = cx;
		this.environment = environment;
		this.scope = scope;
		this.repository = repository;
		this.scheduler = scheduler;

        // --- Define functions and classes

		// Define the new classes
		ScriptableObject.defineClass(scope, TaskFactoryJSWrapper.class);
		ScriptableObject.defineClass(scope, TaskJSWrapper.class);
        ScriptableObject.defineClass(scope, JSScheduler.class);

        // Launchers
        ScriptableObject.defineClass(scope, JSOARLauncher.class);

        // ComputationalResources

        // Add functions
		addFunction(scope, "qname", new Class<?>[] { Object.class, String.class });
        addFunction(scope, "includeRepository", null);


        // TODO: would be good to have this at a global level
		//addFunction(scope, "include", new Class<?>[] { String.class });

		// Add this object
		ScriptableObject.defineProperty(scope, "xpm", this, 0);

        // --- Add new objects

		addNewObject(cx, scope, "xp", "Namespace", new Object[] { "xp",
				Manager.EXPERIMAESTRO_NS });
		addNewObject(cx, scope, "scheduler", "Scheduler",
				new Object[] { scheduler });
	}

	static private void addNewObject(Context cx, Scriptable scope,
			final String objectName, final String className,
			final Object[] params) {
		ScriptableObject.defineProperty(scope, objectName,
				cx.newObject(scope, className, params), 0);
	}

	/**
	 * Add a new javascript function to the scope
	 * 
	 * @param scope
	 * @param fname
	 * @param prototype
	 * @throws NoSuchMethodException
	 */
	static private void addFunction(Scriptable scope, final String fname,
			Class<?>[] prototype) throws NoSuchMethodException {
        if (prototype == null)
            prototype = new Class[] {Context.class, Scriptable.class, Object[].class,  Function.class  };
		final FunctionObject f = new FunctionObject(fname,
				XPMObject.class.getMethod("js_" + fname, prototype), scope);
		ScriptableObject.putProperty(scope, fname, f);
	}



    /**
     * Includes a repository
     * @param _connector
     * @param path
     * @return
     */
    public void includeRepository(Object _connector, String path) throws Exception, NoSuchMethodException, InstantiationException, IllegalAccessException {
        // Get the connector
        if (_connector instanceof Wrapper)
            _connector = ((Wrapper) _connector).unwrap();

        Connector connector;
        if (_connector instanceof JSConnector)
            connector =  ((JSConnector)_connector).getConnector();
        else
            connector = (Connector)_connector;

        includeRepository(new Locator(connector, path));
    }

    public void includeRepository(String path) throws Exception, IllegalAccessException, InstantiationException {
      Locator scriptpath = currentScript.resolvePath(path, true);
        LOGGER.debug("Including repository file [%s]", scriptpath);
        includeRepository(scriptpath);
    }

    private void includeRepository(Locator scriptpath) throws Exception {
        // Run the script in a new environment
        final ScriptableObject newScope = context.initStandardObjects();
        final TreeMap<String, String> newEnvironment = new TreeMap<String, String>(environment);

        final XPMObject xpmObject = new XPMObject(scriptpath, context, newEnvironment, newScope, repository, scheduler);

        Context.getCurrentContext().evaluateReader(newScope, new InputStreamReader(scriptpath.getInputStream()), scriptpath.toString(), 1, null);
    }

    static public void js_includeRepository(Context cx, Scriptable thisObj, Object[] args,  Function funObj) throws Exception {
        // Get the XPM object
        XPMObject xpm = (XPMObject) thisObj.get("xpm", thisObj);

        if (args.length == 1)
            xpm.includeRepository((String) FunctionObject.convertArg(cx, thisObj, args[0], FunctionObject.JAVA_STRING_TYPE));
        else if (args.length == 2)
            xpm.includeRepository(args[0], (String) FunctionObject.convertArg(cx, thisObj, args[1], FunctionObject.JAVA_STRING_TYPE));
        else
            throw new IllegalArgumentException("includeRepository expects one or two arguments");
    }



    /**
       * Returns a QName object
       *
       * @param ns
       *            The namespace: can be the URI string, or a javascript
       *            Namespace object
       * @param localName
       *            the localname
       * @return a QName object
       */
	static public Object js_qname(Object ns, String localName) {
        // First unwrap the object
		if (ns instanceof Wrapper)
			ns = ((Wrapper) ns).unwrap();

        // If ns is a javascript Namespace object
		if (ns instanceof ScriptableObject) {
			ScriptableObject scriptableObject = (ScriptableObject) ns;
			if (scriptableObject.getClassName().equals("Namespace")) {
				Object object = scriptableObject.get("uri", null);
				return new QName(object.toString(), localName);
			}
		}

        // If ns is a string
		if (ns instanceof String)
			return new QName((String) ns, localName);

		throw new ExperimaestroException("Not implemented (%s)", ns.getClass());
	}

	/**
	 * Add an experiment
	 * 
	 * @param object
	 * @return
	 */
	public Scriptable addTaskFactory(NativeObject object) {
		JSTaskFactory f = new JSTaskFactory(scope, object, repository);
		repository.addFactory(f);
		return context.newObject(scope, "XPMTaskFactory",
				new Object[] { Context.javaToJS(f, scope) });
	}

	/**
	 * Include a javascript
	 * 
	 * @param path
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public void include(String path) throws Exception,
            IOException {

        Locator file = currentScript.resolvePath(path, true);
		LOGGER.debug("Including file [%s]", file);

        Locator oldWorkingDirectory = currentScript;
		Context.getCurrentContext().evaluateReader(scope, new InputStreamReader(file.getInputStream()), file.toString(), 1, null);

        currentScript = oldWorkingDirectory;
	}



	public static Object get(Scriptable scope, final String name) {
		Object object = scope.get(name, scope);
		if (object == null && object == Undefined.instance)
			object = null;
		else

		if (object instanceof Wrapper)
			object = ((Wrapper) object).unwrap();
		return object;
	}

	/**
	 * Get the information about a given task
	 *
     * @param namespace The namespace
	 * @param id The ID within the namespace
	 * @return
	 */
	public Scriptable getTaskFactory(String namespace, String id) {
		TaskFactory factory = repository.getFactory(new QName(namespace, id));
		LOGGER.info("Creating a new JS task factory %s", factory);
		return context.newObject(scope, "XPMTaskFactory",
				new Object[] { Context.javaToJS(factory, scope) });
	}

	/**
	 * Get the information about a given task
	 * 
	 * @param localPart
	 * @return
	 */
	public Scriptable getTask(String namespace, String localPart) {
		return getTask(new QName(namespace, localPart));
	}

	public Scriptable getTask(QName qname) {
		TaskFactory factory = repository.getFactory(qname);
        if (factory == null)
            throw new ExperimaestroException("Could not find a task with name [%s]", qname);
		LOGGER.info("Creating a new JS task %s", factory);
		return context.newObject(scope, "XPMTask",
				new Object[] { Context.javaToJS(factory.create(), scope) });
	}

	/**
	 * Returns the script path if available
	 */
	public String getScriptPath() {
		return currentScript.getPath();
	}

	/**
	 * Recursive flattening of an array
	 * 
	 * @param array
	 *            The array to flatten
	 * @param list
	 *            A list of strings that will be filled
	 */
	static public void flattenArray(NativeArray array, List<String> list) {
		int length = (int) array.getLength();

		for (int i = 0; i < length; i++) {
			Object el = array.get(i, array);
			if (el instanceof NativeArray) {
				flattenArray((NativeArray) el, list);
			} else
				list.add(toString(el));
		}

	}

	static String toString(Object object) {
		if (object instanceof NativeJavaObject)
			return ((NativeJavaObject) object).unwrap().toString();
		return object.toString();
	}

	public String addData(Connector connector, String identifier) throws DatabaseException {
		LockMode mode = LockMode.SINGLE_WRITER;
		SimpleData resource = new SimpleData(scheduler, connector, identifier, mode, false);
		scheduler.add(resource);
		return identifier;
	}

	/**
	 * Simple evaluation of shell commands
	 * 
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public NativeArray evaluate(Object jsargs) throws Exception,
            InterruptedException {
		final String[] args;
		if (jsargs instanceof NativeArray) {
			NativeArray array = ((NativeArray) jsargs);
			int length = (int) array.getLength();
			args = new String[length];
			for (int i = 0; i < length; i++) {
				Object el = array.get(i, array);
				if (el instanceof NativeJavaObject)
					el = ((NativeJavaObject) el).unwrap();
				LOGGER.debug("arg %d: %s/%s", i, el, el.getClass());
				args[i] = el.toString();
			}
		} else
			throw new RuntimeException(format(
					"Cannot handle an array of type %s", jsargs.getClass()));

		// Run the process and captures the output
        String command = CommandLineTask.getCommandLine(args);
        Process p = currentScript.getConnector().exec(null, command, null, false, null, null);
		BufferedReader input = new BufferedReader(new InputStreamReader(
				p.getInputStream()));

		int len = 0;
		char[] buffer = new char[8192];
		StringBuffer sb = new StringBuffer();
		while ((len = input.read(buffer, 0, buffer.length)) >= 0)
			sb.append(buffer, 0, len);
		input.close();

		int error = p.waitFor();
		return new NativeArray(new Object[] { error, sb.toString() });
	}

    /**
	 * Log a message to be returned to the client
	 */
	public void log(String format, Object... objects) {
		String msg = format(format, objects);
		log.get().add(msg);
		LOGGER.debug(msg);
	}

	/**
	 * Get the log for the current thread
	 * 
	 * @return
	 */
	static public ArrayList<String> getLog() {
		return log.get();
	}

	/**
	 * Get a QName
	 */
	public QName qName(String namespaceURI, String localPart) {
		return new QName(namespaceURI, localPart);
	}

	/**
	 * Get experimaestro namespace
	 */
	public String ns() {
		return Manager.EXPERIMAESTRO_NS;
	}

	// XML Utilities

	public Object domToE4X(Node node) {
		return JSUtils.domToE4X(node, context, scope);
	}

	public String xmlToString(Node node) {
		return XMLUtils.toString(node);
	}

	public static void resetLog() {
		log.set(new ArrayList<String>());
	}

	public File filepath(String filepath, String... names) {
		File file = new File(filepath);
		for (String name : names)
			file = new File(file, name);
		return file;
	}

	public File filepath(File file, String... names) {
		for (String name : names)
			file = new File(file, name);
		return file;
	}

	/** Declare an alternative */
	public void declareAlternative(QName qname) {
		AlternativeType type = new AlternativeType(qname);
		repository.addType(type);
	}

	/**
	 * Add a module
	 */
	public void addModule(Object object) {
		JSModule module = new JSModule(repository, scope, (NativeObject) object);
		LOGGER.debug("Adding module [%s]", module.getId());
		repository.addModule(module);
	}

	/**
	 * Execute an experimental plan
	 * 
	 * @throws ParseException
	 *             If the plan is not readable
	 */
	public Object experiment(QName qname, String planString)
			throws ParseException {
		// Get the task
		TaskFactory taskFactory = repository.getFactory(qname);
		if (taskFactory == null)
			throw new ExperimaestroException("No task factory with id [%s]",
					qname);

		// Parse the plan

		PlanParser planParser = new PlanParser(new StringReader(planString));
		sf.net.experimaestro.plan.Node plans = planParser.plan();
		LOGGER.info("Plan is %s", plans.toString());
		for (Map<String, String> plan : plans) {
			// Run a plan
			LOGGER.info("Running plan: %s",
					Output.toString(" * ", plan.entrySet()));
			Task task = taskFactory.create();
			for (Map.Entry<String, String> kv : plan.entrySet())
				task.setParameter(DotName.parse(kv.getKey()), kv.getValue());
			task.run();
		}
		return null;
	}

	/**
	 * Runs an XPath
	 * 
	 * @param path
	 * @param xml
	 * @return
	 * @throws XPathExpressionException
	 */
	public Object xpath(String path, Object xml)
			throws XPathExpressionException {
		Node dom = JSUtils.toDOM(xml);
		XPath xpath = XPathFactory.newInstance().newXPath();
		xpath.setNamespaceContext(new NSContext(dom));
		XPathFunctionResolver old = xpath.getXPathFunctionResolver();
		xpath.setXPathFunctionResolver(new XPMXPathFunctionResolver(old));

		XPathExpression expression = xpath.compile(path);
		String list = (String) expression.evaluate(
				dom instanceof Document ? ((Document) dom).getDocumentElement()
						: dom, XPathConstants.STRING);
		return list;
	}

	/**
	 * Add an XML Schema declaration
	 * 
	 * @param module
	 * @param path
	 */
	public void addSchema(Object module, final String path) throws IOException {
		LOGGER.info("Loading XSD file [%s], with script path [%s]", path, currentScript.toString());
		Locator file = currentScript.resolvePath(path, true);
		XSLoaderImpl xsLoader = new XSLoaderImpl();
        XSModel xsModel = null;

        xsModel = xsLoader.load(new LSInput() {
            @Override
            public Reader getCharacterStream() {
                return null;
            }

            @Override
            public void setCharacterStream(Reader reader) {
                throw new AssertionError("Should not be called");
            }

            @Override
            public InputStream getByteStream() {
                try {
                    return currentScript.resolvePath(path, true).getInputStream();
                } catch (Exception e) {
                    throw new ExperimaestroException(e);
                }
            }

            @Override
            public void setByteStream(InputStream inputStream) {
                throw new AssertionError("Should not be called");
            }

            @Override
            public String getStringData() {
                return null;
            }

            @Override
            public void setStringData(String s) {
                throw new AssertionError("Should not be called");
            }

            @Override
            public String getSystemId() {
                return null;
            }

            @Override
            public void setSystemId(String s) {
                throw new AssertionError("Should not be called");
            }

            @Override
            public String getPublicId() {
                return null;
            }

            @Override
            public void setPublicId(String s) {
                throw new AssertionError("Should not be called");
            }

            @Override
            public String getBaseURI() {
                return null;
            }

            @Override
            public void setBaseURI(String s) {
                throw new AssertionError("Should not be called");
            }

            @Override
            public String getEncoding() {
                return null;
            }

            @Override
            public void setEncoding(String s) {
                throw new AssertionError("Should not be called");
            }

            @Override
            public boolean getCertifiedText() {
                return false;
            }

            @Override
            public void setCertifiedText(boolean b) {
                throw new AssertionError("Should not be called");
            }
        });

        // Add to the repository
		repository.addSchema(JSModule.getModule(repository, module), xsModel);
	}

}
