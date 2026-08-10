package com.wild.android.ble

class PreviewBuffer(
    private val capacity: Int,
) {
    private val values = FloatArray(capacity)
    private var writeIndex = 0
    private var size = 0

    @Synchronized
    fun append(samples: List<Float>) {
        for (sample in samples) {
            values[writeIndex] = sample
            writeIndex = (writeIndex + 1) % capacity
            if (size < capacity) {
                size += 1
            }
        }
    }

    @Synchronized
    fun snapshot(maxPoints: Int = capacity): List<Float> {
        if (size == 0) {
            return emptyList()
        }

        val points = minOf(size, maxPoints)
        val startIndex = ((writeIndex - points) + capacity) % capacity
        return buildList(points) {
            repeat(points) { index ->
                add(values[(startIndex + index) % capacity])
            }
        }
    }

    @Synchronized
    fun clear() {
        writeIndex = 0
        size = 0
    }
}
