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

package com.jeanbarrossilva;

import com.google.common.collect.ContiguousSet;
import com.jeanbarrossilva.OneTimeCopyOnWriteArrayList.Spliterator;
import com.speedment.common.function.TriConsumer;

import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.ObjectAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static com.jeanbarrossilva.Asserts.combine;
import static com.jeanbarrossilva.InstanceOfAssertFactories.COPY_ON_WRITE_ARRAY_LIST;
import static com.jeanbarrossilva.OneTimeCopyOnWriteArrayListAssert.assertThat;
import static com.jeanbarrossilva.Spliterators.toIterable;
import static java.util.Arrays.asList;
import static java.util.Arrays.fill;
import static java.util.Arrays.stream;
import static java.util.Collections.shuffle;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.ARRAY;
import static org.assertj.core.api.InstanceOfAssertFactories.BOOLEAN;
import static org.assertj.core.api.InstanceOfAssertFactories.INTEGER;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  OneTimeCopyOnWriteArrayListTests.AdditionTests.class,
  OneTimeCopyOnWriteArrayListTests.BackingArraySharingTests.class,
  OneTimeCopyOnWriteArrayListTests.CloningTests.class,
  OneTimeCopyOnWriteArrayListTests.ComparisonTests.class,
  OneTimeCopyOnWriteArrayListTests.ContainingTests.class,
  OneTimeCopyOnWriteArrayListTests.ConversionTests.class,
  OneTimeCopyOnWriteArrayListTests.IndexingTests.class,
  OneTimeCopyOnWriteArrayListTests.InitialCapacityTests.class,
  OneTimeCopyOnWriteArrayListTests.IteratorTests.class,
  OneTimeCopyOnWriteArrayListTests.RemovalTests.class,
  OneTimeCopyOnWriteArrayListTests.SubListingTests.class,
  OneTimeCopyOnWriteArrayListTests.SpliteratorTests.class,
  OneTimeCopyOnWriteArrayListTests.ViewingTests.class,
})
public class OneTimeCopyOnWriteArrayListTests {
  private static final int DEFAULT_SAMPLE_COUNT = 12;
  private static final ContiguousSet<Integer> SUBLIST_RANGE =
    ContiguousSet.closedOpen(2, DEFAULT_SAMPLE_COUNT - 2);
  private static final int SUBLIST_RANGE_END_INDEX =
    SUBLIST_RANGE.first() + SUBLIST_RANGE.size();

  public static final class AdditionTests {
    @Test
    public void adds() {
      withEmptyLists((list, isBacked) -> {
        final Integer[] addedElements =
          sampleNonImmutableTreeSetLikeArray(/* size = */ 2);
        list.add(addedElements[0]);
        list.add(1, addedElements[1]);
        assertThat(list).containsExactly(addedElements);
      });
    }

    @Test
    public void addingEmptyCollectionToBackedListDoesNotCoW() {
      final Integer[] backingArray = sampleNonImmutableTreeSetLikeArray();
      final var list = new OneTimeCopyOnWriteArrayList<>(backingArray);
      list.addAll(List.of());
      assertThat(list)
        .didNotCoW(backingArray, OneTimeCopyOnWriteArrayListTests::random);
    }

    @Test
    public void addsPopulatedCollection() {
      withEmptyLists((arrayList, isBacked) -> {
        final OneTimeCopyOnWriteArrayListAssert<Integer> listAssert =
          assertThat(arrayList);
        final List<Integer> addedList = sampleNonImmutableTreeSetLikeList();
        arrayList.addAll(addedList);
        if (isBacked)
          listAssert.didCoW();
        listAssert.containsExactlyElementsOf(addedList);
      });
    }
  }

  public static final class BackingArraySharingTests {
    @Test
    public void sharesBackingArrayWithAnotherOneTimeCoWArrayList() {
      final Integer[] backingArray = sampleNonImmutableTreeSetLikeArray();
      final var baseList = new OneTimeCopyOnWriteArrayList<>(backingArray);
      final var otherList = new OneTimeCopyOnWriteArrayList<>(baseList);
      final var newFirstElement = random(backingArray[0]);
      backingArray[0] = newFirstElement;
      assertThat(baseList)
        .didNotCoW(backingArray, OneTimeCopyOnWriteArrayListTests::random)
        .first()
        .isEqualTo(newFirstElement);
      assertThat(otherList)
        .didNotCoW(backingArray, OneTimeCopyOnWriteArrayListTests::random)
        .first()
        .isEqualTo(newFirstElement);
    }
  }

