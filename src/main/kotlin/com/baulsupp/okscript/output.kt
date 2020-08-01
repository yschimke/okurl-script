package com.baulsupp.okscript

import okhttp3.Response

suspend fun show(url: String) {
  val response = client.execute(request(url))

  outputHandler.showOutput(response)
}

suspend fun showOutput(response: Response) {
  outputHandler.showOutput(response)
}
