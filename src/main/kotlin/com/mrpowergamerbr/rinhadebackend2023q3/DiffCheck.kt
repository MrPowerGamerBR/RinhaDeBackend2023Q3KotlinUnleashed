package com.mrpowergamerbr.rinhadebackend2023q3

import java.io.File

fun main() {
    val original = File("pessoas-power.txt")
        .readLines()
        .toSet()
    val correct = File("pessoas.txt")
        .readLines()
        .toSet()

    val diff = (correct - original)

    diff.forEach {
        println(it)
    }

    println(
        diff.count { it.startsWith(" WARMUP") }
    )
}