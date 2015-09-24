package sf.net.experimaestro.manager.js;

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

import org.apache.commons.lang.NotImplementedException;
import org.mozilla.javascript.*;
import sf.net.experimaestro.exceptions.XPMRhinoException;
import sf.net.experimaestro.manager.scripting.MethodFunction;
import sf.net.experimaestro.utils.JSUtils;

/**
 * Wrapper for a function in javascript
 */
public class JavaScriptFunction implements Function {
    private final MethodFunction function;
    private final Object javaThis;

    public JavaScriptFunction(Object javaThis, MethodFunction function) {
        this.javaThis = javaThis;
        this.function = function;
    }

    @Override
    public Object call(Context context, Scriptable scope, Scriptable thisObj, Object[] objects) {
        JavaScriptContext jcx = new JavaScriptContext(context, scope);
        try {
            final Object result = function.call(jcx, javaThis, null, objects);
            return JavaScriptRunner.wrap(jcx, result);
        } catch(RhinoException e) {
            throw e;
        } catch(Throwable e) {
            throw new XPMRhinoException(e);
        }

    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        throw new NotImplementedException();
    }

    @Override
    public String getClassName() {
        return "MethodFunction";
    }

    @Override
    public Object get(String name, Scriptable start) {
        throw new NotImplementedException();
    }

    @Override
    public Object get(int index, Scriptable start) {
        throw new NotImplementedException();
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return false;
    }

    @Override
    public boolean has(int index, Scriptable start) {
        throw new NotImplementedException();
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        throw new NotImplementedException();
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        throw new NotImplementedException();
    }

    @Override
    public void delete(String name) {
        throw new NotImplementedException();
    }

    @Override
    public void delete(int index) {
        throw new NotImplementedException();
    }

    @Override
    public Scriptable getPrototype() {
        return null;
    }

    @Override
    public void setPrototype(Scriptable prototype) {
        throw new NotImplementedException();
    }

    @Override
    public Scriptable getParentScope() {
        throw new NotImplementedException();
    }

    @Override
    public void setParentScope(Scriptable parent) {
        throw new NotImplementedException();
    }

    @Override
    public Object[] getIds() {
        return new Object[]{};
    }

    @Override
    public Object getDefaultValue(Class<?> hint) {
        throw new NotImplementedException();
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
        throw new NotImplementedException();
    }
}
