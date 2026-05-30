package sui.k.als.qvm

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import sui.k.als.R
import sui.k.als.ui.*

@Composable
fun QvmArchive(state: MutableMap<String, Any>) {
    val name = state["name"]?.toString() ?: ""
    val valid = name.isNotEmpty() && qvmNameRegex.matches(name)
    ALSList(
        stringResource(R.string.cfg_name),
        value = name,
        first = true,
        last = true,
        background = if (!valid) Color.Red else null,
        onValueChange = { state["name"] = it })
}
