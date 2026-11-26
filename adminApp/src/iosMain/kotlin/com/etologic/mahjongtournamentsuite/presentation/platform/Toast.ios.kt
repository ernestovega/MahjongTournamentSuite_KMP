package com.etologic.mahjongtournamentsuite.presentation.platform

import platform.Foundation.NSRunLoop
import platform.Foundation.NSTimer
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert

/** iOS has no toast: a short message that closes by itself after two seconds. */
actual fun showWarningToast(message: String) {
    val alert = UIAlertController.alertControllerWithTitle(title = null, message = message, preferredStyle = UIAlertControllerStyleAlert)
    IosViewControllers.present(alert)
    val timer = NSTimer.timerWithTimeInterval(TOAST_SECONDS, repeats = false) { _ ->
        alert.dismissViewControllerAnimated(true, completion = null)
    }
    NSRunLoop.mainRunLoop.addTimer(timer, forMode = platform.Foundation.NSDefaultRunLoopMode)
}

private const val TOAST_SECONDS = 2.0
