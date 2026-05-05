package ir.exchangerate.app.data.model

data class Rate(
    val currency: Currency,
    val priceRial: Long,
    val highRial: Long?,
    val lowRial: Long?,
    val changeRial: Long?,
    val changePercent: Double?,
    val direction: Direction,
    val sourceTimeText: String?,
    val fetchedAt: Long,
)

enum class Direction { UP, DOWN, FLAT }
