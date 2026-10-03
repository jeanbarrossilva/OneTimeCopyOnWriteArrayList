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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.lang.Math.clamp;
import static java.lang.Math.max;
import static java.util.Arrays.asList;
import static java.util.Arrays.copyOf;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.checkFromToIndex;
import static java.util.Objects.checkIndex;

/**
 * An {@link ArrayList} with
 * <a href="https://en.wikipedia.org/wiki/Copy-on-write">copy-on-write</a> (CoW)
 * semantics.
 * <p>
 * Upon instantiating this class, the backing array won't be copied immediately,
 * nor copied at every write. Rather, it's only referenced strongly, with the
 * list acting as a view to the array by default; then, upon the first write to
 * the list (e.g., a call to {@link #add(Object)}), the array is copied and the
 * list's strong reference to it is dropped.
 * <p>
 * As a consequence, changes to the array are only reflected on the list until
 * the first write to the list. Conversely, changes to the list never alter the
 * array.
 * <p>
 * This class isn't thread-safe, as the backing array gets copied only one time
 * after the first write to the list; therefore, subsequent writes to the list
 * are subject to the same race conditions as a standard, non-concurrent {@link
 * ArrayList} is. For thread-safety, see {@link CopyOnWriteArrayList}.
 * <p>
 * Finally, this implementation is optimized for Android's libcore—although no
 * unsafe operations (e.g., reflection) are done and, thus, its outputs should
 * be the same on any JDK implementation.
 *
 * @author Jean Silva
 * @param <Element> An element of this list.
 */
public class OneTimeCopyOnWriteArrayList<Element> extends ArrayList<Element> {
  private Element[] backingArray;
  private boolean isImmutableTreeSetLike;

  // this allows us to optimize CoW. this being `false` denotes that we're in
  // the middle of copying the backing array to this list, and allows us to
  // make so that `toArray()`—which is how CoW gets done through libcore's
  // implementation— returns the backing array directly.
  private boolean willReturnSafeBackingArray = true;

  // ideally, this capacity is the same as the superclass'; but, because the
  // superclass' isn't part of Android API 36.1's libcore's public API, we
  // hard-code it here.
  static final int DEFAULT_INITIAL_CAPACITY = 10;

  final class Spliterator implements java.util.Spliterator<Element> {
    private int currentIndex;
    private int endIndex;

    static final int DEFAULT = SIZED | SUBSIZED;
    static final int IMMUTABLE_TREE_SET_LIKE =
      DEFAULT | DISTINCT | IMMUTABLE | NONNULL | ORDERED | SORTED;

    public Spliterator() {
      this(/* startIndex = */ 0, /* endIndex = */ backingArray.length);
    }

    public Spliterator(final int startIndex, final int endIndex) {
      this.currentIndex = startIndex;
      this.endIndex = endIndex;
    }

    @Override
    @SuppressWarnings("MagicConstant")
    public int characteristics() {
      return isImmutableTreeSetLike ? IMMUTABLE_TREE_SET_LIKE : DEFAULT;
    }

    @Override
    public Comparator<? super Element> getComparator() {
      return null;
    }

    @Override
    public boolean tryAdvance(final Consumer<? super Element> action) {
      if (currentIndex == endIndex)
        return false;
      if (action != null)
        action.accept(backingArray[currentIndex++]);
      return true;
    }

    @Override
    public Spliterator trySplit() {
      final long size = estimateSize();
      if (size == 0)
        return null;
      final var result = new Spliterator(
        /* startIndex = */
        clamp(currentIndex + size / 2, Integer.MIN_VALUE, Integer.MAX_VALUE),
        endIndex
      );
      endIndex = result.currentIndex;
      return result;
    }

    @Override
    public long estimateSize() {
      return endIndex - currentIndex;
    }
  }

