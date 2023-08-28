package com.mrpowergamerbr.rinhadebackend2023q3

import com.mrpowergamerbr.rinhadebackend2023q3.data.Pessoa
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.util.IsolationLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.time.LocalDate
import java.util.*
import java.util.concurrent.Executors

class Database private constructor(val dataSource: HikariDataSource) {
    companion object {
        // Technically we should use repeatable read to avoid dirty and non-repeatable reads! Very useful and safe!!
        // But we won't, because read commited is the default PostgreSQL behavior
        private val ISOLATION_LEVEL = IsolationLevel.TRANSACTION_READ_COMMITTED
        private val logger = KotlinLogging.logger {}
        private val CACHE_ENABLED = System.getenv("RINHA_CACHE")?.toBoolean() ?: true

        fun createPool(): Database {
            val hikariConfig = HikariConfig()

            hikariConfig.driverClassName = "org.postgresql.Driver"

            // https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert
            hikariConfig.addDataSourceProperty("reWriteBatchedInserts", "true")

            // We don't want to commit manually, this avoids unnecessary roundtrips from/to the server
            // A real application would use commit false and would manage their transactions manually tho
            hikariConfig.isAutoCommit = true

            // Useful to check if a connection is not returning to the pool, will be shown in the log as "Apparent connection leak detected"
            hikariConfig.leakDetectionThreshold = 30L * 1000
            hikariConfig.transactionIsolation = ISOLATION_LEVEL.name

            // https://github.com/lukas8219/rinha-be-2023-q3/blob/main/database.js#L8
            hikariConfig.maximumPoolSize = System.getenv("RINHA_POOL_SIZE")?.toInt() ?: 8
            hikariConfig.poolName = "RinhaPool"

            hikariConfig.jdbcUrl = "jdbc:postgresql://${System.getenv("POSTGRESQL_ADDRESS")}/${System.getenv("POSTGRESQL_DATABASE")}?ApplicationName=${"RinhaDeBackend2023Q3Port"}"

            hikariConfig.username = System.getenv("POSTGRESQL_USERNAME")
            hikariConfig.password = System.getenv("POSTGRESQL_PASSWORD")

            val hikariDataSource = HikariDataSource(hikariConfig)

            return Database(hikariDataSource)
        }
    }

    private val threadPool = Executors.newCachedThreadPool()
        .asCoroutineDispatcher()
    private val permits = Semaphore(dataSource.maximumPoolSize * 4)

    private suspend fun <T> withConnection(block: (Connection) -> (T)) = permits.withPermit {
        withContext(threadPool) {
            dataSource.connection.use {
                return@use block.invoke(it)
            }
        }
    }

    suspend fun createTables() {
        logger.info { "Creating table \"pessoas\" if not exists" }
        withConnection {
            val stmt = it.createStatement()
            stmt.execute("""CREATE TABLE IF NOT EXISTS pessoas (
    id UUID,
    apelido TEXT CONSTRAINT ID_PK PRIMARY KEY,
    nome TEXT,
    nascimento DATE,
    stack TEXT[],
    text TEXT
);

    CREATE UNLOGGED TABLE IF NOT EXISTS cache (
        key TEXT CONSTRAINT CACHE_ID_PK PRIMARY KEY,
        data jsonb
    );

CREATE INDEX IF NOT EXISTS IDX_PESSOAS_TEXT_TGRM ON pessoas USING GIST (text GIST_TRGM_OPS(SIGLEN=64));
            """)
        }
    }

