package com.slowie.atvLauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.media.tv.TvInputManager
import android.media.tv.TvInputInfo
import android.media.tv.TvContract
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

// Keep these deliberately small for low-memory Android TV devices. The cache
// is in addition to Compose, graphics, and the currently displayed wallpaper.
private val appArtworkCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private val uriBitmapCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

internal fun trimBitmapCaches() {
    appArtworkCache.evictAll()
    uriBitmapCache.evictAll()
}

@Composable
internal fun rememberClockText(): String {
    val formatter = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    var currentTime by remember { mutableStateOf(formatter.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            currentTime = formatter.format(Date(now))
            val delayDuration = (60_000L - (now % 60_000L)).coerceAtLeast(1_000L)
            delay(delayDuration)
        }
    }

    return currentTime
}

@Composable
internal fun rememberEstimatedSystemDarkMode(): Boolean {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            currentTimeMillis = now
            val delayDuration = (60_000L - (now % 60_000L)).coerceAtLeast(1_000L)
            delay(delayDuration)
        }
    }

    return remember(currentTimeMillis) {
        isEstimatedNight(currentTimeMillis)
    }
}

internal fun isEstimatedNight(
    timeMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val zonedDateTime = Instant.ofEpochMilli(timeMillis).atZone(zoneId)
    val date = zonedDateTime.toLocalDate()
    val time = zonedDateTime.toLocalTime()
    val sunrise = approximateSunrise(date)
    val sunset = approximateSunset(date)
    return time.isBefore(sunrise) || !time.isBefore(sunset)
}

private fun approximateSunrise(date: LocalDate): LocalTime {
    val daylightHours = approximateDaylightHours(date)
    val sunriseHour = 12.0 - (daylightHours / 2.0)
    return fractionalHourToLocalTime(sunriseHour)
}

private fun approximateSunset(date: LocalDate): LocalTime {
    val daylightHours = approximateDaylightHours(date)
    val sunsetHour = 12.0 + (daylightHours / 2.0)
    return fractionalHourToLocalTime(sunsetHour)
}

private fun approximateDaylightHours(date: LocalDate): Double {
    val dayOfYear = date.dayOfYear.toDouble()
    val seasonalOffset = cos((2.0 * PI * (dayOfYear - 172.0)) / 365.25)
    return 12.0 + (2.0 * seasonalOffset)
}

private fun fractionalHourToLocalTime(hour: Double): LocalTime {
    val clampedHour = hour.coerceIn(0.0, 23.999)
    val wholeHours = clampedHour.toInt()
    val minutes = ((clampedHour - wholeHours) * 60.0).roundToInt().coerceIn(0, 59)
    return LocalTime.of(wholeHours, minutes)
}

@Composable
internal fun rememberNetworkStatus(): MutableState<NetworkStatus> {
    val context = LocalContext.current
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    val state = remember { mutableStateOf(getCurrentNetworkStatus(connectivityManager)) }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                state.value = getCurrentNetworkStatus(connectivityManager)
            }

            override fun onLost(network: android.net.Network) {
                state.value = getCurrentNetworkStatus(connectivityManager)
            }

            override fun onCapabilitiesChanged(
                network: android.net.Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                state.value = getCurrentNetworkStatus(connectivityManager)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    return state
}

internal fun getCurrentNetworkStatus(connectivityManager: ConnectivityManager): NetworkStatus {
    val network = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(network)
    val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val transport = when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> TransportType.ETHERNET
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> TransportType.WIFI
        else -> TransportType.NONE
    }
    return NetworkStatus(hasInternet = hasInternet, transport = transport)
}

