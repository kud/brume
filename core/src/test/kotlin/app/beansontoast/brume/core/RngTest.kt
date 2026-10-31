package app.beansontoast.brume.core

import app.beansontoast.brume.core.rng.Pcg32
import app.beansontoast.brume.core.rng.SplitMix64
import org.junit.Assert.assertEquals
import org.junit.Test

class RngTest {
    @Test fun `splitmix64 matches the reference vector`() {
        assertEquals(0xE220A8397B1DCDAFuL.toLong(), SplitMix64(0).next())
    }

    @Test fun `pcg32 matches the reference demo for seed 42 sequence 54`() {
        val rng = Pcg32(42, 54)
        val expected = listOf(0xa15c02b7u, 0x7b47f409u, 0xba1d3330u, 0x83d2f293u, 0xbfa4784bu, 0xcbed606eu)
        assertEquals(expected.map { it.toInt() }, List(6) { rng.nextInt() })
    }

    @Test fun `a seed replays the same stream`() {
        val a = Pcg32.seeded(7); val b = Pcg32.seeded(7)
        repeat(1000) { assertEquals(a.nextFloat(), b.nextFloat()) }
    }

    @Test fun `floats stay in range`() {
        val rng = Pcg32.seeded(3)
        repeat(100_000) {
            val f = rng.nextFloat(); check(f >= 0f && f < 1f)
            val s = rng.nextSigned(); check(s >= -1f && s < 1f)
        }
    }
}
