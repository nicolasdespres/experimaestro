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

import com.google.common.collect.Iterables;
import org.apache.commons.lang.NotImplementedException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import sf.net.experimaestro.utils.XMLUtils;

import javax.xml.xquery.XQException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Container for global definitions
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */
public class Manager {

    public static final String EXPERIMAESTRO_NS = "http://experimaestro.lip6.fr";
    public static final Map<String, String> PREDEFINED_PREFIXES = new TreeMap<>();
    public static final String EXPERIMAESTRO_PREFIX = "xp";

    public static final String XMLSCHEMA_NS = "http://www.w3.org/2001/XMLSchema";

    public static final QName XP_PATH = new QName(EXPERIMAESTRO_NS, "path");
    public static final QName XP_RESOURCE = new QName(EXPERIMAESTRO_NS, "resource");
    public static final QName XP_ARRAY = new QName(EXPERIMAESTRO_NS, "array");
    public static final QName XP_NAME = new QName(EXPERIMAESTRO_NS, "name");

    static {
        PREDEFINED_PREFIXES.put("xp", EXPERIMAESTRO_NS);
        PREDEFINED_PREFIXES.put("xs", XMLSCHEMA_NS);
    }


    /**
     * Get the namespaces (default and element based)
     *
     * @param element
     * @throws XQException
     */
    public static Map<String, String> getNamespaces(Element element) {
        TreeMap<String, String> map = new TreeMap<>();
        for (Entry<String, String> mapping : PREDEFINED_PREFIXES.entrySet())
            map.put(mapping.getKey(), mapping.getValue());
        for (Entry<String, String> mapping : XMLUtils.getNamespaces(element))
            map.put(mapping.getKey(), mapping.getValue());
        return map;
    }


    /**
     * Unwraps the value from XML to an object
     *
     * @param node
     * @return
     */
    static public Object unwrapToObject(Node node) {
        if (node instanceof DocumentFragment && node.getChildNodes().getLength() == 1) {
            node = node.getFirstChild();
        }
        if (node instanceof Text || node instanceof Attr)
            return node.getTextContent();

        if (node instanceof Document)
            node = ((Document) node).getDocumentElement();

        // Check if we have an associated value...
        Element element = (Element) node;
        if (element.hasAttributeNS(EXPERIMAESTRO_NS, "value")) {
            return ValueType.unwrap(element);
        }

        // ... otherwise, returns the text content
        return element.getTextContent();
    }

    /**
     * Unwraps the value from XML to a string
     *
     * @param node
     * @return
     */
    static public String unwrapToString(Node node) {
        if (node instanceof DocumentFragment && node.getChildNodes().getLength() == 1) {
            node = node.getFirstChild();
        }
        if (node instanceof Text || node instanceof Attr)
            return node.getTextContent();

        if (node instanceof Document)
            node = ((Document) node).getDocumentElement();

        // Check if we have an associated value...
        Element element = (Element) node;
        if (ValueType.VALUE.isAttribute(element)) {
            return ValueType.VALUE.getAttribute(element);
        }

        // ... otherwise, returns the text content
        return element.getTextContent();
    }

    /**
     * Wrap a node into a document
     *
     * @param node
     * @return
     */
    public static Document wrap(Object object) {
        if (object instanceof Document)
            return (Document) object;

        Document document = XMLUtils.newDocument();
        if (object instanceof Node) {
            Node node = (Node) object;
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE:
                    document.appendChild(document.adoptNode(node.cloneNode(true)));
                    break;

                case Node.TEXT_NODE:
                case Node.ATTRIBUTE_NODE:
                    Element element = document.createElementNS(EXPERIMAESTRO_NS, "value");
                    element.setAttributeNS(EXPERIMAESTRO_NS, "value", node.getTextContent());
                    document.appendChild(element);
                    break;

                case Node.DOCUMENT_FRAGMENT_NODE:
                    Iterable<Element> elements = XMLUtils.elements(node.getChildNodes());
                    int size = Iterables.size(elements);
                    if (size == 1) {
                        document.appendChild(document.adoptNode(Iterables.get(elements, 0).cloneNode(true)));
                        break;
                    }

                    throw new NotImplementedException(String.format("Cannot convert a fragment with %d children", size));

                default:
                    throw new NotImplementedException("Cannot convert " + node.getClass() + " into a document");
            }

        } else {
            Iterable<? extends Node> elements = XMLUtils.iterable((NodeList) object);
            int size = Iterables.size(elements);
            if (size == 1) {
                document.appendChild(document.adoptNode(Iterables.get(elements, 0).cloneNode(true)));
            } else
                throw new NotImplementedException(String.format("Cannot convert a fragment with %d children", size));
        }

        return document;
    }

}