internal fun loadInstalledApps(context: Context): List<LauncherApp> {
    val pm = context.packageManager
    val appsByPackage = LinkedHashMap<String, LauncherApp>()

    fun addApp(app: LauncherApp) {
        if (!appsByPackage.containsKey(app.packageName)) {
            appsByPackage[app.packageName] = app
        }
    }

    val leanbackIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = pm.queryIntentActivities(leanbackIntent, 0) +
        pm.queryIntentActivities(launcherIntent, 0)

    resolveInfos.forEach { info ->
        val pkg = info.activityInfo.packageName
        if (pkg == context.packageName) return@forEach
        val label = info.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
        val component = ComponentName(pkg, info.activityInfo.name)
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setComponent(component)
        }
        addApp(
            LauncherApp(
                label = label,
                packageName = pkg,
                componentName = component,
                launchIntent = launchIntent,
            )
        )
    }

    pm.getInstalledApplications(0).forEach { appInfo ->
        val pkg = appInfo.packageName
        if (pkg == context.packageName) return@forEach
        if (appsByPackage.containsKey(pkg)) return@forEach
        val launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            val label = pm.getApplicationLabel(appInfo)?.toString()?.ifBlank { pkg } ?: pkg
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addApp(
                LauncherApp(
                    label = label,
                    packageName = pkg,
                    componentName = launchIntent.component,
                    launchIntent = launchIntent,
                )
            )
        }
    }

    return appsByPackage.values.sortedBy { it.label.lowercase() }
}

internal fun loadDeviceInputs(context: Context): List<InputSource> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return emptyList()
    val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        ?: return emptyList()
    return try {
        val hardwareTypes = setOf<Int>(
            TvInputInfo.TYPE_HDMI,
            TvInputInfo.TYPE_COMPONENT,
            TvInputInfo.TYPE_COMPOSITE,
            TvInputInfo.TYPE_SVIDEO,
            TvInputInfo.TYPE_DISPLAY_PORT,
            TvInputInfo.TYPE_DVI,
        )
        val excludedLabelFragments = listOf(
            "tuner",
            "google play movies",
            "movies & tv",
        )
        manager.tvInputList.mapNotNull { input ->
            if (!input.isPassthroughInput) return@mapNotNull null
            if (input.type !in hardwareTypes) return@mapNotNull null
            val label = input.loadLabel(context)?.toString()?.trim().orEmpty()
            if (label.isEmpty()) return@mapNotNull null
            val normalizedLabel = label.lowercase()
            if (excludedLabelFragments.any { fragment -> normalizedLabel.contains(fragment) }) {
                return@mapNotNull null
            }
            InputSource(id = input.id, label = label)
        }.distinctBy { it.label.trim().lowercase() }
            .sortedBy { it.label }
    } catch (e: Exception) {
        emptyList()
    }
}

internal fun getCachedAppArtwork(app: LauncherApp): Bitmap? {
    return appArtworkCache.get(app.artworkCacheKey())
}

internal fun prefetchArtworkForGridItemIds(
    context: Context,
    itemIds: Collection<String>,
    appsByPackage: Map<String, LauncherApp>,
    foldersById: Map<String, LauncherFolder>,
) {
    itemIds.forEach { itemId ->
        if (isFolderId(itemId)) {
            foldersById[itemId]?.appPackageNames?.forEach { packageName ->
                appsByPackage[packageName]?.let { loadAppArtwork(context, it) }
            }
        } else {
            appsByPackage[itemId]?.let { loadAppArtwork(context, it) }
        }
    }
}

internal fun gridItemIdsForRowRange(
    visibleGridItemIds: List<String>,
    centerRow: Int,
    rowRadius: Int = 1,
): List<String> {
    if (visibleGridItemIds.isEmpty()) return emptyList()
    val maxRow = ((visibleGridItemIds.size - 1) / 6).coerceAtLeast(0)
    val startRow = (centerRow - rowRadius).coerceAtLeast(0)
    val endRow = (centerRow + rowRadius).coerceAtMost(maxRow)
    val startIndex = startRow * 6
    val endIndex = ((endRow + 1) * 6).coerceAtMost(visibleGridItemIds.size)
    return visibleGridItemIds.subList(startIndex, endIndex)
}

