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
package org.linphone.ui.main.meetings.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ConferenceScheduler
import org.linphone.core.ConferenceSchedulerListenerStub
import org.linphone.core.Factory
import org.linphone.core.Participant
import org.linphone.core.ParticipantInfo
import org.linphone.core.tools.Log
import org.linphone.ui.main.model.SelectedAddressModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.TimestampUtils

class ScheduleMeetingViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Schedule Meeting ViewModel]"
    }

    val isBroadcastSelected = MutableLiveData<Boolean>()

    val showBroadcastHelp = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val description = MutableLiveData<String>()

    val allDayMeeting = MutableLiveData<Boolean>()

    val fromDate = MutableLiveData<String>()

    val toDate = MutableLiveData<String>()

    val fromTime = MutableLiveData<String>()

    val toTime = MutableLiveData<String>()

    val timezone = MutableLiveData<String>()

    val sendInvitations = MutableLiveData<Boolean>()

    val participants = MutableLiveData<ArrayList<SelectedAddressModel>>()

    val operationInProgress = MutableLiveData<Boolean>()

    val conferenceCreatedEvent = MutableLiveData<Event<Boolean>>()

    private var startTimestamp = 0L
    private var endTimestamp = 0L

    internal var startHour = 0
    internal var startMinutes = 0

    internal var endHour = 0
    internal var endMinutes = 0

    private lateinit var conferenceScheduler: ConferenceScheduler

    private val conferenceSchedulerListener = object : ConferenceSchedulerListenerStub() {
        @WorkerThread
        override fun onStateChanged(
            conferenceScheduler: ConferenceScheduler,
            state: ConferenceScheduler.State?
        ) {
            Log.i("$TAG Conference state changed [$state]")
            when (state) {
                ConferenceScheduler.State.Error -> {
                    operationInProgress.postValue(false)
                    // TODO: show error toast
                }
                ConferenceScheduler.State.Ready -> {
                    val conferenceAddress = conferenceScheduler.info?.uri
                    Log.i(
                        "$TAG Conference info created, address will be ${conferenceAddress?.asStringUriOnly()}"
                    )
                    if (sendInvitations.value == true) {
                        Log.i("$TAG User asked for invitations to be sent, let's do it")
                        val chatRoomParams = coreContext.core.createDefaultChatRoomParams()
                        chatRoomParams.isGroupEnabled = false
                        chatRoomParams.backend = ChatRoom.Backend.FlexisipChat
                        chatRoomParams.isEncryptionEnabled = true
                        chatRoomParams.subject = "Meeting invitation" // Won't be used
                        conferenceScheduler.sendInvitations(chatRoomParams)
                    } else {
                        Log.i("$TAG User didn't asked for invitations to be sent")
                        operationInProgress.postValue(false)
                        conferenceCreatedEvent.postValue(Event(true))
                    }
                }
                else -> {
                }
            }
        }

        @WorkerThread
        override fun onInvitationsSent(
            conferenceScheduler: ConferenceScheduler,
            failedInvitations: Array<out Address>?
        ) {
            when (val failedCount = failedInvitations?.size) {
                0 -> {
                    Log.i("$TAG All invitations have been sent")
                }
                participants.value.orEmpty().size -> {
                    Log.e("$TAG No invitation sent!")
                    // TODO: show error toast
                }
                else -> {
                    Log.w("$TAG [$failedCount] invitations couldn't have been sent for:")
                    for (failed in failedInvitations.orEmpty()) {
                        Log.w(failed.asStringUriOnly())
                    }
                    // TODO: show error toast
                }
            }

            operationInProgress.postValue(false)
            conferenceCreatedEvent.postValue(Event(true))
        }
    }

    init {
        isBroadcastSelected.value = false
        showBroadcastHelp.value = false
        allDayMeeting.value = false
        sendInvitations.value = true

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.HOUR, 1)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val nextFullHour = cal.timeInMillis
        startHour = cal.get(Calendar.HOUR_OF_DAY)
        startMinutes = 0

        cal.add(Calendar.HOUR, 1)
        val twoHoursLater = cal.timeInMillis
        endHour = cal.get(Calendar.HOUR_OF_DAY)
        endMinutes = 0

        startTimestamp = nextFullHour
        endTimestamp = twoHoursLater

        Log.i(
            "$TAG Default start time is [$startHour:$startMinutes], default end time is [$startHour:$startMinutes]"
        )
        Log.i("$TAG Default start date is [$startTimestamp], default end date is [$endTimestamp]")

        computeDateLabels()
        computeTimeLabels()

        timezone.value = AppUtils.getFormattedString(
            R.string.meeting_schedule_timezone_title,
            TimeZone.getDefault().displayName.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(
                        Locale.getDefault()
                    )
                } else {
                    it.toString()
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            if (::conferenceScheduler.isInitialized) {
                conferenceScheduler.removeListener(conferenceSchedulerListener)
            }
        }
    }

    @UiThread
    fun getCurrentlySelectedStartDate(): Long {
        return startTimestamp
    }

    @UiThread
    fun setStartDate(timestamp: Long) {
        startTimestamp = timestamp
        endTimestamp = timestamp
        computeDateLabels()
    }

    @UiThread
    fun getCurrentlySelectedEndDate(): Long {
        return endTimestamp
    }

    @UiThread
    fun setEndDate(timestamp: Long) {
        endTimestamp = timestamp
        computeDateLabels()
    }

    @UiThread
    fun setStartTime(hours: Int, minutes: Int) {
        startHour = hours
        startMinutes = minutes

        endHour = hours + 1
        endMinutes = minutes

        computeTimeLabels()
    }

    @UiThread
    fun setEndTime(hours: Int, minutes: Int) {
        endHour = hours
        endMinutes = minutes

        computeTimeLabels()
    }

    @UiThread
    fun selectMeeting() {
        isBroadcastSelected.value = false
        showBroadcastHelp.value = false
    }

    @UiThread
    fun selectBroadcast() {
        isBroadcastSelected.value = true
        showBroadcastHelp.value = true
    }

    @UiThread
    fun closeBroadcastHelp() {
        showBroadcastHelp.value = false
    }

    @UiThread
    fun addParticipants(toAdd: ArrayList<String>) {
        coreContext.postOnCoreThread {
            val list = arrayListOf<SelectedAddressModel>()
            for (participant in toAdd) {
                val address = Factory.instance().createAddress(participant)
                if (address == null) {
                    Log.e("$TAG Failed to parse [$participant] as address!")
                } else {
                    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                        address
                    )
                    val model = SelectedAddressModel(address, avatarModel) {
                        // onRemoveFromSelection
                    }
                    list.add(model)
                }
            }
            participants.postValue(list)
        }
    }

    @UiThread
    fun schedule() {
        coreContext.postOnCoreThread { core ->
            Log.i(
                "$TAG Scheduling ${if (isBroadcastSelected.value == true) "broadcast" else "meeting"}"
            )
            operationInProgress.postValue(true)

            val localAccount = core.defaultAccount
            val localAddress = localAccount?.params?.identityAddress

            val conferenceInfo = Factory.instance().createConferenceInfo()
            conferenceInfo.organizer = localAddress
            conferenceInfo.subject = subject.value
            conferenceInfo.description = description.value

            val startTime = startTimestamp / 1000 // Linphone expects timestamp in seconds
            conferenceInfo.dateTime = startTime
            val duration = ((endTimestamp - startTimestamp) / 1000).toInt() // Linphone expects duration in seconds
            conferenceInfo.duration = duration

            val participantsList = participants.value.orEmpty()
            val participantsInfoList = arrayListOf<ParticipantInfo>()
            for (participant in participantsList) {
                val info = Factory.instance().createParticipantInfo(participant.address)
                if (info == null) {
                    Log.e(
                        "$TAG Failed to create Participant Info from address [${participant.address.asStringUriOnly()}]"
                    )
                    continue
                }

                // For meetings, all participants must have Speaker role
                info.role = Participant.Role.Speaker
                participantsInfoList.add(info)
            }

            val participantsInfoArray = arrayOfNulls<ParticipantInfo>(participantsInfoList.size)
            participantsInfoList.toArray(participantsInfoArray)
            conferenceInfo.setParticipantInfos(participantsInfoArray)

            if (!::conferenceScheduler.isInitialized) {
                conferenceScheduler = core.createConferenceScheduler()
                conferenceScheduler.addListener(conferenceSchedulerListener)
            }

            conferenceScheduler.account = localAccount
            // Will trigger the conference creation/update automatically
            conferenceScheduler.info = conferenceInfo
        }
    }

    @UiThread
    private fun computeDateLabels() {
        val start = TimestampUtils.toString(
            startTimestamp,
            onlyDate = true,
            timestampInSecs = false,
            shortDate = false,
            hideYear = false
        )
        fromDate.value = start
        Log.i("$TAG Computed start date for timestamp [$startTimestamp] is [$start]")

        val end = TimestampUtils.toString(
            endTimestamp,
            onlyDate = true,
            timestampInSecs = false,
            shortDate = false,
            hideYear = false
        )
        toDate.value = end
        Log.i("$TAG Computed end date for timestamp [$endTimestamp] is [$end]")
    }

    @UiThread
    private fun computeTimeLabels() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startTimestamp
        cal.set(Calendar.HOUR_OF_DAY, startHour)
        cal.set(Calendar.MINUTE, startMinutes)
        val start = TimestampUtils.timeToString(cal.timeInMillis, timestampInSecs = false)
        Log.i("$TAG Computed start time for timestamp [$startTimestamp] is [$start]")
        fromTime.value = start

        cal.timeInMillis = endTimestamp
        cal.set(Calendar.HOUR_OF_DAY, endHour)
        cal.set(Calendar.MINUTE, endMinutes)
        val end = TimestampUtils.timeToString(cal.timeInMillis, timestampInSecs = false)
        Log.i("$TAG Computed end time for timestamp [$endTimestamp] is [$end]")
        toTime.value = end
    }
}