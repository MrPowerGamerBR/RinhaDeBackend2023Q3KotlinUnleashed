package com.mrpowergamerbr.rinhadebackend2023q3

import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking

object RinhaDeBackend2023Q3Launcher {
    @JvmStatic
    fun main(args: Array<String>) {
        // Enable coroutine names, they are visible when dumping the coroutines
        System.setProperty("kotlinx.coroutines.debug", "on")

        // Enable coroutines stacktrace recovery
        System.setProperty("kotlinx.coroutines.stacktrace.recovery", "true")

        // It is recommended to set this to false to avoid performance hits with the DebugProbes option!
        DebugProbes.enableCreationStackTraces = false
        DebugProbes.install()

        val database = Database.createPool()
        runBlocking {
            database.createTables()
        }
        val m = RinhaDeBackend2023Q3(database)
        m.start()
    }
}