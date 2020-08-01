package com.baulsupp.okscript

import com.baulsupp.okurl.location.Location
import com.baulsupp.okurl.moshi.Rfc3339InstantJsonAdapter
import com.baulsupp.okurl.services.mapbox.model.MapboxLatLongAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant
import java.util.Date

val moshi = Moshi.Builder()
  .add(Location::class.java, MapboxLatLongAdapter().nullSafe())
  .add(KotlinJsonAdapterFactory())
  .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
  .add(Instant::class.java, Rfc3339InstantJsonAdapter.nullSafe())
  .build()!!

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
