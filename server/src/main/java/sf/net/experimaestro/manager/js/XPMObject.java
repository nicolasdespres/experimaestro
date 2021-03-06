package sf.net.experimaestro.manager.js;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.log4j.Level;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import sf.net.experimaestro.connectors.*;
import sf.net.experimaestro.exceptions.*;
import sf.net.experimaestro.manager.*;
import sf.net.experimaestro.manager.experiments.Experiment;
import sf.net.experimaestro.manager.experiments.TaskReference;
import sf.net.experimaestro.manager.java.JavaTasksIntrospection;
import sf.net.experimaestro.manager.js.object.JSCommand;
import sf.net.experimaestro.manager.json.*;
import sf.net.experimaestro.manager.scripting.*;
import sf.net.experimaestro.scheduler.*;
import sf.net.experimaestro.server.TasksServlet;
import sf.net.experimaestro.utils.Cleaner;
import sf.net.experimaestro.utils.JSUtils;
import sf.net.experimaestro.utils.Output;
import sf.net.experimaestro.utils.XMLUtils;
import sf.net.experimaestro.utils.io.LoggerPrintWriter;
import sf.net.experimaestro.utils.log.Logger;

import javax.persistence.EntityManager;
import javax.xml.xpath.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static java.lang.String.format;
import static sf.net.experimaestro.utils.JSUtils.unwrap;

/*
 * This file is part of experimaestro.
 * Copyright (c) 2014 B. Piwowarski <benjamin@bpiwowar.net>
 *
 * experimaestro is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * experimaestro is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with experimaestro.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This class contains both utility static methods and functions that can be
 * called from javascript
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */

/**
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */
public class XPMObject {

    /**
     * The filename used to the store the signature in generated directory names
     */
    public static final String OLD_XPM_SIGNATURE = ".xpm-signature";
    public static final String XPM_SIGNATURE = "signature.xpm";

    public static final String COMMAND_LINE_JOB_HELP = "Schedule a command line job.<br>The options are <dl>" +
            "<dt>launcher</dt><dd></dd>" +
            "<dt>stdin</dt><dd></dd>" +
            "<dt>stdout</dt><dd></dd>" +
            "<dt>lock</dt><dd>An array of couples (resource, lock type). The lock depends on the resource" +
            "at hand, but are generally READ, WRITE, EXCLUSIVE.</dd>" +
            "";

    final static ThreadLocal<XPMObject> threadXPM = new ThreadLocal<>();
    final static private Logger LOGGER = Logger.getLogger();
    static HashSet<String> COMMAND_LINE_OPTIONS = new HashSet<>(
            ImmutableSet.of("stdin", "stdout", "lock", "connector", "launcher"));

    /**
     * Our scope (global among javascripts)
     */
    final Scriptable scope;

    /**
     * The environment
     */
    private final Map<String, String> environment;

    /**
     * The logger
     */
    private Logger taskLogger;

    /**
     * The connector for default inclusion
     */
    Path currentScriptPath;

    /**
     * Properties set by the script that will be returned
     */
    Map<String, Object> properties = new HashMap<>();

    /**
     * List of submitted jobs (so that we don't submit them twice with the same script
     * by default)
     */
    Map<String, Resource> submittedJobs = new HashMap<>();

    /**
     * Simulate flags: jobs will not be submitted (but commands will be evaluated)
     */
    boolean _simulate;

    /**
     * TaskReference context for this XPM object
     */
    private ScriptContext taskContext;

    /**
     * The context (local)
     */
    private Context context;
    /**
     * Root logger
     */
    private Logger rootLogger;

    /**
     * The current connector
     */
    private Connector connector;

    /**
     * The script context associated to this XPM object
     */
    private ScriptContext scriptContext;

    /**
     * Initialise a new XPM object
     *
     * @param scriptContext     The script execution context
     * @param currentScriptPath The xpath to the current script
     * @param context           The JS context
     * @param environment       The environment variables
     * @param scope             The JS scope for execution
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     */
    XPMObject(ScriptContext scriptContext,
              Connector connector,
              Path currentScriptPath,
              Context context,
              Map<String, String> environment,
              Scriptable scope)
            throws IllegalAccessException, InstantiationException,
            InvocationTargetException, SecurityException, NoSuchMethodException {
        LOGGER.debug("Current script is %s", currentScriptPath);
        this.scriptContext = scriptContext;
        this.currentScriptPath = currentScriptPath;
        this.connector = connector;
        this.context = context;
        this.environment = environment;
        this.scope = scope;
        this.rootLogger = scriptContext.getLogger(currentScriptPath.getFileName().toString());
        this.taskLogger = LOGGER;

        context.setWrapFactory(JSBaseObject.XPMWrapFactory.INSTANCE);

        // --- Add new objects

        // Add functions from our Function object
        Map<String, ArrayList<Method>> functionsMap = ClassDescription.analyzeClass(XPMFunctions.class).getMethods();
        final XPMFunctions xpmFunctions = new XPMFunctions(this);
        for (Map.Entry<String, ArrayList<Method>> entry : functionsMap.entrySet()) {
            MethodFunction function = new MethodFunction(entry.getKey());
            function.add(xpmFunctions, entry.getValue());
            ScriptableObject.putProperty(scope, entry.getKey(), new JavaScriptFunction(function));
        }

        // Add functions from our the common scripting base
        Map<String, ArrayList<Method>> scriptingFunctionsMap = ClassDescription.analyzeClass(Functions.class).getMethods();
        final Functions scriptingFunctions = new Functions();
        for (Map.Entry<String, ArrayList<Method>> entry : scriptingFunctionsMap.entrySet()) {
            MethodFunction function = new MethodFunction(entry.getKey());
            function.add(scriptingFunctions, entry.getValue());
            ScriptableObject.putProperty(scope, entry.getKey(), new JavaScriptFunction(function));
        }

        // tasks object
        XPMContext.addNewObject(context, scope, "tasks", "Tasks", new Object[]{});

        // logger
        XPMContext.addNewObject(context, scope, "logger", ClassDescription.getClassName(JSLogger.class), new Object[]{this, "xpm"});

        // xpm object
        XPMContext.addNewObject(context, scope, "xpm", "XPM", new Object[]{});

        ((JSXPM) get(scope, "xpm")).set(this);

    }

