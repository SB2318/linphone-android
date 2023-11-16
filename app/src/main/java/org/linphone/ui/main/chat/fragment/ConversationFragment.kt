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
package org.linphone.ui.main.chat.fragment

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.ChatMessage
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatBubbleLongPressMenuBinding
import org.linphone.databinding.ChatConversationFragmentBinding
import org.linphone.ui.main.chat.adapter.ChatMessageBottomSheetAdapter
import org.linphone.ui.main.chat.adapter.ConversationEventAdapter
import org.linphone.ui.main.chat.model.ChatMessageDeliveryModel
import org.linphone.ui.main.chat.model.ChatMessageModel
import org.linphone.ui.main.chat.model.ChatMessageReactionsModel
import org.linphone.ui.main.chat.view.RichEditText
import org.linphone.ui.main.chat.viewmodel.ConversationViewModel
import org.linphone.ui.main.chat.viewmodel.ConversationViewModel.Companion.SCROLLING_POSITION_NOT_SET
import org.linphone.ui.main.chat.viewmodel.SendMessageInConversationViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.addCharacterAtPosition
import org.linphone.utils.hideKeyboard
import org.linphone.utils.setKeyboardInsetListener
import org.linphone.utils.showKeyboard

@UiThread
class ConversationFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Conversation Fragment]"
    }

    private lateinit var binding: ChatConversationFragmentBinding

    private lateinit var viewModel: ConversationViewModel

    private lateinit var sendMessageViewModel: SendMessageInConversationViewModel

    private lateinit var adapter: ConversationEventAdapter

    private lateinit var bottomSheetAdapter: ChatMessageBottomSheetAdapter

    private val args: ConversationFragmentArgs by navArgs()

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { list ->
        if (!list.isNullOrEmpty()) {
            for (uri in list) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val path = FileUtils.getFilePath(requireContext(), uri, false)
                        Log.i("$TAG Picked file [$uri] matching path is [$path]")
                        if (path != null) {
                            withContext(Dispatchers.Main) {
                                sendMessageViewModel.addAttachment(path)
                            }
                        }
                    }
                }
            }
        } else {
            Log.w("$TAG No file picked")
        }
    }

    private val dataObserver = object : AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (positionStart == 0 && adapter.itemCount == itemCount) {
                // First time we fill the list with messages
                Log.i("$TAG [$itemCount] events have been loaded")
                binding.eventsList.scrollToPosition(adapter.itemCount - 1)
            } else {
                Log.i("$TAG [$itemCount] new events have been loaded, scrolling to bottom")
                binding.eventsList.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private val textObserver = object : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun afterTextChanged(p0: Editable?) {
            sendMessageViewModel.isParticipantsListOpen.value = false

            val split = p0.toString().split(" ")
            for (part in split) {
                if (part == "@") {
                    Log.i("$TAG '@' found, opening participants list")
                    sendMessageViewModel.isParticipantsListOpen.value = true
                }
            }
        }
    }

    private var bottomSheetDeliveryModel: ChatMessageDeliveryModel? = null

    private var bottomSheetReactionsModel: ChatMessageReactionsModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConversationEventAdapter()
        bottomSheetAdapter = ChatMessageBottomSheetAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatConversationFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        coreContext.notificationsManager.resetCurrentlyDisplayedChatRoomId()
        sharedViewModel.closeSlidingPaneEvent.value = Event(true)
        coreContext.notificationsManager.resetCurrentlyDisplayedChatRoomId()
        // If not done, when going back to ConversationsListFragment this fragment will be created again
        return findNavController().popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // This fragment is displayed in a SlidingPane "child" area
        isSlidingPaneChild = true

        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationViewModel::class.java]
        sendMessageViewModel = ViewModelProvider(this)[SendMessageInConversationViewModel::class.java]

        binding.viewModel = viewModel
        binding.sendMessageViewModel = sendMessageViewModel

        binding.setBackClickListener {
            goBack()
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        adapter.viewLifecycleOwner = viewLifecycleOwner
        binding.eventsList.setHasFixedSize(true)
        binding.eventsList.layoutManager = LinearLayoutManager(requireContext())

        val localSipUri = args.localSipUri
        val remoteSipUri = args.remoteSipUri
        Log.i(
            "$TAG Looking up for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
        )
        val chatRoom = sharedViewModel.displayedChatRoom
        viewModel.findChatRoom(chatRoom, localSipUri, remoteSipUri)

        viewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (!found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        Log.e("$TAG Failed to find chat room, going back")
                        goBack()
                        // TODO: show toast
                    }
                } else {
                    sendMessageViewModel.configureChatRoom(viewModel.chatRoom)
                }
            }
        }

        viewModel.events.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            Log.i("$TAG Events (messages) list updated with [${items.size}] items")

            if (binding.eventsList.adapter != adapter) {
                binding.eventsList.adapter = adapter
            }

            (view.parent as? ViewGroup)?.doOnPreDraw {
                sharedViewModel.openSlidingPaneEvent.value = Event(true)
            }
        }

        binding.messageBottomSheet.bottomSheetList.setHasFixedSize(true)
        val bottomSheetLayoutManager = LinearLayoutManager(requireContext())
        binding.messageBottomSheet.bottomSheetList.layoutManager = bottomSheetLayoutManager

        val emojisBottomSheetBehavior = BottomSheetBehavior.from(binding.sendArea.root)
        emojisBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        emojisBottomSheetBehavior.isDraggable = false // To allow scrolling through the emojis

        adapter.chatMessageLongPressEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showChatMessageLongPressMenu(model)
            }
        }

        adapter.showDeliveryForChatMessageModelEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showDeliveryBottomSheetDialog(model, showDelivery = true)
            }
        }

        adapter.showReactionForChatMessageModelEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showDeliveryBottomSheetDialog(model, showReactions = true)
            }
        }

        adapter.scrollToRepliedMessageEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val repliedMessageId = model.replyToMessageId
                if (repliedMessageId.isNullOrEmpty()) {
                    Log.w("$TAG Chat message [${model.id}] doesn't have a reply to ID!")
                } else {
                    val originalMessage = adapter.currentList.find {
                        !it.isEvent && (it.model as ChatMessageModel).id == repliedMessageId
                    }
                    if (originalMessage != null) {
                        val position = adapter.currentList.indexOf(originalMessage)
                        Log.i("$TAG Scrolling to position [$position]")
                        binding.eventsList.scrollToPosition(position)
                    } else {
                        Log.w("$TAG Failed to find matching message in adapter's items!")
                    }
                }
            }
        }

        binding.setOpenFilePickerClickListener {
            Log.i("$TAG Opening media picker")
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }

        binding.setGoToInfoClickListener {
            if (findNavController().currentDestination?.id == R.id.conversationFragment) {
                val action =
                    ConversationFragmentDirections.actionConversationFragmentToConversationInfoFragment(
                        localSipUri,
                        remoteSipUri
                    )
                findNavController().navigate(action)
            }
        }

        sendMessageViewModel.emojiToAddEvent.observe(viewLifecycleOwner) {
            it.consume { emoji ->
                binding.sendArea.messageToSend.addCharacterAtPosition(emoji)
            }
        }

        sendMessageViewModel.participantUsernameToAddEvent.observe(viewLifecycleOwner) {
            it.consume { username ->
                Log.i("$TAG Adding username [$username] after '@'")
                // Also add a space for convenience
                binding.sendArea.messageToSend.addCharacterAtPosition("$username ")
            }
        }

        sendMessageViewModel.requestKeyboardHidingEvent.observe(viewLifecycleOwner) {
            it.consume {
                binding.search.hideKeyboard()
            }
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            viewModel.applyFilter(filter.trim())
        }

        viewModel.focusSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    // To automatically open keyboard
                    binding.search.showKeyboard()
                } else {
                    binding.search.hideKeyboard()
                }
            }
        }

        viewModel.fileToDisplayEvent.observe(viewLifecycleOwner) {
            it.consume { file ->
                if (findNavController().currentDestination?.id == R.id.conversationFragment) {
                    Log.i("$TAG User clicked on file [$file], let's display it in file viewer")
                    val action =
                        ConversationFragmentDirections.actionConversationFragmentToFileViewerFragment(
                            file
                        )
                    findNavController().navigate(action)
                }
            }
        }

        viewModel.isGroup.observe(viewLifecycleOwner) { group ->
            if (group) {
                Log.i("$TAG Adding text observer to chat message sending area")
                binding.sendArea.messageToSend.addTextChangedListener(textObserver)
            }
        }

        viewModel.conferenceToJoinEvent.observe(viewLifecycleOwner) {
            it.consume { conferenceUri ->
                Log.i("$TAG Requesting to go to waiting room for conference URI [$conferenceUri]")
                sharedViewModel.goToMeetingWaitingRoomEvent.value = Event(conferenceUri)
            }
        }

        viewModel.openWebBrowserEvent.observe(viewLifecycleOwner) {
            it.consume { url ->
                Log.i("$TAG Requesting to open web browser on page [$url]")
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(browserIntent)
                } catch (ise: IllegalStateException) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                    )
                }
            }
        }

        sharedViewModel.richContentUri.observe(
            viewLifecycleOwner
        ) {
            it.consume { uri ->
                Log.i("$TAG Found rich content URI: $uri")
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val path = FileUtils.getFilePath(requireContext(), uri, false)
                        Log.i("$TAG Rich content URI [$uri] matching path is [$path]")
                        if (path != null) {
                            withContext(Dispatchers.Main) {
                                sendMessageViewModel.addAttachment(path)
                            }
                        }
                    }
                }
            }
        }

        sharedViewModel.textToShareFromIntent.observe(viewLifecycleOwner) { text ->
            if (text.isNotEmpty()) {
                Log.i("$TAG Found text to share from intent")
                sendMessageViewModel.textToSend.value = text

                sharedViewModel.textToShareFromIntent.value = ""
            }
        }

        sharedViewModel.filesToShareFromIntent.observe(viewLifecycleOwner) { files ->
            if (files.isNotEmpty()) {
                Log.i("$TAG Found [${files.size}] files to share from intent")
                for (path in files) {
                    sendMessageViewModel.addAttachment(path)
                }

                sharedViewModel.filesToShareFromIntent.value = arrayListOf()
            }
        }

        binding.sendArea.messageToSend.setControlEnterListener(object :
                RichEditText.RichEditTextSendListener {
                override fun onControlEnterPressedAndReleased() {
                    Log.i("$TAG Detected left control + enter key presses, sending message")
                    sendMessageViewModel.sendMessage()
                }
            })

        binding.root.setKeyboardInsetListener { keyboardVisible ->
            if (keyboardVisible) {
                sendMessageViewModel.isEmojiPickerOpen.value = false
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val id = LinphoneUtils.getChatRoomId(args.localSipUri, args.remoteSipUri)
        Log.i("$TAG Asking notifications manager not to notify chat messages for chat room [$id]")
        coreContext.notificationsManager.setCurrentlyDisplayedChatRoomId(id)

        if (viewModel.scrollingPosition != SCROLLING_POSITION_NOT_SET) {
            binding.eventsList.scrollToPosition(viewModel.scrollingPosition)
        } else {
            binding.eventsList.scrollToPosition(adapter.itemCount - 1)
        }

        try {
            adapter.registerAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to register data observer to adapter: $e")
        }
    }

    override fun onPause() {
        coreContext.postOnCoreThread {
            bottomSheetReactionsModel?.destroy()
            bottomSheetDeliveryModel?.destroy()
        }

        if (viewModel.isGroup.value == true) {
            binding.sendArea.messageToSend.removeTextChangedListener(textObserver)
        }

        try {
            adapter.unregisterAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to unregister data observer to adapter: $e")
        }
        coreContext.notificationsManager.resetCurrentlyDisplayedChatRoomId()

        val layoutManager = binding.eventsList.layoutManager as LinearLayoutManager
        viewModel.scrollingPosition = layoutManager.findFirstVisibleItemPosition()

        super.onPause()
    }

    private fun showChatMessageLongPressMenu(chatMessageModel: ChatMessageModel) {
        // TODO: handle backward compat for blurring
        val blurEffect = RenderEffect.createBlurEffect(16F, 16F, Shader.TileMode.MIRROR)
        binding.root.setRenderEffect(blurEffect)

        val dialog = Dialog(requireContext(), R.style.Theme_LinphoneDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val layout: ChatBubbleLongPressMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.chat_bubble_long_press_menu,
            null,
            false
        )

        layout.root.setOnClickListener {
            dialog.dismiss()
        }

        layout.setDeleteClickListener {
            Log.i("$TAG Deleting message")
            viewModel.deleteChatMessage(chatMessageModel)
            dialog.dismiss()
        }

        layout.setCopyClickListener {
            Log.i("$TAG Copying message text into clipboard")
            val text = chatMessageModel.text.value?.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val label = "Message"
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))

            dialog.dismiss()
        }

        layout.setPickEmojiClickListener {
            Log.i("$TAG Opening emoji-picker for reaction")
            val emojiSheetBehavior = BottomSheetBehavior.from(layout.emojiPickerBottomSheet.root)
            emojiSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        layout.setReplyClickListener {
            Log.i("$TAG Updating sending area to reply to selected message")
            sendMessageViewModel.replyToMessage(chatMessageModel)
            dialog.dismiss()
        }

        layout.model = chatMessageModel
        chatMessageModel.dismissLongPressMenuEvent.observe(viewLifecycleOwner) {
            dialog.dismiss()
        }

        dialog.setContentView(layout.root)
        dialog.setOnDismissListener {
            binding.root.setRenderEffect(null)
        }

        dialog.window
            ?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        val d: Drawable = ColorDrawable(
            AppUtils.getColor(R.color.gray_300)
        )
        d.alpha = 102
        dialog.window?.setBackgroundDrawable(d)
        dialog.show()
    }

    @UiThread
    private fun showDeliveryBottomSheetDialog(
        chatMessageModel: ChatMessageModel,
        showDelivery: Boolean = false,
        showReactions: Boolean = false
    ) {
        val bottomSheetBehavior = BottomSheetBehavior.from(binding.messageBottomSheet.root)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        binding.messageBottomSheet.setHandleClickedListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        if (binding.messageBottomSheet.bottomSheetList.adapter != bottomSheetAdapter) {
            binding.messageBottomSheet.bottomSheetList.adapter = bottomSheetAdapter
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Wait for previous bottom sheet to go away
                delay(200)

                withContext(Dispatchers.Main) {
                    if (showDelivery) {
                        prepareBottomSheetForDeliveryStatus(chatMessageModel)
                    } else if (showReactions) {
                        prepareBottomSheetForReactions(chatMessageModel)
                    }

                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }

    @UiThread
    private fun prepareBottomSheetForDeliveryStatus(chatMessageModel: ChatMessageModel) {
        coreContext.postOnCoreThread {
            bottomSheetDeliveryModel?.destroy()

            val model = ChatMessageDeliveryModel(chatMessageModel.chatMessage) { deliveryModel ->
                coreContext.postOnMainThread {
                    displayDeliveryStatuses(deliveryModel)
                }
            }
            bottomSheetDeliveryModel = model
        }
    }

    @UiThread
    private fun prepareBottomSheetForReactions(chatMessageModel: ChatMessageModel) {
        coreContext.postOnCoreThread {
            bottomSheetReactionsModel?.destroy()

            val model = ChatMessageReactionsModel(chatMessageModel.chatMessage) { reactionsModel ->
                coreContext.postOnMainThread {
                    if (reactionsModel.allReactions.isEmpty()) {
                        Log.i("$TAG No reaction to display, closing bottom sheet")
                        val bottomSheetBehavior = BottomSheetBehavior.from(
                            binding.messageBottomSheet.root
                        )
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    } else {
                        displayReactions(reactionsModel)
                    }
                }
            }
            bottomSheetReactionsModel = model
        }
    }

    @UiThread
    private fun displayDeliveryStatuses(model: ChatMessageDeliveryModel) {
        val tabs = binding.messageBottomSheet.tabs
        tabs.removeAllTabs()
        tabs.addTab(
            tabs.newTab().setText(model.readLabel.value).setId(
                ChatMessage.State.Displayed.toInt()
            )
        )
        tabs.addTab(
            tabs.newTab().setText(
                model.receivedLabel.value
            ).setId(
                ChatMessage.State.DeliveredToUser.toInt()
            )
        )
        tabs.addTab(
            tabs.newTab().setText(model.sentLabel.value).setId(
                ChatMessage.State.Delivered.toInt()
            )
        )
        tabs.addTab(
            tabs.newTab().setText(
                model.errorLabel.value
            ).setId(
                ChatMessage.State.NotDelivered.toInt()
            )
        )

        tabs.setOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val state = tab?.id ?: ChatMessage.State.Displayed.toInt()
                bottomSheetAdapter.submitList(
                    model.computeListForState(ChatMessage.State.fromInt(state))
                )
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        val initialList = model.displayedModels
        bottomSheetAdapter.submitList(initialList)
        Log.i("$TAG Submitted [${initialList.size}] items for default delivery status list")
    }

    @UiThread
    private fun displayReactions(model: ChatMessageReactionsModel) {
        val totalCount = model.allReactions.size
        val label = getString(R.string.message_reactions_info_all_title, totalCount.toString())

        val tabs = binding.messageBottomSheet.tabs
        tabs.removeAllTabs()
        tabs.addTab(
            tabs.newTab().setText(label).setId(0).setTag("")
        )

        var index = 1
        for (reaction in model.differentReactions.value.orEmpty()) {
            val count = model.reactionsMap[reaction]
            val tabLabel = getString(
                R.string.message_reactions_info_emoji_title,
                reaction,
                count.toString()
            )
            tabs.addTab(
                tabs.newTab().setText(tabLabel).setId(index).setTag(reaction)
            )
            index += 1
        }

        tabs.setOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val filter = tab?.tag.toString()
                if (filter.isEmpty()) {
                    bottomSheetAdapter.submitList(model.allReactions)
                } else {
                    bottomSheetAdapter.submitList(model.filterReactions(filter))
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        val initialList = model.allReactions
        bottomSheetAdapter.submitList(initialList)
        Log.i("$TAG Submitted [${initialList.size}] items for default reactions list")
    }
}