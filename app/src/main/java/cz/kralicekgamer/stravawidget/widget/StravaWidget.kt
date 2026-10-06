package cz.kralicekgamer.stravawidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cz.kralicekgamer.stravawidget.data.MenuRepository
import cz.kralicekgamer.stravawidget.data.WidgetContent
import cz.kralicekgamer.stravawidget.ui.MainActivity

class StravaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val version by MenuRepository.version.collectAsState()
            val content = remember(version) { MenuRepository.current(context) }
            GlanceTheme { WidgetBody(content) }
        }
    }
}

@Composable
private fun WidgetBody(content: WidgetContent) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when (content) {
            is WidgetContent.Day -> DayContent(content)
            WidgetContent.LoggedOut -> Message("Strava není přihlášená", "Klepni a přihlas se, ať vidíš svá jídla.")
            WidgetContent.Loading -> Message("Načítám jídelníček", "Chvilku to potrvá.")
            WidgetContent.NoMenu -> Message("Žádný jídelníček", "Na nejbližší dny jídelna nic nevypsala.")
            is WidgetContent.Error -> Message("Jídelníček se nenačetl", content.failure.message)
        }
    }
}

@Composable
private fun ColumnScope.DayContent(day: WidgetContent.Day) {
    // Ve vyšším widgetu je místo na větší písmo.
    val roomy = LocalSize.current.height >= 160.dp

    DayLabel(day.label)
    // Seznam se dá posouvat, takže se vejde snídaně, oběd i večeře i v malé výšce.
    LazyColumn(modifier = GlanceModifier.defaultWeight()) {
        items(day.meals) { meal ->
            ListRow { MealRow(meal.label, meal.name, emphasized = !meal.isSoup, roomy = roomy) }
        }
        if (day.nothingOrdered) {
            item {
                ListRow { MealRow("Jídlo", "Nic objednáno", emphasized = false, roomy = roomy) }
            }
        }
    }
}

@Composable
private fun ListRow(content: @Composable () -> Unit) {
    // Položky seznamu nedědí klepnutí z rámu widgetu.
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        content()
    }
}

@Composable
private fun DayLabel(label: String) {
    Text(
        text = label,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    )
    Spacer(GlanceModifier.height(8.dp))
}

@Composable
private fun MealRow(
    label: String,
    name: String,
    emphasized: Boolean,
    roomy: Boolean,
) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = label,
            maxLines = 1,
            modifier = GlanceModifier.width(64.dp).padding(top = if (roomy) 4.dp else 2.dp),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        Text(
            text = name,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (roomy) 16.sp else 14.sp,
                fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun Message(title: String, body: String) {
    Text(
        text = title,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    )
    Spacer(GlanceModifier.height(4.dp))
    Text(
        text = body,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
    )
}