  private final class Sublist extends AbstractList<Element>
    implements RandomAccess {
    private int startIndex;
    private int endIndex;

    Sublist(
      final int startIndex,
      final int endIndex
    ) throws IndexOutOfBoundsException {
      checkFromToIndex(startIndex, endIndex, backingArray.length);
      this.startIndex = startIndex;
      this.endIndex = endIndex;
    }

    @Override
    public boolean add(final Element element) {
      OneTimeCopyOnWriteArrayList.this.add(endIndex, element);
      return true;
    }

    @Override
    public void add(final int index, final Element element) {
      OneTimeCopyOnWriteArrayList.this.add(abs(index), element);
    }

    @Override
    public boolean addAll(final Collection<? extends Element> c) {
      return OneTimeCopyOnWriteArrayList.this.addAll(endIndex, c);
    }

    @Override
    public boolean addAll(
      final int index,
      final Collection<? extends Element> c
    ) {
      return OneTimeCopyOnWriteArrayList.this.addAll(abs(index), c);
    }

    @Override
    public void clear() {
      removeRange(startIndex, endIndex);
    }

    @Override
    public boolean equals(final Object o) {
      if (o instanceof List<?> castO) {
        if (size() != castO.size())
          return false;
        for (int index = 0; index < size(); index++)
          if (!Objects.equals(get(index), castO.get(index)))
            return false;
        return true;
      }
      return false;
    }

    @Override
    public Element get(final int index) throws IndexOutOfBoundsException {
      checkIndex(abs(index), size());
      return backingArray[abs(index)];
    }

    @Override
    public Iterator iterator() {
      return new Iterator(startIndex, endIndex);
    }

    @Override
    public boolean remove(final Object o) {
      final boolean didRemove = remove(indexOf(o)) != null;
      if (didRemove)
        shrink();
      return didRemove;
    }

    @Override
    public Element remove(final int index) {
      final Element removedElement =
        OneTimeCopyOnWriteArrayList.this.remove(abs(index));
      if (removedElement != null)
        shrink();
      return removedElement;
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
      if (c.isEmpty())
        return false;
      boolean didRemoveAny = false;
      for (int index = startIndex; index < endIndex; index++)
        for (final Object element: c)
          if (Objects.equals(backingArray[index], element)) {
            remove(index);
            didRemoveAny = true;
          }
      return didRemoveAny;
    }

    @Override
    public int size() {
      return endIndex - startIndex;
    }

    @Override
    public Spliterator spliterator() {
      return new Spliterator(startIndex, endIndex);
    }

    private int abs(final int index) {
      return startIndex + index;
    }

    private void shrink() {
      startIndex++;
      endIndex--;
    }
  }

  private class Iterator implements java.util.Iterator<Element> {
    private int previousIndex;
    private int endIndex;
    private boolean isImmutable;

    public Iterator() {
      this(/* startIndex = */ 0, /* endIndex = */ backingArray.length);
    }

    public Iterator(final int startIndex, final int endIndex) {
      checkFromToIndex(startIndex, endIndex, size());
      this.previousIndex = startIndex - 1;
      this.endIndex = endIndex;
      this.isImmutable = true;
    }

    @Override
    public Element next() throws NoSuchElementException {
      final int currentIndex = getCurrentIndex();
      final Element result;
      isImmutable = false;
      try { result = get(currentIndex); }
      catch (final IndexOutOfBoundsException cause) {
        throw new NoSuchElementException(cause);
      }
      previousIndex = currentIndex;
      return result;
    }

    @Override
    public boolean hasNext() {
      return getCurrentIndex() < endIndex;
    }

    @Override
    public void remove() throws IllegalStateException, NoSuchElementException {
      if (isImmutable)
        throw new IllegalStateException(
          "next() was never called; or removed twice after a next()"
        );
      try { OneTimeCopyOnWriteArrayList.this.remove(previousIndex); }
      catch (final IndexOutOfBoundsException cause) {
        throw new NoSuchElementException();
      }
      previousIndex--;
      endIndex--;
      isImmutable = true;
    }

    private int getCurrentIndex() {
      return previousIndex + 1;
    }
  }

  /**
   * Instantiates a one-time CoW array list, with linear-search-based indexing
   * and a default initial capacity of 10 elements for the backing array.
   */
  public OneTimeCopyOnWriteArrayList() {
    this(DEFAULT_INITIAL_CAPACITY);
  }

  /**
   * Instantiates a one-time CoW array list with linear-search-based indexing.
   *
   * @param initialCapacity Maximum amount of elements that the list will be
   *  able to hold until it grows or gets trimmed to its size, after its backing
   *  array has been copied.
   * @see #trimToSize()
   * @throws IllegalArgumentException If the initial capacity is negative.
   */
  public OneTimeCopyOnWriteArrayList(final int initialCapacity)
    throws IllegalArgumentException {
    super(initialCapacity);
    this.backingArray = null;
    this.isImmutableTreeSetLike = false;
  }

