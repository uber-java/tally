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

package com.uber.m3.tally.sanitizers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * ValidCharacters is a collection of valid characters.
 */
public class ValidCharacters {

    /**
     * DEFAULT_REPLACEMENT_CHARACTER is the default character used for replacements.
     */
    public static char DEFAULT_REPLACEMENT_CHARACTER = '_';

    /**
     * ALPHANUMERIC_RANGE is the range of alphanumeric characters.
     */
    public static final Set<CharRange> ALPHANUMERIC_RANGE =
            new HashSet<>(Arrays.asList(
            CharRange.of('a', 'z'),
            CharRange.of('A', 'Z'),
            CharRange.of('0', '9')));

    /**
     * UNDERSCORE_CHARACTERS contains the underscore character.
     */
    public static final Set<Character> UNDERSCORE_CHARACTERS = Collections.singleton('_');

    /**
     * UNDERSCORE_DASH_CHARACTERS contains the underscore and dash characters.
     */
    public static final Set<Character> UNDERSCORE_DASH_CHARACTERS = new HashSet<>(Arrays.asList('_', '-'));

    /**
     * UNDERSCORE_DASH_DOT_CHARACTERS contains the underscore, dash and dot characters.
     */
    public static final Set<Character> UNDERSCORE_DASH_DOT_CHARACTERS = new HashSet<>(Arrays.asList('_', '-', '.'));

    private final Set<CharRange> validRanges;
    private final Set<Character> validCharacters;

    private ValidCharacters(Set<CharRange> validRanges, Set<Character> validCharacters) {
        this.validRanges = (validRanges != null) ? validRanges : Collections.emptySet();
        this.validCharacters = (validCharacters != null) ? validCharacters : Collections.emptySet();
    }

    /**
     * returns an instance of ValidCharacters
     * @param ranges
     * @param characters
     * @return
     */
    public static ValidCharacters of(Set<CharRange> ranges, Set<Character> characters) {
        return new ValidCharacters(ranges, characters);
    }

    /**
     * returns if a char is valid
     * a char is valid if it's within range of any range of validRanges  or within validCharacters
     * @param ch
     * @return
     */
    private boolean isValid(char ch){
        return  validRanges.stream().anyMatch(range -> (range.isWithinRange(ch)))
                || validCharacters.contains(ch);
    }

    Function<String, String> sanitizeStringFunc(char replaceChar) {
        return input -> {
            StringBuilder output = null;

            for (int i = 0; i < input.length(); i++) {
                char currChar = input.charAt(i);

                // first check if the provided character is valid
                boolean isCurrValid = isValid(currChar);

                // if it's valid, we can optimize allocations by avoiding copying
                if (isCurrValid) {
                    if (output != null) {
                        output.append(currChar);
                    }
                    continue;
                }

                // the character is invalid, and the buffer has not been initialized
                // so we initialize the buffer and back-fill.
                if (output == null) {
                    output = new StringBuilder(input.length());
                    output.append(input, 0, i);
                }

                // write the replacement character
                output.append(replaceChar);
            }

            // return input un-touched if the buffer has not been initialized
            // otherwise, return the newly constructed buffer string
            return (output == null) ? input : output.toString();
        };
    }
}