    static XPMObject getXPMObject(Scriptable scope) {
        while (scope.getParentScope() != null)
            scope = scope.getParentScope();
        return ((JSXPM) scope.get("xpm", scope)).xpm;
    }

    static XPMObject include(Context cx, Scriptable thisObj, Object[] args,
                             Function funObj, boolean repositoryMode) throws Exception {
        XPMObject xpm = getXPM(thisObj);

        if (args.length == 1)
            // Use the current connector
            return xpm.include(Context.toString(args[0]), repositoryMode);
        else if (args.length == 2)
            // Use the supplied connector
            return xpm.include(args[0], Context.toString(args[1]), repositoryMode);
        else
            throw new IllegalArgumentException("includeRepository expects one or two arguments");

    }

    /**
     * Retrievs the XPMObject from the JavaScript context
     */
    public static XPMObject getXPM(Scriptable thisObj) {
        if (thisObj instanceof NativeCall) {
            // XPM cannot be found if the scope is a native call object
            thisObj = thisObj.getParentScope();
        }
        return ((JSXPM) thisObj.get("xpm", thisObj)).xpm;
    }

    /**
     * Javascript constructor calling {@linkplain #include(String, boolean)}
     */
    static public Map<String, Object> js_include_repository(Context cx, Scriptable thisObj, Object[] args,
                                                            Function funObj) throws Exception {

        final XPMObject xpmObject = include(cx, thisObj, args, funObj, true);
        return xpmObject.properties;
    }

    /**
     * Javascript constructor calling {@linkplain #include(String, boolean)}
     */
    static public void js_include(Context cx, Scriptable thisObj, Object[] args,
                                  Function funObj) throws Exception {

        include(cx, thisObj, args, funObj, false);
    }

    /**
     * Returns a JSPath that corresponds to the path. This can
     * be used when building command lines containing path to resources
     * or executables
     *
     * @return A {@JSPath}
     */
    @Help("Returns a Path corresponding to the path")
    static public Object js_path(Context cx, Scriptable thisObj, Object[] args, Function funObj) throws FileSystemException, URISyntaxException {
        if (args.length != 1)
            throw new IllegalArgumentException("path() needs one argument");

        XPMObject xpm = getXPM(thisObj);

        if (args[0] instanceof JSPath)
            return args[0];

        final Object o = unwrap(args[0]);

        if (o instanceof JSPath)
            return o;

        if (o instanceof Path)
            return xpm.newObject(JSPath.class, o);

        if (o instanceof String) {
            final Path path = Paths.get(new URI(o.toString()));
            if (!path.isAbsolute()) {
                return xpm.newObject(JSPath.class, xpm.currentScriptPath.getParent().resolve(path));
            }
            return path;
        }

        throw new XPMRuntimeException("Cannot convert type [%s] to a file xpath", o.getClass().toString());
    }

    @Help(
            value = "Format a string",
            arguments = @JSArguments({
                    @Argument(name = "format", type = "String", help = "The string used to format"),
                    @Argument(name = "arguments...", type = "Object", help = "A list of objects")
            })
    )
    static public String js_format(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (args.length == 0)
            return "";

        Object fargs[] = new Object[args.length - 1];
        for (int i = 1; i < args.length; i++)
            fargs[i - 1] = unwrap(args[i]);
        String format = JSUtils.toString(args[0]);
        return String.format(format, fargs);
    }

    /**
     * Returns an XML element that corresponds to the wrapped value
     *
     * @return An XML element
     */
    static public Object js_value(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        if (args.length != 1)
            throw new IllegalArgumentException("value() needs one argument");
        final Object object = unwrap(args[0]);

        Document doc = XMLUtils.newDocument();
        XPMObject xpm = getXPM(thisObj);
        return JSUtils.domToE4X(doc.createElement(JSUtils.toString(object)), xpm.context, xpm.scope);

    }

    /**
     * Sets the current workdir
     */
    static public void js_set_workdir(Context cx, Scriptable thisObj, Object[] args, Function funObj) throws FileSystemException, URISyntaxException {
        XPMObject xpm = getXPM(thisObj);
        xpm.getScriptContext().setWorkingDirectory(((JSPath) js_path(cx, thisObj, args, funObj)).getPath());
    }

