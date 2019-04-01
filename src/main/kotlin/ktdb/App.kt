package ktdb

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.system.exitProcess

class FileCorruptionError() : Exception("Database file is corrupted.")
class NegativeID() : Exception("ID must be positive.")
class StringTooLong() : Exception("String is too long.")
class SyntaxError(input: String) : Exception("Syntax error. Could not parse statement.")
class TableFull() : Exception("Error: Table full.")
class UnrecognizedCommand(input: String) : Exception("Unrecognized command '$input'")
class UnrecognizedStatement(input: String) : Exception("Unrecognized keyword at start of '$input'")

val DESER_REGEX = """^(-\d]+):(\d+):(\d+):(.+)$""".toRegex(RegexOption.IGNORE_CASE)
val UTF_8 = Charset.forName("UTF-8")

data class Row(val id: Int, val username: String, val email: String) {
    fun serialize(buf: ByteBuffer) {
        buf.putInt(id)

        val ubytes = username.toByteArray(UTF_8)
        buf.putInt(ubytes.size)
        buf.put(ubytes)

        val ebytes = email.toByteArray(UTF_8)
        buf.putInt(ebytes.size)
        buf.put(ebytes)
    }

    companion object {
        val COLUMN_USERNAME_SIZE = 32
        val COLUMN_EMAIL_SIZE = 255
        val SIZE = 4 * 3 + COLUMN_USERNAME_SIZE + COLUMN_EMAIL_SIZE

        fun deserialize(buf: ByteBuffer): Row {
            val id = buf.getInt()

            val ucount = buf.getInt()
            val ubytes = ByteArray(ucount)
            buf.get(ubytes)
            val username = ubytes.toString(UTF_8)

            val ebytes = ByteArray(buf.getInt())
            buf.get(ebytes)
            val email = ebytes.toString(UTF_8)

            return Row(id, username, email)
        }
    }
}

class Page() {
    val rows = mutableListOf<Row>()

    fun addRow(row: Row) = rows.add(row)

    fun eachRow(callback: (row: Row) -> Unit) {
        for (row in rows) { callback(row) }
    }

    fun isFull() = rows.size >= Page.MAX_ROWS

    companion object {
        val SIZE = 4096
        val MAX_ROWS = SIZE / Row.SIZE
    }
}

class Pager(val channel: SeekableByteChannel, val pages: MutableList<Page>) {
    fun close() {
        channel.truncate(0)

        eachPage({ page -> writePage(page) })

        channel.close()
    }

    fun eachPage(callback: (page: Page) -> Unit) {
        for (page in pages) { callback(page) }
    }

    fun getNextAvailablePage(): Page? {
        if (pages.isEmpty()) {
            val firstPage = Page()
            pages.add(firstPage)
            return firstPage
        }

        val lastPage = pages.last()
        if (!lastPage.isFull()) return lastPage

        if (isFull()) return null

        val nextPage = Page()
        pages.add(nextPage)
        return nextPage
    }

    fun isFull() = pages.size >= Pager.MAX_PAGES && pages.last().isFull()

    fun writePage(page: Page) {
        val buf = ByteBuffer.allocate(Page.SIZE)
        buf.clear()

        page.eachRow({ row -> row.serialize(buf) })

        buf.flip()
        while (buf.hasRemaining()) {
            channel.write(buf)
        }
    }

    companion object {
        val MAX_PAGES = 100

        fun open(path: Path): Pager {
            val channel = Files.newByteChannel(path, StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE)
            val pages = mutableListOf<Page>()

            val buf = ByteBuffer.allocate(Page.SIZE)
            while (channel.read(buf) != -1) {
                buf.flip()

                val page = Page()
                pages.add(page)

                while (!page.isFull() && buf.hasRemaining()) {
                    page.addRow(Row.deserialize(buf))
                }

                buf.clear()
            }

            return Pager(channel, pages)
        }
    }
}

class Table(val pager: Pager) {
    fun addRow(row: Row) {
        val page = pager.getNextAvailablePage()
        when (page) {
            null -> throw TableFull()
            else -> page.addRow(row)
        }
    }

    fun close() = pager.close()

    fun eachRow(callback: (row: Row) -> Unit) {
        pager.eachPage({ page -> page.eachRow({ row -> callback(row) }) })
    }
}

object DB {
    val FILE_SYSTEM = FileSystems.getDefault()
    val DEFAULT_PATH = FILE_SYSTEM.getPath(".", "db/ktdb.db")

    fun open(filename: String?): Table {
        val path = if (filename != null) FILE_SYSTEM.getPath(filename) else DEFAULT_PATH
        val pager = Pager.open(path)

        return Table(pager)
    }

    fun close(table: Table) {
        table.close()
    }
}

sealed class Statement
class InsertStatement(val row: Row) : Statement()
class SelectStatement : Statement()

val INSERT_REGEX = """^insert ([-\d]+) (\S+) (\S+)""".toRegex(RegexOption.IGNORE_CASE)
val SELECT_REGEX = """^select.*""".toRegex(RegexOption.IGNORE_CASE)

fun printRow(row: Row) {
    println("(${row.id}, ${row.username}, ${row.email})")
}

fun printPrompt() {
    print("db > ")
}

fun doMetaCommand(input: String, table: Table) {
    when (input) {
        ".exit" -> {
            DB.close(table)
            exitProcess(0)
        }
        else -> throw UnrecognizedCommand(input)
    }
}

fun prepareInsert(input: String): InsertStatement {
    val result = INSERT_REGEX.find(input)
    if (result == null) throw SyntaxError(input)

    val (idStr, username, email) = result.destructured
    val id = idStr.toInt()

    if (id < 0) throw NegativeID()
    if (username.length > Row.COLUMN_USERNAME_SIZE) throw StringTooLong()
    if (email.length > Row.COLUMN_EMAIL_SIZE) throw StringTooLong()

    val row = Row(id, username, email)
    return InsertStatement(row)
}

fun prepareSelect(input: String): SelectStatement {
    val result = SELECT_REGEX.find(input)
    if (result == null) throw SyntaxError(input)

    return SelectStatement()
}

fun prepareStatement(input: String): Statement {
    val lowerInput = input.toLowerCase()

    if (lowerInput.startsWith("insert")) return prepareInsert(input)
    if (lowerInput.startsWith("select")) return prepareSelect(input)

    throw UnrecognizedStatement(input)
}

fun executeInsert(table: Table, statement: InsertStatement) {
    table.addRow(statement.row)
}

fun executeSelect(table: Table, statement: SelectStatement) {
    table.eachRow({ row -> printRow(row) })
}

fun executeStatement(table: Table, statement: Statement) {
    when (statement) {
        is InsertStatement -> executeInsert(table, statement)
        is SelectStatement -> executeSelect(table, statement)
    }
}

fun main(args: Array<String>) {
    val filename = if (args.size > 0) args[0] else null
    val table = DB.open(filename)

    do {
        printPrompt()
        val input = readLine() ?: ".exit"

        if (input.startsWith(".")) {
            try {
                doMetaCommand(input, table)
            } catch (e: UnrecognizedCommand) {
                println(e.message)
            } finally {
                continue
            }
        }

        try {
            val statement = prepareStatement(input)
            executeStatement(table, statement)
            println("Executed.")
        } catch (e: Exception) {
            println(e.message)
        } finally {
            continue
        }
    } while (true)
}
