package sui.k.als.qemu.gzvm

import android.system.Os
import android.system.OsConstants
import sui.k.als.vm.VMNative
import sui.k.als.app.qemu.gzvm.QemuGzvmConfig
import sui.k.als.log.ALSLog
import java.io.FileInputStream
import java.io.RandomAccessFile

object QemuGzvmPreflight {
    internal fun run(config: QemuGzvmConfig) {
        val error = VMNative.grantRoot()
        check(error == 0) {
            "KernelSU direct root failed: ${Os.strerror(error)} ($error)"
        }
        check(Os.geteuid() == 0) { "KernelSU did not elevate the QEMU thread" }
        ALSLog.flush()
        ALSLog.info("QEMU-GZVM", "root granted")
        verifyFiles(config)
        verifyDevice("/dev/gzvm")
        if (config.network) {
            verifyDevice("/dev/tun")
            configureTapNetwork()
        }
    }

    private fun verifyFiles(config: QemuGzvmConfig) {
        config.uefiPath.takeIf(String::isNotBlank)?.let { FileInputStream(it).use { } }
        config.efiVirtioRomPath.takeIf(String::isNotBlank)?.let { FileInputStream(it).use { } }
        config.diskPaths.filter(String::isNotBlank).forEach { RandomAccessFile(it, "rw").use { } }
        if (config.cdrom) {
            config.isoPaths.filter(String::isNotBlank).forEach { FileInputStream(it).use { } }
        }
    }

    private fun verifyDevice(path: String) {
        val fd = try {
            Os.open(path, OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
        } catch (error: Exception) {
            throw IllegalStateException("QEMU root thread cannot open $path: ${error.message}", error)
        }
        Os.close(fd)
    }

    private fun configureTapNetwork() {
        val process = ProcessBuilder("/system/bin/sh", "-c", "set -e; $tapNetworkCommand")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        check(process.waitFor() == 0) {
            "Failed to configure tap0${if (output.isBlank()) "" else ": $output"}"
        }
    }

    private const val tapNetworkCommand = "ip link show tap0 >/dev/null 2>&1 || ip tuntap add dev tap0 mode tap; ip addr flush dev tap0; ip addr add 100.99.99.1/24 dev tap0; ip link set tap0 up; sysctl -w net.ipv4.ip_forward=1; while ip rule del from 100.99.99.0/24 lookup wlan0 2>/dev/null; do :; done; while ip rule del to 100.99.99.0/24 lookup main 2>/dev/null; do :; done; ip rule add pref 100 from 100.99.99.0/24 lookup wlan0; ip rule add pref 101 to 100.99.99.0/24 lookup main; while iptables -D INPUT -i tap0 -j ACCEPT 2>/dev/null; do :; done; while iptables -D FORWARD -i tap0 -o wlan0 -j ACCEPT 2>/dev/null; do :; done; while iptables -D FORWARD -i wlan0 -o tap0 -m state --state ESTABLISHED,RELATED -j ACCEPT 2>/dev/null; do :; done; iptables -I INPUT 1 -i tap0 -j ACCEPT; iptables -I FORWARD 1 -i tap0 -o wlan0 -j ACCEPT; iptables -I FORWARD 1 -i wlan0 -o tap0 -m state --state ESTABLISHED,RELATED -j ACCEPT; while iptables -t nat -D POSTROUTING -s 100.99.99.0/24 -o wlan0 -j MASQUERADE 2>/dev/null; do :; done; iptables -t nat -I POSTROUTING 1 -s 100.99.99.0/24 -o wlan0 -j MASQUERADE"
}
