package com.uber.m3.util;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;


/**
 * We're creating this surrogate structure to avoid incurring penalties
 * associated w/ {@code HashSet}s (like computing hash-codes for every inserted element),
 * since we aren't leveraging {@code HashSet}'s primary advantage (fast lookups)
 *
 * NOTE: This structure does NOT validate whether element is already present in the
 *       set, therefore relying on the caller do the validation prior to insertion to
 *       maintain {@link Set} invariant that there are no duplicates present
 *
 * @param <E> type of the element stored
 */
public class ListSet<E> extends ArrayList<E> implements Set<E> {

    public ListSet() {}

    public ListSet(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public int size() {
        return super.size();
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return super.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return super.iterator();
    }

    @Override
    public Object[] toArray() {
        return super.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return super.toArray(a);
    }

    @Override
    public boolean add(E e) {
        return super.add(e);
    }


    @Override
    public boolean remove(Object o) {
        return super.remove(o);
    }


    @Override
    public boolean containsAll(Collection<?> c) {
        return super.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return super.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return super.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return super.removeAll(c);
    }

    @Override
    public void clear() {
        super.clear();
    }
}