    /**
     * Returns the current script location
     */
    static public JSPath js_script_file(Context cx, Scriptable thisObj, Object[] args, Function funObj)
            throws FileSystemException {
        if (args.length != 0)
            throw new IllegalArgumentException("script_file() has no argument");

        XPMObject xpm = getXPM(thisObj);

        return new JSPath(xpm.currentScriptPath);
    }

    @Help(value = "Returns a file relative to the current connector")
    public static Scriptable js_file(Context cx, Scriptable thisObj, Object[] args, Function funObj) throws FileSystemException {
        XPMObject xpm = getXPM(thisObj);
        if (args.length != 1)
            throw new IllegalArgumentException("file() takes only one argument");
        final String arg = JSUtils.toString(args[0]);
        return xpm.context.newObject(xpm.scope, JSPath.JSCLASSNAME,
                new Object[]{xpm.currentScriptPath.getParent().resolve(arg)});
    }

    @Help(value = "Unwrap an annotated XML value into a native JS object")
    public static Object js_unwrap(Object object) {
        return object.toString();
    }

    /**
     * Returns a QName object
     *
     * @param ns        The namespace: can be the URI string, or a javascript
     *                  Namespace object
     * @param localName the localname
     * @return a QName object
     */
    static public Object js_qname(Object ns, String localName) {
        // First unwrapToObject the object
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

        throw new XPMRuntimeException("Not implemented (%s)", ns.getClass());
    }

    public static Object get(Scriptable scope, final String name) {
        Object object = scope.get(name, scope);
        if (object != null && object == Undefined.instance)
            object = null;
        else if (object instanceof Wrapper)
            object = ((Wrapper) object).unwrap();
        return object;
    }

    /**
     * Runs an XPath
     *
     * @param path
     * @param xml
     * @return
     * @throws javax.xml.xpath.XPathExpressionException
     */
    static public Object js_xpath(String path, Object xml)
            throws XPathExpressionException {
        Node dom = (Node) JSUtils.toDOM(null, xml);
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
     * Recursive flattening of an array
     *
     * @param array The array to flatten
     * @param list  A list of strings that will be filled
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

    private static Path getPath(Connector connector, Object stdout) throws IOException {
        if (stdout instanceof String || stdout instanceof ConsString)
            return connector.getMainConnector().resolveFile(stdout.toString());

        if (stdout instanceof JSPath)
            return connector.getMainConnector().resolveFile(stdout.toString());

        if (stdout instanceof Path)
            return (Path) stdout;

        throw new XPMRuntimeException("Unsupported stdout type [%s]", stdout.getClass());
    }

    public static XPMObject getThreadXPM() {
        return threadXPM.get();
    }

    /**
     * Clone properties from this XPM instance
     */
    private XPMObject clone(Path scriptpath, Scriptable scriptScope, TreeMap<String, String> newEnvironment) throws IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        try(final ScriptContext copy = scriptContext.copy()) {
            final XPMObject clone = new XPMObject(copy, connector, scriptpath, context, newEnvironment, scriptScope);
            clone.submittedJobs = this.submittedJobs;
            clone._simulate = _simulate;
            return clone;
        }
    }

    public Logger getRootLogger() {
        return rootLogger;
    }

    private boolean simulate() {
        return _simulate || (taskContext != null && taskContext.simulate());
    }

    /**
     * Includes a repository
     *
     * @param _connector
     * @param path
     * @param repositoryMode True if we include a repository
     * @return
     */
    public XPMObject include(Object _connector, String path, boolean repositoryMode)
            throws Exception {
        // Get the connector
        if (_connector instanceof Wrapper)
            _connector = ((Wrapper) _connector).unwrap();

        Connector connector;
        if (_connector instanceof JSConnector)
            connector = ((JSConnector) _connector).getConnector();
        else
            connector = (Connector) _connector;

        return include(connector.resolve(path), repositoryMode);
    }

    /**
     * Includes a repository
     *
     * @param path           The xpath, absolute or relative to the current evaluated script
     * @param repositoryMode If true, creates a new javascript scope that will be independant of this one
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public XPMObject include(String path, boolean repositoryMode) throws Exception {
        Path scriptpath = currentScriptPath.getParent().resolve(path);
        LOGGER.debug("Including repository file [%s]", scriptpath);
        return include(scriptpath, repositoryMode);
    }

    /**
     * Central method called for any script inclusion
     *
     * @param scriptPath     The path to the script
     * @param repositoryMode If true, runs in a separate environement
     * @throws Exception if something goes wrong
     */

    private XPMObject include(Path scriptPath, boolean repositoryMode) throws Exception {

        Path oldResourceLocator = currentScriptPath;
        try (InputStream inputStream = Files.newInputStream(scriptPath)) {
            Scriptable scriptScope = scope;
            XPMObject xpmObject = this;
            currentScriptPath = scriptPath;

            if (repositoryMode) {
                // Run the script in a new environment
                scriptScope = XPMContext.newScope();
                final TreeMap<String, String> newEnvironment = new TreeMap<>(environment);
                xpmObject = clone(scriptPath, scriptScope, newEnvironment);
                threadXPM.set(xpmObject);
            }

            // Avoid adding the protocol if this is a local file
            final String sourceName = scriptPath.toString();


            Context.getCurrentContext().evaluateReader(scriptScope, new InputStreamReader(inputStream), sourceName, 1, null);

            return xpmObject;
        } catch (FileNotFoundException e) {
            throw new XPMRhinoException("File not found: %s", scriptPath);
        } finally {
            threadXPM.set(this);
            currentScriptPath = oldResourceLocator;
        }

    }

