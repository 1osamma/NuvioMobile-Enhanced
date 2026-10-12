package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

private const val fontPreferenceKey = "nuvio.customSubtitleFont"
private const val fontChangedNotification = "NuvioSubtitleFontChanged"

private fun fontDirectory(): String {
    val documents = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true,
    ).first() as String
    return "$documents/SubtitleFonts"
}

private fun importedFonts(): List<String> =
    (NSFileManager.defaultManager.contentsOfDirectoryAtPath(fontDirectory(), error = null) as? List<*>)
        .orEmpty()
        .filterIsInstance<String>()
        .filter { it.substringAfterLast('.', "").lowercase() in setOf("ttf", "otf") }
        .sortedBy { it.lowercase() }

private fun notifyFontChanged() {
    NSNotificationCenter.defaultCenter.postNotificationName(fontChangedNotification, null)
}

@Composable
internal actual fun rememberSubtitleFontPicker(onError: (String) -> Unit): SubtitleFontPicker {
    val files = remember { mutableStateOf(importedFonts()) }
    val selection = remember {
        mutableStateOf(NSUserDefaults.standardUserDefaults.stringForKey(fontPreferenceKey))
    }
    val delegateHolder = remember { mutableStateOf<SubtitleFontDocumentDelegate?>(null) }

    return SubtitleFontPicker(
        fonts = files.value,
        selected = selection.value,
        launchImport = {
            val presenter = topSubtitlePickerViewController()
            if (presenter == null) {
                onError("Unable to open the Files picker.")
            } else {
                val picker = UIDocumentPickerViewController(
                    documentTypes = listOf("public.truetype-ttf-font", "public.opentype-font", "public.data"),
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                )
                val delegate = SubtitleFontDocumentDelegate(
                    onPicked = { url ->
                        val accessing = url.startAccessingSecurityScopedResource()
                        try {
                            val name = url.lastPathComponent ?: error("Missing font file name.")
                            require(name.substringAfterLast('.', "").lowercase() in setOf("ttf", "otf")) {
                                "Please choose a .ttf or .otf font."
                            }
                            val data = NSData.dataWithContentsOfURL(url)
                                ?: error("Unable to read selected font.")
                            require(data.length.toLong() in 1..20_971_520) {
                                "Font file must be smaller than 20 MB."
                            }
                            val dir = fontDirectory()
                            check(NSFileManager.defaultManager.createDirectoryAtPath(
                                dir, withIntermediateDirectories = true, attributes = null, error = null,
                            )) { "Unable to create subtitle fonts folder." }
                            check(data.writeToFile("$dir/$name", atomically = true)) {
                                "Unable to save imported font."
                            }
                            files.value = importedFonts()
                            NSUserDefaults.standardUserDefaults.setObject(name, forKey = fontPreferenceKey)
                            selection.value = name
                            notifyFontChanged()
                        } catch (error: Throwable) {
                            onError(error.message ?: "Unable to import font.")
                        } finally {
                            if (accessing) url.stopAccessingSecurityScopedResource()
                            delegateHolder.value = null
                        }
                    },
                    onDismiss = { delegateHolder.value = null },
                )
                delegateHolder.value = delegate
                picker.delegate = delegate
                presenter.presentViewController(picker, animated = true, completion = null)
            }
        },
        selectFont = { name ->
            if (name == null) {
                NSUserDefaults.standardUserDefaults.removeObjectForKey(fontPreferenceKey)
                selection.value = null
            } else if (name in files.value) {
                NSUserDefaults.standardUserDefaults.setObject(name, forKey = fontPreferenceKey)
                selection.value = name
            }
            notifyFontChanged()
        },
        deleteFont = { name ->
            if (name in files.value) {
                NSFileManager.defaultManager.removeItemAtPath("${fontDirectory()}/$name", error = null)
                files.value = importedFonts()
                if (selection.value == name) {
                    NSUserDefaults.standardUserDefaults.removeObjectForKey(fontPreferenceKey)
                    selection.value = null
                }
                notifyFontChanged()
            }
        },
    )
}

private class SubtitleFontDocumentDelegate(
    private val onPicked: (NSURL) -> Unit,
    private val onDismiss: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPicked) ?: onDismiss()
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onDismiss()
    }
}

private fun topSubtitlePickerViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
