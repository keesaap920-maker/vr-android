package com.visionosmobile.ui

import android.content.Context
import android.graphics.Bitmap
import com.visionosmobile.app.AndroidApp
import com.visionosmobile.app.AppCatalog
import java.util.ArrayDeque
import com.visionosmobile.core.*
import kotlin.math.*

/** All application actions must be explicitly authorized by the host Android activity. */
interface WorkspaceHost {
    fun launchPackage(packageName: String)
    fun selectFile(mimeType: String)
    fun openExternal(url: String)
    fun navigateBrowser(url: String)
    fun browserTap(u: Float, v: Float)
    fun browserScroll(down: Boolean)
    fun browserActivate(enabled: Boolean)
    fun browserBack()
    fun browserForward()
    fun browserType(char: String)
    fun recenter()
    fun setHandtracking(enabled: Boolean)
    fun playVideo(command: String)
    fun exitVR()
}

data class Tile(val text: String, val action: String, val icon: Bitmap? = null)

data class WindowPanel(
    val id: String,
    val title: String,
    var x: Float,
    var y: Float,
    var z: Float,
    var width: Float,
    var height: Float,
    var columns: Int = 3,
    var rows: Int = 2,
    var tiles: List<Tile> = emptyList(),
    var info: String = "",
    var revision: Int = 1
) {
    fun rectangle() = SpatialRectangle(id, Vec3(x, y, z), width, height)
    fun bump() { revision++ }
}

data class PointerFocus(val hit: RayHit?, val tile: Int) {
    val key get() = if (hit == null) "none" else "${hit.panelId}:$tile"
}

/** Shared mutable workspace state is protected by its own monitor. */
class Workspace(private val context: Context, private val host: WorkspaceHost) {
    private val panels = mutableListOf<WindowPanel>()
    private var applications: List<AndroidApp> = emptyList()
    private var appPage = 0
    private val lastUsed = ArrayDeque<String>()
    private val prefs = context.getSharedPreferences("visionos_settings", Context.MODE_PRIVATE)
    @Volatile var ipdMeters = prefs.getFloat("ipd", 0.064f)
        private set
    @Volatile var distortion = prefs.getFloat("distortion", 0.0f)
        private set
    @Volatile var sceneMode = prefs.getInt("scene", 0)
        private set
    @Volatile var renderScale = prefs.getFloat("renderScale", 0.85f)
        private set
    @Volatile var handEnabled = prefs.getBoolean("handEnabled", false)
        private set
    @Volatile var handStatus = "Uitgeschakeld"
        private set
    @Volatile var latestFps = 0f
    @Volatile var mediaFilename = "Nog geen video geselecteerd"
    @Volatile var browserUrl = "https://www.wikipedia.org"
        private set
    private var addressDraft = "https://www.wikipedia.org"
    private var typedIntoAddress = true
    private var holdFocus: String = "none"
    private var hoverStart = 0L
    private var lastFire = 0L
    private var focus: PointerFocus = PointerFocus(null, -1)
    private var movingWindowId: String? = null
    private var moveWithPointer = false
    @Volatile var windowChangeVersion = 0L
        private set

    init { openHome() }

    @Synchronized fun snapshot(): List<WindowPanel> = panels
        .filter { it.id != "home" || panels.none { p -> p.id == "media" } }
        .map { it.copy(tiles = it.tiles.toList()) }
    @Synchronized fun focusKey(): String = focus.key
    @Synchronized fun focusPanelId(): String? = focus.hit?.panelId

    @Synchronized fun refreshApplications() {
        applications = AppCatalog.load(context)
        panels.firstOrNull { it.id == "apps" }?.let { p ->
            p.tiles = currentAppTiles()
            p.columns = 4
            p.rows = 5
            p.info = "${applications.size} apps • pagina ${appPage+1} van ${maxOf(1,(applications.size+17)/18)}"
            p.bump()
        }
    }

    private fun currentAppTiles():List<Tile> = applications.drop(appPage*18).take(18)
        .map {Tile(it.label,"app:${it.packageName}",it.icon)} +
        listOf(Tile("← Vorige", "apppage:back"),Tile("Volgende →", "apppage:next"))

