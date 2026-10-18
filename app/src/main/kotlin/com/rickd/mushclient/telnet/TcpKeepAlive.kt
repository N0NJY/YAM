package com.rickd.mushclient.telnet

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.net.Socket

/**
 * TCP keepalive for a telnet socket, so a connection that dies silently (a router reboot, a
 * dropped Wi-Fi link — nothing ever closes the socket) is noticed instead of hanging forever.
 *
 * The OS default waits two hours before the first probe. These values notice a dead link in
 * about a minute (30s idle, then a probe every 10s, giving up after 3 misses) — gentler on a
 * tablet's battery and Wi-Fi power-saving than MushTato's desktop-tuned 10s/5s/3, and still
 * well inside the point where auto-reconnect (30s) takes over.
 */
object TcpKeepAlive {
    const val IDLE_SECONDS = 30
    const val INTERVAL_SECONDS = 10
    const val PROBE_COUNT = 3

    /** Applies the fine-grained timing; swapped out in JVM tests, which have no Android OS. */
    fun interface Tuner {
        fun tune(socket: Socket, idleSeconds: Int, intervalSeconds: Int, probeCount: Int)
    }

    /**
     * Always sets SO_KEEPALIVE. The timing is best-effort: if the platform refuses a knob, the
     * socket keeps keepalive with the OS's own default timing rather than failing the connect.
     */
    fun enable(socket: Socket, tuner: Tuner = AndroidTuner) {
        socket.keepAlive = true
        runCatching { tuner.tune(socket, IDLE_SECONDS, INTERVAL_SECONDS, PROBE_COUNT) }
    }

    // Linux option numbers (TCP_KEEPIDLE=4, TCP_KEEPINTVL=5, TCP_KEEPCNT=6): OsConstants only
    // exposes these names from API 34, but YAM supports API 29+, and every Android kernel is Linux.
    private const val TCP_KEEPIDLE = 4
    private const val TCP_KEEPINTVL = 5
    private const val TCP_KEEPCNT = 6

    private val AndroidTuner = Tuner { socket, idle, interval, count ->
        // fromSocket duplicates the descriptor; options set on the duplicate apply to the
        // same underlying socket, and closing the duplicate leaves the socket open.
        val pfd = ParcelFileDescriptor.fromSocket(socket)
        try {
            val fd = pfd.fileDescriptor
            Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, TCP_KEEPIDLE, idle)
            Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, TCP_KEEPINTVL, interval)
            Os.setsockoptInt(fd, OsConstants.IPPROTO_TCP, TCP_KEEPCNT, count)
        } finally {
            pfd.close()
        }
    }
}
