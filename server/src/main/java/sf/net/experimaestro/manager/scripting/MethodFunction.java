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

import com.google.common.collect.Iterables;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractList;
import java.util.ArrayList;

/**
 * Represents all the methods with the same name within the same object
 */
public class MethodFunction extends GenericFunction {
    final String name;
    final ArrayList<Group> groups = new ArrayList<>();
    public MethodFunction(String name) {
        this.name = name;
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }

    @Override
    protected String getName() {
        return name;
    }

    @Override
    protected Iterable<MethodDeclaration> declarations() {
        return Iterables.concat(new AbstractList<Iterable<MethodDeclaration>>() {
            @Override
            public Iterable<MethodDeclaration> get(final int index) {
                Group group = groups.get(index);
                return Iterables.transform(group.methods,
                        m -> new MethodDeclaration(group.thisObject, m));
            }

            @Override
            public int size() {
                return groups.size();
            }
        });
    }

    public void add(Object thisObj, ArrayList<Method> methods) {
        groups.add(new Group(thisObj, methods));
    }


    /**
     * Represent all the methods from a given ancestor (or self)
     */
    static class Group {
        final Object thisObject;
        ArrayList<Method> methods = new ArrayList<>();

        Group(Object thisObject, ArrayList<Method> methods) {
            this.thisObject = thisObject;
            this.methods = methods;
        }
    }

    static public class MethodDeclaration extends Declaration<Method> {
        final Object baseObject;
        final Method method;

        public MethodDeclaration(Object baseObject, Method method) {
            super(method);
            this.baseObject = baseObject;
            this.method = method;
        }

        @Override
        public Object invoke(Object[] transformedArgs) throws InvocationTargetException, IllegalAccessException {
            boolean isStatic = (method.getModifiers() & Modifier.STATIC) != 0;
            return method.invoke(isStatic ? null : baseObject, transformedArgs);
        }
    }


}
