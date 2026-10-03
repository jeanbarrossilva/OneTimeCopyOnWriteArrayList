package com.jeanbarrossilva.dias.core;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assert;

import java.util.function.Function;

/** Extensions for {@link Assert}s. */
public class Asserts {
  private Asserts() {}

  /**
   * Combines two asserts, joining their description.
   *
   * @param <Self> Asserter to be transformed.
   * @param <Result> Result of the transformation.
   * @param self Assert to which the other resulted from the transformation will
   *   be combined.
   * @param transform Transforms {@code self} into another assert.
   * @return The result of the transformation, whose description is a
   *   combination of the description of both asserts.
   * @throws NullPointerException If the transformation yields a {@code null}
   *   assert.
   */
  @SuppressWarnings("unchecked")
  public static <Self extends AbstractAssert<?, ?>,
                 Result extends AbstractAssert<?, ?>> Result combine(
    final Self self,
    final Function<Self, Result> transform
  ) throws NullPointerException {
    if (self == null)
      return null;
    final String combinedDescriptionPrefix = self.info.hasDescription()
      ? self.descriptionText()
      : self.actual().getClass().getSimpleName();
    Result result = transform.apply(self);
    if (result == null)
      throw new NullPointerException("transformed into null");
    result = (Result) result
      .as(combinedDescriptionPrefix + result.descriptionText());
    return result;
  }
}