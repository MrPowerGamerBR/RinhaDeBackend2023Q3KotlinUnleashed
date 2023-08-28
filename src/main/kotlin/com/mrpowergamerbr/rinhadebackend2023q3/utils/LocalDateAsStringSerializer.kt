package com.mrpowergamerbr.rinhadebackend2023q3.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object LocalDateAsStringSerializer : KSerializer<LocalDate> {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
        .withResolverStyle(ResolverStyle.STRICT) // We want to throw an exception if it is a invalid date (example: impossible date due to leap years)

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        val formattedDate = value.format(dateFormatter)
        encoder.encodeString(formattedDate)
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        val formattedDate = decoder.decodeString()
        return LocalDate.parse(formattedDate, dateFormatter)
    }
}