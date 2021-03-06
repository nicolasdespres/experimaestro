package sf.net.experimaestro.manager.java;

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

import net.bpiwowar.experimaestro.tasks.JsonArgument;
import net.bpiwowar.experimaestro.tasks.Runner;
import net.bpiwowar.experimaestro.tasks.TaskDescription;
import sf.net.experimaestro.connectors.Connector;
import sf.net.experimaestro.exceptions.XPMRuntimeException;
import sf.net.experimaestro.manager.*;
import sf.net.experimaestro.manager.json.Json;
import sf.net.experimaestro.manager.json.JsonObject;
import sf.net.experimaestro.manager.json.JsonString;
import sf.net.experimaestro.scheduler.*;
import sf.net.experimaestro.tasks.Path;
import sf.net.experimaestro.utils.introspection.ClassInfo;
import sf.net.experimaestro.utils.introspection.FieldInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * TaskReference factory created from java reflection
 */
public class JavaTaskFactory extends TaskFactory {
    public static final String JVM_OPTIONS = "$jvm";
    final JavaTasksIntrospection javaTasksIntrospection;
    final Connector connector;
    final String taskClassname;
    final ArrayList<PathArgument> pathArguments = new ArrayList<>();
    private final Type output;
    Map<String, Input> inputs = new HashMap<>();

    /**
     * Prefixes for namespaces - used for unique directory naming
     */
    Map<String, String> prefixes = new HashMap<>();

    /**
     * Initialise a task
     *
     * @param javaTasksIntrospection The introspection object
     * @param connector              The connector
     * @param repository             The repository
     * @param classInfo              The java class from which to build a task factory
     * @param namespaces             The namespaces
     */
    public JavaTaskFactory(JavaTasksIntrospection javaTasksIntrospection, Connector connector, Repository repository, ClassInfo classInfo, Map<String, String> namespaces) {
        super(repository);
        this.javaTasksIntrospection = javaTasksIntrospection;
        this.connector = connector;
        this.taskClassname = classInfo.getName();

        namespaces.forEach((key, value) -> prefixes.put(value, key));

        final TaskDescription description = classInfo.getAnnotation(TaskDescription.class);
        if (description == null) {
            throw new XPMRuntimeException("The class %s has no TaskDescription annotation", classInfo);
        }

        namespaces.putAll(Manager.PREDEFINED_PREFIXES);
        this.id = QName.parse(description.id(), namespaces);
        this.output = new Type(QName.parse(description.output(), namespaces));

        for (FieldInfo field : classInfo.getDeclaredFields()) {
            //final Object jsonArgument = field.getAnnotation(jsonArgumentClass.class);
            final JsonArgument jsonArgument = field.getAnnotation(JsonArgument.class);

            // TODO: add default values, etc.
            String fieldName = field.getName();
            if (jsonArgument != null) {
                Input input = new JsonInput(getType(field));
                input.setDocumentation(jsonArgument.help());
                input.setOptional(!jsonArgument.required());
                String name = getString(jsonArgument.name(), fieldName);
                inputs.put(name, input);
            }

            final Path path = field.getAnnotation(Path.class);
            if (path != null) {
                String copy = getString(path.copy(), fieldName);
                String relativePath = getString(path.value(), fieldName);
                pathArguments.add(new PathArgument(copy, relativePath));
            }
        }

        // Adds JVM
        JsonInput input = new JsonInput(new Type(Manager.XP_OBJECT));
        input.setOptional(true);
        inputs.put(JVM_OPTIONS, input);
    }

    private static String getString(String value, String defaultValue) {
        return "".equals(value) ? defaultValue : value;
    }

    private Type getType(FieldInfo field) {
        final ClassInfo type = field.getType();

        if (type.belongs(java.lang.Integer.class) || type.belongs(Integer.TYPE)
                || type.belongs(java.lang.Long.class) || type.belongs(Long.TYPE)
                || type.belongs(Short.class) || type.belongs(Short.TYPE)) {
            return new ValueType(ValueType.XP_INTEGER);
        }

        if (type.belongs(java.lang.Double.class) || type.belongs(java.lang.Float.class)) {
            return new ValueType(ValueType.XP_REAL);
        }

        if (type.belongs(String.class))
            return new ValueType(ValueType.XP_STRING);


        // Otherwise, just return any
        return new ValueType(Manager.XP_ANY);
    }

    @Override
    public Map<String, Input> getInputs() {
        return inputs;
    }

    @Override
    public Type getOutput() {
        return output;
    }

    @Override
    public Task create() {
        final JavaTask task = new JavaTask(this);
        task.init();
        return task;
    }

    @Override
    public Commands commands(JsonObject json, boolean simulate) {
        final Command command = new Command();

        Command classpath = new Command();
        final Commands commands = new Commands(command);

        Arrays.asList(javaTasksIntrospection.classpath).stream().forEach(f -> {
            classpath.add(new Command.Path(f));
            classpath.add(new Command.String(":"));
        });

        command.add("java", "-cp");
        command.add(classpath);

        // Sets JVM options
        final Json jvm = json.get(JVM_OPTIONS);
        if (jvm != null && jvm instanceof JsonObject) {
            final Json memory = ((JsonObject) jvm).get("memory");
            if (memory instanceof JsonString) {
                final Object s = memory.get();
                command.add("-Xmx" + s);
            }
        }

        // Runner class name
        command.add(Runner.class.getName());

        // TaskReference class name
        command.add(taskClassname);

        // Working directory
        command.add(Command.WorkingDirectory.INSTANCE);

        // Parameter file
        command.add(new Command.JsonParameterFile("json", json));

        // Check dependencies
        if (!simulate) {
            for (Json element : json.values()) {
                Transaction.run(em -> {
                    if (element instanceof JsonObject) {
                        JsonObject object = (JsonObject) element;
                        final Json r = object.get(Manager.XP_RESOURCE.toString());
                        if (r == null) return;

                        final Object o = r.get();
                        Resource resource;
                        if (o instanceof Resource) {
                            resource = (Resource) o;
                        } else {
                            resource = Resource.getByLocator(em, o.toString());
                            if (resource == null) {
                                throw new XPMRuntimeException("Cannot find the resource %s the task %s depends upon",
                                        o.toString(), getId());
                            }
                        }
                        final Dependency lock = resource.createDependency("READ");
                        commands.addDependency(lock);
                    }
                });
            }
        }

        return commands;
    }
}