    @Synchronized fun openHome() {
        if (panels.any { it.id == "home" }) return
        panels.add(WindowPanel("home", "VisionOS Mobile", 0f, 0.10f, -2.6f, 3.0f, 1.90f,
            3, 3,
            listOf(
                Tile("Apps", "open:apps"), Tile("Web", "open:browser"), Tile("Cinema", "open:media"),
                Tile("Files", "open:files"), Tile("Settings", "open:settings"), Tile("Recent", "open:recent"),
                Tile("Help", "open:help"), Tile("Recenter", "sys:recenter"), Tile("Hand input", "sys:hand")
            ), "Draai je hoofd om te richten. Kijk 1,7 s naar een knop, of druk Volume+ om te klikken."
        ))
        windowChangeVersion++
    }

    @Synchronized fun open(id: String) {
        if (panels.any { it.id == id }) return
        if (panels.size >= 6) panels.removeAt(1.coerceAtMost(panels.lastIndex))
        val offset = panels.count { it.id != "home" } % 3
        val x = when(offset) {0->-2.25f;1->2.25f;else->0f}
        val z = -2.2f - (panels.size % 2) * 0.08f
        val p = when (id) {
            "apps" -> WindowPanel(id, "Geïnstalleerde apps", x,0.02f,z,2.9f,2.25f,4,5,
                currentAppTiles(),
                "Reguliere apps openen in Android, niet in een VR-venster.")
            "browser" -> WindowPanel(id,"Spatial browser",x,0f,z,3.15f,2.2f,6,1,
                listOf(Tile("Adres", "browser:address"), Tile("Terug", "browser:back"),
                    Tile("Verder", "browser:forward"), Tile("Omlaag", "browser:scrollDown"),
                    Tile("Omhoog", "browser:scrollUp"), Tile("Typen", "keyboard:web")),
                "Webinhoud: experimentele WebView-offscreenweergave")
            "media" -> WindowPanel(id,"Cinema",0f,-1.00f,-2.45f,3.3f,0.85f,3,2,
                listOf(Tile("Video kiezen", "file:video/*"), Tile("Afspelen", "video:play"),
                    Tile("Pauze", "video:pause"), Tile("Stop", "video:stop"),
                    Tile("Groter", "win:enlarge"), Tile("Kleiner", "win:shrink")),
                "Open eerst een video via het Android-bestandsvenster")
            "files" -> WindowPanel(id,"Bestanden",x,0f,z,2.5f,1.8f,3,2,
                listOf(Tile("Afbeelding", "file:image/*"), Tile("Video", "file:video/*"),
                    Tile("Document", "file:application/pdf"), Tile("Alle bestanden", "file:*/*")),
                "Bestandstoegang via de Android-documentkiezer.")
            "settings" -> WindowPanel(id,"Instellingen",x,0f,z,2.85f,2.2f,3,3, listOf(
                Tile("IPD -", "setting:ipd-"), Tile("IPD +", "setting:ipd+"),
                Tile("Lens -", "setting:lens-"),Tile("Lens +", "setting:lens+"),
                Tile("Omgeving", "setting:environment"), Tile("Kwaliteit", "setting:quality"),
                Tile("Handtracking", "sys:hand"), Tile("Centreer", "sys:recenter"),
                Tile("Verlaat VR", "sys:exit")), settingsText())
            "recent" -> WindowPanel(id,"Recent gebruikt",x,0f,z,2.8f,1.8f,3,2,
                lastUsed.take(6).map { Tile(it.substringAfterLast('.').take(16),"app:$it") },
                "Recent via de VR-launcher gestarte apps")
            "help" -> WindowPanel(id,"Bediening",x,0f,z,2.8f,2.0f,2,2,
                listOf(Tile("Recenter", "sys:recenter"),Tile("Instellingen", "open:settings"),
                    Tile("Handtracking", "sys:hand"),Tile("Sluiten", "win:close")),
                "GAZE: 1,7 s richten = klik. VOLUME+: klik. VOLUME-: recenter. PINCH: klik.\n"+
                    "Window: bovenste titelbalk lang richten = verplaatsen; dubbelklik is niet nodig.")
            "keyboard" -> WindowPanel(id,"Virtueel toetsenbord",0f,-0.73f,-1.85f,3.0f,1.15f,10,4,
                keyboardTiles(), "Kies een toets met gaze/pinch. Enter bevestigt.")
            "gallery" -> WindowPanel(id,"Afbeelding",x,0f,z,3.1f,2.15f,3,1,
                listOf(Tile("Volgende bestand", "file:image/*")), "")
            else -> return
        }
        panels.add(p)
        windowChangeVersion++
        if (id == "apps") refreshApplications()
        if (id == "browser") host.navigateBrowser(browserUrl)
    }

