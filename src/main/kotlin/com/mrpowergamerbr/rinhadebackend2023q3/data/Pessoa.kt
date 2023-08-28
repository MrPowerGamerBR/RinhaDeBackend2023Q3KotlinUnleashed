package com.mrpowergamerbr.rinhadebackend2023q3.data

import com.mrpowergamerbr.rinhadebackend2023q3.utils.LocalDateAsStringSerializer
import com.mrpowergamerbr.rinhadebackend2023q3.utils.UUIDAsStringSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import java.time.LocalDate
import java.util.*

@Serializable
data class Pessoa(
    @Serializable(UUIDAsStringSerializer::class)
    val id: UUID,
    val apelido: String,
    val nome: String,
    @Serializable(LocalDateAsStringSerializer::class)
    val nascimento: LocalDate,
    val stack: List<String>?
)