internal fun loadAppArtwork(context: Context, app: LauncherApp): Bitmap? {
    getCachedAppArtwork(app)?.let { return it }

    val pm = context.packageManager
    val (targetWidth, targetHeight) = getTileTargetSizePx(context)

    readDiskCachedArtwork(context, app, targetWidth, targetHeight)?.let { cached ->
        appArtworkCache.put(app.artworkCacheKey(), cached)
        return cached
    }

    val drawable = app.componentName?.let { component ->
        runCatching { pm.getActivityBanner(component) }.getOrNull()
            ?: runCatching { pm.getActivityLogo(component) }.getOrNull()
            ?: runCatching { pm.getActivityIcon(component) }.getOrNull()
    } ?: runCatching { pm.getApplicationBanner(app.packageName) }.getOrNull()
        ?: runCatching { pm.getApplicationLogo(app.packageName) }.getOrNull()
        ?: runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()

    val artwork = drawable?.let { drawableToBitmap(it, targetWidth, targetHeight) } ?: return null
    appArtworkCache.put(app.artworkCacheKey(), artwork)
    writeDiskCachedArtwork(context, app, artwork, targetWidth, targetHeight)
    return artwork
}

internal fun openTvInput(context: Context, inputId: String) {
    val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    launchFirstAvailable(context, listOf(intent))
}

internal fun getTileTargetSizePx(context: Context): Pair<Int, Int> {
    val focusScale = AppCardFocusScale
    val density = context.resources.displayMetrics.density
    val widthPx = (DrawerCardWidth.value * focusScale * density).roundToInt()
    val heightPx = (DrawerCardHeight.value * focusScale * density).roundToInt()
    return widthPx to heightPx
}

internal fun drawableToBitmap(drawable: Drawable, targetWidth: Int, targetHeight: Int): Bitmap {
    val intrinsicWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else targetWidth
    val intrinsicHeight = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else targetHeight

    // Fit artwork inside the Android TV banner-shaped tile instead of
    // enlarging the shorter dimension and cropping logos/icons.
    val scale = minOf(
        targetWidth.toFloat() / intrinsicWidth.toFloat(),
        targetHeight.toFloat() / intrinsicHeight.toFloat()
    )
    val drawWidth = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
    val drawHeight = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
    val left = ((targetWidth - drawWidth) / 2f).toInt()
    val top = ((targetHeight - drawHeight) / 2f).toInt()

    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(left, top, left + drawWidth, top + drawHeight)
    drawable.draw(canvas)
    return bitmap
}

private fun LauncherApp.artworkCacheKey(): String {
    return componentName?.flattenToShortString() ?: packageName
}

