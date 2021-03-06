package sf.net.experimaestro.manager.scripting;

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

import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.mutable.MutableInt;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Wrapper;
import sf.net.experimaestro.exceptions.WrappedException;
import sf.net.experimaestro.exceptions.XPMRhinoException;
import sf.net.experimaestro.manager.js.JSBaseObject;
import sf.net.experimaestro.manager.js.JavaScriptContext;
import sf.net.experimaestro.manager.json.Json;
import sf.net.experimaestro.utils.JSUtils;
import sf.net.experimaestro.utils.Output;
import sf.net.experimaestro.utils.arrays.ListAdaptator;

import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

import static java.lang.Math.max;
import static java.lang.StrictMath.min;

/**
 * Base class for scripting methods or constructors
 */
public abstract class GenericFunction {

    static private final Function IDENTITY = Function.identity();
    static private final Function<Wrapper, Object> UNWRAPPER = x -> x.unwrap();
    static private final Function<Object, String> TOSTRING = x -> x.toString();

    /**
     * Transform the arguments
     *
     * @param cx          The script context
     * @param lcx
     * @param declaration
     * @param args
     * @param offset      The offset within the target parameters    @return
     */
    static Object[] transform(LanguageContext lcx, ScriptContext cx, Declaration declaration, Object[]
            args, Function[] converters, int offset) {
        final Executable executable = declaration.executable();
        final Class<?>[] types = executable.getParameterTypes();
        Object methodArgs[] = new Object[types.length];

        // --- Add context and scope if needed
        Expose annotation = executable.getAnnotation(Expose.class);
        if (annotation != null && (annotation.scope() && annotation.context())) {
            throw new UnsupportedOperationException("Annotations scope and context cannot be used at the same time");
        }

        if (annotation == null ? false : annotation.scope()) {
            JavaScriptContext jcx = (JavaScriptContext) lcx;
            methodArgs[0] = jcx.context();
            methodArgs[1] = jcx.scope();
        }

        if (annotation == null ? false : annotation.context()) {
            methodArgs[0] = lcx;
            methodArgs[1] = cx;
        }

        // --- Copy the non vararg parameters
        final int length = types.length - (executable.isVarArgs() ? 1 : 0) - offset;
        int size = min(length, args.length);
        for (int i = 0; i < size; i++) {
            methodArgs[i + offset] = converters[i].apply(args[i]);
        }

        // --- Deals with the vararg pararameters
        if (executable.isVarArgs()) {
            final Class<?> varargType = types[types.length - 1].getComponentType();
            int nbVarargs = args.length - length;
            final Object array[] = (Object[]) Array.newInstance(varargType, nbVarargs);
            for (int i = 0; i < nbVarargs; i++) {
                array[i] = converters[i + length].apply(args[i + length]);
            }
            methodArgs[methodArgs.length - 1] = array;
        }

        return methodArgs;
    }

    /**
     * Gives a score to a given declaration
     *
     * @param cx          The script context
     * @param declaration The underlying method or constructor
     * @param args        The arguments
     * @param converters  A list of converters that will be filled by this method
     * @param offset      The offset for the converters
     * @return A score (minimum integer if no conversion is possible)
     */
    static int score(LanguageContext lcx, ScriptContext cx, Declaration declaration, Object[] args, Function[] converters, MutableInt offset) {

        final Executable executable = declaration.executable();
        final Class<?>[] types = executable.getParameterTypes();
        final boolean isVarArgs = executable.isVarArgs();

        // Get the annotations
        Expose annotation = declaration.executable.getAnnotation(Expose.class);
        final boolean contextAnnotation = annotation == null ? false : annotation.context();
        final boolean scopeAnnotation = annotation == null ? false : annotation.scope();
        int optional = annotation == null ? 0 : annotation.optional();

        // Start the scoring
        Converter converter = new Converter();

        // Offset in the types
        offset.setValue(contextAnnotation || scopeAnnotation ? 2 : 0);

        // Number of "true" arguments (not scope, not vararg)
        final int nbArgs = types.length - offset.intValue() - (isVarArgs ? 1 : 0);

        // The number of arguments should be in:
        // [nbArgs - optional, ...] if varargs
        // [nbArgs - optional, nbArgs] otherwise

        if (args.length < nbArgs - optional)
            return Integer.MIN_VALUE;

        if (!isVarArgs && args.length > nbArgs)
            return Integer.MIN_VALUE;

        // If the optional arguments are at the beginning, then shift
        if (annotation != null && annotation.optionalsAtStart()) {
            offset.add(max(nbArgs - args.length, 0));
        }

        // Normal arguments
        for (int i = 0; i < args.length && i < nbArgs && converter.isOK(); i++) {
            final Object o = args[i];
            converters[i] = converter.converter(lcx, cx, o, types[i + offset.intValue()]);
        }

        // Var args
        if (isVarArgs) {
            Class<?> type = ClassUtils.primitiveToWrapper(types[types.length - 1].getComponentType());
            int nbVarArgs = args.length - nbArgs;
            for (int i = 0; i < nbVarArgs && converter.isOK(); i++) {
                final Object o = args[nbArgs + i];
                converters[nbArgs + i] = converter.converter(lcx, cx, o, type);
            }
        }

        return converter.score;
    }

