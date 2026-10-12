package com.jeanbarrossilva

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
internal open class ConstructionBenchmark {
    private val backingArray = Array(BACKING_ARRAY_SIZE) { it }
    private val baseList = backingArray.toList()

    @Benchmark
    fun oneTimeCopyOnWriteArrayList() {
        OneTimeCopyOnWriteArrayList(backingArray)
    }

    @Benchmark
    fun standardArrayList() {
        ArrayList(baseList)
    }
}