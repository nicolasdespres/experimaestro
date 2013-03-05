/*
 * This file is part of experimaestro.
 * Copyright (c) 2013 B. Piwowarski <benjamin@bpiwowar.net>
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

package sf.net.experimaestro.manager.plans;

import com.google.common.collect.AbstractIterator;

import java.util.Iterator;

/**
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 * @date 22/2/13
 */
public class FunctionOperator extends UnaryOperator {
    Function function;

    public FunctionOperator(Function function) {
        this.function = function;
    }

    @Override
    protected Iterator<ReturnValue> _iterator() {
        return new AbstractIterator<ReturnValue>() {
            Iterator<Value> iterator = input.iterator();
            @Override
            protected ReturnValue computeNext() {
                if (!iterator.hasNext())
                    return endOfData();

                Value value = iterator.next();
                return new ReturnValue(new long[][] { value.context }, function.f(value.nodes));
            }
        };
    }
}
