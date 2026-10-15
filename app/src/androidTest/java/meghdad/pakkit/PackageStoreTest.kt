package meghdad.pakkit

import androidx.test.ext.junit.runners.AndroidJUnit4
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.data.PackageStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageStoreTest : WatchTest() {
    @Test
    fun failedMetadataWriteRestoresThePreviousReview() {
        val apk = fixture()
        store.stage(apk)
        var failNext = true
        val real = store.preferences
        val faulty =
            object : android.content.SharedPreferences by real {
                override fun edit(): android.content.SharedPreferences.Editor {
                    val editor = real.edit()
                    return object : android.content.SharedPreferences.Editor by editor {
                        override fun putString(
                            key: String,
                            value: String?,
                        ): android.content.SharedPreferences.Editor {
                            editor.putString(key, value)
                            return this
                        }

                        override fun commit(): Boolean {
                            val written = editor.commit()
                            if (failNext) {
                                failNext = false
                                return false
                            }
                            return written
                        }

                        override fun remove(key: String): android.content.SharedPreferences.Editor {
                            editor.remove(key)
                            return this
                        }
                    }
                }
            }
        val wrapped =
            object : android.content.ContextWrapper(context) {
                override fun getSharedPreferences(name: String, mode: Int) = faulty
            }
        assertTrue(
            runCatching {
                PackageStore(wrapped).update(apk.id, InstallState.INSTALLING, "Preparing")
            }
                .isFailure
        )
        assertEquals(apk, store.pending())
        failNext = true
        assertTrue(runCatching { PackageStore(wrapped).dismiss(apk.id) }.isFailure)
        assertEquals(apk, store.pending())
        assertTrue("Failed removal keeps the original APK", store.file(apk.id).exists())
    }

    @Test
    fun persistsReviewAcrossStoreInstancesAndRejectsReplacement() {
        val apk = fixture()
        store.stage(apk)
        assertEquals(apk, PackageStore(context).pending())
        assertTrue(runCatching { store.stage(apk.copy(detail = "replacement")) }.isFailure)
        assertEquals(apk, store.pending())
    }

    @Test
    fun oldReviewsRemainAvailableAndUncommittedFilesAreCleaned() {
        val apk = fixture().copy(created = 1L)
        store.stage(apk)
        val orphan = fixture()
        store.file(orphan.id).setLastModified(System.currentTimeMillis())
        store.clean()
        assertEquals(apk, store.pending())
        assertTrue(store.file(apk.id).exists())
        assertFalse(store.file(orphan.id).exists())
    }

    @Test
    fun lateUpdatesCannotResurrectDismissedReview() {
        val apk = fixture()
        store.stage(apk)
        store.dismiss(apk.id)
        assertFalse(store.update(apk.id, InstallState.INSTALLED, "late callback"))
        assertNull(store.pending())
        assertFalse(store.file(apk.id).exists())
    }

    @Test
    fun readsStableSavedReviewFormatAndIgnoresMalformedData() {
        val apk = fixture()
        store.preferences.edit().putString(PackageStore.PENDING_KEY, apk.json().toString()).commit()
        assertEquals(apk, store.pending())
        store.dismiss(apk.id)
        store.preferences.edit().putString(PackageStore.PENDING_KEY, "malformed").commit()
        assertNull(store.pending())
        store.preferences.edit().remove(PackageStore.PENDING_KEY).commit()
    }
}
