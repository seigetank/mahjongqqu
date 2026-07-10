package com.mahjongqqu.engine

internal class DeterministicRandom(seed: Seed) {
    private var state: Long = seed.bytes.foldIndexed(0x6A09E667F3BCC909UL.toLong()) { index, acc, byte ->
        acc xor ((byte.toLong() and 0xffL) shl ((index % 8) * 8))
    }

    fun nextLong(): Long {
        state += 0x9E3779B97F4A7C15UL.toLong()
        var z = state
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9UL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBUL.toLong()
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "Bound must be positive." }
        return (nextLong().ushr(1) % bound).toInt()
    }
}
