/*
 * Copyright © 2026 Jean Silva
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *                  https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.jeanbarrossilva.dias.core;

import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.description.Description;

import java.util.Arrays;
import java.util.function.Function;

import static org.assertj.core.api.InstanceOfAssertFactories.ARRAY;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

/**
 * AssertJ asserter specialized on {@link OneTimeCopyOnWriteArrayList}s.
 *
 * @param <Element> An element of the list.
 */
public class OneTimeCopyOnWriteArrayListAssert<Element>
  extends AbstractListAssert<OneTimeCopyOnWriteArrayListAssert<Element>,
                             OneTimeCopyOnWriteArrayList<Element>,
                             Element,
                             ObjectAssert<Element>> {
  private final static Description NULL_DESCRIPTION = new Description() {
    @Override
    public String value() {
      return "null";
    }
  };

  private OneTimeCopyOnWriteArrayListAssert(
    final OneTimeCopyOnWriteArrayList<Element> actual
  ) {
    super(actual, OneTimeCopyOnWriteArrayListAssert.class);
  }

  /**
   * Asserts that writes to the backing array are reflected on the list,
   * denoting that CoW hasn't taken place. Once this method returns or throws,
   * both the list's actual backing array and the given one remain unchanged.
   *
   * @param baseBackingArray The backing array with which the list was
   *   instantiated. It's passed in as an argument because {@link
   *   #assertThat(OneTimeCopyOnWriteArrayList)} may have been called
   *   <i>after</i> the list CoWed; in such a case, its backing array would
   *   already have been dereferenced.
   * @param differentiator Generator of an element referentially different from
   *   the received one.
   * @see #didCoW()
   */
  public OneTimeCopyOnWriteArrayListAssert<Element> didNotCoW(
    final Element[] baseBackingArray,
    final Function<Element, Element> differentiator
  ) throws AssertionError {
    backingArray()
      .asInstanceOf(ARRAY)
      .containsExactly(baseBackingArray)
      .isNotEmpty();
    final Element oldElement = baseBackingArray[0];
    baseBackingArray[0] = differentiator.apply(oldElement);
    final boolean didCow = actual().getFirst() != baseBackingArray[0];
    baseBackingArray[0] = oldElement;
    if (didCow)
      failWithMessage(
        "unexpected CoW; writes to the backing array aren't reflected on the " +
          OneTimeCopyOnWriteArrayList.class.getSimpleName()
      );
    return this;
  }

  /**
   * Asserts that the list's backing array has been dereferenced and,
   * consequently, that the list isn't a view to that array anymore. By the time
   * this assertion is made, the list should've been written to (e.g., had an
   * element added to or removed from it) and be independent of that array.
   */
  public OneTimeCopyOnWriteArrayListAssert<Element> didCoW()
    throws AssertionError {
    final Object[] backingArray = backingArray().actual();
    if (backingArray != null)
      failWithActualExpectedAndMessage(
        new Description() {
          @Override
          public String value() {
            return Arrays.toString(backingArray);
          }
        },
        NULL_DESCRIPTION,
        "didn't CoW; "
          + OneTimeCopyOnWriteArrayList.class.getSimpleName()
          + " still references its backing array"
      );
    return this;
  }

  /**
   * Instantiates an asserter for a {@link OneTimeCopyOnWriteArrayList}.
   *
   * @param actual One-time CoW array list for which the returned asserter is.
   * @param <Element> An element of the given list.
   */
  public static <Element> OneTimeCopyOnWriteArrayListAssert<Element> assertThat(
    final OneTimeCopyOnWriteArrayList<Element> actual
  ) {
    return new OneTimeCopyOnWriteArrayListAssert<>(actual);
  }

  @Override
  protected ObjectAssert<Element> toAssert(
    final Element value,
    final String description
  ) {
    return Assertions.assertThat(value).as(description);
  }

  @Override
  protected OneTimeCopyOnWriteArrayListAssert<Element> newAbstractIterableAssert(
    final Iterable<? extends Element> iterable
  ) {
    return assertThat(new OneTimeCopyOnWriteArrayList<>(iterable));
  }

  /** Obtains an asserter for the list's backing array. */
  private ObjectAssert<Object[]> backingArray() throws AssertionError {
    try {
      return asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
        .extracting("backingArray", Assertions.as(type(Object[].class)));
    } catch (final AssertionError error) {
      final String message = error.getMessage();
      if (message == null)
        throw error;

      // since the error is "untyped", we're relying on its message, which is
      // an implementation detail and can be different across versions of
      // AssertJ. not ideal; also, not a huge deal.
      if (message.lines()
                 .skip(1)
                 .findFirst()
                 .map(line -> !line.equals("Expecting actual not to be null"))
                 .orElse(true))
        throw error;

      return new ObjectAssert<>(null);
    }
  }
}