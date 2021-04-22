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

    private Function<String, String> nameSanitizer;
    private Function<String, String> tagKeySanitizer;
    private Function<String, String> tagValueSanitizer;

    private char replacementChar = ValidCharacters.DEFAULT_REPLACEMENT_CHARACTER;
    private ValidCharacters nameValidCharacters;
    private ValidCharacters tagKeyValidCharacters;
    private ValidCharacters tagValueValidCharacters;

    public ScopeSanitizerBuilder withNameSanitizer(Function<String, String> nameSanitizer) {
        if (nameValidCharacters != null) {
            throw new IllegalArgumentException("only one of them can be provided: nameValidCharacters, nameSanitizer");
        }
        this.nameSanitizer = nameSanitizer;
        return this;
    }

    public ScopeSanitizerBuilder withTagKeySanitizer(Function<String, String> tagKeySanitizer) {
        if (tagKeyValidCharacters != null) {
            throw new IllegalArgumentException("only one of them can be provided: tagKeyValidCharacters, tagKeySanitizer");
        }
        this.tagKeySanitizer = tagKeySanitizer;
        return this;
    }

    public ScopeSanitizerBuilder withTagValueSanitizer(Function<String, String> tagValueSanitizer) {
        if (tagValueValidCharacters != null) {
            throw new IllegalArgumentException("only one of them can be provided: tagValueValidCharacters, tagValueSanitizer");
        }
        this.tagValueSanitizer = tagValueSanitizer;
        return this;
    }

    public ScopeSanitizerBuilder withReplacementCharacter(char replacementChar) {
        this.replacementChar = replacementChar;
        return this;
    }

    public ScopeSanitizerBuilder withNameValidCharacters(ValidCharacters validCharacters) {
        if(this.nameSanitizer != null){
            throw new IllegalArgumentException("only one of them can be provided: nameValidCharacters, nameSanitizer");
        }
        this.nameValidCharacters = validCharacters;
        return this;
    }

    public ScopeSanitizerBuilder withTagKeyValidCharacters(ValidCharacters validCharacters) {
        if(this.tagKeySanitizer != null){
            throw new IllegalArgumentException("only one of them can be provided: tagKeyValidCharacters, tagKeySanitizer");
        }
        this.tagKeyValidCharacters = validCharacters;
        return this;
    }

    public ScopeSanitizerBuilder withTagValueValidCharacters(ValidCharacters validCharacters) {
        if(this.tagValueSanitizer != null){
            throw new IllegalArgumentException("only one of them can be provided: tagValueValidCharacters, tagValueSanitizer");
        }
        this.tagValueValidCharacters = validCharacters;
        return this;
    }

    public ScopeSanitizer build() {
        if(nameSanitizer == null){
            nameSanitizer = Function.identity();
        }
        if (nameValidCharacters != null) {
            nameSanitizer = nameValidCharacters.sanitizeStringFunc(replacementChar);
        }

        if (tagKeySanitizer == null){
            tagKeySanitizer = Function.identity();
        }
        if (tagKeyValidCharacters != null) {
            tagKeySanitizer = tagKeyValidCharacters.sanitizeStringFunc(replacementChar);
        }

        if (tagValueSanitizer == null){
            tagValueSanitizer = Function.identity();
        }
        if (tagValueValidCharacters != null) {
            tagValueSanitizer = tagValueValidCharacters.sanitizeStringFunc(replacementChar);
        }
        return new SanitizerImpl(nameSanitizer, tagKeySanitizer, tagValueSanitizer);
    }
}
