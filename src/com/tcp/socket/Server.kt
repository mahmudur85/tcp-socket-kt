package com.tcp.socket

import java.io.IOException
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import kotlin.coroutines.CoroutineContext


class Server(bufferSize: Int, private var listener: Listener): CoroutineScope {
    private val log: Logger = Logger.getLogger(Server::class.simpleName)

    private var mJob: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = mJob + Dispatchers.IO

    private var closed = false
    private var selector: Selector
    private lateinit var serverChannel: ServerSocketChannel
    private val readBuffer: ByteBuffer = ByteBuffer.allocate(bufferSize)
    private var clientSocket: SocketChannel? = null

    private var socketThread: Thread? = null

    private val updateLock = Any()

    init {
        log.level = Level.INFO
        try {
            selector = Selector.open()
        } catch (ex: IOException) {
            throw RuntimeException("Error opening selector.", ex)
        }
    }

    fun bind(tcpPort: Int){
        finish()
        synchronized(updateLock) {
            selector.wakeup()
            try {
                serverChannel = selector.provider().openServerSocketChannel()
                serverChannel.socket().bind(InetSocketAddress(tcpPort))
                serverChannel.configureBlocking(false)
                serverChannel.register(selector, SelectionKey.OP_ACCEPT)
                log.info("Accepting connections on port: $tcpPort/TCP")
            } catch (ex: IOException) {
                finish()
                throw ex
            }
        }
        log.info("Server opened.")

        socketThread = Thread(SocketRunnable(),"socket-thread")
        socketThread?.priority = Thread.MAX_PRIORITY
        socketThread?.start()
    }

    private inner class SocketRunnable: Runnable{
        override fun run() {
            try {
                while (!Thread.interrupted()) {
                    log.info("Waiting for a event...")
                    selector.select()
                    val selectedKeys = selector.selectedKeys()
                    val iter = selectedKeys.iterator()
                    while (iter.hasNext()) {
                        val key = iter.next()

                        if (key.isAcceptable) {
                            val client = serverChannel.accept()
                            client.configureBlocking(false)
                            client.register(selector, SelectionKey.OP_READ)
                            log.info("Received a connection from ${client?.socket()?.remoteSocketAddress}")
                            clientSocket = client
                            listener.connected(client?.socket()?.inetAddress?.hostName.toString())
                        }

                        if (key.isReadable) {
                            readBuffer.clear()
                            val client = key.channel() as SocketChannel
                            var size = 0
                            try{
                                size = client.read(readBuffer)
                                readBuffer.limit(size)
                                readBuffer.flip()
                                log.info(
                                    "Received `$size` bytes from ${clientSocket?.socket()?.inetAddress}"
                                )
                                listener.received(readBuffer.array(),
                                    clientSocket?.socket()?.inetAddress?.hostAddress.toString()
                                )
                            }catch (e: IOException){
                                log.log(Level.WARNING, e.toString(), e)
                                listener.disConnected("${clientSocket?.socket()?.inetAddress?.hostAddress}")
                                clientSocket?.close()
                                clientSocket = null
                            }

                            if(size == -1){
                                log.info(
                                    "Connection closed by remote ${clientSocket?.socket()?.inetAddress}"
                                )
                                listener.disConnected("${clientSocket?.socket()?.inetAddress?.hostAddress}")
                                clientSocket?.close()
                                clientSocket = null
                            }
                        }

                        if (!key.isValid) {
                            continue
                        }

                        iter.remove()
                    }
                }
            } catch (e: IOException) {
                log.log(Level.WARNING, e.toString(), e)
            } catch (ex: Exception ) {
                log.log(Level.WARNING, ex.toString(), ex)
            }

            synchronized (this) {
                closed = true
                listener.disConnected("${clientSocket?.socket()?.inetAddress}")
            }
        }
    }

    suspend fun send(data: String){
        coroutineScope {
            launch {
                try {
                    val writeBuffer: ByteBuffer = ByteBuffer.allocate(data.length)
                    writeBuffer.put(data.toByteArray())
                    writeBuffer.flip()
                    clientSocket?.write(writeBuffer)
                    log.info("sent ${data.length} bytes to ${clientSocket?.socket()}")
                    writeBuffer.clear()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun finish(){
        if (socketThread?.isAlive == true) {
            socketThread?.interrupt()
        }
    }

    interface Listener {
        fun connected(remoteAddress: String)
        fun disConnected(remoteAddress: String)
        fun received(data: ByteArray, remoteAddress: String)
    }
}