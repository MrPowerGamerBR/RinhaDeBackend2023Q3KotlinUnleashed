package com.mrpowergamerbr.rinhadebackend2023q3

import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

object RinhaDeBackend2023Q3Launcher {
    private val logger = KotlinLogging.logger {}

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
            while (true) {
                try {
                    database.createTables()
                    break
                } catch (e: Exception) {
                    logger.warn(e) { "Something went wrong while trying to create the tables! Retrying again later..." }
                    Thread.sleep(2_000)
                }
            }
        }

        val m = RinhaDeBackend2023Q3(database)
        m.start()
    }
}