package com.baulsupp.oksocial.services.google

/**
 * https://developers.google.com/discovery/v1/using
 */
class DiscoveryDocument(private val map: Map<String, Any>) {
  val baseUrl = "" + map["rootUrl"] + map["servicePath"]

  val endpoints = expandEndpoints(map)

  val urls = endpoints.map { e -> e.url() }.distinct()

  private fun expandEndpoints(map: Map<String, Any>): List<DiscoveryEndpoint> {
    val resources = getResources(map)

    return resources.values.flatMap { r ->
      getMethods(r).values.map { m ->
        DiscoveryEndpoint(baseUrl, m)
      } + expandEndpoints(r)
    }
  }

  private fun getResources(map: Map<String, Any>): Map<String, Map<String, Any>> {
    return if (!map.containsKey("resources")) {
      emptyMap()
    } else map["resources"] as Map<String, Map<String, Any>>
  }

  private fun getMethods(resource: Map<String, Any>): Map<String, Map<String, Any>> {
    return if (!resource.containsKey("methods")) {
      emptyMap()
    } else resource["methods"] as Map<String, Map<String, Any>>
  }

  val apiName: String
    get() = map["title"] as String

  val docLink: String
    get() = map["documentationLink"] as String

  fun findEndpoint(url: String): DiscoveryEndpoint? {
    return endpoints.filter { e ->
      matches(url, e)
    }.sortedBy { it.httpMethod() != "GET" }.firstOrNull()
  }

  private fun matches(url: String, e: DiscoveryEndpoint): Boolean {
    return e.url() == url || e.matches(url)
  }

  companion object {
    fun parse(definition: String): DiscoveryDocument {
      return DiscoveryDocument(JsonUtil.map(definition))
    }
  }
}
