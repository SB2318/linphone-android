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
package org.linphone.ui.main.calls.fragment

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.databinding.CallsListFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.calls.adapter.CallsListAdapter
import org.linphone.ui.main.calls.viewmodel.CallsListViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.Event
import org.linphone.utils.setKeyboardInsetListener

class CallsListFragment : GenericFragment() {

    private lateinit var binding: CallsListFragmentBinding

    private val listViewModel: CallsListViewModel by navGraphViewModels(
        R.id.callsListFragment
    )

    private lateinit var adapter: CallsListAdapter

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.newContactFragment) {
            // Holds fragment in place while new contact fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallsListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        postponeEnterTransition()

        binding.root.setKeyboardInsetListener { keyboardVisible ->
            val portraitOrientation = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            listViewModel.bottomNavBarVisible.value = !portraitOrientation || !keyboardVisible
        }

        adapter = CallsListAdapter(viewLifecycleOwner)
        binding.callsList.setHasFixedSize(true)
        binding.callsList.adapter = adapter

        adapter.callLogLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = CallsListMenuDialogFragment(model.callLog) {
                    adapter.resetSelection()
                }
                modalBottomSheet.show(parentFragmentManager, CallsListMenuDialogFragment.TAG)
            }
        }

        adapter.callLogClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                sharedViewModel.showCallLogEvent.value = Event(model.id ?: "")
            }
        }

        adapter.callLogCallBackClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                coreContext.postOnCoreThread {
                    coreContext.startCall(model.address)
                }
            }
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.callsList.layoutManager = layoutManager

        binding.setOnAvatarClickListener {
            (requireActivity() as MainActivity).toggleDrawerMenu()
        }

        listViewModel.callLogs.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            startPostponedEnterTransition()
        }
    }
}
