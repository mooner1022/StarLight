package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.RunState
import dev.mooner.starlight.agent.store.AgentStorage
import dev.mooner.starlight.agent.store.ConversationArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Conversations moving to the StarLight folder, and out and back in as a zip. */
class AgentStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun conversation(id: String, state: RunState = RunState.IDLE) =
        Conversation(id = id, title = "Chat $id", createdAt = 1L, updatedAt = 2L, state = state)

    private fun write(root: java.io.File, conversation: Conversation, attachment: String? = null) {
        root.resolve("conversations").mkdirs()
        root.resolve("conversations/${conversation.id}.json").writeText(AgentJson.encodeToString(Conversation.serializer(), conversation))
        if (attachment != null) {
            root.resolve("attachments/${conversation.id}").mkdirs()
            root.resolve("attachments/${conversation.id}/$attachment").writeText("file of ${conversation.id}")
        }
    }

    @Test
    fun migrationMovesConversationsAndKeepsWhatIsThere() {
        val private = tmp.newFolder("files", "agent")
        val shared = tmp.newFolder("StarLight", "agent")
        write(private, conversation("cOld"), "att_1")
        write(private, conversation("cBoth"), "att_2")
        write(shared, conversation("cBoth").copy(title = "kept"), "att_3")
        shared.resolve("index.json").writeText("[]")

        AgentStorage.migrate(private, shared)

        assertFalse(private.exists())
        assertTrue(shared.resolve("conversations/cOld.json").isFile)
        assertEquals("file of cOld", shared.resolve("attachments/cOld/att_1").readText())
        // The copy already in the StarLight folder wins.
        assertTrue(shared.resolve("conversations/cBoth.json").readText().contains("kept"))
        assertTrue(shared.resolve("attachments/cBoth/att_3").isFile)
        assertFalse(shared.resolve("attachments/cBoth/att_2").exists())
        // Rebuilt from the files on the next start, so the moved conversation is listed.
        assertFalse(shared.resolve("index.json").exists())
    }

    @Test
    fun nothingToMoveLeavesTheIndexAlone() {
        val private = tmp.newFolder("files", "agent")
        val shared = tmp.newFolder("StarLight", "agent")
        shared.resolve("index.json").writeText("[]")
        AgentStorage.migrate(private, shared)
        assertFalse(private.exists())
        assertTrue(shared.resolve("index.json").isFile)
    }

    @Test
    fun exportedConversationsComeBackWithTheirFiles() {
        val from = tmp.newFolder("from")
        write(from, conversation("c1"), "att_1")
        write(from, conversation("c2", RunState.RUNNING))
        from.resolve("conversations/c1.json.bak").writeText("half written")
        from.resolve("index.json").writeText("[]")

        val zip = ByteArrayOutputStream()
        assertEquals(2, ConversationArchive.export(from, zip))
        val entries = ZipInputStream(ByteArrayInputStream(zip.toByteArray())).use { input ->
            generateSequence { input.nextEntry?.name }.toSortedSet()
        }
        assertEquals(sortedSetOf("attachments/c1/att_1", "conversations/c1.json", "conversations/c2.json"), entries)

        val attachments = tmp.newFolder("to", "attachments")
        val added = mutableListOf<Conversation>()
        val result = ConversationArchive.import(
            input = ByteArrayInputStream(zip.toByteArray()),
            staging = tmp.root.resolve("staging"),
            attachmentsDir = attachments,
            exists = { it == "c1" },
            add = { added += it },
        )
        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        // A run that was going on comes back as cut short.
        assertEquals(listOf("c2" to RunState.INTERRUPTED), added.map { it.id to it.state })
        assertFalse(attachments.resolve("c1").exists())
        assertFalse(tmp.root.resolve("staging").exists())
    }
    @Test
    fun importedConversationsComeWithoutTheirCheckpoints() {
        val from = tmp.newFolder("from")
        write(from, conversation("c1"), "att_1")
        // What the agent changed on the device the conversation comes from, kept for a rewind there.
        from.resolve("attachments/c1/checkpoints/m1").mkdirs()
        from.resolve("attachments/c1/checkpoints/m1/manifest.json").writeText("{}")
        from.resolve("attachments/c1/checkpoints/m1/b0").writeText("main.js as it was over there")

        val zip = ByteArrayOutputStream()
        ConversationArchive.export(from, zip)

        val attachments = tmp.newFolder("to", "attachments")
        val result = ConversationArchive.import(
            input = ByteArrayInputStream(zip.toByteArray()),
            staging = tmp.root.resolve("staging"),
            attachmentsDir = attachments,
            exists = { false },
            add = {},
        )
        assertEquals(1, result.imported)
        assertTrue(attachments.resolve("c1/att_1").isFile)
        // A rewind here would write them over this device's project of the same name.
        assertFalse(attachments.resolve("c1/checkpoints").exists())
    }
}
