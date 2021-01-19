// Copyright (c) 2021 Uber Technologies, Inc.
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

import java.util.HashMap;
import java.util.Map;

/**
 * A simple immutable map that does not allow modifying entries after instantiation.
 * @param <K> the key type
 * @param <V> the value type
 */
public class ImmutableMap<K, V> implements Map<K, V> {
    public static final ImmutableMap EMPTY = new ImmutableMap();

    private final HashMap<K, V> map;

    // Not final to allow for lazy evaluation
    private ImmutableSet<K> keySet;
    private ImmutableList<V> values;
    private ImmutableSet<Entry<K, V>> entrySet;

    private ImmutableMap() {
        this.map = new HashMap<>(0);
    }

    /**
     * Constructor using a given map.
     * @param map map to wrap an {@link ImmutableMap} around
     */
    public ImmutableMap(Map<K, V> map) {
        this.map = new HashMap<>(map);
    }

    /**
     * A helper {@link ImmutableMap} factory method for one mapping.
     * @param key1 the first key
     * @param val1 the first value
     * @param <K>  the key type
     * @param <V>  the value type
     * @return an {@link ImmutableMap} with the given parameters
     */
    public static <K, V> ImmutableMap<K, V> of(K key1, V val1) {
        return new Builder<K, V>(1).put(key1, val1).build();
    }

    /**
     * A helper {@link ImmutableMap} factory method for two mappings.
     * @param key1 the first key
     * @param val1 the first value
     * @param key2 the second key
     * @param val2 the second value
     * @param <K>  the key type
     * @param <V>  the value type
     * @return an {@link ImmutableMap} with the given parameters
     */
    public static <K, V> ImmutableMap<K, V> of(K key1, V val1, K key2, V val2) {
        return new Builder<K, V>(2)
            .put(key1, val1)
            .put(key2, val2)
            .build();
    }

    /**
     * A helper {@link ImmutableMap} factory method for three mappings.
     * @param key1 the first key
     * @param val1 the first value
     * @param key2 the second key
     * @param val2 the second value
     * @param key3 the third key
     * @param val3 the third value
     * @param <K>  the key type
     * @param <V>  the value type
     * @return an {@link ImmutableMap} with the given parameters
     */
    public static <K, V> ImmutableMap<K, V> of(K key1, V val1, K key2, V val2, K key3, V val3) {
        return new Builder<K, V>(3)
            .put(key1, val1)
            .put(key2, val2)
            .put(key3, val3)
            .build();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return map.get(key);
    }

    @Override
    public final V put(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void putAll(Map m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableSet<K> keySet() {
        if (keySet == null) {
            keySet = new ImmutableSet<>(map.keySet());
        }

        return keySet;
    }

    @Override
    public ImmutableList<V> values() {
        if (values == null) {
            values = new ImmutableList<>(map.values());
        }

        return values;
    }

    @Override
    public ImmutableSet<Entry<K, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new ImmutableSet<>(map.entrySet());
        }

        return entrySet;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof ImmutableMap)) {
            return false;
        }

        return map.equals(((ImmutableMap) other).map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return map.toString();
    }

    /**
     * Helper class to construct {@link ImmutableMap}s.
     * @param <K> the key type
     * @param <V> the value type
     */
    public static class Builder<K, V> {
        private HashMap<K, V> map;

        public Builder() {
            this(16, 0.75f);
        }

        public Builder(int initialCapacity) {
            this(initialCapacity, 1);
        }

        public Builder(int initialCapacity, float loadFactor) {
            map = new HashMap<>(initialCapacity, loadFactor);
        }

        public Builder<K, V> put(K key, V value) {
            map.put(key, value);

            return this;
        }

        public Builder<K, V> putAll(Map<K, V> otherMap) {
            map.putAll(otherMap);

            return this;
        }

        public ImmutableMap<K, V> build() {
            return new ImmutableMap<>(map);
        }
    }
}
