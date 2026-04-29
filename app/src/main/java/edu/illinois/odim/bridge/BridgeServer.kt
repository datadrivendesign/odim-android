package edu.illinois.odim.bridge

import android.util.Log
import edu.illinois.odim.MyAccessibilityService
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

class BridgeServer(private val service: MyAccessibilityService, host: String, port: Int) : NanoHTTPD(host, port) {
    private val router = BridgeRouter(service)
    private val myPort = 8765

    override fun serve(session: IHTTPSession): Response {
        return try {
            runBlocking {
                router.route(session)
            }
        } catch (e: Exception) {
            Log.e("BridgeServer", "Error serving request: ${session.uri}", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"error\": \"${e.message}\"}"
            )
        }
    }

    override fun start() {
        super.start(SOCKET_READ_TIMEOUT, false)
        Log.i("BridgeServer", "Bridge server started on $hostname:$myPort")
    }

    override fun stop() {
        super.stop()
        Log.i("BridgeServer", "Bridge server stopped")
    }
}
