package com.lyricstage.core.lyrics

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs [request] and returns its status code and body text. The body is read on OkHttp's own
 * thread, and cancelling the coroutine cancels the call, which also aborts a body that is
 * still downloading. So a lookup that is no longer needed stops at once.
 */
internal suspend fun OkHttpClient.fetch(request: Request): Pair<Int, String?> = suspendCancellableCoroutine { cont ->
    val call = newCall(request)
    cont.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            val result = try {
                response.use { it.code to it.body?.string() }
            } catch (e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
                return
            }
            if (cont.isActive) cont.resume(result)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    })
}
