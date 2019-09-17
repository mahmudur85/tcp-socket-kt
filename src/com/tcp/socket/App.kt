package com.tcp.socket

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) = runBlocking<Unit>{
    val server: TCPSocket = TCPSocket(5891, object: TCPSocket.Listener{
        override fun connected(remoteAddress: String) {
        }

        override fun disConnected(remoteAddress: String) {
        }

        override fun received(data: ByteArray, remoteAddress: String) {
        }
    })

    val serverTask = launch {
        server.accept()
    }
    serverTask.join()
}