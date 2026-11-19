package org.tangowiz.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

object ShortWindowValidation {
    private fun checkbox(view: View): CheckBox? {
        if (view is CheckBox && view.text == "Disable short window") return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) checkbox(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    fun run(test: Instrumentation) {
        val store = Store(test.targetContext)
        val original = store.disableShortWindow
        val resources = File(test.targetContext.filesDir, "resources-v1")
        val pointer = File(resources, "active.json")
        val backup = pointer.takeIf { it.exists() }?.readBytes()
        val bundle = File(resources, "bundle-${"0".repeat(64)}-00000000-0000-0000-0000-000000000002")
        var activity: Activity? = null
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { runCatching(action).onFailure { failure = it } }
            failure?.let { throw it }
        }
        try {
            check(!original) { "Expected default-off disable preference" }
            bundle.mkdirs()
            val files = JSONObject()
            for ((name, bytes) in mapOf("catalog.bin" to "TWIDX002fixture".toByteArray(),
                "nmfp.tflite" to byteArrayOf(1))) {
                File(bundle, name).writeBytes(bytes)
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                files.put(name, JSONObject().put("bytes", bytes.size).put("sha256", hash))
            }
            val manifest = JSONObject().put("formatVersion", 2).put("bundleVersion", "short-window-test")
                .put("archive", JSONObject().put("url", "https://test.invalid/library.zip")
                    .put("bytes", 1).put("sha256", "0".repeat(64))).put("files", files)
            File(bundle, "verified.json").writeText(manifest.toString())
            pointer.writeText(manifest.put("directory", bundle.name).toString())
            activity = test.startActivitySync(
                Intent(test.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            val advanced = MainActivity::class.java.getDeclaredMethod("advanced").apply { isAccessible = true }
            for (save in listOf(false, true, true)) {
                lateinit var dialog: AlertDialog
                main { dialog = advanced.invoke(activity) as AlertDialog }
                test.waitForIdleSync()
                val before = store.disableShortWindow
                main {
                    val setting = checkbox(dialog.window!!.decorView) ?: error("Missing checkbox")
                    check(setting.isChecked == before)
                    setting.performClick()
                    dialog.getButton(if (save) AlertDialog.BUTTON_POSITIVE else AlertDialog.BUTTON_NEGATIVE).performClick()
                }
                check(Store(test.targetContext).disableShortWindow == if (save) !before else before)
            }
            val rule = ShortWindowAgreement()
            check((0..2).map { rule.observe(it * .5, "same", "same", .608, 60, .670, 10) }
                == listOf(false, false, true))
            rule.clear()
            check((0..2).none { rule.observe(it * .5, "same", "different", .608, 60, .670, 10) })
            test.finish(Activity.RESULT_OK, Bundle().apply {
                putString("report", "Default unchecked, Cancel and Save verified, agreement and disagreement verified")
            })
        } catch (failure: Throwable) {
            test.finish(Activity.RESULT_CANCELED, Bundle().apply { putString("error", failure.stackTraceToString()) })
        } finally {
            store.disableShortWindow = original
            main { activity?.finish() }
            if (backup == null) pointer.delete() else pointer.writeBytes(backup)
            bundle.deleteRecursively()
        }
    }
}
