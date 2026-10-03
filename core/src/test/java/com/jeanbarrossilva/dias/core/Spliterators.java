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

import java.util.ArrayList;
import java.util.Spliterator;

import static java.util.Collections.unmodifiableList;
import static org.apache.commons.collections4.list.LazyList.lazyList;

/** Extensions for {@link Spliterator}s. */
public class Spliterators {
  private Spliterators() {}

  /**
   * Converts a spliterator into an iterable.
   *
   * @param self Spliterator to be converted into a list.
   * @param <Element> An element of the spliterator.
   */
  @SuppressWarnings("unchecked")
  public static <Element> Iterable<Element> toIterable(
    final Spliterator<? extends Element> self
  ) {
    if (!self.hasCharacteristics(Spliterator.SIZED)) {
      final var backingList = new ArrayList<Element>();
      return lazyList(backingList, () -> {
        final Element[] elements = (Element[]) new Object[1];
        self.tryAdvance(element -> elements[0] = element);
        return elements[0];
      });
    }
    final long exactSize = self.getExactSizeIfKnown();
    final var backingList =
      new ArrayList<Element>(/* initialCapacity = */ clamped(exactSize));
    self.forEachRemaining(backingList::add);
    return unmodifiableList(backingList);
  }

  private static int clamped(final long n) {
    return Math.clamp(n, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }
}