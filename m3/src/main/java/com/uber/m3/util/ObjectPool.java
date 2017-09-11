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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * An object pool used to reduce the number of objects required to be allocated
 * and subsequently garbage-collected.
 * @param <E> the type of object this is a pool of
 */
public class ObjectPool<E> {
    private BlockingQueue<E> objects;
    private Construct<E> construct;

    /**
     * Initializes an {@link ObjectPool} and preallocates objects in it up to capacity.
     * Defaults fair-ness (FIFO for requests) to false. This improves throughput
     * with the slight chance of starvation.
     * @param capacity  capacity of this pool
     * @param construct the constructor of E
     */
    public ObjectPool(int capacity, Construct<E> construct) {
        this(capacity, construct, false);
    }

    /**
     * Initializes an {@link ObjectPool} and preallocates objects in it up to capacity.
     * @param capacity  capacity of this pool
     * @param construct the constructor of E
     * @param fair      whether the pool is fair (FIFO for requests)
     */
    public ObjectPool(int capacity, Construct<E> construct, boolean fair) {
        objects = new ArrayBlockingQueue<>(capacity, fair);

        this.construct = construct;

        for (int i = 0; i < objects.size(); i++) {
            objects.add(construct.instance());
        }
    }

    /**
     * Gets an object from the pool. If the pool is empty, it will create and return
     * a new instance instead of blocking until an object is available.
     * @return an object from the pool if available. Otherwise, a new instance of the object.
     */
    public E get() {
        if (objects.isEmpty()) {
            // Rather than block, return a newly created instance instead
            return construct.instance();
        }

        try {
            return objects.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return construct.instance();
        }
    }

    /**
     * Puts an object back into the pool.
     * @param element the element to put back
     * @return whether the element got put back or not
     */
    public boolean put(E element) {
        // Use `offer` instead of `put` (which blocks until there is space), because a full
        // ObjectPool is a good state to be in already. We can get into this state because the
        // get() function will create a new instance instead of waiting for an object
        // to be in the pool.
        return objects.offer(element);
    }
}