  /**
   * Instantiates a one-time CoW array list whose backing array might change or
   * contain incomparable, equal or unsorted elements. In such a list, elements
   * will be indexed linearly (rather than through binary search).
   *
   * @param backingArray Array to which the list acts as a backing array until
   *   the first modification on the list; it's also the array to be copied upon
   *   such modification.
   * @see #OneTimeCopyOnWriteArrayList(Object[], boolean)
   */
  public OneTimeCopyOnWriteArrayList(final Element[] backingArray) {
    this(backingArray, /* isImmutableTreeSetLike = */ false);
  }

  /**
   * Instantiates a one-time CoW array list containing the elements of another
   * iterable, in the iterable's defined order.
   * <p>
   * In case such iterable is a one-time CoW array list, its backing array will
   * be shared with the instantiated list; therefore, until it's written to, the
   * resulting list will also act as a view to that array. Otherwise, such
   * iterable's elements are copied to the new list.
   * <p>
   * Binary-search-based indexing will be supported by the instantiated list if
   * the given iterable is a one-time CoW array list, hasn't been written to and
   * supports it too, for as long as the resulting list remains unchanged; or
   * such iterable is a {@link TreeSet}. Both being false, indexing uses linear
   * search, as a standard {@link ArrayList} does.
   *
   * @param base Iterable from which the one-time CoW array list will be
   *   instantiated.
   */
  public OneTimeCopyOnWriteArrayList(
    final Iterable<? extends Element> base
  ) {
    super(
      /* initialCapacity = */
      base instanceof Collection<? extends Element> castBase
        ? castBase.size()
        : DEFAULT_INITIAL_CAPACITY
    );
    this.backingArray =
      base instanceof OneTimeCopyOnWriteArrayList<? extends Element> castBase
        ? castBase.backingArray
        : null;
    this.isImmutableTreeSetLike =
      base instanceof OneTimeCopyOnWriteArrayList<? extends Element> castBase
        && castBase.isImmutableTreeSetLike;
    if (!(base instanceof OneTimeCopyOnWriteArrayList<? extends Element>))
      super.addAll(asCollection(base));
  }

  /**
   * Instantiates a one-time CoW array list.
   *
   * @param backingArray Array to which the list acts as a view until the first
   *   write to the list; it's also the array to be copied upon such write.
   * @param isImmutableTreeSetLike Whether the backing array will remain
   *   unchanged throughout the list's lifetime and contain only comparable,
   *   distinct elements which are already sorted. This being {@code true}
   *   enables the list to index the backing array through binary search (as
   *   opposed to linearly).
   *   <p>
   *   The immutability, comparability, duplicate-free and sorting aspects are
   *   invariants, and ensuring they're satisfied is a responsibility of the
   *   caller. A one-time CoW list employs minimal to zero checking on whether
   *   these assumptions hold true, and violating them may result in a negative
   *   performance impact or undefined indexing.
   * @see Comparable
   * @see TreeSet
   */
  public OneTimeCopyOnWriteArrayList(
    final Element[] backingArray,
    final boolean isImmutableTreeSetLike
  ) {
    super(
      /* initialCapacity = */ backingArray == null
                                ? DEFAULT_INITIAL_CAPACITY
                                : backingArray.length
    );
    this.backingArray =
      backingArray == null || backingArray.length == 0 ? null : backingArray;
    this.isImmutableTreeSetLike = isImmutableTreeSetLike;
  }

  @Override
  public boolean add(final Element element) {
    if (backingArray != null)
      copyAndDereferenceBackingArray();
    return super.add(element);
  }

  @Override
  public boolean addAll(final int index, Collection<? extends Element> c) {
    if (backingArray != null) {
      checkIndex(index, backingArray.length + 1);
      copyAndDereferenceBackingArray();
    }
    return super.addAll(index, c);
  }

  @Override
  public boolean addAll(final Collection<? extends Element> c) {
    if (c.isEmpty())
      return false;
    if (backingArray != null)
      copyAndDereferenceBackingArray();
    return super.addAll(c);
  }

  @Override
  public void clear() {
    if (backingArray != null) {
      if (backingArray.length == 0)
        return;
      copyAndDereferenceBackingArray();
    }
    super.clear();
  }

