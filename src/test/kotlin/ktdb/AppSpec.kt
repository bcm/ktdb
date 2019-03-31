package ktdb

import kotlin.test.assertEquals
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AppSpec : Spek({
    fun runApp(commands: List<String>): List<String> {
        val process = ProcessBuilder("gradle", "run", "-q", "--console=plain")
            .directory(null)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val writer = process.outputStream.bufferedWriter()
        val reader = process.inputStream.bufferedReader()

        for (command in commands) {
            writer.write(command, 0, command.length)
            writer.newLine()
            writer.flush()
        }

        return reader.readText().split("\n")
    }

    describe("database") {
        it("inserts and retrieves a row") {
            val result = runApp(listOf(
                "insert 1 user1 person1@example.com",
                "select",
                ".exit"
            ))

            assertEquals(listOf(
                "db > Executed.",
                "db > (1, user1, person1@example.com)",
                "Executed.",
                "db > "
            ), result)
        }

        it("prints an error message when the table is full") {
            val script = mutableListOf<String>()
            (1..1401).map { i -> script.add("insert $i user$i person$i@example.com") }
            script.add(".exit")

            val result = runApp(script)
            assertEquals("db > Error: Table full.", result.get(result.size - 2))
        }

        it("allows inserting strings that are the maximum length") {
            val longUsername = "a".repeat(32)
            val longEmail = "a".repeat(255)
            val script = listOf(
                "insert 1 $longUsername $longEmail",
                "select",
                ".exit"
            )

            val result = runApp(script)
            assertEquals(listOf(
                "db > Executed.",
                "db > (1, $longUsername, $longEmail)",
                "Executed.",
                "db > "
            ), result)
        }

        it("prints an error message if strings are too long") {
            val longUsername = "a".repeat(33)
            val longEmail = "a".repeat(256)
            val script = listOf(
                "insert 1 $longUsername $longEmail",
                "select",
                ".exit"
            )

            val result = runApp(script)
            assertEquals(listOf(
                "db > String is too long.",
                "db > Executed.",
                "db > "
            ), result)
        }

        it("prints an error message if id is negative") {
            val script = listOf(
                "insert -1 bcm bcm@maz.org",
                "select",
                ".exit"
            )

            val result = runApp(script)
            assertEquals(listOf(
                "db > ID must be positive.",
                "db > Executed.",
                "db > "
            ), result)
        }
    }
})
