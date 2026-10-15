package meghdad.pakkit

import androidx.test.ext.junit.runners.AndroidJUnit4
import meghdad.pakkit.apk.ApkRules
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.data.PackageStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageStoreTest : WatchTest() {
    @Test
    fun persistsReviewAcrossStoreInstancesAndRejectsReplacement() {
        val apk = fixture()
        store.stage(apk)
        assertEquals(apk, PackageStore(context).pending())
        assertTrue(runCatching { store.stage(apk.copy(detail = "replacement")) }.isFailure)
        assertEquals(apk, store.pending())
    }

    @Test
    fun expiredReviewAndOrphanedFileAreRemoved() {
        val apk = fixture().copy(created = System.currentTimeMillis() - ApkRules.TTL_MS)
        store.stage(apk)
        val orphan = fixture()
        store.file(orphan.id).setLastModified(System.currentTimeMillis() - ApkRules.TTL_MS - 1000)
        store.clean()
        assertNull(store.pending())
        assertFalse(store.file(apk.id).exists())
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
