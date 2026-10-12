package com.jeanbarrossilva

import com.jeanbarrossilva.OneTimeCopyOnWriteArrayList
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
internal open class WriteBenchmark {
    private val backingArray = Array(BACKING_ARRAY_SIZE) { it }
    private val oneTimeCopyOnWriteArrayList = OneTimeCopyOnWriteArrayList(backingArray)

    private val baseList = backingArray.toList()
    private val standardArrayList = ArrayList(baseList)

    @Benchmark
    fun oneTimeCopyOnWriteArrayList() {
        oneTimeCopyOnWriteArrayList.add(oneTimeCopyOnWriteArrayList.size)
    }

    @Benchmark
    fun standardArrayList() {
        standardArrayList.add(standardArrayList.size)
    }
}