  @Override
  @SuppressWarnings("unchecked")
  public ArrayList<Element> clone() {
    return backingArray == null
      ? (ArrayList<Element>) super.clone()
      : new OneTimeCopyOnWriteArrayList<>(backingArray, isImmutableTreeSetLike);
  }

  @Override
  public boolean contains(final Object o) {
    return indexOf(o) >= 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (backingArray == null)
      return super.equals(o);
    if (o instanceof List<?> castO) {
      if (backingArray.length != castO.size())
        return false;
      for (int index = 0; index < backingArray.length; index++)
        if (!Objects.equals(backingArray[index], castO.get(index)))
          return false;
      return true;
    }
    return false;
  }

  @Override
  public Element get(final int index) throws IndexOutOfBoundsException {
    return backingArray == null ? super.get(index) : backingArray[index];
  }

  @Override
  public Element getFirst() throws NoSuchElementException {
    if (backingArray == null)
      return super.getFirst();
    if (backingArray.length == 0)
      throw new NoSuchElementException();
    return backingArray[0];
  }

  @Override
  public Element getLast() throws NoSuchElementException {
    if (backingArray == null)
      return super.getLast();
    if (backingArray.length == 0)
      throw new NoSuchElementException();
    return backingArray[backingArray.length - 1];
  }

  @Override
  public int hashCode() {
    return backingArray == null
      ? super.hashCode()
      : Arrays.hashCode(backingArray);
  }

  @Override
  public int indexOf(final Object o) {
    if (backingArray == null)
      return super.indexOf(o);
    if (isImmutableTreeSetLike)
      return findIndexWithBinaryOrLinearSearchFromHead(o);
    return findIndexWithLinearSearchFromHead(o);
  }

  @Override
  public boolean isEmpty() {
    return backingArray == null ? super.isEmpty() : backingArray.length == 0;
  }

  @Override
  public java.util.Iterator<Element> iterator() {
    return backingArray == null ? super.iterator() : new Iterator();
  }

  @Override
  @SuppressWarnings("CatchMayIgnoreException")
  public int lastIndexOf(final Object o) {
    if (backingArray == null)
      return super.lastIndexOf(o);
    if (isImmutableTreeSetLike)
      try { return findIndexWithBinarySearch(o); }
      catch (final IllegalStateException exception) {}
    for (int index = backingArray.length - 1; index >= 0; index--)
      if (Objects.equals(backingArray[index], o))
        return index;
    return -1;
  }

  @Override
  public ListIterator<Element> listIterator() {
    return backingArray == null
      ? super.listIterator()
      : asList(backingArray).listIterator();
  }

  @Override
  public ListIterator<Element> listIterator(final int index) {
    return backingArray == null
      ? super.listIterator(index)
      : asList(backingArray).listIterator(index);
  }

  @Override
  @SuppressWarnings("CatchMayIgnoreException")
  public boolean remove(final Object o) {
    if (isImmutableTreeSetLike) {
      int index = -2;
      try { index = findIndexWithBinarySearch(o); }
      catch (final IllegalStateException exception) {}
      if (index == -1)
        return false;
      else if (index >= 0) {
        copyAndDereferenceBackingArray();
        super.remove(index);
        return true;
      }
    }
    if (backingArray != null)
      copyAndDereferenceBackingArray();
    return super.remove(o);
  }

  @Override
  public Element remove(final int index) throws IndexOutOfBoundsException {
    if (backingArray != null) {
      checkIndex(index, backingArray.length);
      copyAndDereferenceBackingArray();
    }
    return super.remove(index);
  }

  @Override
  public boolean removeAll(final Collection<?> c) {
    if (c.isEmpty())
      return false;
    if (backingArray != null)
      copyAndDereferenceBackingArray();
    return super.removeAll(c);
  }

  @Override
  public Element removeFirst() throws NoSuchElementException {
    if (backingArray == null)
      return super.removeFirst();
    if (backingArray.length == 0)
      throw new NoSuchElementException();
    copyAndDereferenceBackingArray();
    return super.removeFirst();
  }

  @Override
  public boolean removeIf(final Predicate<? super Element> filter)
    throws NullPointerException {
    if (backingArray != null) {
      if (backingArray.length == 0)
        return false;
      copyAndDereferenceBackingArray();
    }
    return super.removeIf(filter);
  }

  @Override
  public Element removeLast() throws NoSuchElementException {
    if (backingArray == null)
      return super.removeLast();
    if (backingArray.length == 0)
      throw new NoSuchElementException();
    copyAndDereferenceBackingArray();
    return super.removeLast();
  }

