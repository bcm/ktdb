package ktdb

import kotlin.system.exitProcess

class NegativeID() : Exception("ID must be positive.")
class StringTooLong() : Exception("String is too long.")
class SyntaxError(input: String) : Exception("Syntax error. Could not parse statement.")
class TableFull() : Exception("Error: Table full.")
class UnrecognizedCommand(input: String) : Exception("Unrecognized command '$input'")
class UnrecognizedStatement(input: String) : Exception("Unrecognized keyword at start of '$input'")

class Row(val id: Int, val username: String, val email: String) {
    companion object {
        val SIZE = 291
    }
}

class Table() {
    val pages = mutableListOf<Page>()

    fun addRow(row: Row) {
        if (pages.isEmpty()) {
            val firstPage = Page()
            firstPage.addRow(row)
            pages.add(firstPage)
        } else {
            val lastPage = pages.last()
            if (lastPage.isFull()) {
                val nextPage = Page()
                nextPage.addRow(row)
                pages.add(nextPage)
            } else {
                lastPage.addRow(row)
            }
        }
    }

    fun eachRow(callback: (row: Row) -> Unit) {
        for (page in pages) {
            for (row in page.rows) {
                callback(row)
            }
        }
    }

    fun isFull() = pages.size >= Table.MAX_PAGES && pages.last().isFull()

    companion object {
        val MAX_PAGES = 100
    }
}

class Page() {
    val rows = mutableListOf<Row>()

    fun addRow(row: Row) = rows.add(row)

    fun isFull() = rows.size >= Page.MAX_ROWS

    companion object {
        val SIZE = 4096
        val MAX_ROWS = SIZE / Row.SIZE
    }
}

sealed class Statement
class InsertStatement(val row: Row) : Statement()
class SelectStatement : Statement()

val INSERT_REGEX = """^insert ([-\d]+) (\S+) (\S+)""".toRegex(RegexOption.IGNORE_CASE)
val SELECT_REGEX = """^select.*""".toRegex(RegexOption.IGNORE_CASE)
val COLUMN_USERNAME_SIZE = 32
val COLUMN_EMAIL_SIZE = 255

fun printRow(row: Row) {
    println("(${row.id}, ${row.username}, ${row.email})")
}

fun printPrompt() {
    print("db > ")
}

fun doMetaCommand(input: String) {
    when (input) {
        ".exit" -> exitProcess(0)
        else -> throw UnrecognizedCommand(input)
    }
}

fun prepareInsert(input: String): InsertStatement {
    val result = INSERT_REGEX.find(input)
    if (result == null) throw SyntaxError(input)

    val (idStr, username, email) = result.destructured
    val id = idStr.toInt()

    if (id < 0) throw NegativeID()
    if (username.length > COLUMN_USERNAME_SIZE) throw StringTooLong()
    if (email.length > COLUMN_EMAIL_SIZE) throw StringTooLong()

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
    if (table.isFull()) throw TableFull()

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
    val table = Table()

    do {
        printPrompt()
        val input = readLine() ?: ".exit"

        if (input.startsWith(".")) {
            try {
                doMetaCommand(input)
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