    /**
     * Get the name of the method or constructor
     */
    protected abstract String getName();

    protected abstract <T extends Declaration> Iterable<T> declarations();

    public Object call(LanguageContext lcx, ScriptContext cx, Object thisObj, Object[] args) {
        Declaration argmax = null;
        int max = Integer.MIN_VALUE;

        Function argmaxConverters[] = new Function[args.length];
        Function converters[] = new Function[args.length];
        int argMaxOffset = 0;

        for (Declaration method : declarations()) {
            MutableInt offset = new MutableInt(0);
            int score = score(lcx, cx, method, args, converters, offset);
            if (score > max) {
                max = score;
                argmax = method;
                Function tmp[] = argmaxConverters;
                argMaxOffset = offset.intValue();
                argmaxConverters = converters;
                converters = tmp;
            }
        }

        if (argmax == null) {
            String context = "";
            if (thisObj instanceof JSBaseObject)
                context = " in an object of class " + ClassDescription.getClassName(thisObj.getClass());

            throw ScriptRuntime.typeError(String.format("Could not find a matching method for %s(%s)%s",
                    getName(),
                    Output.toString(", ", args, o -> o.getClass().toString()),
                    context
            ));
        }

        // Call the constructor
        try {
            Object[] transformedArgs = transform(lcx, cx, argmax, args, argmaxConverters, argMaxOffset);
            final Object result = argmax.invoke(transformedArgs);

            return result;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof XPMRhinoException) {
                throw (XPMRhinoException) e.getCause();
            }
            throw new WrappedException(new XPMRhinoException(e.getCause()));
        } catch (Throwable e) {
            throw new WrappedException(new XPMRhinoException(e));
        }

    }

    abstract static public class Declaration<T extends Executable> {
        private final T executable;

        public Declaration(T executable) {
            this.executable = executable;
        }

        public Executable executable() {
            return executable;
        }

        public abstract Object invoke(Object[] transformedArgs) throws InvocationTargetException, IllegalAccessException, InstantiationException;

    }

    static public class ListConverter implements Function {
        private final Class<?> arrayClass;
        ArrayList<Function> functions = new ArrayList<>();

        public ListConverter(Class<?> arrayClass) {
            this.arrayClass = arrayClass;
        }

        @Override
        public Object apply(Object input) {
            final Collection collection = (Collection) input;
            final Object[] objects = (Object[]) Array.newInstance(arrayClass, functions.size());
            final Iterator iterator = collection.iterator();
            int i = 0;
            while (iterator.hasNext()) {
                objects[i] = functions.get(i).apply(iterator.next());
                ++i;
            }
            assert i == objects.length;
            return objects;
        }

        public void add(Function function) {
            functions.add(function);
        }
    }

    /**
     * A converter
     */
    static public class Converter {
        int score = Integer.MAX_VALUE;

        Function converter(LanguageContext lcx, ScriptContext cx, Object o, Class<?> type) {
            if (o == null) {
                score--;
                return IDENTITY;
            }

            // Assignable: OK
            type = ClassUtils.primitiveToWrapper(type);
            if (type.isAssignableFrom(o.getClass())) {
                if (o.getClass() != type)
                    score--;
                if (o instanceof Wrapper)
                    return object -> ((Wrapper) object).unwrap();
                return IDENTITY;
            }

            // Arrays
            if (type.isArray()) {
                Class<?> innerType = type.getComponentType();

                if (o.getClass().isArray())
                    o = ListAdaptator.create((Object[]) o);

                if (o instanceof Collection) {
                    final Collection array = (Collection) o;
                    final Iterator iterator = array.iterator();
                    final ListConverter listConverter = new ListConverter(innerType);

                    while (iterator.hasNext()) {
                        listConverter.add(converter(lcx, cx, iterator.next(), innerType));
                        if (score == Integer.MIN_VALUE) {
                            return null;
                        }
                    }

                    return listConverter;

                }
            }

            // Case of string: anything can be converted, but with different
            // scores
            if (type == String.class) {
                if (o instanceof Scriptable) {
                    switch (((Scriptable) o).getClassName()) {
                        case "String":
                        case "ConsString":
                            return TOSTRING;
                        default:
                            score -= 10;
                    }
                } else if (o instanceof CharSequence) {
                    score--;
                } else {
                    score -= 10;
                }
                return TOSTRING;
            }

            // Cast to integer
            if (type == Integer.class && o instanceof Number) {
                if ((((Number) o).intValue()) == ((Number) o).doubleValue()) {
                    return input -> ((Number) input).intValue();
                }
            }

            // Native object to JSON
            if (o instanceof NativeObject && Json.class.isAssignableFrom(type)) {
                score -= 10;
                JavaScriptContext jcx = (JavaScriptContext) lcx;
                return nativeObject -> JSUtils.toJSON(jcx.scope(), nativeObject);
            }


            // Everything else failed... unwrap and try again
            if (o instanceof Wrapper) {
                score -= 1;
                Function converter = converter(lcx, cx, ((Wrapper) o).unwrap(), type);
                return converter != null ? UNWRAPPER.andThen(converter) : null;
            }

            score = Integer.MIN_VALUE;
            return null;
        }

        public boolean isOK() {
            return score != Integer.MIN_VALUE;
        }

    }
}
