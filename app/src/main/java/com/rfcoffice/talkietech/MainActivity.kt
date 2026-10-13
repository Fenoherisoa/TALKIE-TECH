package com.rfcoffice.talkietech

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val port = 45454
    private val sampleRate = 16000
    private val packetMagic = "TT01|"
    private val teal = Color.rgb(0, 191, 166)
    private val dark = Color.rgb(16, 24, 32)
    private val active = AtomicBoolean(false)
    private var listening = false
    private var sending = false
    private var socket: DatagramSocket? = null
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var wifiLock: android.net.wifi.WifiManager.MulticastLock? = null
    private lateinit var status: TextView
    private lateinit var userInput: EditText
    private lateinit var channelInput: EditText
    private lateinit var mode: Spinner
    private lateinit var talkButton: Button
    private lateinit var connectButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        buildUi()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 8)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(dark)
        }
        val title = TextView(this).apply {
            text = "TALKIE TECH"
            textSize = 29f
            setTextColor(teal)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 64))
        root.addView(label("COMMUNICATION LOCALE • SANS INTERNET"))
        userInput = EditText(this).apply {
            hint = "Nom d'utilisateur (ex. FENO-01)"
            setTextColor(Color.WHITE); setHintTextColor(0xffaab4bd.toInt())
            setSingleLine(true); setText("TECH-USER")
            setPadding(16, 10, 16, 10)
            setBackgroundColor(0xff25313b.toInt())
        }
        root.addView(userInput, LinearLayout.LayoutParams(-1, 54).apply { topMargin = 14 })
        channelInput = EditText(this).apply {
            hint = "Canal (ex. TECH-01)"
            setTextColor(Color.WHITE); setHintTextColor(0xffaab4bd.toInt())
            setSingleLine(true); setText("TECH-01")
            setPadding(16, 10, 16, 10)
            setBackgroundColor(0xff25313b.toInt())
        }
        root.addView(channelInput, LinearLayout.LayoutParams(-1, 54).apply { topMargin = 10 })
        mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("ÉMETTEUR + RÉCEPTEUR", "ÉMETTEUR uniquement", "RÉCEPTEUR uniquement"))
        }
        root.addView(mode, LinearLayout.LayoutParams(-1, 52).apply { topMargin = 12 })
        connectButton = Button(this).apply {
            text = "SE CONNECTER AU WI-FI LOCAL"
            setBackgroundColor(teal); setTextColor(dark)
            setOnClickListener { if (active.get()) stopLocal() else startLocal() }
        }
        root.addView(connectButton, LinearLayout.LayoutParams(-1, 56).apply { topMargin = 18 })
        status = TextView(this).apply {
            text = "État : déconnecté\nConnectez tous les téléphones au même hotspot Wi-Fi (sans données mobiles)."
            textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(8, 16, 8, 16)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 100))
        talkButton = Button(this).apply {
            text = "MAINTENIR POUR PARLER"
            textSize = 17f; setTextColor(Color.WHITE); setBackgroundColor(0xff34434f.toInt())
            isEnabled = false
            setOnTouchListener { _, event ->
                if (!active.get() || mode.selectedItemPosition == 2) return@setOnTouchListener true
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { beginTalking(); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { endTalking(); true }
                    else -> true
                }
            }
        }
        root.addView(talkButton, LinearLayout.LayoutParams(-1, 116).apply { topMargin = 8 })
        val foot = TextView(this).apply {
            text = "TALKIE TECH • RFC OFFICE\nAudio local en temps réel • Canal virtuel"
            gravity = Gravity.CENTER; setTextColor(0xffaab4bd.toInt()); textSize = 12f
        }
        root.addView(foot, LinearLayout.LayoutParams(-1, 64))
        setContentView(root)
    }

    private fun label(s: String) = TextView(this).apply {
        text = s; textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.LTGRAY)
    }

    private fun startLocal() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 8); return
        }
        try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
            wifiLock = wm.createMulticastLock("talkietech-multicast").apply { setReferenceCounted(false); acquire() }
            socket = DatagramSocket().apply { broadcast = true }
            active.set(true)
            listening = mode.selectedItemPosition != 1
            connectButton.text = "DÉCONNECTER"
            talkButton.isEnabled = mode.selectedItemPosition != 2
            status.text = "État : actif sur le réseau Wi-Fi local\nCanal : ${safeChannel()} • Nom : ${safeUser()}\nTous les téléphones doivent être sur le même réseau."
            if (listening) startReceiver()
        } catch (e: Exception) {
            stopLocal()
            status.text = "Connexion impossible : vérifiez le Wi-Fi local. (${e.javaClass.simpleName})"
        }
    }

    private fun safeUser(): String = userInput.text.toString().trim().replace("|", "_").take(24).ifBlank { "TECH-USER" }
    private fun safeChannel(): String = channelInput.text.toString().trim().uppercase().replace("|", "_").take(20).ifBlank { "TECH-01" }

    private fun startReceiver() {
        thread(name = "TalkieReceiver") {
            var receiveSocket: DatagramSocket? = null
            try {
                receiveSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 0
                    bind(java.net.InetSocketAddress(port))
                }
                val buf = ByteArray(4096)
                while (active.get()) {
                    val packet = DatagramPacket(buf, buf.size)
                    receiveSocket.receive(packet)
                    val bytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    val split = findSeparator(bytes)
                    if (split <= 0) continue
                    val header = String(bytes, 0, split, Charsets.UTF_8)
                    val parts = header.split('|')
                    if (parts.size < 3 || parts[0] != "TT01" || parts[1] != safeChannel() || parts[2] == safeUser()) continue
                    val pcmStart = split + 1
                    if (pcmStart >= bytes.size) continue
                    playPcm(bytes.copyOfRange(pcmStart, bytes.size))
                    runOnUiThread { status.text = "Mandray feo avy amin'i ${parts[2]}\nCanal : ${safeChannel()} • Wi-Fi local" }
                }
            } catch (_: SocketException) {
            } catch (e: Exception) {
                runOnUiThread { if (active.get()) status.text = "Olana amin'ny réception audio : ${e.javaClass.simpleName}" }
            } finally { receiveSocket?.close() }
        }
    }

    private fun findSeparator(bytes: ByteArray): Int {
        for (i in 0 until bytes.size) if (bytes[i] == '\n'.code.toByte()) return i
        return -1
    }

    private fun playPcm(pcm: ByteArray) {
        try {
            if (track == null) {
                val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(maxOf(min, 4096))
                    .setTransferMode(AudioTrack.MODE_STREAM).build().apply { play() }
            }
            track?.write(pcm, 0, pcm.size)
        } catch (_: Exception) { }
    }

    private fun beginTalking() {
        if (sending || !active.get()) return
        sending = true
        talkButton.text = "MITENY… AVOTSY MBA HAMPITSO"
        talkButton.setBackgroundColor(0xffd94b4b.toInt())
        thread(name = "TalkieTransmitter") {
            val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val chunkSize = 640
            var rec: AudioRecord? = null
            try {
                rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, chunkSize * 4))
                recorder = rec
                rec.startRecording()
                val dest = InetAddress.getByName("255.255.255.255")
                val pcm = ByteArray(chunkSize)
                while (sending && active.get()) {
                    val count = rec.read(pcm, 0, pcm.size)
                    if (count > 0) {
                        val header = "$packetMagic${safeChannel()}|${safeUser()}|\n".toByteArray(Charsets.UTF_8)
                        val data = header + pcm.copyOf(count)
                        socket?.send(DatagramPacket(data, data.size, dest, port))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Émission impossible : ${e.javaClass.simpleName}" }
            } finally {
                try { rec?.stop() } catch (_: Exception) {}
                rec?.release(); recorder = null
                runOnUiThread { talkButton.text = "MAINTENIR POUR PARLER"; talkButton.setBackgroundColor(0xff34434f.toInt()) }
            }
        }
    }

    private fun endTalking() { sending = false }

    private fun stopLocal() {
        sending = false
        active.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        track?.stop(); track?.release(); track = null
        try { wifiLock?.release() } catch (_: Exception) {}
        wifiLock = null
        connectButton.text = "SE CONNECTER AU WI-FI LOCAL"
        talkButton.isEnabled = false
        talkButton.text = "MAINTENIR POUR PARLER"
        talkButton.setBackgroundColor(0xff34434f.toInt())
        status.text = "État : déconnecté\nMiverena amin'ny hotspot Wi-Fi mitovy ny téléphones rehetra."
    }

    override fun onDestroy() { stopLocal(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 8 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) status.text = "Micro autorisé. Connectez-vous au Wi-Fi local."
    }
}