    /**
     * Creates a new JavaScript object
     */
    Scriptable newObject(Class<?> aClass, Object... arguments) {
        return context.newObject(scope, ClassDescription.getClassName(aClass), arguments);
    }

    /**
     * Get the information about a given task
     *
     * @param namespace The namespace
     * @param id        The ID within the namespace
     * @return
     */
    public Scriptable getTaskFactory(String namespace, String id) {
        TaskFactory factory = scriptContext.getFactory(new QName(namespace, id));
        LOGGER.debug("Creating a new JS task factory %s", factory.getId());
        return context.newObject(scope, "TaskFactory",
                new Object[]{Context.javaToJS(factory, scope)});
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
        TaskFactory factory = scriptContext.getFactory(qname);
        if (factory == null)
            throw new XPMRuntimeException("Could not find a task with name [%s]", qname);
        LOGGER.info("Creating a new JS task [%s]", factory.getId());
        return new JSTaskWrapper(factory.create(), this);
    }

    /**
     * Simple evaluation of shell commands (does not createSSHAgentIdentityRepository a job)
     *
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public String evaluate(Object jsargs, NativeObject options) throws Exception {
        Command command = JSCommand.getCommand(jsargs);

        // Get the connector
        final Connector commandConnector;
        if (options != null && options.has("connector", options)) {
            commandConnector = ((JSConnector) options.get("connector", options)).getConnector();
        } else {
            commandConnector = this.connector;
        }

        // Run the process and captures the output
        AbstractProcessBuilder builder = commandConnector.getMainConnector().processBuilder();

        try (CommandContext commandEnv = new CommandContext.Temporary(commandConnector.getMainConnector())) {
            // Transform the list
            builder.command(Lists.newArrayList(Iterables.transform(command.list(), argument -> {
                try {
                    return argument.toString(commandEnv);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })));

            if (options != null && options.has("stdout", options)) {
                Path stdout = getPath(commandConnector, unwrap(options.get("stdout", options)));
                builder.redirectOutput(AbstractCommandBuilder.Redirect.to(stdout));
            } else {
                builder.redirectOutput(AbstractCommandBuilder.Redirect.PIPE);
            }

            builder.redirectError(AbstractCommandBuilder.Redirect.PIPE);


            builder.detach(false);
            builder.environment(environment);

            XPMProcess p = builder.start();

            new Thread("stderr") {
                BufferedReader errorStream = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                @Override
                public void run() {
                    errorStream.lines().forEach(line -> getRootLogger().info(line));
                }
            }.start();


            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            int len = 0;
            char[] buffer = new char[8192];
            StringBuilder sb = new StringBuilder();
            while ((len = input.read(buffer, 0, buffer.length)) >= 0) {
                sb.append(buffer, 0, len);
            }
            input.close();

            int error = p.waitFor();
            if (error != 0) {
                throw new XPMRhinoException("Command returned an error code %d", error);
            }
            return sb.toString();
        }
    }

    /**
     * Log a message to be returned to the client
     */
    public void log(String format, Object... objects) {
        rootLogger.info(format, objects);
    }

    // XML Utilities

