package com.baulsupp.oksocial.services.google;

import com.baulsupp.oksocial.authenticator.AuthUtil;
import com.baulsupp.oksocial.util.JsonUtil;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class DiscoveryRegistry {
  private final Map<String, Object> map;

  public DiscoveryRegistry(Map<String, Object> map) {
    this.map = map;
  }

  public static DiscoveryRegistry loadStatic() throws IOException {
    URL url = DiscoveryRegistry.class.getResource("discovery.json");

    String definition = Resources.toString(url, StandardCharsets.UTF_8);

    return DiscoveryRegistry.parse(definition);
  }

  public static DiscoveryRegistry parse(String definition) throws IOException {
    return new DiscoveryRegistry(JsonUtil.map(definition));
  }

  private Map<String, Map<String, Object>> getItems() {
    //noinspection unchecked
    return (Map<String, Map<String, Object>>) map.get("items");
  }

  public CompletableFuture<DiscoveryDocument> load(OkHttpClient client, String discoveryDocPath) {
    Request request = new Request.Builder().url(discoveryDocPath).build();
    CompletableFuture<Map<String, Object>> mapFuture =
        AuthUtil.enqueueJsonMapRequest(client, request);

    return mapFuture.thenApply(s -> new DiscoveryDocument(s));
  }
}