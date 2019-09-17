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

class TCPSocket(private val port: Int, val listener: Listener): CoroutineScope {
    private val log: Logger = Logger.getLogger(TCPSocket::class.simpleName)

    private var mJob: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = mJob + Dispatchers.IO

    private var doAccept: Boolean = true
    private var doRead: Boolean = true

    private val serverSocket: ServerSocket = ServerSocket(this.port)

    private lateinit var dataOutputStream: DataOutputStream

    suspend fun accept(){
        while(doAccept){
            try {
                log.info("Waiting for a new connection...")
                val sock: Socket = serverSocket.accept()
                log.info("Received a connection from ${sock.remoteSocketAddress}")
                coroutineScope {
                    launch {
                        reader(sock)
                    }
                }
            }catch (e: IOException){
                log.log(Level.WARNING, e.toString(), e)
            }
        }
    }

    private suspend fun reader(socket: Socket){
        while (doRead){
            try{
                val inBufferReader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val data = inBufferReader.readLine()
                if (!data.isNullOrEmpty()) {
                    log.info(
                        "Received `${data.length}` bytes from ${socket.inetAddress}"
                    )
                }else{
                    socket.close()
                    break
                }
            }catch (e: IOException){
                log.log(Level.WARNING, e.toString(), e)
                doRead = false
            }
        }
    }

    interface Listener {
        fun connected(remoteAddress: String)
        fun disConnected(remoteAddress: String)
        fun received(data: ByteArray, remoteAddress: String)
    }
}