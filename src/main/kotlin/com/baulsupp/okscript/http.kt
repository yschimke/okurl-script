package com.baulsupp.okscript

import com.baulsupp.okurl.credentials.DefaultToken
import com.baulsupp.okurl.credentials.Token
import com.baulsupp.okurl.util.ClientException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend inline fun <reified T> query(
  url: String,
  tokenSet: Token = DefaultToken,
  noinline init: Request.Builder.() -> Unit = {}
): T {
  return query(request(url, tokenSet, init))
}

suspend inline fun <reified T> query(request: Request): T {
  val stringResult = client.queryForString(request)

  @Suppress("BlockingMethodInNonBlockingContext")
  return moshi.adapter(T::class.java)
    .fromJson(stringResult)!!
}

fun warmup(vararg urls: String) {
  client.warmup(*urls)
}

fun jsonPostRequest(
  url: String,
  body: String
): Request {
  return requestBuilder(url, DefaultToken).post(
    body.toRequestBody(JSON)
  )
    .build()
}

suspend inline fun <reified T> OkHttpClient.query(
  url: String,
  tokenSet: Token = DefaultToken
): T {
  return this.query(request(url, tokenSet))
}

suspend inline fun <reified T> OkHttpClient.query(request: Request): T {
  val stringResult = this.queryForString(request)

  @Suppress("BlockingMethodInNonBlockingContext")
  return moshi.adapter(T::class.java)
    .fromJson(stringResult)!!
}

suspend inline fun <reified T> OkHttpClient.queryPages(
  url: String,
  crossinline paginator: T.() -> Pagination,
  tokenSet: Token = DefaultToken,
  pageLimit: Int = Int.MAX_VALUE
): List<T> = coroutineScope {
  var page = query<T>(url, tokenSet)
  val resultList = mutableListOf(page)

  var pages = paginator(page)

  while (pages !== End && resultList.size < pageLimit) {
    if (pages is Next) {
      page = query(pages.url, tokenSet)
      resultList.add(page)
      pages = paginator(page)
    } else if (pages is Rest) {
      val deferList = pages.urls.take(pageLimit)
        .map {
          async {
            query<T>(it, tokenSet)
          }
        }
      deferList.forEach { resultList.add(it.await()) }
      break
    }
  }

  resultList
}

suspend inline fun <reified V> OkHttpClient.queryMap(request: Request): Map<String, V> {
  val stringResult = this.queryForString(request)

  @Suppress("BlockingMethodInNonBlockingContext")
  return moshi.mapAdapter<V>()
    .fromJson(stringResult)!!
}

suspend inline fun <reified V> OkHttpClient.queryMap(
  url: String,
  tokenSet: Token = DefaultToken
): Map<String, V> =
  this.queryMap(request(url, tokenSet))

suspend inline fun <reified V> OkHttpClient.queryList(request: Request): List<V> {
  val stringResult = this.queryForString(request)

  @Suppress("BlockingMethodInNonBlockingContext")
  return moshi.listAdapter<V>()
    .fromJson(stringResult)!!
}

sealed class Pagination

object End : Pagination()

data class Next(val url: String) : Pagination()

data class Rest(val urls: List<String>) : Pagination()

suspend inline fun <reified V> OkHttpClient.queryList(
  url: String,
  tokenSet: Token = DefaultToken
): List<V> =
  this.queryList(request(url, tokenSet))

suspend inline fun <reified V> OkHttpClient.queryOptionalMap(request: Request): Map<String, V>? {
  val stringResult = this.queryForString(request)

  @Suppress("BlockingMethodInNonBlockingContext")
  return moshi.mapAdapter<V>()
    .fromJson(stringResult)
}

suspend inline fun <reified V> OkHttpClient.queryOptionalMap(
  url: String,
  tokenSet: Token = DefaultToken
): Map<String, V>? =
  this.queryOptionalMap(request(url, tokenSet))

suspend inline fun <reified T> OkHttpClient.queryMapValue(
  url: String,
  tokenSet: Token = DefaultToken,
  vararg keys: String
): T? =
  this.queryMapValue<T>(request(url, tokenSet), *keys)

@Suppress("UNCHECKED_CAST")
suspend inline fun <reified T> OkHttpClient.queryMapValue(
  request: Request,
  vararg keys: String
): T? {
  val queryMap = this.queryMap<Any>(request)

  val result = keys.fold(queryMap as Any) { map, key -> (map as Map<String, Any>).getValue(key) }

  return result as T
}

// TODO
fun HttpUrl.request(): Request = Request.Builder()
  .url(this)
  .build()

suspend fun OkHttpClient.queryForString(request: Request): String = withContext(Dispatchers.IO) {
  @Suppress("BlockingMethodInNonBlockingContext")
  execute(request).body!!.string()
}

suspend fun OkHttpClient.queryForString(
  url: String,
  tokenSet: Token = DefaultToken
): String =
  this.queryForString(request(url, tokenSet))

suspend fun OkHttpClient.execute(request: Request): Response {
  val call = this.newCall(request)

  val response = call.await()

  if (!response.isSuccessful) {
    val responseString = withContext(Dispatchers.IO) {
      @Suppress("BlockingMethodInNonBlockingContext")
      response.body!!.string()
    }

    val msg: String = if (responseString.isNotEmpty()) {
      responseString
    } else {
      response.statusMessage()
    }

    throw ClientException(msg, response.code)
  }

  return response
}

val JSON = "application/json".toMediaType()

fun form(init: FormBody.Builder.() -> Unit = {}): FormBody = FormBody.Builder()
  .apply(init)
  .build()

fun request(
  url: String? = null,
  tokenSet: Token = DefaultToken,
  init: Request.Builder.() -> Unit = {}
): Request = requestBuilder(url, tokenSet).apply(init)
  .build()

fun requestBuilder(
  url: String? = null,
  tokenSet: Token = DefaultToken
): Request.Builder = Request.Builder()
  .apply { if (url != null) url(url) }
  .tag(
    Token::class.java,
    tokenSet
  )

fun Request.Builder.tokenSet(tokenSet: Token): Request.Builder = tag(Token::class.java, tokenSet)

fun Request.Builder.postJsonBody(body: Any) {
  val content = moshi.adapter(body.javaClass)
    .toJson(body)!!
  post(content.toRequestBody(JSON))
}

fun Request.edit(init: Request.Builder.() -> Unit = {}) = newBuilder().apply(init)
  .build()

fun HttpUrl.edit(init: HttpUrl.Builder.() -> Unit = {}) = newBuilder().apply(init)
  .build()

suspend fun Call.await(): Response {
  return suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation {
      cancel()
    }
    enqueue(object : Callback {
      override fun onFailure(
        call: Call,
        e: IOException
      ) {
        if (!cont.isCompleted) {
          cont.resumeWithException(e)
        }
      }

      override fun onResponse(
        call: Call,
        response: Response
      ) {
        if (!cont.isCompleted) {
          cont.resume(response)
        }
      }
    })
  }
}

fun newWebSocket(
  url: String,
  listener: WebSocketListener
): WebSocket = client.newWebSocket(
  Request.Builder()
    .url(url)
    .build(), listener
)

