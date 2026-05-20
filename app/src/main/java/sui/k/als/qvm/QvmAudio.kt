package sui.k.als.qvm

import androidx.compose.runtime.*
import androidx.compose.ui.res.*
import sui.k.als.R
import sui.k.als.ui.*

@Composable
fun QvmAudio(qvmMap: MutableMap<String, Any>) {
    LaunchedEffect(qvmMap["audio"]) {
        qvmMap["audio_cmd"] =
            if (qvmMap["audio"] == 1) "-audiodev aaudio,id=snd0 -device virtio-sound-pci,audiodev=snd0,disable-legacy=on,disable-modern=off " else ""
    }
    ALSList(
        stringResource(R.string.audio_output),
        checked = qvmMap["audio"] == 1,
        first = true,
        last = true
    ) {
        qvmMap["audio"] = if (qvmMap["audio"] == 1) 0 else 1
    }
}