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

package com.uber.m3.tally;

import java.util.Map;

/**
 * A namespace wrapper around a stats reporter, ensuring that
 * all emitted values have a given prefix or set of tags.
 */
public interface Scope extends AutoCloseable {
    /**
     * Creates and returns a {@link Counter} with the specified name.
     * @param name the name of this {@link Counter}
     * @return a {@link Counter} with the specified name
     */
    Counter counter(String name);

    /**
     * Creates and returns a {@link Gauge} with the specified name.
     * @param name the name of this {@link Gauge}
     * @return a {@link Gauge} with the specified name
     */
    Gauge gauge(String name);

    /**
     * Creates and returns a {@link Timer} with the specified name.
     * @param name the name of this {@link Timer}
     * @return a {@link Timer} with the specified name
     */
    Timer timer(String name);

    /**
     * Creates and returns a {@link Histogram} with specified name and buckets.
     * @param name the name of this {@link Histogram}
     * @param buckets the buckets of this {@link Histogram}
     * @return a {@link Histogram} with the specified name and buckets
     */
    Histogram histogram(String name, Buckets buckets);

    /**
     * Returns a child scope with the given and current tags.
     * @param tags tags of subscope
     * @return the tagged subscope
     */
    Scope tagged(Map<String, String> tags);

    /**
     * Returns a child scope with the given name.
     * @param name name of subscope to create
     * @return the subscope created
     */
    Scope subScope(String name);

    /**
     * Returns the capabilities of this {@link Scope}.
     * @return the capabilities of this {@link Scope}
     */
    Capabilities capabilities();

    @Override
    void close() throws ScopeCloseException;
}