    private fun keyboardTiles(): List<Tile> =
        ("QWERTYUIOP" + "ASDFGHJKL " + "ZXCVBNM,. ").map { c -> Tile(c.toString(),"key:$c") } +
            listOf(Tile("Space", "key: "),Tile("←", "key:BACK"),Tile("Enter", "key:ENTER"), Tile("Close", "win:close"))

    private fun settingsText() = "IPD ${(ipdMeters*1000).toInt()} mm • distortion ${"%.2f".format(distortion)} • Render ${(renderScale*100).toInt()}%\n"+
        "Hand: $handStatus • FPS ${latestFps.toInt()}"

    @Synchronized fun updateHandStatus(text: String) {
        if (handStatus != text) {
            handStatus = text
            panels.firstOrNull { it.id == "settings" }?.let { it.info = settingsText(); it.bump() }
        }
    }

    @Synchronized fun selectAt(hit: RayHit?) {
        if (hit == null) return
        val panel = panels.firstOrNull { it.id == hit.panelId } ?: return
        val choice = PanelLayout.tileAt(hit.u, hit.v, panel.columns, panel.rows)
        if (choice == 0) {
            if (hit.u > 0.9f) { closePanel(panel.id); return }
            if (hit.u > 0.8f) { panel.width=(panel.width*1.12f).coerceAtMost(4.2f);panel.height=(panel.height*1.12f).coerceAtMost(2.8f);panel.bump();return }
            if (movingWindowId == panel.id) endMove()
            else { movingWindowId = panel.id;moveWithPointer = true }
            return
        }
        if (panel.id == "browser" && hit.v > 0.35f && hit.u > 0.05f && hit.u < 0.95f) {
            host.browserTap(hit.u, hit.v)
            return
        }
        val tile = panel.tiles.getOrNull(choice - 1) ?: return
        dispatch(tile.action, panel.id)
    }

    @Synchronized fun updatePointer(hit: RayHit?, now: Long, allowDwell: Boolean) {
        val panel = panels.firstOrNull { it.id == hit?.panelId }
        val tile = if (panel != null && hit != null) PanelLayout.tileAt(hit.u,hit.v,panel.columns,panel.rows) else -1
        val next = PointerFocus(hit,tile)
        val nextKey = next.key
        focus = next
        if (nextKey != holdFocus) { hoverStart = now; holdFocus = nextKey }
        if (allowDwell && nextKey != "none" && tile >= 0 && now - hoverStart > 1700 && now - lastFire > 1900) {
            lastFire = now
            hoverStart = now
            selectAt(hit)
        }
    }

    @Synchronized fun click() = selectAt(focus.hit)
    @Synchronized fun movePointer(direction: Vec3) {
        val id = movingWindowId ?: return
        val panel = panels.firstOrNull { it.id == id } ?: return
        if (direction.z >= -0.05f) return
        val t = panel.z / direction.z
        panel.x = (direction.x * t).coerceIn(-3.2f,3.2f)
        panel.y = (direction.y * t).coerceIn(-1.6f,1.7f)
        windowChangeVersion++
    }
    @Synchronized fun endMove() { movingWindowId = null; moveWithPointer = false }
    @Synchronized fun isMoving() = moveWithPointer
    @Synchronized fun updateFilename(name: String) {
        mediaFilename = name
        panels.firstOrNull { it.id == "media" }?.let { it.info = name; it.bump() }
        panels.firstOrNull { it.id == "gallery" }?.let { it.info = name; it.bump() }
    }
    @Synchronized fun closePanel(id: String) {
        panels.removeAll { it.id == id }
        if (id == "browser") host.browserActivate(false)
        if (panels.isEmpty()) openHome()
        windowChangeVersion++
    }

