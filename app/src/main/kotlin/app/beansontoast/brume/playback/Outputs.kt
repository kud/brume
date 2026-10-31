package app.beansontoast.brume.playback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** What kind of output the sound goes to; the words for it live in the strings, never a colour. */
enum class OutputKind { SPEAKER, WIRED, BLUETOOTH, HEARING_AID, USB, OTHER }

/**
 * Where the sound plays now. [key] names the device for its saved balance and levels; it is built
 * from the kind and the product name only, never a MAC address, so it needs no permission.
 */
data class Output(val kind: OutputKind, val name: String?) {
    val key: String get() = "${kind.name}:${name.orEmpty()}"
}

internal fun kindOf(type: Int): OutputKind = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputKind.SPEAKER
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG -> OutputKind.WIRED
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 26 /* BLE_HEADSET */, 27 /* BLE_SPEAKER */ -> OutputKind.BLUETOOTH
    23 /* HEARING_AID */ -> OutputKind.HEARING_AID
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> OutputKind.USB
    else -> OutputKind.OTHER
}

/** The more personal the output, the likelier media goes there: the order Android routes in. */
private val preference = listOf(OutputKind.HEARING_AID, OutputKind.BLUETOOTH, OutputKind.USB, OutputKind.WIRED, OutputKind.SPEAKER)

private fun outputOf(device: AudioDeviceInfo): Output {
    val kind = kindOf(device.type)
    // A Bluetooth or USB product name is the headset's own ("WH-1000XM4"); the speaker's is the phone model.
    val name = device.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() && kind != OutputKind.SPEAKER && kind != OutputKind.WIRED }
    return Output(kind, name)
}

/** The output media plays on now. API 33 asks the router itself; before that, the likeliest sink. */
fun currentOutput(audio: AudioManager): Output {
    if (Build.VERSION.SDK_INT >= 33) {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        audio.getAudioDevicesForAttributes(attrs).firstOrNull()?.let { return outputOf(it) }
    }
    val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map(::outputOf)
    return preference.firstNotNullOfOrNull { kind -> outputs.firstOrNull { it.kind == kind } } ?: Output(OutputKind.SPEAKER, null)
}

/** Calls [onChange] with the current output now and on every device added or removed. */
fun watchOutputs(context: Context, onChange: (Output) -> Unit) {
    val audio = context.getSystemService(AudioManager::class.java) ?: return
    val main = Handler(Looper.getMainLooper())
    // The router settles a moment after the device list changes; read it then.
    val refresh = { main.postDelayed({ onChange(currentOutput(audio)) }, 300) }
    audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { refresh() }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { refresh() }
    }, main)
    onChange(currentOutput(audio))
}

/**
 * Opens the system's own output switcher (Android 14+), or Bluetooth settings before that.
 * Pairing and routing stay with the system; Brume never pairs anything.
 */
fun openOutputSwitcher(context: Context) {
    if (Build.VERSION.SDK_INT >= 34 && MediaRouter2.getInstance(context).showSystemOutputSwitcher()) return
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