private fun artworkDiskCacheDir(context: Context): File {
    val dir = File(context.cacheDir, "artwork_v2")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun artworkDiskFile(
    context: Context,
    app: LauncherApp,
    width: Int,
    height: Int,
): File {
    val safeKey = app.artworkCacheKey().replace(Regex("[^A-Za-z0-9._-]"), "_")
    return File(artworkDiskCacheDir(context), "${safeKey}_${width}x${height}.png")
}

private fun readDiskCachedArtwork(
    context: Context,
    app: LauncherApp,
    width: Int,
    height: Int,
): Bitmap? {
    val file = artworkDiskFile(context, app, width, height)
    if (!file.exists() || file.length() == 0L) return null
    return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
}

private fun writeDiskCachedArtwork(
    context: Context,
    app: LauncherApp,
    bitmap: Bitmap,
    width: Int,
    height: Int,
) {
    val target = artworkDiskFile(context, app, width, height)
    val parent = target.parentFile ?: return
    val temp = runCatching {
        File.createTempFile(target.name, ".tmp", parent)
    }.getOrNull() ?: return
    try {
        FileOutputStream(temp).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (target.exists()) target.delete()
        temp.renameTo(target)
    } catch (_: Exception) {
        temp.delete()
    }
}

internal fun isFolderId(id: String): Boolean = id.startsWith(FOLDER_ID_PREFIX)

internal fun generateFolderId(): String = "$FOLDER_ID_PREFIX${System.currentTimeMillis()}_${System.nanoTime()}"

internal fun loadFolders(prefs: SharedPreferences): List<LauncherFolder> {
    val raw = prefs.getString(KEY_FOLDERS, null) ?: return emptyList()
    return try {
        val jsonArray = JSONArray(raw)
        buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() && isFolderId(it) } ?: continue
                val name = item.optString("name").ifBlank { DEFAULT_FOLDER_NAME }
                val apps = item.optJSONArray("apps")
                    ?.let { appArray ->
                        buildList {
                            for (appIndex in 0 until appArray.length()) {
                                val packageName = appArray.optString(appIndex)
                                if (packageName.isNotBlank()) {
                                    add(packageName)
                                }
                            }
                        }
                    }
                    .orEmpty()
                add(
                    LauncherFolder(
                        id = id,
                        name = name,
                        appPackageNames = apps,
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun saveFolders(prefs: SharedPreferences, folders: List<LauncherFolder>) {
    val payload = JSONArray().apply {
        folders.forEach { folder ->
            put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("apps", JSONArray(folder.appPackageNames))
            )
        }
    }
    prefs.edit().putString(KEY_FOLDERS, payload.toString()).apply()
}

internal fun normalizeFolders(
    current: List<LauncherFolder>,
    apps: List<LauncherApp>,
    hiddenPackages: Set<String>,
): List<LauncherFolder> {
    val visibleInstalledPackages = apps
        .map { it.packageName }
        .filterNot { hiddenPackages.contains(it) }
        .toSet()
    val claimedPackages = LinkedHashSet<String>()

    return current.mapNotNull { folder ->
        val cleanedApps = folder.appPackageNames
            .filter { visibleInstalledPackages.contains(it) }
            .filter { claimedPackages.add(it) }
        if (cleanedApps.isEmpty()) {
            null
        } else {
            folder.copy(
                name = folder.name.ifBlank { DEFAULT_FOLDER_NAME },
                appPackageNames = cleanedApps,
            )
        }
    }
}

internal fun folderPackages(folders: List<LauncherFolder>): Set<String> {
    return folders.flatMapTo(LinkedHashSet()) { it.appPackageNames }
}

internal fun loadDockSlots(prefs: SharedPreferences): List<String?> {
    val raw = prefs.getString(KEY_DOCK, null) ?: return List(DOCK_SIZE) { null }
    val parts = raw.split("|")
    val slots = parts.map { part -> part.ifBlank { null } }.toMutableList()
    while (slots.size < DOCK_SIZE) slots.add(null)
    return slots.take(DOCK_SIZE)
}

internal fun loadGridOrder(prefs: SharedPreferences): List<String> {
    val raw = prefs.getString(KEY_GRID_ORDER, null) ?: return emptyList()
    return raw.split("|").filter { it.isNotBlank() }
}

internal fun saveGridOrder(prefs: SharedPreferences, order: List<String>) {
    prefs.edit().putString(KEY_GRID_ORDER, order.joinToString("|")).apply()
}

internal fun normalizeDockSlots(
    currentDock: List<String?>,
    apps: List<LauncherApp>,
    hiddenPackages: Set<String>,
    folders: List<LauncherFolder>,
): List<String?> {
    val installed = apps.map { it.packageName }.toSet()
    val validFolderIds = folders.filter { it.appPackageNames.isNotEmpty() }.map { it.id }.toSet()
    val visible = apps.filter { !hiddenPackages.contains(it.packageName) }
    val normalized = currentDock.map { pkg ->
        when {
            pkg == null -> null
            validFolderIds.contains(pkg) -> pkg
            installed.contains(pkg) && !hiddenPackages.contains(pkg) -> pkg
            else -> null
        }
    }.toMutableList()

    val alreadyDocked = normalized.filterNotNull().toMutableSet()
    val fillApps = visible.filter { !alreadyDocked.contains(it.packageName) }.toMutableList()
    for (index in normalized.indices) {
        if (normalized[index] == null && fillApps.isNotEmpty()) {
            val app = fillApps.removeAt(0)
            normalized[index] = app.packageName
            alreadyDocked.add(app.packageName)
        }
    }

    while (normalized.size < DOCK_SIZE) normalized.add(null)
    return normalized.take(DOCK_SIZE)
}

internal fun normalizeGridOrder(
    current: List<String>,
    apps: List<LauncherApp>,
    hiddenPackages: Set<String>,
    folders: List<LauncherFolder>,
): List<String> {
    val packageNamesInFolders = folderPackages(folders)
    val visibleStandalonePackages = apps
        .map { it.packageName }
        .filterNot { hiddenPackages.contains(it) || packageNamesInFolders.contains(it) }
    val folderIds = folders.map { it.id }
    val validEntries = (visibleStandalonePackages + folderIds).toSet()
    val normalized = current.filter { validEntries.contains(it) }.toMutableList()

    folderIds.forEach { folderId ->
        if (!normalized.contains(folderId)) normalized.add(folderId)
    }
    visibleStandalonePackages.forEach { pkg ->
        if (!normalized.contains(pkg)) normalized.add(pkg)
    }
    return normalized
}

internal fun dedupeGridOrder(order: List<String>): List<String> {
    val seen = HashSet<String>()
    return order.filter { seen.add(it) }
}

internal fun visibleIndexToOrderIndex(
    order: List<String>,
    docked: Set<String>,
    visibleIndex: Int,
): Int {
    if (visibleIndex <= 0) {
        val first = order.indexOfFirst { !docked.contains(it) }
        return if (first == -1) order.size else first
    }
    var count = 0
    for (i in order.indices) {
        if (docked.contains(order[i])) continue
        if (count == visibleIndex) return i
        count++
    }
    return order.size
}

internal fun saveDockSlots(prefs: SharedPreferences, dock: List<String?>) {
    val raw = dock.joinToString("|") { it.orEmpty() }
    prefs.edit().putString(KEY_DOCK, raw).apply()
}

internal fun loadHiddenPackages(prefs: SharedPreferences): Set<String> {
    return prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
}

internal fun saveHiddenPackages(prefs: SharedPreferences, hidden: Set<String>) {
    prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply()
}

internal fun loadHiddenInputs(prefs: SharedPreferences): Set<String> {
    return prefs.getStringSet(KEY_HIDDEN_INPUTS, emptySet()) ?: emptySet()
}

internal fun saveHiddenInputs(prefs: SharedPreferences, hidden: Set<String>) {
    prefs.edit().putStringSet(KEY_HIDDEN_INPUTS, hidden).apply()
}

internal fun loadCachedApps(prefs: SharedPreferences): List<LauncherApp> {
    val raw = prefs.getString(KEY_APP_CACHE, null) ?: return emptyList()
    return try {
        val jsonArray = JSONArray(raw)
        buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                val label = item.optString("label").takeIf { it.isNotBlank() } ?: continue
                val packageName = item.optString("packageName").takeIf { it.isNotBlank() } ?: continue
                val componentName = item.optString("component")
                    .takeIf { it.isNotBlank() }
                    ?.let(ComponentName::unflattenFromString)
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (componentName != null) {
                        setComponent(componentName)
                    } else {
                        `package` = packageName
                    }
                }
                add(
                    LauncherApp(
                        label = label,
                        packageName = packageName,
                        componentName = componentName,
                        launchIntent = launchIntent,
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun saveCachedApps(prefs: SharedPreferences, apps: List<LauncherApp>) {
    val payload = JSONArray().apply {
        apps.forEach { app ->
            put(
                JSONObject()
                    .put("label", app.label)
                    .put("packageName", app.packageName)
                    .put("component", app.componentName?.flattenToString())
            )
        }
    }
    prefs.edit().putString(KEY_APP_CACHE, payload.toString()).apply()
}

internal fun loadBackgroundUri(prefs: SharedPreferences): String? {
    return prefs.getString(KEY_BACKGROUND_URI, null)
}

internal fun loadLightModeBackgroundUri(prefs: SharedPreferences): String? {
    return prefs.getString(KEY_BACKGROUND_URI_LIGHT, null)
}

internal fun loadDarkModeBackgroundUri(prefs: SharedPreferences): String? {
    return prefs.getString(KEY_BACKGROUND_URI_DARK, null)
}

internal fun loadBackgroundPresetId(prefs: SharedPreferences): String? {
    return prefs.getString(KEY_BACKGROUND_PRESET, null)
}

internal fun loadLightModePresetId(prefs: SharedPreferences): String? {
    return prefs.getString(KEY_BACKGROUND_PRESET_LIGHT, null)
}

internal fun loadDarkModePresetId(prefs: SharedPreferences): String? {
    return prefs.getString(KEY_BACKGROUND_PRESET_DARK, null)
}

internal fun loadThemeMode(prefs: SharedPreferences): ThemeMode {
    val storedMode = prefs.getString(KEY_THEME_MODE, null)
    if (storedMode != null) {
        return runCatching { ThemeMode.valueOf(storedMode) }.getOrDefault(ThemeMode.SYSTEM)
    }
    return if (prefs.contains(KEY_DARK_MODE)) {
        if (prefs.getBoolean(KEY_DARK_MODE, false)) ThemeMode.DARK else ThemeMode.LIGHT
    } else {
        ThemeMode.SYSTEM
    }
}

internal fun saveThemeMode(prefs: SharedPreferences, mode: ThemeMode) {
    prefs.edit()
        .putString(KEY_THEME_MODE, mode.name)
        .putBoolean(KEY_DARK_MODE, mode == ThemeMode.DARK)
        .apply()
}

internal fun loadHomeResumeTarget(prefs: SharedPreferences): HomeResumeTarget? {
    val itemId = prefs.getString(KEY_LAST_LAUNCHED_TARGET_ID, null)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val folderId = prefs.getString(KEY_LAST_LAUNCHED_FOLDER_ID, null)
        ?.takeIf { it.isNotBlank() }
    return HomeResumeTarget(itemId = itemId, folderId = folderId)
}

internal fun saveHomeResumeTarget(
    prefs: SharedPreferences,
    itemId: String,
    folderId: String? = null,
) {
    prefs.edit()
        .putString(KEY_LAST_LAUNCHED_TARGET_ID, itemId)
        .apply {
            if (folderId.isNullOrBlank()) {
                remove(KEY_LAST_LAUNCHED_FOLDER_ID)
            } else {
                putString(KEY_LAST_LAUNCHED_FOLDER_ID, folderId)
            }
        }
        .apply()
}

internal fun loadIconOverrides(prefs: SharedPreferences): Map<String, String> {
    return prefs.all
        .filterKeys { it.startsWith(KEY_ICON_OVERRIDE_PREFIX) }
        .mapNotNull { (key, value) ->
            val packageName = key.removePrefix(KEY_ICON_OVERRIDE_PREFIX)
            val uri = value as? String ?: return@mapNotNull null
            packageName to uri
        }
        .toMap()
}

internal fun clearIconOverride(prefs: SharedPreferences, packageName: String) {
    prefs.edit().remove(KEY_ICON_OVERRIDE_PREFIX + packageName).apply()
}

internal fun displayLabelFor(app: LauncherApp, labelOverrides: Map<String, String>): String {
    return labelOverrides[app.packageName]?.takeIf { it.isNotBlank() } ?: app.label
}

internal fun loadLabelOverrides(prefs: SharedPreferences): Map<String, String> {
    return prefs.all
        .filterKeys { it.startsWith(KEY_LABEL_OVERRIDE_PREFIX) }
        .mapNotNull { (key, value) ->
            val packageName = key.removePrefix(KEY_LABEL_OVERRIDE_PREFIX)
            val label = value as? String ?: return@mapNotNull null
            if (label.isBlank()) return@mapNotNull null
            packageName to label
        }
        .toMap()
}

internal fun saveLabelOverride(prefs: SharedPreferences, packageName: String, label: String) {
    if (label.isBlank()) {
        prefs.edit().remove(KEY_LABEL_OVERRIDE_PREFIX + packageName).apply()
    } else {
        prefs.edit().putString(KEY_LABEL_OVERRIDE_PREFIX + packageName, label).apply()
    }
}

internal fun clearLabelOverride(prefs: SharedPreferences, packageName: String) {
    prefs.edit().remove(KEY_LABEL_OVERRIDE_PREFIX + packageName).apply()
}

internal fun persistReadPermission(context: Context, uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    try {
        context.contentResolver.takePersistableUriPermission(uri, flags)
    } catch (e: SecurityException) {
        // Ignore if we can't persist; the app can still try to read via the uri.
    }
}

internal fun importBackgroundUri(context: Context, uri: Uri): Uri? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val outputFile = File(context.filesDir, "custom_background.img")
        input.use { source ->
            FileOutputStream(outputFile).use { output ->
                source.copyTo(output)
            }
        }
        Uri.fromFile(outputFile)
    } catch (e: Exception) {
        null
    }
}

internal fun loadBackgroundBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return decodeBitmapFromUri(
        context = context,
        uri = uri,
        targetLongestSide = getBackgroundTargetLongestSidePx(context),
    )
}

internal fun loadTileBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    val (targetWidth, targetHeight) = getTileTargetSizePx(context)
    val cacheKey = "${uri}#${targetWidth}x${targetHeight}"
    uriBitmapCache.get(cacheKey)?.let { return it }

    val bitmap = decodeBitmapFromUri(
        context = context,
        uri = uri,
        targetLongestSide = maxOf(targetWidth, targetHeight),
    ) ?: return null
    uriBitmapCache.put(cacheKey, bitmap)
    return bitmap
}

/** Creates a small blurred copy for devices without Android 12 RenderEffect. */
internal fun createSoftwareBlurredBackdrop(source: Bitmap): Bitmap {
    // Keep the working image small so the stronger blur remains inexpensive.
    val longestSide = 96
    val scale = (longestSide.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1))
        .coerceAtMost(1f)
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    var bitmap = Bitmap.createScaledBitmap(source, width, height, true)
    val pixels = IntArray(width * height)
    val blurred = IntArray(pixels.size)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    repeat(4) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var red = 0
                var green = 0
                var blue = 0
                var alpha = 0
                var count = 0
                for (sampleY in (y - 10).coerceAtLeast(0)..(y + 10).coerceAtMost(height - 1)) {
                    for (sampleX in (x - 10).coerceAtLeast(0)..(x + 10).coerceAtMost(width - 1)) {
                        val color = pixels[sampleY * width + sampleX]
                        alpha += color ushr 24
                        red += color shr 16 and 0xFF
                        green += color shr 8 and 0xFF
                        blue += color and 0xFF
                        count++
                    }
                }
                blurred[y * width + x] = (alpha / count shl 24) or
                        (red / count shl 16) or
                        (green / count shl 8) or
                        (blue / count)
            }
        }
        pixels.indices.forEach { pixels[it] = blurred[it] }
    }
    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

