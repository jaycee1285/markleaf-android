package com.markleaf.notes.data.sync

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.markleaf.notes.core.text.NoteTitleSource
import com.markleaf.notes.data.local.entity.toDomain
import com.markleaf.notes.data.local.entity.toEntity
import com.markleaf.notes.domain.model.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilenameTitleTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `both sync modes use filename instead of heading or first line`() = runBlocking {
        for (metadata in listOf(MirrorMetadata.Frontmatter, MirrorMetadata.Sidecar("test-device"))) {
            val dir = temporaryFolder.newFolder()
            File(dir, "Meeting.notes.md").writeText("# Something else\n\nBody")
            File(dir, "Shopping.txt").writeText("Not the filename\n\nBody")
            val notes = mutableListOf<Note>()
            val result = MirrorImport.importChangesFrom(
                context, DocumentFile.fromFile(dir), emptyList(),
                applyUpdate = { error("No existing note") }, applyCreate = { notes.add(it) },
                metadata = metadata
            )
            assertEquals(0, result.errors)
            assertEquals(listOf("Meeting.notes", "Shopping"), notes.map { it.title }.sorted())
            assertEquals("# Something else\n\nBody", notes.first { it.title == "Meeting.notes" }.contentMarkdown)
        }
    }

    @Test
    fun `unchanged file adopts filename without changing edit timestamp`() = runBlocking {
        val now = Instant.parse("2026-10-07T12:00:00Z")
        val old = Note("id", "Old heading", "# Old heading", "", now, now, lastImportedAt = now)
        val dir = temporaryFolder.newFolder()
        File(dir, "Actual filename.md").writeText(SyncFrontmatter.encode(old))
        var updated: Note? = null
        MirrorImport.importChangesFrom(
            context, DocumentFile.fromFile(dir), listOf(old),
            applyUpdate = { updated = it }, applyCreate = { error("Already exists") }
        )
        assertEquals("Actual filename", updated?.title)
        assertEquals(now, updated?.updatedAt)
    }

    @Test
    fun `filename persists through storage and overrides subsequent edits under either title rule`() {
        val now = Instant.now()
        val original = Note("id", "My notes", "# Old", "", now, now, fileName = "My notes.md")
        val restored = original.toEntity().toDomain()
        for (source in NoteTitleSource.entries) {
            assertEquals("My notes", restored.titleForContent("First line\n# New heading", source))
        }
    }
}