    /**
     * Log a message to be returned to the client
     */
    public void warning(String format, Object... objects) {
        RhinoException rhinoException = new XPMRhinoException();

        rootLogger.warn(String.format(format, objects) + " in " + rhinoException.getScriptStack()[0]);
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

    public Object domToE4X(Node node) {
        return JSUtils.domToE4X(node, context, scope);
    }

    public String xmlToString(Node node) {
        return XMLUtils.toString(node);
    }

    /**
     * Creates a new command line job
     *
     * @param path     The identifier for this job
     * @param commands The command line(s)
     * @param options  The options
     * @return
     * @throws Exception
     */
    public Resource commandlineJob(Object path, Commands commands, NativeObject options) throws Exception {
        try (Transaction transaction = Transaction.create()) {
            EntityManager em = transaction.em();

            Job job = null;
            // --- XPMProcess arguments: convert the javascript array into a Java array
            // of String
            LOGGER.debug("Adding command line job");

            // --- Create the task


            final Connector connector;

            if (options != null && options.has("connector", options)) {
                connector = ((JSConnector) options.get("connector", options)).getConnector();
            } else {
                connector = this.connector;
            }

            // Resolve the path for the given connector
            if (!(path instanceof Path)) {
                path = connector.getMainConnector().resolve(path.toString());
            }

            job = new Job(connector, (Path) path);
            CommandLineTask task = new CommandLineTask(commands);
            if (submittedJobs.containsKey(path)) {
                getRootLogger().info("Not submitting %s [duplicate]", path);
                if (simulate()) {
                    return job;
                }

                return Resource.getByLocator(em, connector.resolve((Path) path));
            }


            // --- Environment
            task.environment = new TreeMap<>(environment);
            ArrayList<Dependency> dependencies = new ArrayList<>();

            // --- Set defaults
            if (scriptContext.getDefaultLauncher() != null) {
                task.setLauncher(scriptContext.getDefaultLauncher());
            }


            // --- Options
            if (options != null) {

                final ArrayList unmatched = new ArrayList(Sets.difference(options.keySet(), COMMAND_LINE_OPTIONS));
                if (!unmatched.isEmpty()) {
                    throw new IllegalArgumentException(format("Some options are not allowed: %s",
                            Output.toString(", ", unmatched)));
                }


                // --- XPMProcess launcher
                if (options.has("launcher", options)) {
                    final Object launcher = options.get("launcher", options);
                    if (launcher != null && !(launcher instanceof UniqueTag))
                        task.setLauncher((Launcher) ((Wrapper) launcher).unwrap());

                }

                // --- Redirect standard output
                if (options.has("stdin", options)) {
                    final Object stdin = unwrap(options.get("stdin", options));
                    if (stdin instanceof String || stdin instanceof ConsString) {
                        task.setInput(stdin.toString());
                    } else if (stdin instanceof Path) {
                        task.setInput((Path) stdin);
                    } else
                        throw new XPMRuntimeException("Unsupported stdin type [%s]", stdin.getClass());
                }

                // --- Redirect standard output
                if (options.has("stdout", options)) {
                    Path fileObject = getPath(connector, unwrap(options.get("stdout", options)));
                    task.setOutput(fileObject);
                }

                // --- Redirect standard error
                if (options.has("stderr", options)) {
                    Path fileObject = getPath(connector, unwrap(options.get("stderr", options)));
                    task.setError(fileObject);
                }


                // --- Resources to lock
                if (options.has("lock", options)) {
                    List locks = (List) options.get("lock", options);
                    for (int i = locks.size(); --i >= 0; ) {
                        Object lock_i = JSUtils.unwrap(locks.get(i));
                        Dependency dependency = null;

                        if (lock_i instanceof Dependency) {
                            dependency = (Dependency) lock_i;
                        } else if (lock_i instanceof NativeArray) {
                            NativeArray array = (NativeArray) lock_i;
                            if (array.getLength() != 2)
                                throw new XPMRhinoException(new IllegalArgumentException("Wrong number of arguments for lock"));

                            final Object depObject = JSUtils.unwrap(array.get(0, array));
                            Resource resource = null;
                            if (depObject instanceof Resource) {
                                resource = (Resource) depObject;
                            } else {
                                final String rsrcPath = Context.toString(depObject);
                                resource = Resource.getByLocator(em, rsrcPath);
                                if (resource == null)
                                    if (simulate()) {
                                        if (!submittedJobs.containsKey(rsrcPath))
                                            LOGGER.error("The dependency [%s] cannot be found", rsrcPath);
                                    } else {
                                        throw new XPMRuntimeException("Resource [%s] was not found", rsrcPath);
                                    }
                            }

                            final Object lockType = array.get(1, array);
                            LOGGER.debug("Adding dependency on [%s] of type [%s]", resource, lockType);

                            if (!simulate()) {
                                dependency = resource.createDependency(lockType);
                            }
                        } else {
                            throw new XPMRuntimeException("Element %d for option 'lock' is not a dependency but %s",
                                    i, lock_i.getClass());
                        }

                        if (!simulate()) {
                            dependencies.add(dependency);
                        }
                    }

                }


            }


            job.setState(ResourceState.WAITING);
            job.setJobRunner(task);
            if (simulate()) {
                PrintWriter pw = new LoggerPrintWriter(getRootLogger(), Level.INFO);
                pw.format("[SIMULATE] Starting job: %s%n", task.toString());
                pw.format("Command: %s%n", task.getCommands().toString());
                pw.format("Locator: %s", path.toString());
                pw.flush();
            } else {
                // Prepare
                if (taskContext != null) {
                    taskContext.prepare(job);
                }

                // Add dependencies
                dependencies.forEach(job::addDependency);

                // Register within an experimentId
                if (getScriptContext().getExperimentId() != null) {
                    TaskReference reference = taskContext.getTaskReference();
                    reference.add(job);
                    em.persist(reference);
                }

                final Resource old = Resource.getByLocator(transaction.em(), job.getLocator());

                // Replace old if necessary
                if (old != null) {
                    if (!old.canBeReplaced()) {
                        taskLogger.log(old.getState() == ResourceState.DONE ? Level.DEBUG : Level.INFO,
                                "Cannot overwrite task %s [%d]", old.getLocator(), old.getId());
                        return old;
                    } else {
                        taskLogger.info("Replacing resource %s [%d]", old.getLocator(), old.getId());
                        old.lock(transaction, true);
                        em.refresh(old);
                        old.replaceBy(job);
                        job = (Job) old;
                    }
                }

                // Store in scheduler
                job.save(transaction);


                transaction.commit();
            }

            this.submittedJobs.put(job.getLocator().toString(), job);

            return job;
        }

    }

    public void register(Closeable closeable) {
        cleaner().register(closeable);
    }

    public void unregister(AutoCloseable autoCloseable) {
        cleaner().unregister(autoCloseable);
    }

    final private Cleaner cleaner() {
        return getScriptContext().getCleaner();
    }

    Repository getRepository() {
        return scriptContext.getRepository();
    }

    public Scheduler getScheduler() {
        return scriptContext.getScheduler();
    }

    public void setPath(Path locator) {
        this.currentScriptPath = locator;
    }

    public void setTaskContext(ScriptContext taskContext) {
        this.taskContext = taskContext;
        this.taskLogger = taskContext != null ? taskContext.getLogger("XPM") : LOGGER;
    }

    /**
     * Creates a unique (up to the collision probability) ID based on the hash
     *
     * @param basedir    The base directory
     * @param prefix     The prefix for the directory
     * @param id         The task ID or any other QName
     * @param jsonValues the JSON object from which the hash is computed
     * @param directory  True if the path should be a directory
     * @return A unique directory
     */
    public JSPath uniquePath(Scriptable scope, Path basedir, String prefix, QName id, Object jsonValues, boolean directory) throws IOException, NoSuchAlgorithmException {
        if (basedir == null) {
            if ((basedir = getScriptContext().getWorkingDirectory()) == null) {
                throw new XPMRuntimeException("Working directory was not set before unique_directory() is called");
            }
        }
        final Json json = JSUtils.toJSON(scope, jsonValues);
        return new JSPath(Manager.uniquePath(basedir, prefix, id, json, directory));
    }

    public Connector getConnector() {
        return connector;
    }

    public ScriptContext getScriptContext() {
        return ScriptContext.threadContext();
    }


    // --- Javascript methods

    static public class JSXPM extends JSBaseObject {
        XPMObject xpm;

        @Expose
        public JSXPM() {
        }

        static public void log(Level level, Context cx, Scriptable thisObj, Object[] args, Function funObj) {
            if (args.length < 1)
                throw new XPMRuntimeException("There should be at least one argument for log()");

            String format = Context.toString(args[0]);
            Object[] objects = new Object[args.length - 1];
            for (int i = 1; i < args.length; i++)
                objects[i - 1] = unwrap(args[i]);

            ((JSXPM) thisObj).xpm.log(format, objects);
        }

        protected void set(XPMObject xpm) {
            this.xpm = xpm;
        }

        @Override
        public String getClassName() {
            return "XPM";
        }

        @Expose()
        @Help("Retrieve (or creates) a token resource with a given xpath")
        static public TokenResource token_resource(
                @Argument(name = "path", help = "The path of the resource") String path
        ) throws ExperimaestroCannotOverwrite {
            return Transaction.evaluate((em, t) -> {
                final Resource resource = Resource.getByLocator(em, path);
                final TokenResource tokenResource;
                if (resource == null) {
                    tokenResource = new TokenResource(path, 0);
                    tokenResource.save(t);
                } else {
                    if (!(resource instanceof TokenResource))
                        throw new AssertionError(String.format("Resource %s exists and is not a token", path));
                    tokenResource = (TokenResource) resource;
                }

                return tokenResource;
            });
        }

        @Expose("set_property")
        public void setProperty(String name, Object object) {
            final Object x = unwrap(object);
            xpm.properties.put(name, object);
        }

        @Expose("set_default_lock")
        @Help("Adds a new resource to lock for all jobs to be started")
        public void setDefaultLock(
                @Argument(name = "resource", help = "The resource to be locked")
                Resource resource,
                @Argument(name = "parameters", help = "The parameters to be given at lock time")
                Object parameters) {
            xpm.scriptContext.addDefaultLock(resource, parameters);
        }


        @Expose("logger")
        public Scriptable getLogger(String name) {
            return xpm.newObject(JSLogger.class, xpm, name);
        }


        @Expose("log_level")
        @Help(value = "Sets the logger debug level")
        public void setLogLevel(
                @Argument(name = "name") String name,
                @Argument(name = "level") String level
        ) {
            xpm().scriptContext.getLogger(name).setLevel(Level.toLevel(level));
        }


        @Expose("get_script_path")
        public String getScriptPath() {
            return xpm.currentScriptPath.toString();
        }

        @Expose("get_script_file")
        public JSPath getScriptFile() throws FileSystemException {
            return new JSPath(xpm.currentScriptPath);
        }

        /**
         * Add a module
         */
        @Expose("add_module")
        public JSModule addModule(Object object) {
            JSModule module = new JSModule(xpm, xpm.getRepository(), xpm.scope, (NativeObject) object);
            LOGGER.debug("Adding module [%s]", module.module.getId());
            xpm.getRepository().addModule(module.module);
            return module;
        }

        /**
         * Add an experimentId
         *
         * @param object
         * @return
         */
        @Expose("add_task_factory")
        public Scriptable add_task_factory(NativeObject object) throws ValueMismatchException {
            JSTaskFactory factory = new JSTaskFactory(xpm.scope, object, xpm.getRepository());
            xpm.getRepository().addFactory(factory.factory);
            return xpm.context.newObject(xpm.scope, "TaskFactory",
                    new Object[]{factory});
        }

        @Expose("get_task")
        public Scriptable getTask(QName name) {
            return xpm.getTask(name);
        }

        @Expose("get_task")
        public Scriptable getTask(
                String namespaceURI,
                String localName) {
            return xpm.getTask(namespaceURI, localName);
        }


        @Expose(value = "evaluate", optional = 1)
        public String evaluate(
                NativeArray command,
                NativeObject options
        ) throws Exception {
            return xpm.evaluate(command, options);
        }

        @Expose("file")
        @Help(value = "Returns a file relative to the current connector")
        public Scriptable file(@Argument(name = "filepath") String filepath) throws FileSystemException {
            return xpm.context.newObject(xpm.scope, JSPath.JSCLASSNAME,
                    new Object[]{xpm, xpm.currentScriptPath.resolve(filepath)});
        }

        @Expose
        public Scriptable file(@Argument(name = "file") JSPath file) throws FileSystemException {
            return file;
        }


        @Expose(value = "command_line_job", optional = 1)
        @Help(value = COMMAND_LINE_JOB_HELP)
        public Resource commandlineJob(@Argument(name = "jobId") Object path,
                                         @Argument(type = "Array", name = "command") NativeArray jsargs,
                                         @Argument(type = "Map", name = "options") NativeObject jsoptions) throws Exception {
            Commands commands = new Commands(JSCommand.getCommand(jsargs));
            return xpm.commandlineJob(path, commands, jsoptions);
        }

        @Expose(value = "command_line_job", optional = 1)
        @Help(value = COMMAND_LINE_JOB_HELP)
        public Resource commandlineJob(@Argument(name = "jobId") Object path,
                                         @Argument(type = "Array", name = "command") AbstractCommand command,
                                         @Argument(type = "Map", name = "options") NativeObject jsoptions) throws Exception {
            Commands commands = new Commands(command);
            return xpm.commandlineJob(path, commands, jsoptions);
        }

        @Expose(value = "command_line_job", optional = 1)
        @Help(value = COMMAND_LINE_JOB_HELP)
        public Resource commandlineJob(@Argument(name = "jobId") Object jobId,
                                         Commands commands,
                                         @Argument(type = "Map", name = "options") NativeObject jsoptions) throws Exception {
            return xpm.commandlineJob(jobId, commands, jsoptions);
        }

        @Expose(value = "command_line_job", optional = 1)
        @Help(value = COMMAND_LINE_JOB_HELP)
        public Resource commandlineJob(
                JsonObject json,
                @Argument(name = "jobId") Object jobId,
                Object commands,
                @Argument(type = "Map", name = "options") NativeObject jsOptions) throws Exception {

            Commands _commands;
            if (commands instanceof Commands) {
                _commands = (Commands) commands;
            } else if (commands instanceof AbstractCommand) {
                _commands = new Commands((AbstractCommand) commands);
            } else if (commands instanceof NativeArray) {
                _commands = new Commands(JSCommand.getCommand(commands));
            } else {
                throw new XPMRhinoIllegalArgumentException("2nd argument of command_line_job must be a command");
            }

            Resource resource = xpm.commandlineJob(jobId, _commands, jsOptions);

            // Update the json
            json.put(Manager.XP_RESOURCE.toString(), new JsonResource(resource));
            return resource;
        }

        /**
         * Declare an alternative
         *
         * @param qname A qualified name
         */
        @Expose("declare_alternative")
        @Help(value = "Declare a qualified name as an alternative input")
        public void declareAlternative(Object qname) {
            AlternativeType type = new AlternativeType((QName) qname);
            xpm.getRepository().addType(type);
        }


        /**
         * Useful for debugging E4X: outputs the DOM view
         *
         * @param xml an E4X object
         */
        @Expose("output_e4x")
        @Help("Outputs the E4X XML object")
        public void outputE4X(@Argument(name = "xml", help = "The XML object") Object xml) {
            final Iterable<? extends Node> list = JSCommand.xmlAsList(JSUtils.toDOM(null, xml));
            for (Node node : list) {
                output(node);
            }
        }

        @Expose("publish")
        @Help("Publish the repository on the web server")
        public void publish() throws InterruptedException {
            TasksServlet.updateRepository(xpm.currentScriptPath.toString(), xpm.getRepository());
        }

        @Expose
        @Help("Set the simulate flag: When true, the jobs are not submitted but just output")
        public boolean simulate(boolean simulate) {
            xpm._simulate = simulate;
            return simulate;
        }

        @Expose
        public boolean simulate() {
            return xpm._simulate;
        }

        @Expose
        public String env(String key, String value) {
            return xpm.environment.put(key, value);
        }

        @Expose
        public String env(String key) {
            return xpm.environment.get(key);
        }

        private void output(Node node) {
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE:
                    xpm.log("[element %s]", node.getNodeName());
                    for (Node child : XMLUtils.children(node))
                        output(child);
                    xpm.log("[/element %s]", node.getNodeName());
                    break;
                case Node.TEXT_NODE:
                    xpm.log("text [%s]", node.getTextContent());
                    break;
                default:
                    xpm.log("%s", node.toString());
            }
        }
    }

