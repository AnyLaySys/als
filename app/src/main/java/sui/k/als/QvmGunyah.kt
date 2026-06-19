package sui.k.als

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sui.k.als.tty.shellQuote

val defaultGunyahQemuCommand = """
    ./qemu-system-aarch64 -L ./fw -bios edk2-aarch64-gunyah.fd -M virt,confidential-guest-support=prot0 -accel gunyah -cpu host -smp 4 -m 4G -object arm-confidential-guest,id=prot0,swiotlb-size=64M -object iothread,id=io0 -drive file=/sdcard/ubuntu-26.04-desktop-arm64.iso,format=raw,if=none,id=dr0,media=cdrom,readonly=on,cache=unsafe,aio=threads,discard=unmap -device virtio-blk-pci,drive=dr0,num-queues=$(nproc),iothread=io0,disable-legacy=on,disable-modern=off,bootindex=1 -netdev user,id=usernet,hostfwd=tcp::2222-:22 -device virtio-net-pci,netdev=usernet -device virtio-tablet-pci -device virtio-keyboard-pci -device virtio-gpu-pci,xres=2376,yres=1080 -display sdl -audiodev aaudio,id=aa -device virtio-snd-pci,audiodev=aa -serial mon:stdio
""".trimIndent()

fun gunyahScript(qemuCommand: String) = $$"""
    printf "\033[2J\033[3J\033[H";
    [ "$(id -u)" = 0 ] || exit 1;
    X11_DIR=$${shellQuote(x11Dir)};
    QEMU_DIR=$${shellQuote("$alsDir/qemu-gunyah")};
    CACHE_XKB=/data/data/sui.k.als/cache/x11/xkb;
    APK=$(pm path sui.k.als | sed 's/^package://' | tr '\n' ':' | sed 's/:$//');
    [ -n "$APK" ] || exit 1;
    pkill -f 'com.termux.x11.CmdEntryPoint :1' 2>/dev/null || true;
    pkill -f 'als-x11' 2>/dev/null || true;
    rm -rf "$X11_DIR/tmp/.X11-unix" "$X11_DIR/tmp/.X1-lock" "$X11_DIR/tmp/.tX1-lock";
    mkdir -p "$X11_DIR/tmp/.X11-unix" "$X11_DIR/home";
    : > "$X11_DIR/home/.Xauthority";
    rm -rf "$X11_DIR/xkb";
    mkdir -p "$X11_DIR/xkb";
    cp -R "$CACHE_XKB/." "$X11_DIR/xkb/" || exit 1;
    chmod -R 777 "$X11_DIR" 2>/dev/null || true;
    (unset LD_LIBRARY_PATH LD_PRELOAD; CLASSPATH="$APK" TERMUX_X11_TMPDIR="$X11_DIR/tmp" TMPDIR="$X11_DIR/tmp" XDG_RUNTIME_DIR="$X11_DIR/tmp" HOME="$X11_DIR/home" XKB_CONFIG_ROOT="$X11_DIR/xkb" TERMUX_X11_DEBUG=1 TERMUX_X11_OVERRIDE_PACKAGE=sui.k.als /system/bin/app_process -Xnoimage-dex2oat / --nice-name=als-x11 com.termux.x11.CmdEntryPoint :1 -nolock -ac -extension MIT-SHM >"$X11_DIR/x11.log" 2>&1) &
    am start -n sui.k.als/com.termux.x11.MainActivity >/dev/null 2>&1 || true;
    for i in $(seq 1 100); do [ -S "$X11_DIR/tmp/.X11-unix/X1" ] && break; sleep 0.05; done;
    [ -S "$X11_DIR/tmp/.X11-unix/X1" ] || { cat "$X11_DIR/x11.log"; exit 1; };
    for p in X11:6 X11-xcb:1 Xext:6 Xcursor:1 Xi:6 Xfixes:3 Xrandr:2 Xss:1 Xinerama:1 Xrender:1 Xau:6 Xdmcp:6 xcb:1; do n=${p%:*}; v=${p#*:}; [ -e "$QEMU_DIR/lib/lib$n.so" ] && ln -sf "lib$n.so" "$QEMU_DIR/lib/lib$n.so.$v"; done;
    cd "$QEMU_DIR" &&
    export DISPLAY=:1 XAUTHORITY="$X11_DIR/home/.Xauthority" HOME="$X11_DIR/home" TMPDIR="$X11_DIR/tmp" XDG_RUNTIME_DIR="$X11_DIR/tmp" XKB_CONFIG_ROOT="$X11_DIR/xkb" LD_LIBRARY_PATH="$QEMU_DIR/lib:/system/lib64:/vendor/lib64" SDL_VIDEODRIVER=x11 SDL_AUDIODRIVER=aaudio SDL_VIDEO_X11_XRANDR=0 SDL_VIDEO_X11_XINERAMA=0 SDL_VIDEO_X11_XVIDMODE=0 SDL_VIDEO_X11_XCURSOR=0 LANG=C LC_ALL=C &&
    $${qemuCommand.trim()}
""".trimIndent()

fun gunyahCommand(qemuCommand: String) = "cat > ${shellQuote("$alsDir/qemu-gunyah.sh")} <<'ALS_QEMU_GUNYAH'\n${gunyahScript(qemuCommand)}\nALS_QEMU_GUNYAH\nchmod 700 ${shellQuote("$alsDir/qemu-gunyah.sh")}\nsh ${shellQuote("$alsDir/qemu-gunyah.sh")}"

fun Context.openX11() {
    startActivity(Intent().setClassName(packageName, "com.termux.x11.MainActivity"))
}

private val ink = Color.White
private val face = Color(0xFFC0C0C0)

@Composable
private fun RetroButton(text: String, click: () -> Unit) {
    Box(
        Modifier
            .background(face)
            .border(1.dp, ink)
            .clickable(onClick = click)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        Alignment.Center
    ) {
        Text(text, color = Color.Black, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
fun QvmGunyah(started: Boolean, onCreate: (String) -> Unit, onEnter: () -> Unit, onX11: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qvm-gunyah", 0) }
    var editing by remember { mutableStateOf(false) }
    var command by remember {
        mutableStateOf(prefs.getString("command", defaultGunyahQemuCommand) ?: defaultGunyahQemuCommand)
    }
    fun save() = prefs.edit().putString("command", command).apply()
    BackHandler(editing) { editing = false }
    Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .border(1.dp, ink)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("QVM-GUNYAH", color = ink, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
            if (editing) {
                BasicTextField(
                    command,
                    { command = it },
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 420.dp)
                        .border(1.dp, ink)
                        .verticalScroll(rememberScrollState())
                        .padding(9.dp),
                    textStyle = TextStyle(ink, 12.sp, fontFamily = FontFamily.Monospace)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    RetroButton("SAVE") { save() }
                    RetroButton("RUN") {
                        save(); X11.prepare(context); onCreate(command)
                    }
                }
            } else if (started) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    RetroButton("ENTER") { onEnter() }
                    RetroButton("X11") { onX11() }
                }
            } else {
                RetroButton("CREATE") { editing = true }
            }
        }
    }
}
