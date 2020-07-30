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

package com.uber.m3.tally;

import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * @deprecated DO NOT USE
 *
 * Please use {@link ImmutableBuckets} instead
 */
@Deprecated
public abstract class AbstractBuckets<T> implements Buckets<T> {
    protected List<T> buckets;

    AbstractBuckets(T[] buckets) {
        if (buckets == null) {
            throw new IllegalArgumentException("provided buckets could not be null");
        }

        this.buckets = new ImmutableList<>(Arrays.asList(buckets));
    }

    @Override
    public abstract Double[] asValues();

    @Override
    public abstract Duration[] asDurations();

    @Override
    public String toString() {
        return buckets.toString();
    }

    @Override
    public int size() {
        return buckets.size();
    }

    @Override
    public boolean isEmpty() {
        return buckets.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return buckets.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return buckets.iterator();
    }

    @Override
    public Object[] toArray() {
        return buckets.toArray();
    }

    @Override
    public boolean add(T o) {
        return buckets.add(o);
    }

    @Override
    public boolean remove(Object o) {
        return buckets.remove(o);
    }

    @Override
    public boolean addAll(Collection c) {
        return buckets.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection c) {
        return buckets.addAll(index, c);
    }

    @Override
    public void clear() {
        buckets.clear();
    }

    @Override
    public T get(int index) {
        return buckets.get(index);
    }

    @Override
    public T set(int index, T element) {
        return buckets.set(index, element);
    }

    @Override
    public void add(int index, T element) {
        buckets.add(index, element);
    }

    @Override
    public T remove(int index) {
        return buckets.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return buckets.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return buckets.lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
        return buckets.listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return buckets.listIterator(index);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return buckets.subList(fromIndex, toIndex);
    }

    @Override
    public boolean retainAll(Collection c) {
        return buckets.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection c) {
        return buckets.removeAll(c);
    }

    @Override
    public boolean containsAll(Collection c) {
        return buckets.containsAll(c);
    }

    @Override
    public Object[] toArray(Object[] a) {
        return buckets.toArray(a);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof AbstractBuckets)) {
            return false;
        }

        return buckets.equals(((AbstractBuckets) other).buckets);
    }

    @Override
    public int hashCode() {
        return buckets.hashCode();
    }
}
