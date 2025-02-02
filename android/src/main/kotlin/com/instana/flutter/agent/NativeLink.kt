/*
 * (c) Copyright IBM Corp. 2021
 * (c) Copyright Instana Inc. and contributors 2021
 */

package com.instana.flutter.agent

import android.app.Application
import com.instana.android.CustomEvent
import com.instana.android.Instana
import com.instana.android.core.InstanaConfig
import com.instana.android.instrumentation.HTTPCaptureConfig
import com.instana.android.instrumentation.HTTPMarker
import com.instana.android.instrumentation.HTTPMarkerData
import io.flutter.plugin.common.MethodChannel
import java.util.*
import java.util.regex.Pattern

internal class NativeLink {

    private val markerInstanceMap = mutableMapOf<String, HTTPMarker?>()
    private val markerMethodMap = mutableMapOf<String, String>()

    fun setUpInstana(
        result: MethodChannel.Result,
        app: Application?,
        reportingUrl: String?,
        key: String?,
        collectionEnabled: Boolean?
    ) {
        if (key.isNullOrBlank()) {
            result.error(
                ErrorCode.MISSING_OR_INVALID_ARGUMENT.serialized,
                "Instana set up requires a non-blank 'key'",
                null
            )
        } else if (reportingUrl.isNullOrBlank()) {
            result.error(
                ErrorCode.MISSING_OR_INVALID_ARGUMENT.serialized,
                "Instana set up requires a non-blank 'reportingUrl'",
                null
            )
        } else if (app == null) {
            result.error(
                ErrorCode.NOT_SETUP.serialized,
                "Instana is still not set up",
                null
            )
        } else {
            val config = InstanaConfig(
                reportingURL = reportingUrl,
                key = key,
                httpCaptureConfig = HTTPCaptureConfig.MANUAL
            )
            if (collectionEnabled != null) {
                config.collectionEnabled = collectionEnabled
            }
            Instana.setup(
                app,
                config
            )
            result.success(null)
        }
    }

    fun setUserId(result: MethodChannel.Result, userID: String?) {
        Instana.userId = userID
        result.success(null)
    }

    fun setCollectionEnabled(result: MethodChannel.Result, collectionEnabled: Boolean) {
        Instana.setCollectionEnabled(collectionEnabled)
        result.success(null)
    }

    fun setUserName(result: MethodChannel.Result, userName: String?) {
        Instana.userName = userName
        result.success(null)
    }

    fun setUserEmail(result: MethodChannel.Result, userEmail: String?) {
        Instana.userEmail = userEmail
        result.success(null)
    }

    fun setView(result: MethodChannel.Result, viewName: String?) {
        Instana.view = viewName
        result.success(null)
    }

    fun getView(): String? {
        return Instana.view
    }

    fun getSessionID(): String? {
        return Instana.sessionId
    }

    fun setMeta(result: MethodChannel.Result, key: String?, value: String?) {
        if (key.isNullOrBlank()) {
            result.error(
                ErrorCode.MISSING_OR_INVALID_ARGUMENT.serialized,
                "Instana requires non-blank 'meta keys'",
                null
            )
        } else if (value == null) {
            result.error(
                ErrorCode.MISSING_OR_INVALID_ARGUMENT.serialized,
                "Instana requires non-null 'meta values'",
                null
            )
        } else {
            val putSuccess = Instana.meta.put(key, value)
            if (putSuccess) result.success(null)
            else result.error(
                ErrorCode.META_LIST_FULL.serialized,
                "Instana failed to add new meta value",
                null
            )
        }
    }

    fun reportEvent(
        result: MethodChannel.Result,
        eventName: String?,
        startTime: Double?,
        duration: Double?,
        viewName: String?,
        meta: HashMap<String?, String?>?,
        backendTracingID: String?
    ) {
        if (eventName.isNullOrBlank()) {
            result.error(
                ErrorCode.MISSING_OR_INVALID_ARGUMENT.serialized,
                "Instana requires non-blank 'event name'",
                null
            )
        } else {
            val event = CustomEvent(eventName).apply {
                this.startTime = startTime?.toLong()
                this.duration = duration?.toLong()
                this.viewName = viewName
                this.backendTracingID = backendTracingID
                this.meta =
                    meta?.filter { it.key != null && it.value != null } as? HashMap<String, String>
            }
            Instana.reportEvent(event)
            result.success(null)
        }
    }

    fun startCapture(
        result: MethodChannel.Result,
        url: String?,
        method: String?,
        viewName: String?
    ) {
        if (url.isNullOrBlank()) {
            result.error(
                ErrorCode.MISSING_OR_INVALID_ARGUMENT.serialized,
                "Instana requires non-blank 'url'",
                null
            )
        } else if (method.isNullOrBlank()) {
            result.error(
                ErrorCode.MISSING_OR_INVALID_ARGUMENT.serialized,
                "Instana requires non-blank 'method'",
                null
            )
        } else {
            val marker = Instana.startCapture(url, viewName)
            val markerId = UUID.randomUUID().toString()
            markerInstanceMap[markerId] = marker
            // TODO remove markerMethodMap once the native client can receive 'method' in 'startCapture'
            markerMethodMap[markerId] = method
            result.success(markerId)
        }
    }

    fun finishCapture(
        result: MethodChannel.Result,
        markerId: String?,
        responseStatusCode: Int?,
        responseSizeEncodedBytes: Long?,
        responseSizeDecodedBytes: Long?,
        backendTraceId: String?,
        errorMessage: String?
    ) {
        if (markerId.isNullOrBlank()) {
            result.error(
                ErrorCode.MISSING_OR_INVALID_ARGUMENT.serialized,
                "Instana requires non-blank 'markerId'",
                null
            )
        } else {
            markerInstanceMap[markerId]?.finish(
                HTTPMarkerData(
                    requestMethod = markerMethodMap[markerId],
                    responseStatusCode = responseStatusCode,
                    responseSizeEncodedBytes = responseSizeEncodedBytes,
                    responseSizeDecodedBytes = responseSizeDecodedBytes,
                    backendTraceId = backendTraceId,
                    errorMessage = errorMessage
                )
            )
            markerInstanceMap.remove(markerId)
            markerMethodMap.remove(markerId)
            result.success(null)
        }
    }

    fun cancelCapture(result: MethodChannel.Result, markerId: String?) {
        if (markerId.isNullOrBlank()) {
            result.error(
                ErrorCode.MISSING_OR_INVALID_ARGUMENT.serialized,
                "Instana requires non-blank 'markerId'",
                null
            )
        } else {
            markerInstanceMap[markerId]?.cancel()
            result.success(null)
        }
    }
}