  public static final class CloningTests {
    @Test
    public void clones() {
      withSampleLists(
        (arrayList, baseList, isBacked) -> assertThat(arrayList)
          .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
          .extracting(OneTimeCopyOnWriteArrayList::clone)
          .asInstanceOf(COPY_ON_WRITE_ARRAY_LIST)
          .isNotSameAs(arrayList)
          .isEqualTo(arrayList)
      );
    }

    @Test
    public void sharesBackingArrayWithClone() {
      final Integer[] backingArray = sampleNonImmutableTreeSetLikeArray();
      final var originalList = new OneTimeCopyOnWriteArrayList<>(backingArray);
      final var cloneList =
        (OneTimeCopyOnWriteArrayList<Integer>) originalList.clone();
      assertThat(originalList)
        .didNotCoW(backingArray, OneTimeCopyOnWriteArrayListTests::random);
      assertThat(cloneList)
        .didNotCoW(backingArray, OneTimeCopyOnWriteArrayListTests::random);
    }
  }

  public static final class ComparisonTests {
    @Test
    public void onlyEqualsToEmptyListWhenEmpty() {
      withEmptyLists(
        (list, isBacked) -> assertThat(list)
          .isNotEqualTo(random())
          .isNotEqualTo(sampleNonImmutableTreeSetLikeList())
          .isEqualTo(List.of())
      );
    }

    @Test
    public void onlyEqualsToListWithSameElementsWhenPopulated() {
      withSampleLists(
        (list, baseList, isBacked) -> assertThat(list)
          .isNotEqualTo(random())
          .isNotEqualTo(List.of())
          .isEqualTo(baseList)
      );
    }
  }

  public static final class ContainingTests {
    @SuppressWarnings("unchecked")
    @Test
    public void emptyListContainsNoElements() {
      withEmptyLists((list, isBacked) -> {
        final OneTimeCopyOnWriteArrayListAssert<Integer> listAssert =
          assertThat(list);
        combine(
          listAssert,
          baseAssert -> baseAssert
            .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
            .extracting(actual -> actual.contains(0), as(BOOLEAN))
            .describedAs(".contains(0)")
        )
          .isFalse();
        combine(
          listAssert,
          baseAssert -> baseAssert
            .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
            .extracting(actual -> actual.containsAll(List.of()), as(BOOLEAN))
            .describedAs(".containsAll([])")
        )
          .isTrue();
      });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void populatedBackedListContainsElements() {
      final ContiguousSet<Integer> extraneousSet = ContiguousSet
        .closedOpen(DEFAULT_SAMPLE_COUNT, DEFAULT_SAMPLE_COUNT * 2 + 1);
      withSampleLists((arrayList, baseList, isBacked) -> {
        final OneTimeCopyOnWriteArrayListAssert<Integer> arrayListAssert =
          assertThat(arrayList);
        for (final Object extraneousElement: extraneousSet)
          combine(
            arrayListAssert,
            baseAssert -> baseAssert
              .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
              .extracting(
                actual -> actual.contains(extraneousElement),
                as(BOOLEAN)
              )
              .describedAs(".contains(" + extraneousElement + ')')
          )
            .isFalse();
        for (final Object baseElement: baseList)
          combine(
            arrayListAssert,
            baseAssert -> baseAssert
              .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
              .extracting(actual -> actual.contains(baseElement), as(BOOLEAN))
              .describedAs(".contains(" + baseElement + ')')
          )
            .isTrue();
        for (final List<Integer> containedList: new List[]{List.of(), baseList})
          combine(
            arrayListAssert,
            baseAssert -> baseAssert
              .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
              .extracting(
                actual -> actual.containsAll(containedList),
                as(BOOLEAN)
              )
              .describedAs(".containsAll(" + containedList + ')')
          )
            .isTrue();

        combine(
          arrayListAssert,
          baseAssert -> baseAssert
            .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
            .extracting(
              actual -> actual.containsAll(extraneousSet),
              as(BOOLEAN)
            )
            .describedAs(".containsAll(" + extraneousSet + ')')
        )
          .isFalse();
      });
    }
  }

