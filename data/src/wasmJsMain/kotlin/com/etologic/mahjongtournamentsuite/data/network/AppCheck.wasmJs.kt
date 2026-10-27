package com.etologic.mahjongtournamentsuite.data.network

import kotlinx.coroutines.await
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise

/** `appcheck.js` of the web app defines this function. Without it, or without its settings, the token is null. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => (typeof window.mtsAppCheckToken === 'function') ? window.mtsAppCheckToken() : Promise.resolve(null)")
private external fun readAppCheckToken(): Promise<JsAny?>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal actual suspend fun currentAppCheckToken(): String? =
    (readAppCheckToken().await<JsAny?>() as? JsString)?.toString()
