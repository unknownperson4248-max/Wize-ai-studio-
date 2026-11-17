package app.nova.assistant

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** The "JARVIS" button in the Quick Settings panel (swipe down from the top): one tap turns JARVIS on or off. */
class JarvisTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        show()
    }

    override fun onClick() {
        super.onClick()
        Power.set(this, !Power.awake(this))
        show()
    }

    private fun show() {
        val t = qsTile ?: return
        val on = Power.awake(this)
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "JARVIS"
        if (android.os.Build.VERSION.SDK_INT >= 29) t.subtitle = if (on) "On" else "Off"
        t.icon = Icon.createWithResource(this, R.drawable.ic_launcher_fg)
        t.updateTile()
    }
}
