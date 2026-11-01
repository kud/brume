package app.beansontoast.brume.core

import org.junit.Assert

/** kotlin.test-shaped asserts (message last) on JUnit 4, for the newer tests. Explicit imports elsewhere win. */
internal fun assertTrue(condition: Boolean, message: String = "") = Assert.assertTrue(message, condition)
internal fun assertFalse(condition: Boolean, message: String = "") = Assert.assertFalse(message, condition)
internal fun <T> assertEquals(expected: T, actual: T, message: String = "") = Assert.assertEquals(message, expected, actual)
internal fun assertEquals(expected: Float, actual: Float, delta: Float) = Assert.assertEquals(expected, actual, delta)
internal inline fun <reified E : Throwable> assertFailsWith(block: () -> Unit) {
    try { block() } catch (e: Throwable) { if (e is E) return; throw e }
    throw AssertionError("expected ${E::class.simpleName}")
}
