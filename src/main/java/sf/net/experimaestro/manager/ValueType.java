/*
 * This file is part of experimaestro.
 * Copyright (c) 2012 B. Piwowarski <benjamin@bpiwowar.net>
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

package sf.net.experimaestro.manager;

/**
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 * @date 13/9/12
 */
public class ValueType extends Type {
    private static final QName QNAME = new QName(Manager.EXPERIMAESTRO_NS, "value");
    private QName type;

    public ValueType(QName type) {
        super(QNAME);
        this.type = type;
    }

    public QName getValueType() {
        return type;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + type.toString();
    }
}