package sf.net.experimaestro.manager;

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

import bpiwowar.argparser.utils.Output;
import sf.net.experimaestro.utils.arrays.ListAdaptator;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * A variable name with various levels separated by dots
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */
public class DotName extends AbstractList<String> implements Comparable<DotName> {
    /**
     * Empty dot name
     */
    public static final DotName EMPTY = new DotName(new String[]{});

    /**
     * The qualified name
     */
    private String[] array;

    /**
     * Construction
     */
    public DotName(String... array) {
        super();
        this.array = array;
    }

    /**
     * Creates a qualified name which is a prefix of a given qualified name
     *
     * @param key    The qualified name
     * @param length The
     */
    public DotName(DotName key, int length) {
        array = new String[length];
        System.arraycopy(key.array, 0, array, 0, length);
    }

    /**
     * Creates a qualified name with a new prefix
     *
     * @param prefix The prefix
     * @param qName  The qualified name that is used as a base
     */
    public DotName(String prefix, DotName qName) {
        this.array = new String[1 + qName.size()];
        this.array[0] = prefix;
        System.arraycopy(qName.array, 0, this.array, 1, qName.size());
    }

    /**
     * Creates an unqualified name
     */
    private DotName(String name) {
        this.array = new String[]{name};
    }

    /**
     * Build a dot name from a prefix and a series of names
     *
     * @param prefix The prefix
     * @param names  The names to add
     */
    public DotName(DotName prefix, String... names) {
        int N = prefix.size();
        this.array = new String[N + names.length];
        System.arraycopy(prefix.array, 0, this.array, 0, N);
        System.arraycopy(names, 0, this.array, N, names.length);
    }

    /**
     * Concatenate dot names
     *
     * @param dotNames
     */
    public DotName(DotName... dotNames) {
        // Compute total size
        int size = 0;
        for (int i = 0; i < dotNames.length; i++)
            size += dotNames[i].size();

        // Copy into new array
        this.array = new String[size];
        int offset = 0;
        for (int i = 0; i < dotNames.length; i++) {
            System.arraycopy(dotNames[i].array, 0, this.array, offset, dotNames[i].size());
            offset += dotNames[i].size();
        }
    }

    /**
     * Creates a DotName from an unparsed string identifier containing dots.
     *
     * @param name The full identifier to be parsed
     * @return
     */
    public static DotName parse(String name) {
        if (name == null)
            return EMPTY;
        return new DotName(name.split("\\."));
    }

    /**
     * Returns a new qualified name with offset
     *
     * @param offset
     * @return
     */
    public DotName offset(int offset) {
        String[] name = new String[array.length - offset];
        System.arraycopy(array, offset, name, 0, array.length - offset);
        return new DotName(name);
    }

    @Override
    public String toString() {
        return Output.toString(".", array);
    }

    public int size() {
        return array.length;
    }

    /**
     * Returns the length of the common prefix
     */
    public int commonPrefixLength(DotName o) {
        if (o == null)
            return 0;

        int min = Math.min(array.length, o.array.length);
        for (int i = 0; i < min; i++) {
            int z = array[i].compareTo(o.array[i]);
            if (z != 0)
                return i;
        }
        return min;

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(array);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        // If comparing to a string, implicit conversion
        if (obj instanceof String)
            return array.length == 1 && array[0].equals(obj);

        if (obj == null || getClass() != obj.getClass())
            return false;
        DotName other = (DotName) obj;
        return Arrays.equals(array, other.array);
    }

    @Override
    public int compareTo(DotName o) {
        // Compare package name first
        int min = Math.min(array.length, o.array.length);
        for (int i = 0; i < min; i++) {
            int z = array[i].compareTo(o.array[i]);
            if (z != 0)
                return z;
        }

        // The longer one then?
        int z = array.length - o.array.length;
        return z;
    }

    /**
     * Return the unqualified name
     */
    public String getName() {
        return array[array.length - 1];
    }

    /**
     * Return the index<sup>th</sup> element of the qualified name
     */
    public String get(int index) {
        return array[index];
    }

    public boolean isQualified() {
        return array.length > 1;
    }

    @Override
    public Iterator<String> iterator() {
        return new ListAdaptator<String>(array).iterator();
    }

}
