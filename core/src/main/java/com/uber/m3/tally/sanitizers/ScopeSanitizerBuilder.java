// Copyright (c) 2020 Uber Technologies, Inc.
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

package com.uber.m3.tally.sanitizers;

import java.util.function.Function;

/**
 * The SanitizerBuilder returns a Sanitizer for the name, key and value. By
 * default, the name, key and value sanitize functions returns all the input
 * untouched. Custom name, key or value Sanitize functions or ValidCharacters
 * can be provided to override their default behaviour.
 */
public class ScopeSanitizerBuilder {

    private Function<String, String> nameSanitizer = Function.identity();
    private Function<String, String> keySanitizer = Function.identity();
    private Function<String, String> valueSanitizer = Function.identity();

    private char repChar = ValidCharacters.DEFAULT_REPLACEMENT_CHARACTER;
    private ValidCharacters nameCharacters;
    private ValidCharacters keyCharacters;
    private ValidCharacters valueCharacters;

    public ScopeSanitizerBuilder withNameSanitizer(Function<String, String> nameSanitizer) {
        this.nameSanitizer = nameSanitizer;
        return this;
    }

    public ScopeSanitizerBuilder withKeySanitizer(Function<String, String> keySanitizer) {
        this.keySanitizer = keySanitizer;
        return this;
    }

    public ScopeSanitizerBuilder withValueSanitizer(Function<String, String> valueSanitizer) {
        this.valueSanitizer = valueSanitizer;
        return this;
    }

    public ScopeSanitizerBuilder withReplacementCharacter(char repChar) {
        this.repChar = repChar;
        return this;
    }

    public ScopeSanitizerBuilder withNameCharacters(ValidCharacters nameCharacters) {
        this.nameCharacters = nameCharacters;
        return this;
    }

    public ScopeSanitizerBuilder withKeyCharacters(ValidCharacters keyCharacters) {
        this.keyCharacters = keyCharacters;
        return this;
    }

    public ScopeSanitizerBuilder withValueCharacters(ValidCharacters valueCharacters) {
        this.valueCharacters = valueCharacters;
        return this;
    }

    public ScopeSanitizer build() {
        if (nameCharacters != null) {
            nameSanitizer = nameCharacters.sanitizeStringFunc(repChar);
        }
        if (keyCharacters != null) {
            keySanitizer = keyCharacters.sanitizeStringFunc(repChar);
        }
        if (valueCharacters != null) {
            valueSanitizer = valueCharacters.sanitizeStringFunc(repChar);
        }
        return new SanitizerImpl(nameSanitizer, keySanitizer, valueSanitizer);
    }
}
