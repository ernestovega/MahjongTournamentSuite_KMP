package com.etologic.mahjongtournamentsuite.presentation.platform

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(email, password) => {
        try {
            if (!navigator.credentials || typeof PasswordCredential === 'undefined') return;
            const credential = new PasswordCredential({ id: email, password: password, name: email });
            navigator.credentials.store(credential).catch(() => {});
        } catch (_) {}
    }""",
)
private external fun storeBrowserCredential(email: String, password: String)

actual fun saveBrowserCredentials(email: String, password: String) {
    storeBrowserCredential(email, password)
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(onLoaded) => {
        try {
            if (!navigator.credentials || !navigator.credentials.get) return;
            navigator.credentials.get({ password: true, mediation: 'optional' }).then((credential) => {
                if (credential && credential.id && credential.password) {
                    onLoaded(String(credential.id), String(credential.password));
                }
            }).catch(() => {});
        } catch (_) {}
    }""",
)
private external fun readBrowserCredential(onLoaded: (String, String) -> Unit)

actual fun loadBrowserCredentials(onLoaded: (email: String, password: String) -> Unit) {
    readBrowserCredential(onLoaded)
}
