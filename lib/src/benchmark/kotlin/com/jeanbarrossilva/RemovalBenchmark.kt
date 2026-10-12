package com.jeanbarrossilva

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
@Suppress("FunctionName")
internal open class RemovalBenchmark {
    private val backingArray = Array(BACKING_ARRAY_SIZE) { it }
    private val oneTimeCopyOnWriteArrayListWithLinearSearch = OneTimeCopyOnWriteArrayList(backingArray)
    private val oneTimeCopyOnWriteArrayListWithBinarySearch =
        OneTimeCopyOnWriteArrayList(backingArray, /* isImmutableTreeSetLike = */ true)

    private val baseList = backingArray.toList()
    private val standardArrayList = ArrayList(baseList)

    @Benchmark
    fun oneTimeCopyOnWriteArrayList_withLinearSearch() {
        oneTimeCopyOnWriteArrayListWithLinearSearch.remove(ELEMENT)
    }

    @Benchmark
    fun oneTimeCopyOnWriteArrayList_withBinarySearch() {
        oneTimeCopyOnWriteArrayListWithBinarySearch.remove(ELEMENT)
    }

    @Benchmark
    fun standardArrayList() {
        standardArrayList.remove(ELEMENT)
    }
}