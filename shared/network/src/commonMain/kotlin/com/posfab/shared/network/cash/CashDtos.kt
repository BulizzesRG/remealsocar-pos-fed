package com.posfab.shared.network.cash

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

@Serializable
data class OpenCashSessionRequestDto(
    val terminalId: Long,
    val businessUnitId: Long,
    val openingAmount: String,
)

@Serializable
data class CloseCashSessionRequestDto(
    val terminalId: Long,
    val closingAmount: String,
)

@Serializable
data class CashSessionResponseDto(
    val id: Long,
    val terminalId: Long,
    val businessUnitId: Long,
    val status: String,
    val openingAmount: String,
    val closingAmount: String? = null,
    val openedAt: String,
    val closedAt: String? = null,
)

@Serializable
data class CashIdResponseDto(
    val id: Long,
)

@Serializable
data class CashSummaryResponseDto(
    val status: String,
    val previousClosingAmount: String? = null,
    val session: CashSessionSummaryDto? = null,
)

@Serializable
data class CashSessionSummaryDto(
    val sessionId: Long,
    val openedAt: String,
    val openingAmount: String,
    val cashSales: String,
    val creditPayments: String,
    val payouts: String,
    val otherOut: String,
    val expectedInDrawer: String,
    val transferSales: String,
    val cardSales: String,
    val creditSales: String,
    val openAccounts: Int,
    val payoutList: List<CashPayoutItemDto> = emptyList(),
)

@Serializable
data class CashPayoutItemDto(
    val id: Long,
    val amount: String,
    val reason: String,
    val notes: String? = null,
    val createdAt: String,
)

@Serializable(with = CashPayoutRequestSerializer::class)
data class CashPayoutRequestDto(
    val terminalId: Long,
    val amount: String,
    val reason: String,
    val notes: String?,
)

object CashPayoutRequestSerializer : KSerializer<CashPayoutRequestDto> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: CashPayoutRequestDto) {
        val json = encoder as? JsonEncoder ?: error("CashPayoutRequestDto supports JSON only")
        json.encodeJsonElement(
            buildJsonObject {
                put("terminalId", JsonPrimitive(value.terminalId))
                put("amount", JsonPrimitive(value.amount))
                put("reason", JsonPrimitive(value.reason))
                put("notes", value.notes?.let(::JsonPrimitive) ?: JsonNull)
            },
        )
    }

    override fun deserialize(decoder: Decoder): CashPayoutRequestDto {
        val json = decoder as? JsonDecoder ?: error("CashPayoutRequestDto supports JSON only")
        val fields = json.decodeJsonElement().jsonObject
        return CashPayoutRequestDto(
            terminalId = fields.getValue("terminalId").jsonPrimitive.long,
            amount = fields.getValue("amount").jsonPrimitive.content,
            reason = fields.getValue("reason").jsonPrimitive.content,
            notes = fields["notes"]?.jsonPrimitive?.contentOrNull,
        )
    }
}

@Serializable
data class CashPayoutResponseDto(
    val id: Long,
    val amount: String,
    val reason: String,
    val notes: String? = null,
    val createdAt: String,
    val expectedInDrawer: String,
)

@Serializable
data class CashCloseResponseDto(
    val id: Long,
    val terminalId: Long,
    val openedAt: String,
    val closedAt: String,
    val summary: CashSessionSummaryDto,
    val countedAmount: String,
    val delta: String,
)
