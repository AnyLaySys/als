package sui.k.als

fun shellQuote(value: String) = "'${value.replace("'", "'\\''")}'"
