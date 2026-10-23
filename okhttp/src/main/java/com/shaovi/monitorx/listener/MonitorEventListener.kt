package com.shaovi.monitorx.listener

import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 记录 OkHttp 物理生命周期各阶段精细耗时的 EventListener
 */
class MonitorEventListener private constructor(
    private val delegate: EventListener
) : EventListener() {

    data class TimingMetrics(
        var callStartNanos: Long = 0L,
        var dnsStartNanos: Long = 0L,
        var dnsEndNanos: Long = 0L,
        var connectStartNanos: Long = 0L,
        var connectEndNanos: Long = 0L,
        var requestHeadersStartNanos: Long = 0L,
        var requestBodyEndNanos: Long = 0L,
        var responseHeadersStartNanos: Long = 0L,
        var responseBodyEndNanos: Long = 0L,
        var callEndNanos: Long = 0L,

        // 最终算好的毫秒值
        var queueMs: Long = 0L,
        var dnsMs: Long = 0L,
        var connectMs: Long = 0L,
        var requestMs: Long = 0L,
        var serverMs: Long = 0L,
        var downloadMs: Long = 0L
    ) {
        fun calculate(): TimingMetrics {
            val networkStart = sequenceOf(dnsStartNanos, connectStartNanos, requestHeadersStartNanos)
                .filter { it > 0 }
                .minOrNull() ?: 0L
            if (callStartNanos > 0 && networkStart > callStartNanos) {
                queueMs = TimeUnit.NANOSECONDS.toMillis(networkStart - callStartNanos)
            }
            if (dnsStartNanos > 0 && dnsEndNanos > dnsStartNanos) {
                dnsMs = TimeUnit.NANOSECONDS.toMillis(dnsEndNanos - dnsStartNanos)
            }
            if (connectStartNanos > 0 && connectEndNanos > connectStartNanos) {
                connectMs = TimeUnit.NANOSECONDS.toMillis(connectEndNanos - connectStartNanos)
            }
            val sendDone = if (requestBodyEndNanos > 0) requestBodyEndNanos else requestHeadersEndNanos
            if (requestHeadersStartNanos > 0 && sendDone > requestHeadersStartNanos) {
                requestMs = TimeUnit.NANOSECONDS.toMillis(sendDone - requestHeadersStartNanos)
            }
            if (sendDone > 0 && responseHeadersStartNanos > sendDone) {
                serverMs = TimeUnit.NANOSECONDS.toMillis(responseHeadersStartNanos - sendDone)
            }
            if (responseHeadersStartNanos > 0 && responseBodyEndNanos > responseHeadersStartNanos) {
                downloadMs = TimeUnit.NANOSECONDS.toMillis(responseBodyEndNanos - responseHeadersStartNanos)
            }
            return this
        }

        var requestHeadersEndNanos: Long = 0L
    }

    private val metrics = TimingMetrics()

    override fun callStart(call: Call) {
        delegate.callStart(call)
        metrics.callStartNanos = System.nanoTime()
        callMetricsMap[call] = metrics
    }

    override fun proxySelectStart(call: Call, url: HttpUrl) = delegate.proxySelectStart(call, url)

    override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) =
        delegate.proxySelectEnd(call, url, proxies)

    override fun dnsStart(call: Call, domainName: String) {
        delegate.dnsStart(call, domainName)
        metrics.dnsStartNanos = System.nanoTime()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        delegate.dnsEnd(call, domainName, inetAddressList)
        metrics.dnsEndNanos = System.nanoTime()
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        delegate.connectStart(call, inetSocketAddress, proxy)
        metrics.connectStartNanos = System.nanoTime()
    }

    override fun secureConnectStart(call: Call) = delegate.secureConnectStart(call)

    override fun secureConnectEnd(call: Call, handshake: Handshake?) =
        delegate.secureConnectEnd(call, handshake)

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        delegate.connectEnd(call, inetSocketAddress, proxy, protocol)
        metrics.connectEndNanos = System.nanoTime()
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        delegate.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
        metrics.connectEndNanos = System.nanoTime()
    }

    override fun requestHeadersStart(call: Call) {
        delegate.requestHeadersStart(call)
        metrics.requestHeadersStartNanos = System.nanoTime()
    }

    override fun connectionAcquired(call: Call, connection: Connection) =
        delegate.connectionAcquired(call, connection)

    override fun connectionReleased(call: Call, connection: Connection) =
        delegate.connectionReleased(call, connection)

    override fun requestHeadersEnd(call: Call, request: Request) {
        delegate.requestHeadersEnd(call, request)
        metrics.requestHeadersEndNanos = System.nanoTime()
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        delegate.requestBodyEnd(call, byteCount)
        metrics.requestBodyEndNanos = System.nanoTime()
    }

    override fun requestBodyStart(call: Call) = delegate.requestBodyStart(call)

    override fun requestFailed(call: Call, ioe: IOException) = delegate.requestFailed(call, ioe)

    override fun responseHeadersStart(call: Call) {
        delegate.responseHeadersStart(call)
        metrics.responseHeadersStartNanos = System.nanoTime()
    }

    override fun responseHeadersEnd(call: Call, response: Response) =
        delegate.responseHeadersEnd(call, response)

    override fun responseBodyStart(call: Call) = delegate.responseBodyStart(call)

    override fun responseFailed(call: Call, ioe: IOException) = delegate.responseFailed(call, ioe)

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        delegate.responseBodyEnd(call, byteCount)
        metrics.responseBodyEndNanos = System.nanoTime()
        metrics.calculate()
    }

    override fun callEnd(call: Call) {
        try {
            delegate.callEnd(call)
        } finally {
            metrics.callEndNanos = System.nanoTime()
            metrics.calculate()
            callMetricsMap.remove(call)
        }
    }

    override fun callFailed(call: Call, ioe: IOException) {
        try {
            delegate.callFailed(call, ioe)
        } finally {
            metrics.callEndNanos = System.nanoTime()
            metrics.calculate()
            callMetricsMap.remove(call)
        }
    }

    override fun canceled(call: Call) = delegate.canceled(call)

    override fun satisfactionFailure(call: Call, response: Response) =
        delegate.satisfactionFailure(call, response)

    override fun cacheHit(call: Call, response: Response) = delegate.cacheHit(call, response)

    override fun cacheMiss(call: Call) = delegate.cacheMiss(call)

    override fun cacheConditionalHit(call: Call, cachedResponse: Response) =
        delegate.cacheConditionalHit(call, cachedResponse)

    companion object {
        private val callMetricsMap = ConcurrentHashMap<Call, TimingMetrics>()

        fun factory(delegateFactory: EventListener.Factory) = EventListener.Factory { call ->
            val delegate = delegateFactory.create(call)
            if (delegate is MonitorEventListener) delegate else MonitorEventListener(delegate)
        }

        fun getMetrics(call: Call): TimingMetrics? {
            return callMetricsMap[call]
        }

    }
}
