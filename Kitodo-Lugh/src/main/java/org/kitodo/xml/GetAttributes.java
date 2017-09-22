/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.xml;

import java.util.Iterator;

import org.w3c.dom.*;

/**
 * The class provides the ability to iterate over the attributes of a DOM
 * element.
 *
 * @author Matthias Ronge
 */
class GetAttributes implements Iterable<Attr>, Iterator<Attr> {
    /**
     * The attributes of the element.
     */
    private final NamedNodeMap attributes;

    /**
     * The index of the next element to return.
     */
    private int i;

    /**
     * The length of the attribute collection.
     */
    private final int length;

    /**
     * Creates a class to iterate over the attributes of a DOM element.
     *
     * @param element
     *            element over whose attributes shall be iterated
     */
    GetAttributes(Element element) {
        attributes = element.getAttributes();
        length = attributes != null ? attributes.getLength() : -1;
    }

    /**
     * Returns whether the iterator can return another element.
     *
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        return i < length;
    }

    /**
     * Returns an iterator instance, which is this implementation.
     */
    @Override
    public Iterator<Attr> iterator() {
        return this;
    }

    /**
     * Returns the next element while iterating.
     */
    @Override
    public Attr next() {
        return (Attr) attributes.item(i++);
    }

    /**
     * This iterator does not support removal.
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