  public static final class ConversionTests {
    @Test
    public void throwsWhenCopyingToNullArray() {
      withSampleLists(
        (arrayList, baseList, isBacked) -> {
          final AbstractThrowableAssert<?, ?> exceptionAssert =
            assertThatThrownBy(() -> arrayList.toArray((Integer[]) null))
              .isInstanceOf(NullPointerException.class);
          if (isBacked)
            exceptionAssert.hasMessage("a");
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void copiesToSuggestedArrayWhenElementsFitIntoIt() {
      withSampleLists(
        (arrayList, baseList, isBacked) -> {
          final Integer[] suggestedArray = new Integer[DEFAULT_SAMPLE_COUNT];
          combine(
            assertThat(arrayList),
            baseAssert -> baseAssert
              .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
              .extracting(actual -> actual.toArray(suggestedArray), as(ARRAY))
              .describedAs(".toArray(" + Arrays.toString(suggestedArray) + ')')
          )
            .containsExactlyElementsOf(arrayList)
            .isSameAs(suggestedArray);
        }
      );
    }

    @SuppressWarnings("unchecked")
    @Test
    public void setsSentinelValueWhenCopyingToAnArrayLargerThanTheList() {
      withSampleLists(
        (arrayList, baseList, isBacked) -> {
          final Integer[] suggestedArray =
            new Integer[DEFAULT_SAMPLE_COUNT + 1];
          fill(suggestedArray, -1);
          combine(
            assertThat(arrayList),
            baseAssert -> baseAssert
              .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
              .extracting(actual -> actual.toArray(suggestedArray), as(ARRAY))
              .describedAs(".toArray(" + Arrays.toString(suggestedArray) + ')')
          )
            .containsAll(arrayList)
            .isSameAs(suggestedArray);
          combine(
            assertThat(suggestedArray),
            baseAssert -> {
              final int index = suggestedArray.length - 1;
              return baseAssert
                .asInstanceOf(type(Integer[].class))
                .extracting(actual -> actual[index])
                .describedAs("[" + index + ']');
            }
          )
            .isNull();
        }
      );
    }

    @SuppressWarnings("unchecked")
    @Test
    public void copiesToReturnedArrayWhenElementsDoNotFitIntoSuggestedOne() {
      withAllLists(
        (arrayList, baseList, isBacked) -> {
          final Integer[] suggestedArray =
            new Integer[DEFAULT_SAMPLE_COUNT - 1];
          combine(
            assertThat(arrayList),
            baseAssert -> baseAssert
              .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
              .extracting(actual -> actual.toArray(suggestedArray), as(ARRAY))
              .describedAs(".toArray(" + Arrays.toString(suggestedArray) + ')')
          )
            .containsExactlyElementsOf(
              arrayList.isEmpty() ? asList(suggestedArray) : arrayList
            );
        }
      );
    }

    @Test
    public void convertsToArray() {
      withAllLists(
        (arrayList, baseList, isBacked) -> assertThat(arrayList)
          .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
          .extracting(OneTimeCopyOnWriteArrayList::toArray, as(ARRAY))
          .containsExactlyElementsOf(baseList)
      );
    }
  }

  public static final class IndexingTests {
    @Test
    public void returnsNegativeOneWhenFindingIndexOfFirstElementNotInTheList() {
      withEmptyLists(
        (list, isBacked) -> assertThat(list.indexOf(random())).isEqualTo(-1)
      );
    }

    @Test
    public void findsIndexOfFirst() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        final int index = baseList.size() - 1;
        assertThat(arrayList.indexOf(baseList.get(index))).isEqualTo(index);
      });
    }

    @Test
    public void returnsNegativeOneWhenFindingIndexOfLastElementNotInTheList() {
      withEmptyLists(
        (list, isBacked) -> assertThat(list.lastIndexOf(random()))
          .isEqualTo(-1)
      );
    }

    @Test
    public void findsIndexOfLast() {
      withSampleLists(
        (arrayList, baseList, isBacked) ->
          assertThat(arrayList.lastIndexOf(baseList.getFirst())).isZero()
      );
    }
  }

  public static final class InitialCapacityTests {
    @Test
    public void throwsWhenInitialCapacityIsNegative() {
      assertThatThrownBy(() -> new OneTimeCopyOnWriteArrayList<>(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Illegal Capacity: -1");
    }
  }

  public static final class IteratorTests {
    @Test
    public void iterates() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        final Iterator<Integer> iterator = arrayList.iterator();
        final var iteratorAssert = new ObjectAssert<>(iterator);
        for (int index = 0; iterator.hasNext(); index++)
          iteratorAssert
            .extracting(Iterator::next)
            .isEqualTo(baseList.get(index));
      });
    }

