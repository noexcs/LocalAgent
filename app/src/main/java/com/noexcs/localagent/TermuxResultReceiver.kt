package com.noexcs.localagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE

class TermuxResultReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_EXECUTION_ID = "execution_id"
        const val EXTRA_CALLBACK_KEY = "callback_key"

        private const val LOG_TAG = "TermuxResultReceiver"

        @JvmStatic
        val resultCallbacks = mutableMapOf<String, (CommandResult) -> Unit>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOG_TAG, "Received execution result")
        Log.d(LOG_TAG, "Intent extras: ${intent.extras?.keySet()}")

        val resultBundle = intent.getBundleExtra(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE)
        if (resultBundle == null) {
            Log.e(LOG_TAG, "No result bundle at key \"${TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE}\"")
            return
        }

        Log.d(LOG_TAG, "Result bundle keys: ${resultBundle.keySet()}")

        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, 0)
        val callbackKey = intent.getStringExtra(EXTRA_CALLBACK_KEY)

        val stdout = resultBundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT, "") ?: ""
        val stderr = resultBundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR, "") ?: ""
        val exitCode = resultBundle.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE, -1)
        val errCode = resultBundle.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERR, -1)
        val errmsg = resultBundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG, "") ?: ""

        Log.d(LOG_TAG, "Execution id $executionId result:\n" +
                "stdout: `$stdout`\n" +
                "stdout_original_length: `${resultBundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT_ORIGINAL_LENGTH)}`\n" +
                "stderr: `$stderr`\n" +
                "stderr_original_length: `${resultBundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR_ORIGINAL_LENGTH)}`\n" +
                "exitCode: $exitCode\n" +
                "errCode: $errCode\n" +
                "errmsg: `$errmsg`")

        Log.d(LOG_TAG, "Looking for callback: $callbackKey, available: ${resultCallbacks.keys}")

        callbackKey?.let { key ->
            val callback = resultCallbacks.remove(key)
            if (callback != null) {
                Log.d(LOG_TAG, "Invoking callback for $key")
                callback(
                    CommandResult(
                        stdout = stdout,
                        stderr = stderr,
                        exitCode = exitCode,
                        errorMessage = if (errCode != android.app.Activity.RESULT_OK && errmsg.isNotEmpty()) errmsg else null
                    )
                )
            } else {
                Log.e(LOG_TAG, "No callback found for key: $key")
            }
        }
    }
}
