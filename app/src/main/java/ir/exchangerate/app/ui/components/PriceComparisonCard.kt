package ir.exchangerate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import ir.exchangerate.app.data.model.Source
import ir.exchangerate.app.ui.util.formatPrice

@Composable
fun PriceComparisonCard(
    sources: List<SourceRates>,
    unit: DisplayUnit,
    modifier: Modifier = Modifier,
) {
    val minUsd = lowestPrice(sources, Currency.USD)
    val minEur = lowestPrice(sources, Currency.EUR)
    if (minUsd == null && minEur == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .background(Color(0xFF16A34A)),
            )
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Filled.TrendingDown,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "کمترین قیمت در منابع",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                minUsd?.let { (src, price) ->
                    LowestRow(currency = Currency.USD, source = src, priceRial = price, unit = unit)
                }
                minEur?.let { (src, price) ->
                    LowestRow(currency = Currency.EUR, source = src, priceRial = price, unit = unit)
                }
            }
        }
    }
}

@Composable
private fun LowestRow(
    currency: Currency,
    source: Source,
    priceRial: Long,
    unit: DisplayUnit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = currency.flagEmoji, fontSize = 16.sp)
            }
            Column {
                Text(
                    text = currency.displayNameFa,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "از ${source.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(source.accentColorHex),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatPrice(priceRial, unit),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (unit == DisplayUnit.TOMAN) "تومان" else "ریال",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun lowestPrice(sources: List<SourceRates>, currency: Currency): Pair<Source, Long>? {
    return sources
        .mapNotNull { sr -> sr.rates[currency]?.let { sr.source to it.priceRial } }
        .minByOrNull { it.second }
}