    suspend fun insertPerson(apelido: String, nome: String, nascimento: LocalDate, stack: List<String>?): Pessoa {
        return withConnection {
            val stmt = it.prepareStatement("""INSERT INTO
     pessoas(
        id,
        apelido,
        nome,
        nascimento,
        stack,
        text
     )
    VALUES (
        ?,
        ?,
        ?,
        ?,
        ?,
        ?
    )
    RETURNING id""")
            // We don't return "everything" (apelido, nome, nascimento, stack) to avoid unnecessary data transfers
            stmt.setObject(1, UUID.randomUUID())
            stmt.setString(2, apelido)
            stmt.setString(3, nome)
            stmt.setDate(4, Date.valueOf(nascimento))
            stmt.setArray(5, it.createArrayOf("TEXT", stack?.toTypedArray()))
            stmt.setString(6, buildString {
                append(apelido.lowercase())
                append(nome.lowercase())
                stack?.forEach {
                    append(it.lowercase())
                }
            })
            val resultSet = stmt.executeQuery().apply { this.next() }

            return@withConnection Pessoa(
                resultSet.getObject("id", UUID::class.java),
                apelido,
                nome,
                nascimento,
                stack
            )
        }
    }

    suspend fun findById(id: UUID): Pessoa? {
        return withConnection {
            val stmt = it.prepareStatement("""SELECT
        id,
        apelido,
        nome,
        nascimento,
        stack
    FROM
        pessoas
    WHERE "id" = ?""")
            stmt.setObject(1, id)
            val resultSet = stmt.executeQuery()
            if (resultSet.next()) {
                return@withConnection convertToPessoa(resultSet)
            } else null
        }
    }

    suspend fun findByTerm(term: String): List<Pessoa> {
        val queryWildcard = "%${term.lowercase()}%"
        return withConnection {
            val results = mutableListOf<Pessoa>()
            val stmt = it.prepareStatement("""SELECT id, apelido, nome, nascimento, stack
            FROM pessoas P
            WHERE P.text ILIKE ?
            LIMIT 50""")
            stmt.setString(1, queryWildcard)
            val resultSet = stmt.executeQuery()
            while (resultSet.next()) {
                results.add(convertToPessoa(resultSet))
            }
            return@withConnection results
        }
    }

    suspend fun count(): Long {
        return withConnection {
            it.createStatement().executeQuery("SELECT COUNT(*) FROM pessoas").apply { next() }.getLong(1)
        }
    }

    suspend fun getCache(key: String): JsonElement? {
        if (!CACHE_ENABLED)
            return null

        return withConnection {
            val stmt = it.prepareStatement("""SELECT data FROM cache WHERE key = ?""")
            stmt.setString(1, key)
            val query = stmt.executeQuery()
            if (query.next()) {
                Json.parseToJsonElement(query.getString("data"))
            } else null
        }
    }

    suspend fun storeCacheAsJson(key: String, block: () -> (JsonElement)) {
        if (!CACHE_ENABLED)
            return

        // We use a block to avoid transforming the data to JSON if the cache is disabled
        storeCacheAsString(key) { block.invoke().toString() }
    }

    suspend fun storeCacheAsString(key: String, block: () -> (String)) {
        if (!CACHE_ENABLED)
            return

        val jsonAsData = block.invoke()
        withConnection {
            val stmt = it.prepareStatement("""INSERT INTO cache(key, data) VALUES (?, ?::jsonb) ON CONFLICT (key) DO UPDATE SET data = ?::jsonb""")
            stmt.setString(1, key)
            stmt.setString(2, jsonAsData)
            stmt.setString(3, jsonAsData)
            stmt.execute()
        }
    }
    private fun convertToPessoa(resultSet: ResultSet) = Pessoa(
        resultSet.getObject("id", UUID::class.java),
        resultSet.getString("nome"),
        resultSet.getString("apelido"),
        resultSet.getDate("nascimento").toLocalDate(),
        resultSet.getArray("stack")?.let {
            (it.array as Array<String>).toList()
        }
    )

    private fun dumpResultSetInformation(resultSet: ResultSet) {
        val rsmd = resultSet.metaData
        val columnsNumber = rsmd.columnCount
        while (resultSet.next()) {
            println("next")
            for (i in 1..columnsNumber) {
                if (i > 1) print(",  ")
                val columnValue = resultSet.getString(i)
                print(columnValue + " " + rsmd.getColumnName(i))
            }
            println("")
        }
    }
}