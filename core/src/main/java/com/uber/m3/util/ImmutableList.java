// Copyright (c) 2017 Uber Technologies, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.uber.m3.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A simple immutable list that does not allow modifying entries after instantiation.
 * @param <E> the entry type
 */
public class ImmutableList<E> implements List<E> {
    private final ArrayList<E> collection;

    public ImmutableList(Collection<E> collection) {
        this.collection = new ArrayList(collection);
    }

    @Override
    public int size() {
        return collection.size();
    }

    @Override
    public boolean isEmpty() {
        return collection.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return collection.contains(o);
    }

    @Override
    public UnmodifiableIterator<E> iterator() {
        return new UnmodifiableIterator<>(collection.iterator());
    }

    @Override
    public Object[] toArray() {
        return collection.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return collection.toArray(a);
    }

    @Override
    public final boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return collection.containsAll(c);
    }

    @Override
    public final boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean addAll(int index, Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public E get(int index) {
        return collection.get(index);
    }

    @Override
    public final E set(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void add(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final E remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        return collection.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return collection.lastIndexOf(o);
    }

    @Override
    public UnmodifiableListIterator<E> listIterator() {
        return new UnmodifiableListIterator<>(collection.listIterator());
    }

    @Override
    public UnmodifiableListIterator<E> listIterator(int index) {
        return new UnmodifiableListIterator<>(collection.listIterator(index));
    }

    @Override
    public ImmutableList<E> subList(int fromIndex, int toIndex) {
        return new ImmutableList<>(collection.subList(fromIndex, toIndex));
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof ImmutableList)) {
            return false;
        }

        return collection.equals(((ImmutableList) other).collection);
    }

    @Override
    public int hashCode() {
        return collection.hashCode();
    }

    @Override
    public String toString() {
        return collection.toString();
    }
}