    @Exposed
    public static class XPMFunctions {
        XPMObject xpm;

        @Expose
        public XPMFunctions(XPMObject xpm) {
            this.xpm = xpm;
        }

        @Expose(scope = true, value = "merge")
        static public NativeObject merge(Context cx, Scriptable scope, Object... objects) {
            NativeObject returned = new NativeObject();

            for (Object object : objects) {
                object = JSUtils.unwrap(object);
                if (object instanceof NativeObject) {
                    NativeObject nativeObject = (NativeObject) object;
                    for (Map.Entry<Object, Object> entry : nativeObject.entrySet()) {
                        Object key = entry.getKey();
                        if (returned.has(key.toString(), returned))
                            throw new XPMRhinoException("Conflicting id in merge: %s", key);
                        returned.put(key.toString(), returned,
                                JSBaseObject.XPMWrapFactory.INSTANCE.wrap(cx, scope, entry.getValue(), Object.class));
                    }
                } else if (object instanceof JsonObject) {
                    Json json = (Json) object;
                    if (!(json instanceof JsonObject))
                        throw new XPMRhinoException("Cannot merge object of type " + object.getClass());
                    JsonObject jsonObject = (JsonObject) json;
                    for (Map.Entry<String, Json> entry : jsonObject.entrySet()) {
                        returned.put(entry.getKey(), returned, new JSJson(entry.getValue()));
                    }

                } else throw new XPMRhinoException("Cannot merge object of type " + object.getClass());

            }
            return returned;
        }


