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
package org.linphone.ui.main.calls.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import org.linphone.core.Address
import org.linphone.utils.LinphoneUtils

class SuggestionModel @WorkerThread constructor(
    val address: Address,
    private val onClicked: ((Address) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[Suggestion Model]"
    }

    val name = LinphoneUtils.getDisplayName(address)

    val initials = LinphoneUtils.getInitials(name)

    @UiThread
    fun onClicked() {
        onClicked?.invoke(address)
    }
}
