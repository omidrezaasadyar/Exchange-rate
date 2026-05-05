package ir.exchangerate.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.exchangerate.app.data.DisplayUnit
import ir.exchangerate.app.data.SourceRates
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Direction
import ir.exchangerate.app.data.model.Rate
import ir.exchangerate.app.ui.util.formatPercent
import ir.exchangerate.app.ui.util.formatPrice
import ir.exchangerate.app.ui.util.timeAgoFa

@Composable
fun SourceSection(
    sourceRates: SourceRates,
    unit: DisplayUnit,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(sourceRates = sourceRates, nowMillis = nowMillis)

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            val ordered = listOf(Currency.USD, Currency.EUR, Currency.OMR)
            for ((index, currency) in ordered.withIndex()) {
                val rate = sourceRates.rates[currency]
                val error = sourceRates.errors[currency]
                CurrencyRow(currency = currency, rate = rate, error = error, unit = unit)
                if (index != ordered.lastIndex) {
                    Spacer(Modifier.height(2.dp))
                }
            }

            val firstHardError = sourceRates.errors.values.firstOrNull { err ->
                val m = err.message.orEmpty()
                !(m.contains("پیدا نشد") ||
                    m.contains("not found", ignoreCase = true) ||
                    m.contains("نیست"))
            }
            if (sourceRates.rates.isEmpty() && firstHardError != null) {
                Spacer(Modifier.height(4.dp))
                ErrorDetails(error = firstHardError)
            }
        }
    }
}

@Composable
private fun SectionHeader(sourceRates: SourceRates, nowMillis: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = sourceRates.source.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val timeText = sourceRates.lastFetchedAt?.let { timeAgoFa(nowMillis - it) }
                ?: "هنوز دریافت نشده"
            Text(
                text = "آخرین بروزرسانی: $timeText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SourceStatusDot(
            isFetching = sourceRates.isFetching,
            isFresh = sourceRates.isFresh,
            hasData = sourceRates.rates.isNotEmpty(),
        )
    }
}

@Composable
private fun SourceStatusDot(isFetching: Boolean, isFresh: Boolean, hasData: Boolean) {
    val color = when {
        isFetching -> Color(0xFF1F6FEB)
        isFresh -> Color(0xFF16A34A)
        hasData -> Color(0xFFD97706)
        else -> Color(0xFFDC2626)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = when {
                isFetching -> "در حال دریافت"
                isFresh -> "زنده"
                hasData -> "کش"
                else -> "خطا"
            },
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun CurrencyRow(
    currency: Currency,
    rate: Rate?,
    error: Throwable?,
    unit: DisplayUnit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(PaddingValues(horizontal = 12.dp, vertical = 10.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = currency.flagEmoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = currency.displayNameFa,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = currency.symbol,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (rate != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatPrice(rate.priceRial, unit),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (unit == DisplayUnit.TOMAN) "تومان" else "ریال",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    rate.changePercent?.let { pct ->
                        Spacer(Modifier.width(6.dp))
                        ChangeChip(direction = rate.direction, percent = pct)
                    }
                }
            }
        } else {
            val msg = error?.message.orEmpty()
            val notAvailable = msg.contains("پیدا نشد") ||
                msg.contains("not found", ignoreCase = true) ||
                msg.contains("نیست")
            Text(
                text = if (notAvailable) "در این منبع موجود نیست" else msg.take(60).ifBlank { "—" },
                style = MaterialTheme.typography.labelMedium,
                color = if (notAvailable) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun ErrorDetails(error: Throwable) {
    val message = error.message ?: error::class.java.simpleName
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFDC2626).copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "جزئیات خطا",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFDC2626),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ChangeChip(direction: Direction, percent: Double) {
    val tint = when (direction) {
        Direction.UP -> Color(0xFF16A34A)
        Direction.DOWN -> Color(0xFFDC2626)
        Direction.FLAT -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (direction) {
        Direction.UP -> Icons.Filled.ArrowUpward
        Direction.DOWN -> Icons.Filled.ArrowDownward
        Direction.FLAT -> Icons.Filled.Remove
    }
    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(tint.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
            Text(
                text = formatPercent(percent),
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}
