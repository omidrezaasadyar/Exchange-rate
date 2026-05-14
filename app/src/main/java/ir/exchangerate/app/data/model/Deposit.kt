package ir.exchangerate.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Deposit(
    val id: String,
    val personKey: String,
    val currency: Currency,
    val amount: Double,
    val timestampMillis: Long,
) {
    val person: Person?
        get() = runCatching { Person.valueOf(personKey) }.getOrNull()
}
