package ktdb

import kotlin.system.exitProcess

fun printPrompt() {
    print("db > ")
}

fun main(args: Array<String>) {
    do {
        printPrompt()
        val input = readLine()

        when (input) {
            null -> exitProcess(0)
            ".exit" -> exitProcess(0)
            else -> println("Unrecognized command '$input'")
        }
    } while (true)
}
