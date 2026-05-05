package ir.exchangerate.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TgjuResponse(
    val current: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class TgjuItem(
    @SerialName("p") val price: String? = null,
    @SerialName("h") val high: String? = null,
    @SerialName("l") val low: String? = null,
    @SerialName("d") val change: String? = null,
    @SerialName("dp") val changePercent: Double? = null,
    @SerialName("dt") val direction: String? = null,
    @SerialName("t") val time: String? = null,
)
