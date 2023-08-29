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
package org.linphone.ui.main.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.utils.Event

class SharedMainViewModel @UiThread constructor() : ViewModel() {
    /* Sliding Pane & navigation related */

    val isSlidingPaneSlideable = MutableLiveData<Boolean>()

    val closeSlidingPaneEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val openSlidingPaneEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToConversationsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToCallsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val navigateToContactsEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    var currentlyDisplayedFragment = MutableLiveData<Int>()

    /* Top bar related */

    val searchFilter: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    /* Contacts related */

    val contactsListReadyToBeDisplayedEvent = MutableLiveData<Event<Boolean>>()

    val contactEditorReadyToBeDisplayedEvent = MutableLiveData<Event<Boolean>>()

    val showContactEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val showNewContactEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    var sipAddressToAddToNewContact: String = ""

    /* Call logs related */

    val callsListReadyToBeDisplayedEvent = MutableLiveData<Event<Boolean>>()

    val showCallLogEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val forceRefreshCallLogsListEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val resetMissedCallsCountEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }
}