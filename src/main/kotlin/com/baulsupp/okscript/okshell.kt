package com.baulsupp.okscript

import com.baulsupp.oksocial.output.ConsoleHandler
import com.baulsupp.oksocial.output.OutputHandler
import com.baulsupp.oksocial.output.SimpleResponseExtractor
import com.baulsupp.oksocial.output.UsageException
import com.baulsupp.okurl.authenticator.AuthenticatingInterceptor
import com.baulsupp.okurl.authenticator.RenewingInterceptor
import com.baulsupp.okurl.credentials.DefaultToken
import com.baulsupp.okurl.credentials.SimpleCredentialsStore
import com.baulsupp.okurl.credentials.Token
import com.baulsupp.okurl.location.BestLocation
import com.baulsupp.okurl.location.Location
import com.baulsupp.okurl.location.LocationSource
import com.baulsupp.okurl.moshi.Rfc3339InstantJsonAdapter
import com.baulsupp.okurl.okhttp.OkHttpResponseExtractor
import com.baulsupp.okurl.services.mapbox.model.MapboxLatLongAdapter
import com.baulsupp.okurl.util.ClientException
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cache
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
import okhttp3.brotli.BrotliInterceptor
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun usage(msg: String): Nothing = throw UsageException(msg)

inline fun <T, R> Iterable<T>.flatMapMeToo(function: (T) -> Iterable<R>): List<R> {
  return this.map { function(it) }
    .flatten()
}

val client: OkHttpClient by lazy {
  val credentialsStore = SimpleCredentialsStore
  val cacheDirectory = File(System.getenv("HOME"), ".okurl/shell.cache")
  OkHttpClient.Builder()
    .cache(Cache(cacheDirectory, (64 * 1024 * 1024).toLong()))
    .addInterceptor(BrotliInterceptor)
    .addInterceptor(AuthenticatingInterceptor(credentialsStore))
    .addInterceptor(RenewingInterceptor(credentialsStore))
    .build()
}
val outputHandler: OutputHandler<Response> by lazy { ConsoleHandler(OkHttpResponseExtractor()) }
val locationSource: LocationSource by lazy { BestLocation(outputHandler) }

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

val moshi = Moshi.Builder()
  .add(Location::class.java, MapboxLatLongAdapter().nullSafe())
  .add(KotlinJsonAdapterFactory())
  .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
  .add(Instant::class.java, Rfc3339InstantJsonAdapter.nullSafe())
  .build()!!

fun warmup(vararg urls: String) {
  client.warmup(*urls)
}

fun location(): Location? = runBlocking { locationSource.read() }

suspend fun show(url: String) {
  val response = client.execute(request(url))

  outputHandler.showOutput(response)
}

suspend fun showOutput(response: Response) {
  outputHandler.showOutput(response)
}

fun newWebSocket(
  url: String,
  listener: WebSocketListener
): WebSocket = client.newWebSocket(
  Request.Builder()
    .url(url)
    .build(), listener
)

var dateOnlyformat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

fun epochSecondsToDate(seconds: Long) = dateOnlyformat.format(Date(seconds * 1000))!!

suspend fun terminalWidth(): Int? {
  return (outputHandler as? ConsoleHandler<Response>)?.terminalWidth()
}

fun jsonPostRequest(
  url: String,
  body: String
): Request {
  return requestBuilder(url, DefaultToken).post(
    body.toRequestBody(JSON)
  ).build()
}

val simpleOutput = ConsoleHandler(SimpleResponseExtractor)

@Suppress("UNCHECKED_CAST")
inline fun <reified V> Moshi.mapAdapter() =
  this.adapter<Any>(
    Types.newParameterizedType(
      Map::class.java, String::class.java,
      V::class.java
    )
  ) as JsonAdapter<Map<String, V>>

@Suppress("UNCHECKED_CAST")
inline fun <reified V> Moshi.listAdapter() =
  this.adapter<Any>(
    Types.newParameterizedType(List::class.java, V::class.java)
  ) as JsonAdapter<List<V>>

suspend inline fun <reified T> OkHttpClient.query(
  url: String,
  tokenSet: Token = DefaultToken
): T {
  return this.query(request(url, tokenSet))
}

suspend inline fun <reified T> OkHttpClient.query(request: Request): T {
  val stringResult = this.queryForString(request)

  @Suppress("BlockingMethodInNonBlockingContext")
  return moshi.adapter(T::class.java).fromJson(stringResult)!!
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

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun <T> List<T>.toJavaList(): java.util.List<T> {
  return this as java.util.List<T>
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
