/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.network.features

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.features.HttpClientFeature
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.request.header
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.request
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext


class ETagCacheFeature private constructor() {
    private val eTagCaches: MutableMap<String, CacheEntity> = mutableMapOf()

    class Config {
        fun build() = ETagCacheFeature()
    }

    companion object Feature : HttpClientFeature<Config, ETagCacheFeature> {
        private const val ETagHeaderName = "ETag"
        private const val IfNoneMatchHeaderName = "If-None-Match"

        override val key = AttributeKey<ETagCacheFeature>("ETagCacheFeature")

        override fun prepare(block: Config.() -> Unit) = Config().apply(block).build()

        override fun install(feature: ETagCacheFeature, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.State) {
                val url = this.context.url.buildString()
                val cacheEntity = feature.eTagCaches[url]
                if (cacheEntity != null) {
                    this.context.header(IfNoneMatchHeaderName, cacheEntity.eTag)
                }
            }
            scope.receivePipeline.intercept(HttpReceivePipeline.Before) {
                if (it.status == HttpStatusCode.NotModified) {
                    val url = it.request.url.toString()
                    val cacheEntity = feature.eTagCaches[url]
                    if (cacheEntity != null) {
                        proceedWith(
                            WrappedHttpResponse(
                                it,
                                ByteReadChannel(content = cacheEntity.response),
                                HttpStatusCode.OK
                            )
                        )
                        return@intercept
                    }
                }
                val eTag = it.headers[ETagHeaderName]
                if (eTag != null) {
                    val url = it.request.url.toString()
                    val responseBytes = it.readBytes()
                    feature.eTagCaches[url] = CacheEntity(
                        eTag = eTag,
                        response = responseBytes
                    )
                    val bytesChannel = ByteReadChannel(
                        content = responseBytes,
                        offset = 0,
                        length = responseBytes.size
                    )
                    proceedWith(WrappedHttpResponse(it, bytesChannel))
                } else {
                    proceedWith(it)
                }
            }
        }
    }

    class CacheEntity(
        val eTag: String,
        val response: ByteArray
    )

    class WrappedHttpResponse(
        private val originalResponse: HttpResponse,
        override val content: ByteReadChannel,
        private val statusCode: HttpStatusCode? = null
    ) : HttpResponse() {
        override val call: HttpClientCall by originalResponse::call
        override val coroutineContext: CoroutineContext by originalResponse::coroutineContext
        override val headers: Headers by originalResponse::headers
        override val requestTime: GMTDate by originalResponse::requestTime
        override val responseTime: GMTDate by originalResponse::responseTime
        override val status: HttpStatusCode get() = statusCode ?: originalResponse.status
        override val version: HttpProtocolVersion by originalResponse::version
    }
}
