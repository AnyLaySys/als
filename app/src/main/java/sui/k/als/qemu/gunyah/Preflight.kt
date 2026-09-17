package sui.k.als.qemu.gunyah

import android.system.Os
import android.system.OsConstants
import sui.k.als.qemu.vm.VMBackend
import sui.k.als.qemu.vm.VMNative
import sui.k.als.qemu.vm.VMNetwork
import sui.k.als.Log
import java.io.FileInputStream
import java.io.RandomAccessFile

object QemuGunyahPreflight {
    internal fun run(config: QemuGunyahConfig) {
        val error = VMNative.grantRoot()
        check(error == 0) {
            "KernelSU direct root failed: ${Os.strerror(error)} ($error)"
        }
        check(Os.geteuid() == 0) { "KernelSU did not elevate the QEMU thread" }
        Log.flush()
        Log.info("QEMU-Gunyah", "root granted")
        verifyFiles(config)
        verifyDevice("/dev/gunyah")
        if (config.network) {
            verifyDevice("/dev/tun")
            VMNetwork.configure(VMBackend.Gunyah)
        }
    }

    private fun verifyFiles(config: QemuGunyahConfig) {
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
}
