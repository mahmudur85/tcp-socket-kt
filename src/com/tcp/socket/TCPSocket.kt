package com.tcp.socket

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext

class TCPSocket(private val port: Int, private val listener: Listener): CoroutineScope {
    private val log: Logger = Logger.getLogger(TCPSocket::class.simpleName)

    private var mJob: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = mJob + Dispatchers.IO

    private var doAccept: Boolean = true

    private val serverSocket: ServerSocket = ServerSocket(this.port)
    private var acceptThread: Thread? = null
    private var readThread: Thread? = null

    private lateinit var dataOutputStream: DataOutputStream

    suspend fun accept(){
        try {
            while (!Thread.interrupted()) {
                log.info("Waiting for a new connection...")
                val sock: Socket = serverSocket.accept()
                if(doAccept) {
                    log.info("Received a connection from ${sock.remoteSocketAddress}")
                    this.startReadThread(sock, this.listener)
                    this.listener.connected(sock.inetAddress?.hostName.toString())
                }else{
                    sock.close()
                    log.info("Rejecting a new connection from ${sock.remoteSocketAddress}")
                }
            }
        } catch (e: IOException) {
            log.log(Level.WARNING, e.toString(), e)
            this.stopReadThread()
        }
    }

    private fun startReadThread(sock: Socket, listener: Listener) {
        readThread = Thread(ReadRunnable(sock, listener), "accept-thread")
        readThread?.priority = Thread.MAX_PRIORITY
        readThread?.start()
    }

    private fun stopReadThread() {
        if (readThread?.isAlive == true) {
            readThread?.interrupt()
        }
    }

    private inner class ReadRunnable(private val socket: Socket, private val listener: Listener) : Runnable, CoroutineScope {
        private val log: Logger = Logger.getLogger(ReadRunnable::class.simpleName)
        private var mJob: Job = Job()
        override val coroutineContext: CoroutineContext
            get() = mJob + Dispatchers.IO

        override fun run() {
            doAccept = false
            try {
                while (!Thread.interrupted()) {
                    val inBufferReader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val data = inBufferReader.readLine()
                    if (!data.isNullOrEmpty()) {
                        log.info(
                        "Received `${data.length}` bytes from ${socket.inetAddress}:  $data"
                        )
                        this.listener.received(data, socket.inetAddress?.hostName.toString())
                    } else {
                        log.info(
                        "Connection closed by remote ${socket.inetAddress}"
                        )
                        this.listener.disConnected(socket.inetAddress?.hostName.toString())
                        socket.close()
                        break
                    }
                }
            } catch (e: IOException) {
                log.log(Level.WARNING, e.toString(), e)
            }
            doAccept = true
        }
    }

    interface Listener {
        fun connected(remoteAddress: String)
        fun disConnected(remoteAddress: String)
        fun received(data: String, remoteAddress: String)
    }
}