package com.tcp.socket

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger

private val log: Logger = Logger.getLogger("app")

fun main(args: Array<String>) = runBlocking<Unit>{
    val server: TCPSocket = TCPSocket(5891, object: TCPSocket.Listener{
        override fun connected(remoteAddress: String) {
            log.info("new connection from $remoteAddress")
        }

        override fun disConnected(remoteAddress: String) {
            log.info("disconnected from $remoteAddress")
        }

        override fun received(data: String, remoteAddress: String) {
            log.info("$remoteAddress sent ${data.length} bytes -> $data")
        }
    })

    val serverTask = launch {
        server.accept()
    }
    serverTask.join()
}