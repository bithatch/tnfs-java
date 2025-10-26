/*
 * Copyright © 2025 Bithatch (brett@bithatch.co.uk)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package uk.co.bithatch.tnfs.server.extensions;

import static com.ongres.scram.common.util.Preconditions.checkNotNull;

import java.util.Arrays;

import com.ongres.scram.common.util.Preconditions;

/**
 * Helper class to generate Comma Separated Values of StringWritables.
 */
final class StringWritableCsv {

  private StringWritableCsv() {
    throw new IllegalStateException("Utility class");
  }

//  /**
//   * Write a sequence of StringWritableCsv to a StringBuffer. Null StringWritables are not printed,
//   * but separator is still used. Separator is a comma (',')
//   *
//   * @param sb The sb to write to
//   * @param values Zero or more attribute-value pairs to write
//   * @return The same sb, with data filled in (if any)
//   * @throws IllegalArgumentException If sb is null
//   */
//  static  StringBuilder writeTo( StringBuilder sb,
//     StringWritable... values) {
//    checkNotNull(sb, "sb");
//    if (null == values || values.length == 0) {
//      return sb;
//    }
//
//    if (null != values[0]) {
//      Preconditions.castNonNull(values[0]).writeTo(sb);
//    }
//    int i = 1;
//    while (i < values.length) {
//      sb.append(',');
//      if (null != values[i]) {
//        Preconditions.castNonNull(values[i]).writeTo(sb);
//      }
//      i++;
//    }
//
//    return sb;
//  }

  /**
   * Parse a String with a StringWritableCsv into its composing Strings represented as Strings. No
   * validation is performed on the individual attribute-values returned.
   *
   * @param value The String with the set of attribute-values
   * @param n Number of entries to return (entries will be null of there were not enough). 0 means
   *          unlimited
   * @param offset How many entries to skip before start returning
   * @return An array of Strings which represent the individual attribute-values
   * @throws IllegalArgumentException If value is null or either n or offset are negative
   */
  static  String  [] parseFrom( String value, int n, int offset) {
    checkNotNull(value, "value");
    if (n < 0 || offset < 0) {
      throw new IllegalArgumentException("Limit and offset have to be >= 0");
    }

    if (value.isEmpty()) {
      return new String[0];
    }

    String[] split = value.split(",", -1);
    if (split.length < offset) {
      throw new IllegalArgumentException("Not enough items for the given offset");
    }

    return Arrays.copyOfRange(
        split,
        offset,
        (n == 0 ? split.length : n) + offset);
  }

  /**
   * Parse a String with a StringWritableCsv into its composing Strings represented as Strings. No
   * validation is performed on the individual attribute-values returned. Elements are returned
   * starting from the first available attribute-value.
   *
   * @param value The String with the set of attribute-values
   * @param n Number of entries to return (entries will be null of there were not enough). 0 means
   *          unlimited
   * @return An array of Strings which represent the individual attribute-values
   * @throws IllegalArgumentException If value is null or n is negative
   */
  static  String  [] parseFrom( String value, int n) {
    return parseFrom(value, n, 0);
  }

  /**
   * Parse a String with a StringWritableCsv into its composing Strings represented as Strings. No
   * validation is performed on the individual attribute-values returned. All the available
   * attribute-values will be returned.
   *
   * @param value The String with the set of attribute-values
   * @return An array of Strings which represent the individual attribute-values
   * @throws IllegalArgumentException If value is null
   */
  static  String  [] parseFrom( String value) {
    return parseFrom(value, 0, 0);
  }
}
