package no.ntnu.prog2007.questbook

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import no.ntnu.prog2007.questbook.data.photo.MemoryPhotoEntity
import no.ntnu.prog2007.questbook.location.database.QuestBookDatabase
import org.junit.Assert.*
import org.junit.Test

/** Verifies durable photo bytes and result/account links against actual SQLite storage. */
class MemoryPhotoStoreTest {
    @Test fun onlyCompletedOwnedResultsAcceptPhotos() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, QuestBookDatabase::class.java).build()
        try {
            var uid: String? = "me"
            val store = no.ntnu.prog2007.questbook.data.photo.LocalMemoryPhotoStore(db) { uid }
            try { store.save("missing", byteArrayOf(1), ""); fail("Incomplete quest") }
            catch (_: IllegalStateException) {}
            db.trackingDao().insertResult(no.ntnu.prog2007.questbook.data.tracking.QuestResultEntity(
                "result", "quest", "group", "me", "Walk", "Me", 1000, 500, 10.0, "result"))
            uid = "other"
            try { store.save("quest", byteArrayOf(1), ""); fail("Wrong owner") }
            catch (_: IllegalArgumentException) {}
            uid = null
            try { store.save("quest", byteArrayOf(1), ""); fail("Signed out") }
            catch (_: IllegalStateException) {}
            uid = "me"
            store.save("quest", byteArrayOf(1), "Nice walk")
            assertEquals("result", db.memoryPhotoDao().observePhotos("me").first().single().resultId)
        } finally { db.close() }
    }

    @Test fun photosSurviveReopenAndAreAccountScoped() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "memory-photo-test.db"
        context.deleteDatabase(name)
        val bytes = byteArrayOf(1, 2, 3)
        var db = Room.databaseBuilder(context, QuestBookDatabase::class.java, name).build()
        try {
            db.memoryPhotoDao().insert(MemoryPhotoEntity("photo", "result", "quest", "group", "me", "Great walk", bytes, 1000))
            db.close()
            db = Room.databaseBuilder(context, QuestBookDatabase::class.java, name).build()
            val saved = db.memoryPhotoDao().observePhotos("me").first().single()
            assertEquals("result", saved.resultId)
            assertEquals("Great walk", saved.caption)
            assertArrayEquals(bytes, saved.jpegBytes)
            assertTrue(db.memoryPhotoDao().observePhotos("other").first().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
