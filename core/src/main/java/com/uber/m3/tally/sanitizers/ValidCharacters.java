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

package com.uber.m3.tally.sanitizers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    public static final List<CharRange> ALPHANUMERIC_RANGE =
        Arrays.asList(
            CharRange.of('a', 'z'),
            CharRange.of('A', 'Z'),
            CharRange.of('0', '9'));

    /**
     * UNDERSCORE_CHARACTERS contains the underscore character.
     */
    public static final List<Character> UNDERSCORE_CHARACTERS = Collections.singletonList('_');

    /**
     * UNDERSCORE_DASH_CHARACTERS contains the underscore and dash characters.
     */
    public static final List<Character> UNDERSCORE_DASH_CHARACTERS = Arrays.asList('_', '-');

    /**
     * UNDERSCORE_DASH_DOT_CHARACTERS contains the underscore, dash and dot characters.
     */
    public static final List<Character> UNDERSCORE_DASH_DOT_CHARACTERS = Arrays.asList('_', '-', '.');

    private final List<CharRange> ranges;
    private final List<Character> characters;

    private ValidCharacters(List<CharRange> ranges, List<Character> characters) {
        this.ranges = ranges != null ? ranges : Collections.emptyList();
        this.characters = characters != null ? characters : Collections.emptyList();
    }

    public static ValidCharacters of(List<CharRange> ranges, List<Character> characters) {
        return new ValidCharacters(ranges, characters);
    }

    StringSanitizer sanitize(char repChar) {
        return value -> {
            StringBuilder buffer = null;

            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);

                // first check if the provided character is valid
                boolean validCurr =
                    ranges.stream().anyMatch(range -> ch >= range.low() && ch <= range.high())
                        || characters.stream().anyMatch(character -> character.equals(ch));

                // if it's valid, we can optimize allocations by avoiding copying
                if (validCurr) {
                    if (buffer != null) {
                        buffer.append(ch);
                    }
                    continue;
                }

                // the character is invalid, and the buffer has not been initialized
                // so we initialize the buffer and back-fill.
                if (buffer == null) {
                    buffer = new StringBuilder(value.length());
                    buffer.append(value, 0, i);
                }

                // write the replacement character
                buffer.append(repChar);
            }

            // return input un-touched if the buffer has not been initialized
            // otherwise, return the newly constructed buffer string
            return buffer == null ? value : buffer.toString();
        };
    }
}
