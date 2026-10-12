# OneTimeCopyOnWriteArrayList

Java already offers
[`CopyOnWriteArrayList`](https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)
as part of the Java Collections Framework; why another one? The reason comes down to less allocations, at the cost of thread-safety.
The class provided by this library is much more similar to how arrays work in languages like Swift, in which they may act as a view
to another array *until* they're written to, leveraging the benefits of copy-on-write.

## What is copy-on-write?

Copy-on-write (CoW) is an approach intended, primarily, to deal with large data structures, or ones to which an outside user isn't
supposed to write directly. In the case of an array, it allows the array to delegate reads to it to another array that backs it,
preventing unnecessary copies and, consequently, memory allocations.

Consider a list instantiated from another containing 1,000,000 elements:

```java
final List<Object> baseList = IntStream.rangeClosed(0, 999_999)
                                       .mapToObj(index -> new Object())
                                       .toList();
final var arrayList = new ArrayList<>(baseList);
```

Using the Java-provided [`ArrayList`](https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/util/ArrayList.html), we *have*
to copy the entire one-million-element array eagerly; there's no lazy element instantiation mechanism, even if we're not planning on
writing to that list. In case we have an array but are, say, working with an API that expects a list, and we don't want that API to be
able to modify our array (which rules out
[`Arrays.asList(T…)`](https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/util/Arrays.html#asList(T...))), we'd inevitably
need to pay the price of copying each element.

Copying on write allows us to postpone copying for as long as we can, until a write to the list, e.g., adding or removing an element, is
requested.

## Usage

With this library, a variant of the list in the previous snippet can be instantiated by simply calling its constructor that accepts the
array containing what will be its elements:

```java
final Object[] backingArray = IntStream.rangeClosed(0, 999_999)
                                       .mapToObj(index -> new Object())
                                       .toArray();
final var arrayList = new OneTimeCopyOnWriteArrayList<>(backingArray);
```

By doing this, we maintain only a reference to the array, with the array remaining uncopied by the list. Therefore, the list acts as a view
to the array, and will do so indefinitely in case the list is never written to. Otherwise, upon a write to it, a copy of the array will be
made, with such copy becoming the source of truth for the list and the one to which the requested write and any subsequent ones are
performed.

## Performance

A standard array list and ours have a noticeable efficiency gap when they're instantiated, due to ours not copying the backing array.

Besides that, ours also allows opting into an optimization by passing in `true` as the second argument to the class' constructor. This is
recommended if (and *only if*) the backing array will remain unchanged, and its elements are guaranteed to be sorted, comparable and
distinct from one another. Doing so enables binary search (instead of linear, which is the default of a standard array list); consequently,
searching becomes significantly faster.

Benchmarks were performed through [`kotlinx-benchmark`](https://github.com/kotlin/kotlinx-benchmark), with one-million-element array lists
containing boxed integers from 0 to 999,999 in natural order. The following are the results, which compare an `ArrayList` and a
`OneTimeCopyOnWriteArrayList`. For the results of ours where the aforementioned optimization was relevant, there's the
`isImmutableTreeSetLike` column: it holding `true` denotes that it was enabled; `false` means that it was disabled.

### Instantiation

| Implementation                | `isImmutableTreeSetLike` | Throughput (ops/s) | Error (±) |
| ----------------------------- | ------------------------ | ------------------ | --------- |
| `ArrayList`                   | —                        | 787.787            | 14.998    |
| `OneTimeCopyOnWriteArrayList` | —                        | 3,892.680          | 90.431    |

### Removal

| Implementation                | `isImmutableTreeSetLike` | Throughput (ops/s) | Error (±) |
| ----------------------------- | ------------------------ | ------------------ | --------- |
| `ArrayList`                   | —                        | 1,932.373          | 193.670   |
| `OneTimeCopyOnWriteArrayList` | `true`                   | 2,076.264          | 38.150    |
| `OneTimeCopyOnWriteArrayList` | `false`                  | 2,026.245          | 19.749    |

### Search

| Implementation                | `isImmutableTreeSetLike` | Throughput (ops/s) | Error (±)  |
| ----------------------------- | ------------------------ | ------------------ | ---------- |
| `ArrayList`                   | —                        | 2,093.485          | 19.526     |
| `OneTimeCopyOnWriteArrayList` | `true`                   | 40,990,958.064     | 91,738.811 |
| `OneTimeCopyOnWriteArrayList` | `false`                  | 443.678            | 0.922      |

For the implementation of the above benchmarks, see the
[`benchmark` source set](https://github.com/jeanbarrossilva/OneTimeCopyOnWriteArrayList/blob/main/lib/src/benchmark/kotlin/com/jeanbarrossilva).

## Importing

The easiest way to use this library from your code is to copy
[`OneTimeCopyOnWriteArrayList.java`](https://github.com/jeanbarrossilva/OneTimeCopyOnWriteArrayList/blob/main/lib/src/main/java/com/jeanbarrossilva/OneTimeCopyOnWriteArrayList.java)
to your project, since it's just a single file with no external dependencies.