    @Synchronized private fun dispatch(action: String, source: String) {
        when {
            action.startsWith("open:") -> open(action.removePrefix("open:"))
            action.startsWith("app:") -> {
                val pkg = action.removePrefix("app:")
                lastUsed.remove(pkg); lastUsed.addFirst(pkg)
                while (lastUsed.size > 6) lastUsed.removeLast()
                host.launchPackage(pkg)
            }
            action.startsWith("apppage:") -> {
                val maxPage=((applications.size-1).coerceAtLeast(0))/18
                appPage=if(action=="apppage:next") minOf(maxPage,appPage+1) else maxOf(0,appPage-1)
                panels.firstOrNull{it.id=="apps"}?.let {it.tiles=currentAppTiles();it.info="${applications.size} apps • pagina ${appPage+1} van ${maxPage+1}";it.bump()}
            }
            action.startsWith("file:") -> host.selectFile(action.removePrefix("file:"))
            action.startsWith("video:") -> host.playVideo(action.removePrefix("video:"))
            action == "sys:recenter" -> host.recenter()
            action == "sys:exit" -> host.exitVR()
            action == "sys:hand" -> {
                handEnabled = !handEnabled
                prefs.edit().putBoolean("handEnabled",handEnabled).apply()
                host.setHandtracking(handEnabled)
            }
            action == "win:close" -> closePanel(source)
            action == "win:enlarge" || action == "win:shrink" -> {
                panels.firstOrNull { it.id == source }?.let {
                    val factor = if(action == "win:enlarge") 1.15f else 0.87f
                    it.width = (it.width*factor).coerceIn(1.2f,4.2f)
                    it.height = (it.height*factor).coerceIn(0.8f,2.8f)
                    it.bump()
                }
            }
            action == "browser:address" -> { typedIntoAddress = true; addressDraft = browserUrl; open("keyboard") }
            action == "browser:back" -> host.browserBack()
            action == "browser:forward" -> host.browserForward()
            action == "browser:scrollDown" -> host.browserScroll(true)
            action == "browser:scrollUp" -> host.browserScroll(false)
            action == "keyboard:web" -> { typedIntoAddress = false; open("keyboard") }
            action.startsWith("key:") -> {
                val key = action.removePrefix("key:")
                if (typedIntoAddress) {
                    when (key) {
                        "BACK" -> addressDraft = addressDraft.dropLast(1)
                        "ENTER" -> { browserUrl = addressDraft; host.navigateBrowser(browserUrl); closePanel("keyboard") }
                        else -> addressDraft += key
                    }
                    panels.firstOrNull { it.id == "keyboard" }?.let { it.info = addressDraft.takeLast(85); it.bump() }
                } else {
                    when (key) {
                        "BACK" -> host.browserType("BACK")
                        "ENTER" -> host.browserType("\n")
                        else -> host.browserType(key)
                    }
                }
            }
            action.startsWith("setting:") -> {
                when (action.removePrefix("setting:")) {
                    "ipd-" -> ipdMeters = (ipdMeters - 0.002f).coerceIn(0.052f,0.075f)
                    "ipd+" -> ipdMeters = (ipdMeters + 0.002f).coerceIn(0.052f,0.075f)
                    "lens-" -> distortion = (distortion - 0.05f).coerceIn(-0.6f,0.6f)
                    "lens+" -> distortion = (distortion + 0.05f).coerceIn(-0.6f,0.6f)
                    "environment" -> sceneMode = (sceneMode+1)%3
                    "quality" -> renderScale = when { renderScale >= 0.99f -> 0.65f; renderScale < 0.80f -> 0.85f; else -> 1f }
                }
                prefs.edit().putFloat("ipd",ipdMeters).putFloat("distortion",distortion)
                    .putInt("scene",sceneMode).putFloat("renderScale",renderScale).apply()
                panels.firstOrNull { it.id == "settings" }?.let { it.info=settingsText(); it.bump() }
            }
        }
        windowChangeVersion++
    }
}
