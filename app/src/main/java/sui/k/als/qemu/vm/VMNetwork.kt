package sui.k.als.qemu.vm

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import sui.k.als.ALSApplication
import sui.k.als.Log
import java.net.Inet4Address
import java.util.concurrent.LinkedBlockingQueue

internal object VMNetwork {
    private const val device = "tap0"
    private const val gateway = "100.99.99.1/24"
    private const val subnet = "100.99.99.0/24"
    private const val egressTable = 1000
    private val interfaceName = Regex("[A-Za-z0-9_.:-]+")
    private val queue = LinkedBlockingQueue<Egress>()
    private var following = false

    fun configure(backend: VMBackend) {
        run(setupCommand)
        val manager = ALSApplication.instance.getSystemService(ConnectivityManager::class.java)
        val initial = manager.activeNetwork?.let { network ->
            manager.getLinkProperties(network)?.let { egress(network, it) }
        }
        update(initial, backend)
        if (following) {
            return
        }
        following = true
        Thread({ follow(backend, initial) }, "vm-net").apply { isDaemon = true }.start()
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    queue.offer(egress(network, linkProperties) ?: Egress(network.networkHandle, null, null))
                }

                override fun onLost(network: Network) {
                    queue.offer(Egress(network.networkHandle, null, null, true))
                }
            })
        }.onFailure { Log.error("VM", "Default network callback failed", it) }
    }

    private fun follow(backend: VMBackend, initial: Egress?) {
        var current = initial
        while (true) {
            val value = queue.take()
            if (value.device == null) {
                if (value.lost && current?.handle != value.handle) {
                    continue
                }
                runCatching { update(null, backend) }
                    .onSuccess { current = null }
                    .onFailure { Log.error("VM", "Egress reset failed", it) }
                continue
            }
            if (value == current) {
                continue
            }
            runCatching { update(value, backend) }
                .onSuccess {
                    current = value
                    Log.info("VM", "Egress via ${value.device}")
                }
                .onFailure { Log.error("VM", "Egress via ${value.device} failed", it) }
        }
    }

    private fun update(value: Egress?, backend: VMBackend) {
        if (value == null) {
            run(clearEgressCommand)
            if (backend == VMBackend.Gunyah) {
                VMNative.setNetworkHandle(0)
            }
            return
        }
        val device = checkNotNull(value.device)
        check(interfaceName.matches(device)) { "Invalid egress interface $device" }
        run(egressCommand(device, value.gateway))
        if (backend == VMBackend.Gunyah) {
            VMNative.setNetworkHandle(value.handle)
        }
    }

    private fun run(command: String) {
        val process = ProcessBuilder("/system/bin/sh", "-c", "set -e; $command")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        check(process.waitFor() == 0) {
            "Failed to configure $device${if (output.isBlank()) "" else ": $output"}"
        }
    }

    private val clearEgressCommand = "while ip rule del pref 100 from $subnet lookup $egressTable 2>/dev/null; do :; done; while ip route del table $egressTable default 2>/dev/null; do :; done"

    private val setupCommand = listOf(
        "ip link show $device >/dev/null 2>&1 || ip tuntap add dev $device mode tap",
        "ip addr flush dev $device",
        "ip addr add $gateway dev $device",
        "ip link set $device up",
        "sysctl -w net.ipv4.ip_forward=1",
        "while ip rule del pref 101 to $subnet lookup main 2>/dev/null; do :; done",
        "ip rule add pref 101 to $subnet lookup main",
        "while iptables -w 5 -D INPUT -i $device -j ACCEPT 2>/dev/null; do :; done",
        "while iptables -w 5 -D FORWARD -i $device -j ACCEPT 2>/dev/null; do :; done",
        "while iptables -w 5 -D FORWARD -o $device -m state --state ESTABLISHED,RELATED -j ACCEPT 2>/dev/null; do :; done",
        "iptables -w 5 -I INPUT 1 -i $device -j ACCEPT",
        "iptables -w 5 -I FORWARD 1 -i $device -j ACCEPT",
        "iptables -w 5 -I FORWARD 1 -o $device -m state --state ESTABLISHED,RELATED -j ACCEPT",
        "while iptables -w 5 -t nat -D POSTROUTING -s $subnet ! -o $device -j MASQUERADE 2>/dev/null; do :; done",
        "iptables -w 5 -t nat -I POSTROUTING 1 -s $subnet ! -o $device -j MASQUERADE"
    ).joinToString("; ")

    private fun egress(network: Network, linkProperties: LinkProperties): Egress? {
        val route = linkProperties.routes.firstOrNull {
            it.isDefaultRoute && it.gateway is Inet4Address
        } ?: return null
        val value = linkProperties.interfaceName ?: return null
        val address = route.gateway as Inet4Address
        return Egress(network.networkHandle, value, address.takeUnless { it.isAnyLocalAddress }?.hostAddress)
    }

    private fun egressCommand(value: String, gateway: String?): String =
        "$clearEgressCommand; ip route add table $egressTable default ${gateway?.let { "via $it " }.orEmpty()}dev $value; ip rule add pref 100 from $subnet lookup $egressTable"

    private data class Egress(
        val handle: Long,
        val device: String?,
        val gateway: String?,
        val lost: Boolean = false,
    )
}
