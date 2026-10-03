package com.jeanbarrossilva.dias.core.test;

import com.google.common.collect.ContiguousSet;
import com.jeanbarrossilva.dias.core.OneTimeCopyOnWriteArrayList;

import java.util.ArrayList;

/** Extensions for {@link OneTimeCopyOnWriteArrayList}s. */
public final class OneTimeCopyOnWriteArrayLists {
  private OneTimeCopyOnWriteArrayLists() {}

  /**
   * Generates an array intended to back a list. Instantiating a list with the
   * returned array and {@code isImmutableTreeSet} = {@code true} enables the
   * list to find elements in it using binary search (as opposed to linearly, as
   * a standard {@link ArrayList} does).
   *
   * @param length Amount of elements in the backing array.
   * @see OneTimeCopyOnWriteArrayList#OneTimeCopyOnWriteArrayList(Object[], boolean)
   */
  public static Integer[] sampleImmutableTreeSetLikeBackingArray(
    final int length
  ) {
    final Integer[] result = new Integer[length];
    ContiguousSet.closedOpen(0, length).toArray(result);
    return result;
  }
}
