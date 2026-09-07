package com.justbrowse.core.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * mDNS / NSD 局域网服务发现。
 * 注册 _shellsync._tcp 服务 + 发现同网络内其他设备。
 */
class NsdDiscovery(
    context: Context,
    private val serviceName: String = "JustBrowse",
    private val serviceType: String = "_shellsync._tcp"
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val tag = "NsdDiscovery"

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /** 发现的设备回调 */
    var onServiceFound: ((name: String, host: String, port: Int) -> Unit)? = null
    var onServiceLost: ((name: String) -> Unit)? = null

    /** 注册本地服务（发送端） */
    fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@NsdDiscovery.serviceName
            this.serviceType = serviceType
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(tag, "Service registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d(tag, "Service unregistered")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Unregistration failed: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun unregisterService() {
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (_: Exception) {}
        }
        registrationListener = null
    }

    /** 开始发现服务（接收端） */
    fun startDiscovery() {
        if (discoveryListener != null) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "Discovery started: $regType")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "Service found: ${service.serviceName}")
                // 解析服务获取 host + port
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(tag, "Resolve failed: $errorCode")
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        Log.d(tag, "Resolved: ${serviceInfo.serviceName} @ ${serviceInfo.host.hostAddress}:${serviceInfo.port}")
                        onServiceFound?.invoke(
                            serviceInfo.serviceName,
                            serviceInfo.host.hostAddress ?: return,
                            serviceInfo.port
                        )
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(tag, "Service lost: ${service.serviceName}")
                onServiceLost?.invoke(service.serviceName)
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "Discovery stopped")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery start failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery stop failed: $errorCode")
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        discoveryListener = null
    }
}
