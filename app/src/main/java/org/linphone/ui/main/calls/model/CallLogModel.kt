package org.linphone.ui.main.calls.model

import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call.Dir
import org.linphone.core.CallLog
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.TimestampUtils

class CallLogModel(val callLog: CallLog) {
    val id = callLog.callId ?: callLog.refKey

    val address = if (callLog.dir == Dir.Outgoing) callLog.remoteAddress else callLog.fromAddress

    val avatarModel: ContactAvatarModel

    val isOutgoing = MutableLiveData<Boolean>()

    val dateTime = MutableLiveData<String>()

    init {
        // Core thread
        isOutgoing.postValue(callLog.dir == Dir.Outgoing)

        dateTime.postValue(
            TimestampUtils.toString(callLog.startDate, shortDate = false, hideYear = false)
        )

        val friend = coreContext.core.findFriend(address)
        if (friend != null) {
            avatarModel = ContactAvatarModel(friend)
        } else {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = address
            avatarModel = ContactAvatarModel(fakeFriend)
        }
    }
}
