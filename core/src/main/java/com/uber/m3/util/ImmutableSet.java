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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A simple immutable set that does not allow modifying entries after instantiation.
 * @param <E> the entry type
 */
public class ImmutableSet<E> implements Set<E> {
    private final HashSet<E> set;

    public ImmutableSet(Set<E> set) {
        this.set = new HashSet<>(set);
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return set.contains(o);
    }

    @Override
    public UnmodifiableIterator<E> iterator() {
        return new UnmodifiableIterator<>(set.iterator());
    }

    @Override
    public Object[] toArray() {
        return set.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return set.toArray(a);
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
        return set.containsAll(c);
    }

    @Override
    public final boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof ImmutableSet)) {
            return false;
        }

        return set.equals(((ImmutableSet<?>) other).set);
    }

    @Override
    public int hashCode() {
        return set.hashCode();
    }

    /**
     * Helper class to construct {@link ImmutableSet}s.
     * @param <E> the type of elements in this set
     */
    public static class Builder<E> {
        private final HashSet<E> set;

        public Builder() {
            this(16, 0.75f);
        }

        public Builder(int initialCapacity) {
            this(initialCapacity, 1);
        }

        public Builder(int initialCapacity, float loadFactor) {
            set = new HashSet<>(initialCapacity, loadFactor);
        }

        public Builder<E> add(E element) {
            set.add(element);

            return this;
        }

        public Builder<E> addAll(Set<E> otherSet) {
            set.addAll(otherSet);

            return this;
        }

        public ImmutableSet<E> build() {
            return new ImmutableSet<>(set);
        }
    }
}
