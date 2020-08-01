package com.baulsupp.okscript

import com.baulsupp.oksocial.output.UsageException
import com.baulsupp.okurl.credentials.DefaultToken
import com.baulsupp.okurl.location.Location
import com.baulsupp.okurl.util.ClientException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

fun usage(msg: String): Nothing = throw UsageException(msg)

inline fun <T, R> Iterable<T>.flatMapMeToo(function: (T) -> Iterable<R>): List<R> {
  return this.map { function(it) }
    .flatten()
}

suspend fun location(): Location? = locationSource.read()

var dateOnlyformat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

fun Response.statusMessage(): String = this.code.toString() + " " + this.message

fun OkHttpClient.warmup(vararg urls: String) {
  urls.forEach {
    val call = this.newCall(request(it, DefaultToken))
    call.enqueue(object : Callback {
      override fun onFailure(
        call: Call,
        e: IOException
      ) {
        // ignore
      }

      override fun onResponse(
        call: Call,
        response: Response
      ) {
        // ignore
        response.close()
      }
    })
  }
}

fun <T> runScript(block: suspend CoroutineScope.() -> T) {
  runBlocking {
    try {
      block()
    } catch (ue: UsageException) {
      simpleOutput.showError(ue.message)
    } catch (ce: ClientException) {
      simpleOutput.showError(ce.message)
    }
  }
  client.dispatcher.executorService.shutdown()
}
