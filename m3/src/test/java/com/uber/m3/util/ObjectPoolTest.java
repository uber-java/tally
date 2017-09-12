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

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class ObjectPoolTest {
    private final Construct<Object> OBJECT_CONSTRUCT = new Construct<Object>() {
        @Override
        public Object instance() {
            return new Object();
        }
    };

    @Test(expected = IllegalArgumentException.class)
    public void negativeCapacityPool() {
        ObjectPool<Object> pool = new ObjectPool<>(-3, OBJECT_CONSTRUCT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyPool() {
        ObjectPool<Object> pool = new ObjectPool<>(0, OBJECT_CONSTRUCT);
    }

    @Test
    public void oneCapacityPool() {
        ObjectPool<Object> pool = new ObjectPool<>(1, OBJECT_CONSTRUCT);

        Object obj1 = pool.get();
        // Pool should create new instances when pool is empty
        Object obj2 = pool.get();
        Object obj3 = pool.get();

        assertNotNull(obj1);
        assertNotNull(obj2);
        assertNotNull(obj3);

        // Should be able to put obj1 back to the pool because it's empty now
        assertTrue(pool.put(obj1));
        // Should not be able to put obj2 back to the pool because it's full now
        assertFalse(pool.put(obj2));

        // Purposely don't use equals or assertEqual because we want to test that
        // the object we get back is exactly the same (as opposed to sematically
        // the same) as the one we put back.
        assertTrue(obj1 == pool.get());
    }

    @Test
    public void twoCapacityPool() {
        ObjectPool<Object> pool = new ObjectPool<>(2, OBJECT_CONSTRUCT);

        // Should not be able to put a new Object in the pool because it's full now
        assertFalse(pool.put(new Object()));

        Object obj1 = pool.get();
        Object obj2 = pool.get();
        // Pool should create new instances when pool is empty
        Object obj3 = pool.get();

        assertNotNull(obj1);
        assertNotNull(obj2);
        assertNotNull(obj3);

        // Should be able to put obj2 and obj1 back to the pool because it's empty now
        assertTrue(pool.put(obj2));
        assertTrue(pool.put(obj1));
        // Should not be able to put obj3 back to the pool because it's full now
        assertFalse(pool.put(obj3));

        // Make sure we get exactly the same objects in the same order that we put them back in
        assertTrue(obj2 == pool.get());
        assertTrue(obj1 == pool.get());
    }
}