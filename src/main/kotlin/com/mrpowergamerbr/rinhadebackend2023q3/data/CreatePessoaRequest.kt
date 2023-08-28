package com.mrpowergamerbr.rinhadebackend2023q3.data

import com.mrpowergamerbr.rinhadebackend2023q3.utils.LocalDateAsStringSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

@Serializable
data class CreatePessoaRequest(
    val apelido: String,
    val nome: String,
    @Serializable(LocalDateAsStringSerializer::class)
    val nascimento: LocalDate,
    val stack: List<String>?
) {
    init {
        require(apelido.length in 0..32)
        require(nome.length in 0..100)
        stack?.forEach {
            require(it.length in 0..32)
        }
    }
}