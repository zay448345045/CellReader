package dev.zwander.cellreader.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.telephony.AccessNetworkConstants
import android.telephony.TelephonyManager
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.zwander.cellreader.BuildConfig
import dev.zwander.cellreader.MainActivity
import dev.zwander.cellreader.UpdaterService
import dev.zwander.cellreader.data.ARFCNTools
import dev.zwander.cellreader.data.R
import dev.zwander.cellreader.data.data.CellModel
import dev.zwander.cellreader.data.layouts.glance.SignalBarGroup
import dev.zwander.cellreader.data.typeString
import dev.zwander.cellreader.data.util.asMccMnc
import dev.zwander.cellreader.data.util.onAvail
import dev.zwander.cellreader.data.wrappers.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SignalWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Exact

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val size = LocalSize.current

        with(CellModel) {
            Box(
                modifier = GlanceModifier.cornerRadius(8.dp)
                    .appWidgetBackground()
                    .fillMaxSize()
            ) {
                LazyColumn(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    sortedSubIds.forEachIndexed { _, t ->
                        item(t.toLong()) {
                            Box(
                                modifier = GlanceModifier.padding(bottom = 4.dp)
                            ) {
                                Column(
                                    modifier = GlanceModifier.wrapContentHeight()
                                        .fillMaxWidth()
                                        .background(ImageProvider(R.drawable.sim_card_widget_background))
                                        .cornerRadius(12.dp)
                                        .padding(bottom = 4.dp, top = 4.dp),
                                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                                ) {
                                    val subInfo = subInfos[t]

                                    Row(
                                        verticalAlignment = Alignment.Vertical.CenterVertically
                                    ) {
                                        subInfo?.iconBitmap?.let { bmp ->
                                            Image(
                                                provider = ImageProvider(
                                                    Icon.createWithData(
                                                        bmp,
                                                        0,
                                                        bmp.size
                                                    )
                                                ),
                                                contentDescription = null,
                                                modifier = GlanceModifier.size(16.dp)
                                            )
                                        }

                                        Spacer(GlanceModifier.size(8.dp))

                                        Text(
                                            text = (subInfo?.carrierName ?: t.toString()),
                                            style = TextStyle(
                                                color = ColorProvider(Color.White)
                                            )
                                        )
                                    }

                                    val rplmn =
                                        serviceStates[subInfo?.id]?.getNetworkRegistrationInfoListForTransportType(
                                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                                        )
                                            ?.firstOrNull { it.accessNetworkTechnology != TelephonyManager.NETWORK_TYPE_IWLAN }
                                            ?.rplmn.asMccMnc

                                    Row(
                                        verticalAlignment = Alignment.Vertical.CenterVertically,
                                        modifier = GlanceModifier.fillMaxWidth()
                                    ) {
                                        Spacer(GlanceModifier.defaultWeight())

                                        FormatWidgetText(
                                            name = context.resources.getString(R.string.rplmn_format),
                                            value = rplmn
                                        )

                                        Spacer(GlanceModifier.defaultWeight())

                                        FormatWidgetText(
                                            name = context.resources.getString(R.string.carrier_aggregation_format),
                                            value = serviceStates[subInfo?.id]?.isUsingCarrierAggregation
                                        )

                                        Spacer(GlanceModifier.defaultWeight())
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            strengthInfos[t]!!,
                            { index, _ -> "$t:$index".hashCode().toLong() }) { _, item ->
                            StrengthCard(
                                strength = item,
                                size = size,
                                modifier = GlanceModifier.padding(bottom = 4.dp)
                            )
                        }

                        itemsIndexed(
                            cellInfos[t]!!,
                            { _, item ->
                                "$t:${item.cellIdentity}".hashCode().toLong()
                            }) { _, item ->
                            SignalCard(
                                cellInfo = item, size = size,
                                modifier = GlanceModifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    item {
                        Box(
                            modifier = GlanceModifier.fillMaxWidth()
                                .background(ImageProvider(R.drawable.open_app_widget_background))
                                .cornerRadius(12.dp)
                                .padding(bottom = 8.dp, top = 8.dp)
                                .clickable(onClick = actionStartActivity<MainActivity>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = context.resources.getString(R.string.open_app),
                                style = TextStyle(
                                    color = ColorProvider(Color.White)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun CellSignalStrengthWrapper.createItems(
        context: Context,
        full: Boolean = true
    ): Map<String, Any?> {
        return hashMapOf<String, Any?>().apply {
            when {
                this@createItems is CellSignalStrengthLteWrapper -> {
                    put(
                        context.resources.getString(R.string.rsrq_format),
                        rsrq
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this@createItems is CellSignalStrengthNrWrapper -> {
                    csiRsrq.onAvail {
                        put(
                            context.resources.getString(R.string.rsrq_format),
                            it
                        )
                    }

                    ssRsrq.onAvail {
                        put(
                            context.resources.getString(R.string.rsrq_format),
                            it
                        )
                    }
                }
                else -> {}
            }

            if (full) {
                put(
                    context.resources.getString(R.string.asu_format),
                    asuLevel
                )
            }
        }
    }

    private fun CellInfoWrapper.createItems(context: Context): Map<String, Any?> {
        return hashMapOf<String, Any?>().apply {
            with(cellSignalStrength) {
                putAll(this.createItems(context, false))
            }

            with(cellIdentity) {
                when {
                    this is CellIdentityGsmWrapper -> {
                        val arfcnInfo = ARFCNTools.gsmArfcnToInfo(arfcn)
                        val bands = arfcnInfo.map { it.band }

                        if (bands.isNotEmpty()) {
                            put(
                                context.resources.getString(R.string.bands_format),
                                bands.joinToString(", ")
                            )
                        }
                    }
                    this is CellIdentityWcdmaWrapper -> {
                        val arfcnInfo = ARFCNTools.gsmArfcnToInfo(uarfcn)
                        val bands = arfcnInfo.map { it.band }

                        if (bands.isNotEmpty()) {
                            put(
                                context.resources.getString(R.string.bands_format),
                                bands.joinToString(", ")
                            )
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this is CellIdentityTdscdmaWrapper -> {
                        val arfcnInfo = ARFCNTools.gsmArfcnToInfo(uarfcn)
                        val bands = arfcnInfo.map { it.band }

                        if (bands.isNotEmpty()) {
                            put(
                                context.resources.getString(R.string.bands_format),
                                bands.joinToString(", ")
                            )
                        }
                    }
                    this is CellIdentityLteWrapper -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            put(
                                context.resources.getString(R.string.bands_format),
                                bands?.joinToString(", ")
                            )
                        } else {
                            val arfcnInfo = ARFCNTools.gsmArfcnToInfo(earfcn)
                            val bands = arfcnInfo.map { it.band }

                            if (bands.isNotEmpty()) {
                                put(
                                    context.resources.getString(R.string.bands_format),
                                    bands.joinToString(", ")
                                )
                            }
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this is CellIdentityNrWrapper -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            put(
                                context.resources.getString(R.string.bands_format),
                                bands?.joinToString(", ")
                            )
                        } else {
                            val arfcnInfo = ARFCNTools.gsmArfcnToInfo(nrArfcn)
                            val bands = arfcnInfo.map { it.band }

                            if (bands.isNotEmpty()) {
                                put(
                                    context.resources.getString(R.string.bands_format),
                                    bands.joinToString(", ")
                                )
                            }
                        }
                    }
                }

                if (!plmn.isNullOrBlank()) {
                    put(
                        context.resources.getString(R.string.plmn_format),
                        plmn.asMccMnc
                    )
                }
            }
        }
    }

    @Composable
    private fun BaseCard(
        strength: CellSignalStrengthWrapper,
        size: DpSize,
        modifier: GlanceModifier,
        backgroundResource: Int,
        items: Map<String, Any?>
    ) {
        val context = LocalContext.current

        Box(
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth()
                    .background(imageProvider = ImageProvider(backgroundResource))
                    .cornerRadius(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier.padding(start = 8.dp, end = 8.dp)
                        .fillMaxWidth(),
                ) {
                    val type = strength.typeString(context)

                    val itemGridArray = run {
                        val grid = hashMapOf<Int, MutableList<Pair<String, Any?>>>()
                        val rowSize = 3

                        items.entries.forEachIndexed { index, entry ->
                            val gridRowIndex = index / rowSize
                            val gridColumnIndex = index % rowSize

                            if (!grid.containsKey(gridRowIndex)) {
                                grid[gridRowIndex] = mutableListOf()
                            }

                            grid[gridRowIndex]?.add(gridColumnIndex, entry.toPair())
                        }

                        grid
                    }

                    @Composable
                    fun itemGrid() {
                        itemGridArray.forEach { (_, columns) ->
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(GlanceModifier.defaultWeight())
                                columns.forEachIndexed { index, column ->
                                    FormatWidgetText(name = column.first, value = column.second)

                                    if (index < columns.lastIndex) {
                                        Spacer(GlanceModifier.defaultWeight())
                                    }
                                }
                                Spacer(GlanceModifier.defaultWeight())
                            }
                        }
                    }

                    if (size.width >= 190.dp) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SignalBarGroup(level = strength.level, dbm = strength.dbm, type = type)

                            Spacer(GlanceModifier.size(8.dp))

                            Column(
                                modifier = GlanceModifier.fillMaxWidth()
                            ) {
                                itemGrid()
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = GlanceModifier.padding(top = 4.dp, bottom = 4.dp)
                        ) {
                            SignalBarGroup(level = strength.level, dbm = strength.dbm, type = type)

                            Spacer(GlanceModifier.size(8.dp))

                            itemGrid()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SignalCard(cellInfo: CellInfoWrapper, size: DpSize, modifier: GlanceModifier) {
        val context = LocalContext.current

        val items = cellInfo.createItems(context)

        BaseCard(
            strength = cellInfo.cellSignalStrength, size = size, modifier = modifier,
            backgroundResource = R.drawable.signal_card_widget_background, items = items
        )
    }

    @Composable
    private fun StrengthCard(
        strength: CellSignalStrengthWrapper,
        size: DpSize,
        modifier: GlanceModifier
    ) {
        val context = LocalContext.current

        val items = strength.createItems(context)

        BaseCard(
            strength = strength,
            size = size,
            modifier = modifier,
            backgroundResource = R.drawable.signal_strength_widget_background,
            items = items
        )
    }
}

class SignalWidgetReceiver : GlanceAppWidgetReceiver() {
    companion object {
        const val ACTION_REFRESH = "${BuildConfig.APPLICATION_ID}.REFRESH"
    }

    override val glanceAppWidget = SignalWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)

        context.startForegroundService(Intent(context, UpdaterService::class.java))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName =
                ComponentName(context.packageName, checkNotNull(javaClass.canonicalName))
            onUpdate(
                context,
                appWidgetManager,
                appWidgetManager.getAppWidgetIds(componentName)
            )
            GlobalScope.launch {
                glanceAppWidget.updateAll(context)
            }
            return
        }

        super.onReceive(context, intent)
    }
}