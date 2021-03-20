package com.baulsupp.okscript

import com.baulsupp.oksocial.output.handler.ConsoleHandler
import com.baulsupp.oksocial.output.handler.OutputHandler
import com.baulsupp.oksocial.output.responses.SimpleResponseExtractor
import com.baulsupp.okurl.Main
import com.baulsupp.okurl.authenticator.AuthenticatingInterceptor
import com.baulsupp.okurl.authenticator.RenewingInterceptor
import com.baulsupp.okurl.credentials.SimpleCredentialsStore
import com.baulsupp.okurl.location.BestLocation
import com.baulsupp.okurl.location.LocationSource
import com.baulsupp.okurl.okhttp.OkHttpResponseExtractor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import java.io.File

val client: OkHttpClient by lazy {
  val credentialsStore = SimpleCredentialsStore
  val cacheDirectory = File(System.getenv("HOME"), ".okurl/shell.cache")
  OkHttpClient.Builder()
    .cache(Cache(cacheDirectory, (64 * 1024 * 1024).toLong()))
    .addInterceptor(BrotliInterceptor)
    .addInterceptor(AuthenticatingInterceptor(credentialsStore))
    .addInterceptor(RenewingInterceptor(credentialsStore))
    .build().also {
      Main.client = it
      Main.moshi = moshi
    }
}

val outputHandler: OutputHandler<Response> by lazy {
  initLogging()

  ConsoleHandler.instance(OkHttpResponseExtractor())
}

val simpleOutput = ConsoleHandler(SimpleResponseExtractor)

val locationSource: LocationSource by lazy { BestLocation(outputHandler) }