    @Test
    public void throwsWhenEmptyAndIterating() {
      withEmptyLists((list, isBacked) -> {
        final Iterator<Integer> iterator = list.iterator();
        assertThatThrownBy(iterator::next)
          .isInstanceOf(NoSuchElementException.class);
      });
    }

    @Test
    public void throwsWhenIteratingAfterIterationIsOver() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        final Iterator<Integer> iterator = arrayList.iterator();
        while (iterator.hasNext())
          iterator.next();
        assertThatThrownBy(iterator::next)
          .isInstanceOf(NoSuchElementException.class);
      });
    }

    @Test
    public void throwsWhenRemovingWithoutIterating() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        final Iterator<Integer> iterator = arrayList.iterator();
        assertThatThrownBy(iterator::remove)
          .isInstanceOf(IllegalStateException.class);
      });
    }

    @Test
    public void throwsWhenRemovingTwice() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        final Iterator<Integer> iterator = arrayList.iterator();
        iterator.next();
        iterator.remove();
        assertThatThrownBy(iterator::remove)
          .isInstanceOf(IllegalStateException.class);
      });
    }

    @Test
    public void removesFromBackedList() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        final Iterator<Integer> iterator = arrayList.iterator();
        iterator.next();
        iterator.remove();
        assertThat(arrayList).first().isEqualTo(baseList.get(1));
      });
    }
  }

  public static final class RemovalTests {
    @Test
    public void removesByElement() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        arrayList.remove(baseList.getFirst());
        assertThat(arrayList)
          .didCoW()
          .hasSize(baseList.size() - 1)
          .element(0)
          .isEqualTo(baseList.get(1));
      });
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    public void throwsWhenRemovingByOutOfBoundsIndex() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        assertThatThrownBy(() -> arrayList.remove(-1))
          .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> arrayList.remove(arrayList.size()))
          .isInstanceOf(IndexOutOfBoundsException.class);
      });
    }

    @Test
    public void removesByIndex() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        arrayList.remove(1);
        assertThat(arrayList)
          .didCoW()
          .hasSize(baseList.size() - 1)
          .element(1)
          .isEqualTo(baseList.get(2));
      });
    }

    @Test
    public void throwsWhenRemovingFirstFromEmptyList() {
      withEmptyLists(
        (list, isBacked) -> assertThatThrownBy(list::removeFirst)
          .isInstanceOf(NoSuchElementException.class)
      );
    }

    @Test
    public void removesFirst() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        arrayList.removeFirst();
        assertThat(arrayList)
          .didCoW()
          .hasSize(baseList.size() - 1)
          .first()
          .isEqualTo(baseList.get(1));
      });
    }

    @Test
    public void throwsWhenRemovingLastFromEmptyList() {
      withEmptyLists(
        (list, isBacked) -> assertThatThrownBy(list::removeLast)
          .isInstanceOf(NoSuchElementException.class)
      );
    }

    @Test
    public void removesLast() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        arrayList.removeLast();
        assertThat(arrayList)
          .didCoW()
          .hasSize(baseList.size() - 1)
          .last()
          .isEqualTo(baseList.get(baseList.size() - 2));
      });
    }

    @Test
    public void throwsWhenRemovingByRangeWithOutOfBoundsIndices() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        assertThatThrownBy(() -> arrayList.removeRange(-1, arrayList.size()))
          .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> arrayList.removeRange(0, arrayList.size() + 1))
          .isInstanceOf(IndexOutOfBoundsException.class);
      });
    }

    @Test
    public void removesByRange() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        arrayList.removeRange(SUBLIST_RANGE.first(), SUBLIST_RANGE_END_INDEX);
        assertThat(arrayList)
          .didCoW()
          .hasSize(baseList.size() - SUBLIST_RANGE.size())
          .containsAll(baseList.subList(0, SUBLIST_RANGE.first()))
          .containsAll(
            baseList.subList(SUBLIST_RANGE_END_INDEX, baseList.size())
          )
          .doesNotContainAnyElementsOf(
            baseList.subList(SUBLIST_RANGE.first(), SUBLIST_RANGE_END_INDEX)
          );
      });
    }

    @Test
    public void clearingWhenBackedByPopulatedArrayCoWs() {
      final Integer[] backingArray = sampleNonImmutableTreeSetLikeArray();
      final var list = new OneTimeCopyOnWriteArrayList<>(backingArray);
      list.clear();
      assertThat(list).didCoW();
    }
  }

  public static final class SubListingTests {
    @Test
    public void throwsWhenSubListingWithOutOfBoundsIndices() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        assertThatThrownBy(() -> arrayList.subList(-1, arrayList.size()))
          .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> arrayList.subList(0, arrayList.size() + 1))
          .isInstanceOf(IndexOutOfBoundsException.class);
      });
    }

    @Test
    public void subLists() {
      withSampleLists(
        (arrayList, baseList, isBacked) ->
          combine(
            assertThat(arrayList),
            baseAssert -> baseAssert
              .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
              .extracting(
                actual -> actual
                  .subList(SUBLIST_RANGE.first(), SUBLIST_RANGE_END_INDEX),
                as(list(Integer.class))
              )
              .describedAs(".subList("
                + SUBLIST_RANGE.first()
                + ", "
                + SUBLIST_RANGE.first()
                + ')')
          )
            .containsExactlyElementsOf(
              baseList.subList(SUBLIST_RANGE.first(), SUBLIST_RANGE_END_INDEX)
            )
      );
    }

    @Test
    public void writesAreReflectedOnSublist() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        final ListAssert<Integer> sublistAssert = combine(
          assertThat(arrayList),
          baseAssert -> baseAssert
            .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
            .extracting(
              actual -> actual
                .subList(SUBLIST_RANGE.first(), SUBLIST_RANGE_END_INDEX),
              as(list(Integer.class))
            )
            .describedAs(".subList("
                         + SUBLIST_RANGE.first()
                         + ", "
                         + SUBLIST_RANGE_END_INDEX
                         + ')')
        );
        final List<? extends Integer> sublist = sublistAssert.actual();
        final var newObject = random(sublist.getFirst());
        arrayList.set(SUBLIST_RANGE.first(), newObject);
        sublistAssert.first().isEqualTo(newObject);
      });
    }

    @Test
    public void removesByElement() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        final Object removedElement = arrayList.get(SUBLIST_RANGE.first());
        combine(
          assertThat(arrayList),
          baseAssert -> baseAssert
            .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
            .extracting(actual -> actual.remove(removedElement), as(BOOLEAN))
            .describedAs(".remove(" + removedElement + ")")
        )
          .isTrue();
        assertThat(arrayList).didCoW().first().isSameAs(baseList.getFirst());
      });
    }
  }

  public static final class SpliteratorTests {
    @Test
    public void backedListHasCharacteristicsRespectiveToItsNonTreeSetLikeness() {
      final Integer[] backingArray = sampleNonImmutableTreeSetLikeArray();
      final var list = new OneTimeCopyOnWriteArrayList<>(backingArray);
      assertThatHasExactCharacteristics(list, Spliterator.DEFAULT);
    }

    @Test
    public void hasCharacteristicsRespectiveToTheImmutableTreeSetLikeBackingArray() {
      final Integer[] backingArray = sampleNonImmutableTreeSetLikeArray();
      final var list = new OneTimeCopyOnWriteArrayList<>(
                                       backingArray,
        /* isImmutableTreeSetLike = */ true
      );
      assertThatHasExactCharacteristics(
        list,
        Spliterator.IMMUTABLE_TREE_SET_LIKE
      );
    }

    @Test
    public void containsEntireListWhenUnsplit() {
      withSampleLists((arrayList, baseList, isBacked) -> {
        final java.util.Spliterator<Integer> spliterator =
          arrayList.spliterator();
        assertThat(toIterable(spliterator))
          .containsExactlyElementsOf(arrayList);
      });
    }

    @SuppressWarnings("MagicConstant")
    private static <Element> void assertThatHasExactCharacteristics(
      final OneTimeCopyOnWriteArrayList<Element> actual,
      final int characteristics
    ) throws AssertionError {
      final AbstractObjectAssert<?, java.util.Spliterator<Element>> spliteratorAssert =
        assertThat(actual)
          .asInstanceOf(type(OneTimeCopyOnWriteArrayList.class))
          .extracting(OneTimeCopyOnWriteArrayList<Element>::spliterator);
      combine(
        spliteratorAssert,
        baseAssert -> baseAssert
          .extracting(java.util.Spliterator::characteristics)
          .asInstanceOf(INTEGER)
          .inBinary()
          .describedAs("characteristics")
      )
        .isEqualTo(characteristics);
      combine(
        spliteratorAssert,
        baseAssert -> baseAssert
          .extracting(
            spliterator -> spliterator.hasCharacteristics(characteristics)
          )
          .describedAs("hasCharacteristics("
                       + Integer.toBinaryString(characteristics)
                       + ')')
      );
    }
  }

  public static final class ViewingTests {
    @Test
    public void isViewToBackingArrayPriorToFirstWrite() {
      final var backingArray = sampleNonImmutableTreeSetLikeArray();
      final var list = new OneTimeCopyOnWriteArrayList<>(backingArray);
      assertThat(list)
        .didNotCoW(backingArray, OneTimeCopyOnWriteArrayListTests::random);
    }
  }

  private static void withAllLists(
    final TriConsumer<OneTimeCopyOnWriteArrayList<Integer>,
                      List<Integer>,
                      Boolean> asserter
    ) {
    withEmptyLists(
      (list, isBacked) -> asserter.accept(list, List.of(), isBacked)
    );
    final boolean[] backingState = new boolean[1];
    withSampleLists((arrayList, baseList, isBacked) -> {
      asserter.accept(arrayList, baseList, backingState[0]);
      backingState[0] = !backingState[0];
    });
  }

  private static void withEmptyLists(
    final BiConsumer<OneTimeCopyOnWriteArrayList<Integer>, Boolean> asserter
  ) {
    final var standaloneList =
      new OneTimeCopyOnWriteArrayList<Integer>(/* initialCapacity = */ 0);
    final var backedList =
      new OneTimeCopyOnWriteArrayList<>(/* backingArray = */ new Integer[0]);
    asserter.accept(standaloneList, false);
    asserter.accept(backedList, true);
    standaloneList.clear();
    backedList.clear();
  }

  @SuppressWarnings("unchecked")
  private static void withSampleLists(
    final TriConsumer<OneTimeCopyOnWriteArrayList<Integer>,
                      List<Integer>,
                      Boolean> asserter
  ) {
    final var baseArrays = new Integer[][]{
      sampleNonImmutableTreeSetLikeArray(),
      sampleImmutableTreeSetLikeArray()
    };
    final List<Object[]> argumentsList = stream(baseArrays)
      .flatMap(
        baseArray -> {
          final List<Integer> baseList = stream(baseArray).toList();
          return Stream.of(
            new Object[]{new OneTimeCopyOnWriteArrayList<>(baseList), baseList},
            new Object[]{new OneTimeCopyOnWriteArrayList<>(baseArray), baseList}
          );
        }
      )
      .toList();
    for (int index = 0; index < argumentsList.size(); index++) {
      final Object[] arguments = argumentsList.get(index);
      final var arrayList = (OneTimeCopyOnWriteArrayList<Integer>) arguments[0];
      final var baseList = (List<Integer>) arguments[1];
      final boolean isBacked = (index + 1) % 2 == 0;
      asserter.accept(arrayList, baseList, isBacked);
      arrayList.clear();
    }
  }

  private static List<Integer> sampleNonImmutableTreeSetLikeList() {
    final Integer[] backingArray = sampleImmutableTreeSetLikeArray();
    final var result = new ArrayList<Integer>(
      /* initialCapacity = */ backingArray.length
    );
    result.addAll(asList(backingArray));
    return result;
  }

  private static Integer[] sampleNonImmutableTreeSetLikeArray() {
    return sampleNonImmutableTreeSetLikeArray(DEFAULT_SAMPLE_COUNT);
  }

  private static Integer[] sampleNonImmutableTreeSetLikeArray(
    final int length
  ) {
    final Integer[] result = sampleImmutableTreeSetLikeBackingArray(length);
    shuffle(asList(result));
    return result;
  }

  private static Integer[] sampleImmutableTreeSetLikeArray() {
    return sampleImmutableTreeSetLikeBackingArray(DEFAULT_SAMPLE_COUNT);
  }

  private static Integer[] sampleImmutableTreeSetLikeBackingArray(
    final int length
  ) {
    final Integer[] result = new Integer[length];
    ContiguousSet.closedOpen(0, length).toArray(result);
    return result;
  }

  private static int random() {
    return ThreadLocalRandom.current().nextInt();
  }

  private static int random(final int base) {
    final ThreadLocalRandom random = ThreadLocalRandom.current();
    int result;
    do { result = random.nextInt(); } while (result == base);
    return result;
  }
}