/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.help.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.linphone.BuildConfig
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.VersionUpdateCheckResult
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class HelpViewModel @UiThread constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Help ViewModel]"
    }

    val version = MutableLiveData<String>()

    val appVersion = MutableLiveData<String>()

    val sdkVersion = MutableLiveData<String>()

    val checkUpdateAvailable = MutableLiveData<Boolean>()

    val uploadLogsAvailable = MutableLiveData<Boolean>()

    val logsUploadInProgress = MutableLiveData<Boolean>()

    val newVersionAvailableEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData<Event<Pair<String, String>>>()
    }

    val versionUpToDateEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val errorEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val debugLogsCleanedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val uploadDebugLogsFinishedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val uploadDebugLogsErrorEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showConfigFileEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onVersionUpdateCheckResultReceived(
            core: Core,
            result: VersionUpdateCheckResult,
            version: String?,
            url: String?
        ) {
            when (result) {
                VersionUpdateCheckResult.NewVersionAvailable -> {
                    Log.i("$TAG Update available, version [$version], url [$url]")
                    if (!version.isNullOrEmpty() && !url.isNullOrEmpty()) {
                        newVersionAvailableEvent.postValue(Event(Pair(version, url)))
                    }
                }
                VersionUpdateCheckResult.UpToDate -> {
                    Log.i("$TAG This version is up-to-date")
                    versionUpToDateEvent.postValue(Event(true))
                }
                else -> {
                    Log.e("$TAG Can't check for update, an error happened [$result]")
                    errorEvent.postValue(Event(true))
                }
            }
        }

        @WorkerThread
        override fun onLogCollectionUploadStateChanged(
            core: Core,
            state: Core.LogCollectionUploadState,
            info: String
        ) {
            Log.i("$TAG Logs upload state changed [$state]")
            if (state == Core.LogCollectionUploadState.Delivered) {
                logsUploadInProgress.postValue(false)
                uploadDebugLogsFinishedEvent.postValue(Event(info))
            } else if (state == Core.LogCollectionUploadState.NotDelivered) {
                logsUploadInProgress.postValue(false)
                uploadDebugLogsErrorEvent.postValue(Event(true))
            }
        }
    }

    init {
        val currentVersion = BuildConfig.VERSION_NAME
        version.value = currentVersion
        appVersion.value = "${AppUtils.getString(R.string.linphone_app_version)} (${AppUtils.getString(
            R.string.linphone_app_branch
        )})"
        sdkVersion.value = coreContext.sdkVersion
        logsUploadInProgress.value = false

        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            val checkUpdateServerUrl = core.config.getString("misc", "version_check_url_root", "")
            checkUpdateAvailable.postValue(!checkUpdateServerUrl.isNullOrEmpty())
            uploadLogsAvailable.postValue(!core.logCollectionUploadServerUrl.isNullOrEmpty())
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun cleanLogs() {
        coreContext.postOnCoreThread { core ->
            core.resetLogCollection()
            Log.i("$TAG Debug logs have been cleaned")
            debugLogsCleanedEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun shareLogs() {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Uploading debug logs for sharing")
            logsUploadInProgress.postValue(true)
            core.uploadLogCollection()
        }
    }

    @UiThread
    fun checkForUpdate() {
        val currentVersion = version.value.orEmpty()
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Checking for update using current version [$currentVersion]")
            core.checkForUpdate(currentVersion)
        }
    }

    @UiThread
    fun showConfigFile() {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Dumping & displaying Core's config")
            val config = core.config.dump()
            val file = FileUtils.getFileStorageCacheDir(
                "linphonerc.txt",
                overrideExisting = true
            )
            viewModelScope.launch {
                if (FileUtils.dumpStringToFile(config, file)) {
                    Log.i("$TAG .linphonerc string saved as file in cache folder")
                    showConfigFileEvent.postValue(Event(file.absolutePath))
                } else {
                    Log.e("$TAG Failed to save .linphonerc string as file in cache folder")
                }
            }
        }
    }
}