  @Override
  public Element set(final int index, final Element element)
    throws IndexOutOfBoundsException {
    if (backingArray != null) {
      checkIndex(index, backingArray.length);
      final Element oldElement = backingArray[index];
      if (Objects.equals(oldElement, element))
        return oldElement;
      copyAndDereferenceBackingArray();
    }
    return super.set(index, element);
  }

  @Override
  public int size() {
    return backingArray == null ? super.size() : backingArray.length;
  }

  @Override
  public java.util.Spliterator<Element> spliterator() {
    return backingArray == null ? super.spliterator() : new Spliterator();
  }

  @Override
  public List<Element> subList(final int fromIndex, final int toIndex)
    throws IndexOutOfBoundsException {
    return backingArray == null
      ? super.subList(fromIndex, toIndex)
      : new Sublist(fromIndex, toIndex);
  }

  @Override
  public Object[] toArray() {
    if (backingArray == null)
      return super.toArray();
    if (willReturnSafeBackingArray)
      // we're (possibly) copying the array to this list, so we don't need to
      // follow the interface contract of always returning a "safe" array.
      // this is for our use, only.
      return backingArray;
    return copyOf(backingArray, backingArray.length);
  }

  @Override
  @SuppressWarnings({"RedundantCast", "unchecked"})
  public <T> T[] toArray(final T[] a) throws NullPointerException {
    if (backingArray == null)
      return super.toArray(a);
    if (a == null)
      throw new NullPointerException("a");
    if (backingArray.length > a.length)
      return (T[]) copyOf(backingArray, backingArray.length, a.getClass());
    System.arraycopy(
      /* src = */     backingArray,
      /* srcPos = */  0,
      /* dest = */    (Object[]) a,
      /* destPos = */ 0,
      /* length = */  backingArray.length
    );
    if (backingArray.length < a.length)
      a[backingArray.length] = null;
    return a;
  }

  @Override
  protected void removeRange(final int fromIndex, final int toIndex)
    throws IndexOutOfBoundsException {
    if (backingArray != null) {
      checkFromToIndex(fromIndex, toIndex, backingArray.length);
      copyAndDereferenceBackingArray();
    }
    super.removeRange(fromIndex, toIndex);
  }

  private static <Element> Collection<? extends Element> asCollection(
    final Iterable<Element> self
  ) {
    if (self instanceof Collection<? extends Element> castSelf)
      return castSelf;
    final ArrayList<Element> backingList = new ArrayList<>();
    for (final Element element: self)
      backingList.add(element);
    return unmodifiableList(backingList);
  }

  private void copyAndDereferenceBackingArray() {
    // libcore's implementation of addAll() will resort to toArray(), which,
    // normally, would copy the array. but, in this very specific case, copying
    // it isn't what we want; we'll cheat.
    willReturnSafeBackingArray = false;
    super.addAll(this);
    willReturnSafeBackingArray = true;
    backingArray = null;
    isImmutableTreeSetLike = false;
  }

  // findIndexWithBinary*() methods presuppose that isImmutableTreeSetLike is
  // true: their optimization only makes sense if the instantiator did promise
  // to us that an element of the backing array can be found via binary search.

  @SuppressWarnings("CatchMayIgnoreException")
  private int findIndexWithBinaryOrLinearSearchFromHead(final Object key) {
    try { return findIndexWithBinarySearch(key); }
    catch (final IllegalStateException exception) {}
    return findIndexWithLinearSearchFromHead(key);
  }

  private int findIndexWithBinarySearch(final Object key)
    throws IllegalStateException {
    try { return max(Arrays.binarySearch(backingArray, key), -1); }
    catch (final ClassCastException | IllegalArgumentException cause) {
      // welp! we were lied to… :(
      //
      // us getting here denotes that the instantiator of this list told us that
      // the backing array adheres to the contract—even though it doesn't, since
      // we've found an element that's incomparable (casting) or out of order
      // (illegal argument).
      isImmutableTreeSetLike = false;
      throw new IllegalStateException(cause);
    }
  }

  private int findIndexWithLinearSearchFromHead(final Object key) {
    for (int index = 0; index < backingArray.length; index++)
      if (Objects.equals(key, backingArray[index]))
        return index;
    return -1;
  }
}