        @Expose(scope = true)
        public static String digest(Context cx, Scriptable scope, Object... jsons) throws NoSuchAlgorithmException, IOException {
            Json json = JSUtils.toJSON(scope, jsons);
            return Manager.getDigest(json);
        }

        @Expose(scope = true)
        public static String descriptor(Context cx, Scriptable scope, Object... jsons) throws NoSuchAlgorithmException, IOException {
            Json json = JSUtils.toJSON(scope, jsons);
            return Manager.getDescriptor(json);
        }

        @Expose(scope = true)
        @Help(value = "Transform plans outputs with a function")
        public static Scriptable transform(Context cx, Scriptable scope, Callable f, JSAbstractOperator... operators) throws FileSystemException {
            return new JSTransform(cx, scope, f, operators);
        }

        @Expose
        public static JSInput input(String name) {
            return new JSInput(name);
        }

        @Expose(value = "_")
        @JSDeprecated
        public static Object _get_value(Object object) {
            return get_value(object);
        }

        @Expose("$")
        public static Object get_value(Object object) {
            object = unwrap(object);
            if (object instanceof Json)
                return ((Json) object).get();

            return object;
        }

        @Expose("assert")
        public static void _assert(boolean condition, String format, Object... objects) {
            if (!condition)
                throw new EvaluatorException("assertion failed: " + String.format(format, objects));
        }

