package com.noexcs.localagent.agent

import android.content.Context
import android.content.Intent
import android.util.Log
import com.noexcs.localagent.CommandResult
import com.noexcs.localagent.TermuxResultReceiver
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class TermuxExecutor(private val context: Context) {

    companion object {
        private const val LOG_TAG = "TermuxExecutor"
        private val executionIdCounter = AtomicInteger(2000)
    }

    suspend fun execute(
        command: String,
        workdir: String = "/data/data/com.termux/files/home",
        timeoutMs: Long = 60_000
    ): CommandResult = withTimeout(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            try {
                val executionId = executionIdCounter.getAndIncrement()
                val callbackKey = "tool_exec_$executionId"

                TermuxResultReceiver.resultCallbacks[callbackKey] = { result ->
                    if (cont.isActive) cont.resume(result)
                }

                cont.invokeOnCancellation {
                    TermuxResultReceiver.resultCallbacks.remove(callbackKey)
                }

                val intent = Intent().apply {
                    setClassName(
                        TermuxConstants.TERMUX_PACKAGE_NAME,
                        TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE_NAME
                    )
                    action = RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND
                    putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
                    putExtra(RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, arrayOf("-c", command))
                    putExtra(RUN_COMMAND_SERVICE.EXTRA_WORKDIR, workdir)
                    putExtra(RUN_COMMAND_SERVICE.EXTRA_BACKGROUND, true)
                    putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, "LocalAgent Tool")
                }

                val pluginResultsIntent = Intent(context, TermuxResultReceiver::class.java).apply {
                    putExtra(TermuxResultReceiver.EXTRA_EXECUTION_ID, executionId)
                    putExtra(TermuxResultReceiver.EXTRA_CALLBACK_KEY, callbackKey)
                }

                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    executionId,
                    pluginResultsIntent,
                    android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_MUTABLE
                )

                intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT, pendingIntent)

                Log.d(LOG_TAG, "Executing [$executionId]: $command")
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to execute: ${e.message}", e)
                if (cont.isActive) {
                    cont.resume(CommandResult(errorMessage = "Execution error: ${e.message}"))
                }
            }
        }
    }
}
