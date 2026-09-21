package app.daybricks.planner

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.daybricks.planner.domain.*
import app.daybricks.planner.storage.*
import app.daybricks.planner.sync.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StorageIntegrationTest {
    @Test fun syncAcknowledgesSnapshotAndPreservesConcurrentChanges() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, DayBricksDatabase::class.java).build()
        try {
            val local = LocalStateRepository(db)
            val template = BlockTemplate(title = "A local block")
            local.upsert(template)
            val sent = local.pending()
            val concurrent = BlockTemplate(title = "Concurrent block")
            local.upsert(concurrent)
            local.accept(SyncState(templates = listOf(template)), "\"1\"", sent.map { it.sequence })
            assertEquals(setOf(template, concurrent), local.templates.first().toSet())
            assertEquals(1, local.pending().size)
            local.delete(template.id)
            assertEquals(listOf(concurrent), local.templates.first())
            assertTrue(local.pending().last().operation is SyncOperation.DeleteTemplate)
        } finally { db.close() }
    }
}
