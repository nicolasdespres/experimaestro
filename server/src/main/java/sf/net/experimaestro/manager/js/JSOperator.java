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

import sf.net.experimaestro.manager.plans.Operator;

/**
 * Wrapper for operators
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */
public class JSOperator extends JSAbstractOperator {
    private final Operator operator;

    @JSFunction
    public JSOperator(Operator operator) {
        this.operator = operator;
    }

    @Override
    Operator getOperator() {
        return operator;
    }
}