        @Expose()
        @Help("Get a lock over all the resources defined in a JSON object. When a resource is found, don't try " +
                "to lock the resources below")
        public NativeArray get_locks(String lockMode, JsonObject json) {
            ArrayList<Dependency> dependencies = new ArrayList<>();

            get_locks(lockMode, json, dependencies);

            return new NativeArray(dependencies.toArray(new Dependency[dependencies.size()]));
        }

        private void get_locks(String lockMode, Json json, ArrayList<Dependency> dependencies) {
            if (json instanceof JsonObject) {
                final Resource resource = getResource((JsonObject) json);
                if (resource != null) {
                    final Dependency dependency = resource.createDependency(lockMode);
                    dependencies.add(dependency);
                } else {
                    for (Json element : ((JsonObject) json).values()) {
                        get_locks(lockMode, element, dependencies);
                    }

                }
            } else if (json instanceof JsonArray) {
                for (Json arrayElement : ((JsonArray) json)) {
                    get_locks(lockMode, arrayElement, dependencies);
                }

            }
        }

        @Expose(value = "$$")
        @Help("Get the resource associated with the json object")
        public Resource get_resource(Json json) {
            Resource resource;
            if (json instanceof JsonObject) {
                resource = getResource((JsonObject) json);
            } else {
                throw new XPMRhinoException("Cannot get the resource of a Json of type " + json.getClass());
            }

            if (resource != null) {
                return resource;
            }
            throw new XPMRhinoException("Object does not contain a resource (key %s)", Manager.XP_RESOURCE);
        }

        private Resource getResource(JsonObject json) {
            if (json.containsKey(Manager.XP_RESOURCE.toString())) {
                final Object o = json.get(Manager.XP_RESOURCE.toString()).get();
                if (o instanceof Resource) {
                    return (Resource) o;
                } else {
                    final String uri = o instanceof JsonString ? o.toString() : (String) o;
                    if (xpm.simulate()) {
                        final Resource resource = xpm.submittedJobs.get(uri);
                        if (resource == null) {
                            return Transaction.evaluate(em -> Resource.getByLocator(em, uri));
                        }
                        return resource;
                    } else {
                        return Transaction.evaluate(em -> Resource.getByLocator(em, uri));
                    }
                }

            }
            return null;
        }

        @Expose(value = "java_repository", optional = 1, optionalsAtStart = true)
        @Help("Include a repository from introspection of a java project")
        public void includeJavaRepository(Connector connector, String[] paths) throws IOException, ExperimaestroException, ClassNotFoundException {
            if (connector == null)
                connector = LocalhostConnector.getInstance();
            JavaTasksIntrospection.addToRepository(xpm.getRepository(), connector, paths);
        }

        @Expose()
        @Help("Set the experiment for all future commands")
        public void set_experiment(String dotname, Path workdir) throws ExperimaestroCannotOverwrite {
            if (!xpm.simulate()) {
                Experiment experiment = new Experiment(dotname, System.currentTimeMillis(), workdir);
                try (Transaction t = Transaction.create()) {
                    t.em().persist(experiment);
                    xpm.getScriptContext().setExperimentId(experiment.getId());
                    t.commit();
                }
            }
            xpm.getScriptContext().setWorkingDirectory(workdir);
        }

        @Expose
        public void set_workdir(Path workdir) throws FileSystemException {
            xpm.getScriptContext().setWorkingDirectory(workdir);
        }

    }

}