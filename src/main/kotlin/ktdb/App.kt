package ktdb

import kotlin.system.exitProcess

class UnrecognizedCommand(input: String) : Exception("Unrecognized command '$input'")
class UnrecognizedStatement(input: String) : Exception("Unrecognized keyword at start of '$input'")

enum class StatementType {
    INSERT, SELECT
}
class Statement(val type: StatementType)

fun printPrompt() {
    print("db > ")
}

fun doMetaCommand(input: String) {
    when (input) {
        ".exit" -> exitProcess(0)
        else -> throw UnrecognizedCommand(input)
    }
}

val INSERT_REGEX = "^insert.*".toRegex(RegexOption.IGNORE_CASE)
val SELECT_REGEX = "^select.*".toRegex(RegexOption.IGNORE_CASE)

fun prepareStatement(input: String): Statement {
    when {
        INSERT_REGEX.matches(input) -> return Statement(StatementType.INSERT)
        SELECT_REGEX.matches(input) -> return Statement(StatementType.SELECT)
        else -> throw UnrecognizedStatement(input)
    }
}

fun executeStatement(statement: Statement) {
    when (statement.type) {
        StatementType.INSERT -> println("This is where we would do an insert.")
        StatementType.SELECT -> println("This is where we would do a select.")
    }
}

fun main(args: Array<String>) {
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
            executeStatement(statement)
            println("Executed.")
        } catch (e: UnrecognizedStatement) {
            println(e.message)
        } finally {
            continue
        }
    } while (true)
}
