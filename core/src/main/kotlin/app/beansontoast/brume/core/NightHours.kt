package app.beansontoast.brume.core

/** Night is the design case: from [START] to [END] the app is dark and dim whatever the system says. */
object NightHours {
    const val START = 22
    const val END = 6

    fun contains(hour: Int): Boolean = hour >= START || hour < END
}
