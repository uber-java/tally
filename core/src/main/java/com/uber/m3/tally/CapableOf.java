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

/**
 * Default implementation of {@link Capabilities}.
 */
public class CapableOf implements Capabilities {
    // Use static final variables to obtain instances instead of using a constructor for most use cases.
    public static final CapableOf NONE = new CapableOf(false, false);
    public static final CapableOf REPORTING = new CapableOf(true, false);
    public static final CapableOf REPORTING_TAGGING = new CapableOf(true, true);

    private boolean reporting;
    private boolean tagging;

    public CapableOf(boolean reporting, boolean tagging) {
        this.reporting = reporting;
        this.tagging = tagging;
    }

    @Override
    public boolean reporting() {
        return reporting;
    }

    @Override
    public boolean tagging() {
        return tagging;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof CapableOf)) {
            return false;
        }

        CapableOf capabilities = (CapableOf) other;

        return capabilities.reporting == reporting
            && capabilities.tagging == tagging;
    }

    @Override
    public int hashCode() {
        int code = 0;

        code = 31 * code + new Boolean(reporting).hashCode();
        code = 31 * code + new Boolean(tagging).hashCode();

        return code;
    }
}
