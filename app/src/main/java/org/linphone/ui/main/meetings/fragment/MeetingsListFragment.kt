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
package org.linphone.ui.main.meetings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.MeetingsListFragmentBinding
import org.linphone.ui.main.fragment.AbstractTopBarFragment
import org.linphone.ui.main.meetings.adapter.MeetingsListAdapter
import org.linphone.ui.main.meetings.viewmodel.MeetingsListViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.RecyclerViewHeaderDecoration

@UiThread
class MeetingsListFragment : AbstractTopBarFragment() {
    companion object {
        private const val TAG = "[Meetings List Fragment]"
    }

    private lateinit var binding: MeetingsListFragmentBinding

    private lateinit var listViewModel: MeetingsListViewModel

    private lateinit var adapter: MeetingsListAdapter

    override fun onDefaultAccountChanged() {
        Log.i(
            "$TAG Default account changed, updating avatar in top bar & re-computing meetings list"
        )
        listViewModel.applyFilter()
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (
            findNavController().currentDestination?.id == R.id.scheduleMeetingFragment ||
            findNavController().currentDestination?.id == R.id.meetingWaitingRoomFragment
        ) {
            // Holds fragment in place while new contact fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = MeetingsListAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MeetingsListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(this)[MeetingsListViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        binding.meetingsList.setHasFixedSize(true)
        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter, true)
        binding.meetingsList.addItemDecoration(headerItemDecoration)
        binding.meetingsList.layoutManager = LinearLayoutManager(requireContext())

        binding.setNewMeetingClicked {
            if (findNavController().currentDestination?.id == R.id.meetingsListFragment) {
                Log.i("$TAG Navigating to schedule meeting fragment")
                val action =
                    MeetingsListFragmentDirections.actionMeetingsListFragmentToScheduleMeetingFragment()
                findNavController().navigate(action)
            }
        }

        binding.setTodayClickListener {
            scrollToToday()
        }

        adapter.meetingClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                Log.i("$TAG Show conversation with ID [${model.id}]")
                val action = MeetingFragmentDirections.actionGlobalMeetingFragment(model.id)
                binding.meetingsNavContainer.findNavController().navigate(action)
            }
        }

        listViewModel.meetings.observe(viewLifecycleOwner) {
            val currentCount = adapter.itemCount
            val newCount = it.size
            adapter.submitList(it)
            Log.i("$TAG Meetings list ready with [$newCount] items")

            if (binding.meetingsList.adapter != adapter) {
                binding.meetingsList.adapter = adapter
            }

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
                sharedViewModel.isFirstFragmentReady = true

                if (currentCount < newCount) {
                    scrollToToday()
                }
            }
        }

        sharedViewModel.forceRefreshMeetingsListEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG We were asked to refresh the meetings list, doing it now")
                listViewModel.applyFilter()
            }
        }

        sharedViewModel.goToMeetingWaitingRoomEvent.observe(viewLifecycleOwner) {
            it.consume { uri ->
                if (findNavController().currentDestination?.id == R.id.meetingsListFragment) {
                    Log.i("$TAG Navigating to meeting waiting room fragment with URI [$uri]")
                    val action =
                        MeetingsListFragmentDirections.actionMeetingsListFragmentToMeetingWaitingRoomFragment(
                            uri
                        )
                    findNavController().navigate(action)
                }
            }
        }

        // TopBarFragment related

        setViewModelAndTitle(
            binding.topBar.search,
            listViewModel,
            getString(R.string.bottom_navigation_meetings_label)
        )

        initBottomNavBar(binding.bottomNavBar.root)

        initSlidingPane(binding.slidingPaneLayout)

        initNavigation(R.id.meetingsListFragment)
    }

    private fun scrollToToday() {
        Log.i("$TAG Scrolling to today's meeting (if any)")
        val todayMeeting = listViewModel.meetings.value.orEmpty().find {
            it.displayTodayIndicator.value == true
        }
        val index = listViewModel.meetings.value.orEmpty().indexOf(todayMeeting)
        (binding.meetingsList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
            index,
            AppUtils.getDimension(R.dimen.meeting_list_decoration_height).toInt()
        )
    }
}