private fun decodeBitmapFromUri(
    context: Context,
    uri: Uri,
    targetLongestSide: Int,
): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longestSide = maxOf(width, height)
                if (longestSide > targetLongestSide) {
                    val scale = targetLongestSide.toFloat() / longestSide.toFloat()
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
            val sampleSize = if (longestSide <= targetLongestSide) {
                1
            } else {
                Integer.highestOneBit((longestSide / targetLongestSide).coerceAtLeast(1))
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (e: Exception) {
        null
    }
}

private fun getBackgroundTargetLongestSidePx(context: Context): Int {
    val metrics = context.resources.displayMetrics
    val displayLongestSide = maxOf(metrics.widthPixels, metrics.heightPixels)
    // Avoid allocating a 3072px ARGB bitmap on 4K TVs. Decode at the display
    // size, capped at 1920px; the UI scales it to fill the screen.
    return displayLongestSide.coerceAtMost(1920)
}

private fun downloadableBackgroundUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host?.lowercase(Locale.US) ?: return url
    if (host == "images.fanart.tv" || host == "fanart.tv" || host == "www.fanart.tv") {
        return URI(
            uri.scheme ?: "https",
            uri.userInfo,
            "assets.fanart.tv",
            uri.port,
            uri.path,
            uri.query,
            uri.fragment,
        ).toString()
    }
    return url
}

