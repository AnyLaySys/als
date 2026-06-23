package sui.k.als.qvm.gunyah

import android.content.*
import sui.k.als.app.qvm.gunyah.QvmGunyahConfig
import sui.k.als.app.qvm.gunyah.toQvmGunyahDisplayDevice
import sui.k.als.tty.*
import sui.k.als.ui.*

fun QvmGunyahConfig.toQvmGunyahQemuCommand(): String {
    val extra = extraQemuArgs.trim()
    val args = mutableListOf(
        "./qemu-system-aarch64",
        "-L ./fw",
        "-bios edk2-aarch64-gunyah.fd",
        "-M virt,confidential-guest-support=prot0",
        "-accel gunyah",
        "-cpu host",
        "-smp $cpuCores",
        "-m ${memoryMb}M",
        "-object arm-confidential-guest,id=prot0,swiotlb-size=64M"
    )
    if (iothread) args += "-object iothread,id=io0"
    if (diskPath.isNotBlank()) args += "-drive file=${diskPath.qvmGunyahShellPath()},format=raw,if=none,id=hd0,cache=unsafe,aio=threads,discard=unmap"
    if (cdrom && isoPath.isNotBlank()) args += "-drive file=${isoPath.qvmGunyahShellPath()},format=raw,if=none,id=dr0,media=cdrom,readonly=on,cache=unsafe,aio=threads,discard=unmap"
    if (network) args += "-netdev user,id=usernet,hostfwd=tcp::$sshPort-:22"
    if (audio) args += "-audiodev aaudio,id=aa"
    if (diskPath.isNotBlank()) args += $$"-device virtio-blk-pci,drive=hd0,num-queues=$(nproc)$${if (iothread) ",iothread=io0" else ""},disable-legacy=on,disable-modern=off"
    if (cdrom && isoPath.isNotBlank()) args += $$"-device virtio-blk-pci,drive=dr0,num-queues=$(nproc)$${if (iothread) ",iothread=io0" else ""},disable-legacy=on,disable-modern=off,bootindex=1"
    if (network) args += "-device virtio-net-pci,netdev=usernet"
    if (tablet) args += "-device virtio-tablet-pci"
    if (keyboard) args += "-device virtio-keyboard-pci"
    when (displayDevice.toQvmGunyahDisplayDevice()) {
        "virtio-gpu" -> args += "-device virtio-gpu-pci,xres=$width,yres=$height"
        "ramfb" -> args += "-device ramfb"
    }
    if (displayOutput) args += "-display sdl"
    if (audio) args += "-device virtio-snd-pci,audiodev=aa"
    if (serial) args += "-serial mon:stdio"
    if (extra.isNotEmpty()) args += extra
    return args.joinToString(" ")
}

private fun String.qvmGunyahShellPath(): String = "'${replace("'", "'\\''")}'"

fun buildQvmGunyahStartCommand(config: QvmGunyahConfig): String = $$"""
    [ "$(id -u)" = 0 ] || exit 1;
    X11_DIR=$${x11Dir};
    QEMU_DIR=$${"$alsDir/qemu-gunyah"};
    CACHE_XKB=/data/data/sui.k.als/cache/x11/xkb;
    APK=$(pm path sui.k.als | sed 's/^package://' | tr '\n' ':' | sed 's/:$//');
    [ -n "$APK" ] || exit 1;
    pkill -9 -f 'com.termux.x11.CmdEntryPoint' 2>/dev/null || true;
    pkill -9 -f '^x11$' 2>/dev/null || true;
    rm -rf "$X11_DIR/tmp/.X11-unix" "$X11_DIR/tmp/.X1-lock" "$X11_DIR/tmp/.tX1-lock";
    mkdir -p "$X11_DIR/tmp/.X11-unix" "$X11_DIR/home";
    : > "$X11_DIR/home/.Xauthority";
    rm -rf "$X11_DIR/xkb";
    mkdir -p "$X11_DIR/xkb";
    cp -R "$CACHE_XKB/." "$X11_DIR/xkb/" || exit 1;
    chmod -R 777 "$X11_DIR" 2>/dev/null || true;
    (unset LD_LIBRARY_PATH LD_PRELOAD TERMUX_X11_DEBUG; CLASSPATH="$APK" TERMUX_X11_TMPDIR="$X11_DIR/tmp" TMPDIR="$X11_DIR/tmp" XDG_RUNTIME_DIR="$X11_DIR/tmp" HOME="$X11_DIR/home" XKB_CONFIG_ROOT="$X11_DIR/xkb" TERMUX_X11_OVERRIDE_PACKAGE=sui.k.als /system/bin/app_process / --nice-name=x11 com.termux.x11.CmdEntryPoint :1 -nolock -ac >"$X11_DIR/x11.log" 2>&1) &
    am start -n sui.k.als/com.termux.x11.MainActivity >/dev/null 2>&1 || true;
    for i in $(seq 1 200); do [ -S "$X11_DIR/tmp/.X11-unix/X1" ] && break; sleep 0.05; done;
    [ -S "$X11_DIR/tmp/.X11-unix/X1" ] || { cat "$X11_DIR/x11.log" 2>/dev/null; exit 1; };
    for p in X11:6 X11-xcb:1 Xext:6 Xcursor:1 Xi:6 Xfixes:3 Xrandr:2 Xss:1 Xinerama:1 Xrender:1 Xau:6 Xdmcp:6 xcb:1; do n=${p%:*}; v=${p#*:}; [ -e "$QEMU_DIR/lib/lib$n.so" ] && ln -sf "lib$n.so" "$QEMU_DIR/lib/lib$n.so.$v"; done;
    cd "$QEMU_DIR" &&
    export DISPLAY=:1 XAUTHORITY="$X11_DIR/home/.Xauthority" HOME="$X11_DIR/home" TMPDIR="$X11_DIR/tmp" XDG_RUNTIME_DIR="$X11_DIR/tmp" XKB_CONFIG_ROOT="$X11_DIR/xkb" LD_LIBRARY_PATH="$QEMU_DIR/lib:/system/lib64:/vendor/lib64" SDL_VIDEODRIVER=x11 SDL_AUDIODRIVER=aaudio SDL_VIDEO_X11_XRANDR=0 SDL_VIDEO_X11_XINERAMA=0 SDL_VIDEO_X11_XVIDMODE=0 SDL_VIDEO_X11_XCURSOR=0 LANG=C LC_ALL=C &&
    $${config.toQvmGunyahQemuCommand()}
""".trimIndent()

fun Context.openQvmGunyahX11() {
    startActivity(Intent().setClassName(packageName, "com.termux.x11.MainActivity"))
}
