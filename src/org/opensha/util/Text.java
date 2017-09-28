package org.opensha.util;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;

/**
 * Miscellaneous {@code String} utilities.
 *
 * @author Peter Powers
 */
public class Text {

  /** System specific newline string. */
  public static final String NEWLINE = StandardSystemProperty.LINE_SEPARATOR.value();

  /** Null string ("null"). */
  public static final String NULL = "null";

  /**
   * Verifies that the supplied {@code String} is neither {@code null} or empty.
   * Method returns the supplied value and can be used inline.
   *
   * @param name to verify
   * @throws IllegalArgumentException if name is {@code null} or empty
   */
  public static String validateName(String name) {
    checkArgument(!Strings.nullToEmpty(name).trim().isEmpty(), "Name may not be empty or null");
    return name;
  }

}