internal fun downloadBackgroundFromUrl(
    context: Context,
    url: String,
    fileName: String = "background_url.jpg",
): DownloadResult {
    return try {
        val connection = (URL(downloadableBackgroundUrl(url)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ATVLauncher/1.0")
            setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            val cloudflareBlocked = connection.getHeaderField("cf-mitigated") == "challenge"
            val message = if (cloudflareBlocked) {
                "This image host blocks app downloads. Try the direct asset URL or another image host."
            } else {
                "HTTP ${connection.responseCode}"
            }
            connection.disconnect()
            return DownloadResult(null, message)
        }

        val bytes = connection.inputStream.use { it.readBytes() }
        connection.disconnect()

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return DownloadResult(null, "File is not a supported image.")

        val outputFile = File(context.filesDir, fileName)
        FileOutputStream(outputFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        DownloadResult(Uri.fromFile(outputFile), null)
    } catch (e: Exception) {
        DownloadResult(null, e.message ?: "Failed to download image.")
    }
}

internal fun launchApp(context: Context, app: LauncherApp) {
    try {
        context.startActivity(app.launchIntent)
    } catch (e: Exception) {
        // Ignore launch failures.
    }
}

internal fun defaultDockFocusTarget(dockItems: List<DockItem?>): String? {
    return dockItems.getOrNull(0)?.id ?: dockItems.firstOrNull { it != null }?.id
}

internal fun launchLauncherHome(
    context: Context,
    fromBoot: Boolean = false,
    fromLauncher: Boolean = false,
) {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_HOME_TAKEOVER
        if (fromBoot) {
            putExtra(MainActivity.EXTRA_FROM_BOOT, true)
        }
        if (fromLauncher) {
            putExtra(MainActivity.EXTRA_FROM_LAUNCHER, true)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Ignore launch failures.
    }
}

internal fun isHomeCaptureServiceEnabled(context: Context): Boolean {
    val expectedComponent = ComponentName(context, HomeCaptureService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()

    return enabledServices.split(':').any { entry ->
        val component = ComponentName.unflattenFromString(entry) ?: return@any false
        component.packageName == expectedComponent.packageName &&
            component.className == expectedComponent.className
    }
}

internal fun openSystemSettings(context: Context) {
    if (HomeCaptureService.showSystemMenu()) {
        return
    }

    openDevicePreferencesSettings(context)
}

internal fun openDevicePreferencesSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(
            ComponentName(
                "com.android.tv.settings",
                "com.android.tv.settings.MainSettings",
            )
        ),
        Intent(Settings.ACTION_SETTINGS),
    ).onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    launchFirstAvailable(context, intents)
}

internal fun openAppsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

internal fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

internal fun openNetworkSettings(context: Context) {
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

internal fun openDeveloperOptions(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    ).onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    launchFirstAvailable(context, intents)
}

internal fun openDisplaySoundSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_DISPLAY_SETTINGS),
        Intent(Settings.ACTION_SOUND_SETTINGS),
    ).onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    launchFirstAvailable(context, intents)
}

internal fun openDateTimeSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_DATE_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    ).onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    launchFirstAvailable(context, intents)
}

internal fun openRemotesAccessoriesSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(
            ComponentName(
                "com.android.tv.settings",
                "com.android.tv.settings.accessories.AccessoriesActivity",
            )
        ),
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    ).onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    launchFirstAvailable(context, intents)
}

internal fun openAboutSettings(context: Context) {
    val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

internal fun requestPowerOff(context: Context) {
    if (HomeCaptureService.showPowerDialog()) {
        return
    }

    val intent = Intent("android.intent.action.REQUEST_SHUTDOWN").apply {
        putExtra("android.intent.extra.KEY_CONFIRM", false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    launchFirstAvailable(context, listOf(intent))
}

internal fun openAppDetails(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun launchFirstAvailable(context: Context, intents: List<Intent>) {
    val pm = context.packageManager
    intents.forEach { intent ->
        if (intent.resolveActivity(pm) == null) return@forEach
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try next fallback.
        } catch (_: SecurityException) {
            // Activity exists but is not exported/allowed; try next fallback.
        }
    }
}
