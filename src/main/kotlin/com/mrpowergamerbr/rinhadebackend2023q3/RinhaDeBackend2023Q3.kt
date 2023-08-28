package com.mrpowergamerbr.rinhadebackend2023q3

import com.mrpowergamerbr.rinhadebackend2023q3.data.CreatePessoaRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement
import mu.KotlinLogging
import org.postgresql.util.PSQLException
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.format.DateTimeParseException
import java.util.*

class RinhaDeBackend2023Q3(private val database: Database) {
    companion object {
        private val SEARCH_ENABLED = System.getenv("RINHA_SEARCH")?.toBoolean() ?: true
        private val logger = KotlinLogging.logger {}
    }

    fun start() {
        val server = embeddedServer(Netty, System.getenv("WEBSERVER_PORT")?.toInt() ?: 9999) {
            routing {
                get("/") {
                    call.respondText("Provando para os haters do Twitter que a JVM é tão capaz como o beloved deles, o node.js... Aliás, a Loritta é muito fofa! :3")
                }

                post("/pessoas") {
                    val bodyAsString = call.receiveText()
                    try {
                        val createPessoaRequest = Json.decodeFromString<CreatePessoaRequest>(bodyAsString)

                        val result = database.insertPerson(
                            createPessoaRequest.apelido,
                            createPessoaRequest.nome,
                            createPessoaRequest.nascimento,
                            createPessoaRequest.stack
                        )

                        database.storeCacheAsJson("pessoas:id:${result.id}") {
                            Json.encodeToJsonElement(result)
                        }

                        call.response.header("Location", "/pessoas/${result.id}")
                        call.respondText("", status = HttpStatusCode.Created)
                    } catch (e: Exception) {
                        if (e is SerializationException || e is IllegalArgumentException || e is DateTimeParseException || (e is PSQLException && e.serverErrorMessage?.message?.contains("duplicate key value violates unique constraint") == true)) {
                            // TODO: Missing Bad Request "syntatically incorrect" JSON checks, I don't how we can check for those because kotlinx.serialization sort of does that for us
                            call.respondText("", status = HttpStatusCode.UnprocessableEntity)
                        } else throw e
                    }
                }

                get("/pessoas/{id}") {
                    val userIdAsString = call.parameters.getOrFail("id")
                    val userId = try {
                        UUID.fromString(userIdAsString)
                    } catch (e: IllegalArgumentException) {
                        call.respondText("", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    val cached = database.getCache("pessoas:id:${userId}")
                    if (cached != null) {
                        if (cached is JsonNull) {
                            call.respondText("", status = HttpStatusCode.NotFound)
                            return@get
                        } else {
                            call.respondText(
                                cached.toString(),
                                ContentType.Application.Json,
                                HttpStatusCode.OK
                            )
                            return@get
                        }
                    }

                    val queryResult = database.findById(userId)
                    val queryResultAsString = Json.encodeToJsonElement(queryResult).toString()
                    database.storeCacheAsString("pessoas:id:${userId}") { queryResultAsString }

                    if (queryResult == null) {
                        call.respondText("", status = HttpStatusCode.NotFound)
                        return@get
                    }

                    call.respondText(
                        Json.encodeToString(queryResultAsString),
                        ContentType.Application.Json,
                        HttpStatusCode.OK
                    )
                }

                get("/pessoas") {
                    if (!SEARCH_ENABLED) {
                        call.respondText("", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    val searchQuery = call.parameters["t"]
                    if (searchQuery == null) {
                        call.respondText("", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    val cached = database.getCache("cache:findByTerm:${searchQuery}")
                    if (cached != null) {
                        call.respondText(
                            cached.toString(),
                            ContentType.Application.Json,
                            HttpStatusCode.OK
                        )
                        return@get
                    }

                    val queryResults = database.findByTerm(searchQuery)

                    val queryResultsAsString = Json.encodeToJsonElement(queryResults).toString()
                    database.storeCacheAsString("cache:findByTerm:${searchQuery}") { queryResultsAsString }

                    call.respondText(
                        queryResultsAsString,
                        ContentType.Application.Json
                    )
                }

                get("/contagem-pessoas") {
                    val count = database.count().toString()
                    logger.info { "$count inserted users" }
                    call.respondText(count)
                }

                // Dumps all currently running coroutines
                get("/coroutines") {
                    val os = ByteArrayOutputStream()
                    val ps = PrintStream(os)
                    DebugProbes.dumpCoroutines(ps)
                    call.respondText(os.toString(Charsets.UTF_8))
                }
            }
        }
        server.start(true)
    }
}