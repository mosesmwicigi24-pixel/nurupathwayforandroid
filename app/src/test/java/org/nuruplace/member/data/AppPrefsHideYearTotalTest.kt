// The Give header's eye button persists "hide this year's total" under the
// key `give.hideYearTotal` (default: shown). Proven off-device through an
// in-memory SharedPreferences: write, re-attach as a fresh start would, read.
package org.nuruplace.member.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPrefsHideYearTotalTest {
    @Test
    fun `hide year total defaults to visible and round-trips through the store`() {
        val store = MemoryPrefs()
        AppPrefs.attach(store)
        assertFalse(AppPrefs.hideGiveYearTotal)

        AppPrefs.updateHideGiveYearTotal(true)
        assertTrue(AppPrefs.hideGiveYearTotal)
        assertEquals(true, store.values["give.hideYearTotal"])

        // A fresh attach (process restart) reads the persisted choice back.
        AppPrefs.attach(store)
        assertTrue(AppPrefs.hideGiveYearTotal)

        AppPrefs.updateHideGiveYearTotal(false)
        AppPrefs.attach(store)
        assertFalse(AppPrefs.hideGiveYearTotal)
        assertEquals(false, store.values["give.hideYearTotal"])
    }
}

/** Just enough SharedPreferences to hold booleans/floats/strings in a map. */
private class MemoryPrefs : SharedPreferences {
    val values = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removed = HashSet<String>()
        private var clearAll = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clearAll) values.clear()
            removed.forEach { values.remove(it) }
            values.putAll(pending)
        }
    }
}
