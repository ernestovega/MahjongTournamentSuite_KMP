package com.etologic.mahjongtournamentsuite.presentation.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.posix.memcpy

/** The view controllers of the iOS app, to show the pickers and the share sheet of the system over the app. */
@OptIn(ExperimentalForeignApi::class)
object IosViewControllers {
    /** The controller on top: the app, or a sheet that it shows. */
    fun top(): UIViewController? {
        val window = UIApplication.sharedApplication.connectedScenes
            .mapNotNull { (it as? UIWindowScene)?.windows }
            .flatMap { it.mapNotNull { window -> window as? UIWindow } }
            .firstOrNull { it.isKeyWindow() }
            ?: UIApplication.sharedApplication.connectedScenes
                .mapNotNull { (it as? UIWindowScene)?.windows?.firstOrNull() as? UIWindow }
                .firstOrNull()
        var top = window?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: return top
    }

    /** Shows [controller] over the app. On an iPad a share sheet needs a place to point at: the middle of the app. */
    fun present(controller: UIViewController) {
        val top = top() ?: return
        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            controller.popoverPresentationController?.let { popover ->
                popover.sourceView = top.view
                popover.sourceRect = top.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 1.0, 1.0) }
            }
        }
        top.presentViewController(controller, animated = true, completion = null)
    }

    /** Writes [bytes] to a temporary file named [fileName] and returns its address. */
    fun temporaryFile(fileName: String, bytes: ByteArray): NSURL {
        val folder = NSTemporaryDirectory() + "export/"
        NSFileManager.defaultManager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
        val url = NSURL.fileURLWithPath(folder + fileName.replace('/', '-'))
        bytes.toNSData().writeToURL(url, atomically = true)
        return url
    }

    /** The share sheet of iOS with a file: the user sends it to an app or saves it in Files. */
    fun shareFile(fileName: String, bytes: ByteArray) {
        val url = temporaryFile(fileName, bytes)
        present(UIActivityViewController(activityItems = listOf(url), applicationActivities = null))
    }

    /** The "Save to Files" picker of iOS with a file. */
    fun exportFile(fileName: String, bytes: ByteArray) {
        val url = temporaryFile(fileName, bytes)
        present(UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true))
    }
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}
