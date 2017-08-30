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

public class ObjectPool<E> {
    private BlockingQueue<E> objects;
    private Construct<E> construct;

    public ObjectPool(int capacity, Construct<E> construct) {
        this(capacity, construct, false);
    }

    public ObjectPool(int capacity, Construct<E> construct, boolean fair) {
        objects = new ArrayBlockingQueue<>(capacity, fair);

        this.construct = construct;
    }

    public void init() {
        for (int i = 0; i < objects.size(); i++) {
            objects.add(construct.instance());
        }
    }

    public E get() throws InterruptedException {
        if (objects.isEmpty()) {
            // Rather than block
            return construct.instance();
        }

        return objects.take();
    }

    public void put(E element) throws InterruptedException {
        // Should in practice never block because
        objects.put(element);
    }
}
