package com.microsoft.notes.richtext.editor.styled

import android.Manifest
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.microsoft.notes.models.Color
import com.microsoft.notes.models.Media
import com.microsoft.notes.models.Note
import com.microsoft.notes.models.getFontColor
import com.microsoft.notes.noteslib.NotesLibrary
import com.microsoft.notes.noteslib.NotesThemeOverride
import com.microsoft.notes.noteslib.R
import com.microsoft.notes.richtext.editor.EditTextFocusCallback
import com.microsoft.notes.richtext.editor.NotesEditText
import com.microsoft.notes.richtext.editor.NotesEditTextCallback
import com.microsoft.notes.richtext.editor.RibbonUpdateCallback
import com.microsoft.notes.richtext.editor.extensions.increaseRevision
import com.microsoft.notes.richtext.editor.extensions.toLighterColorResource
import com.microsoft.notes.richtext.editor.extensions.toSNCardColorNightResource
import com.microsoft.notes.richtext.editor.styled.gallery.ITEMS_IN_A_ROW
import com.microsoft.notes.richtext.editor.styled.gallery.NestedRecyclerView
import com.microsoft.notes.richtext.editor.styled.gallery.NoteGalleryAdapter
import com.microsoft.notes.ui.feed.NoteContextComponent
import com.microsoft.notes.ui.note.ink.NotesEditInkCallback
import com.microsoft.notes.ui.note.reminder.ReminderLabelComponent
import com.microsoft.notes.ui.theme.ThemedAppCompatImageButton
import com.microsoft.notes.ui.theme.ThemedFrameLayout
import com.microsoft.notes.utils.logging.EventMarkers
import com.microsoft.notes.utils.logging.FormattingStyleType
import com.microsoft.notes.utils.logging.FormattingToggleSource
import com.microsoft.notes.utils.logging.HostTelemetryKeys
import com.microsoft.notes.utils.logging.ImageTrigger
import com.microsoft.notes.utils.logging.Mode
import com.microsoft.notes.utils.logging.NoteContentActionType
import com.microsoft.notes.utils.logging.NotesSDKTelemetryKeys
import com.microsoft.notes.utils.utils.Constants
import com.microsoft.notes.utils.utils.Constants.mp3Base64
import com.microsoft.notes.utils.utils.EventTimer
import com.microsoft.notes.utils.utils.isContinuousClicking
import kotlinx.android.synthetic.main.sn_note_layout_audio_controls.view.img_menu
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit


@Suppress("TooManyFunctions", "LargeClass")
class NoteStyledView(context: Context, attrs: AttributeSet?) :
    ThemedFrameLayout(context, attrs),
    RibbonUpdateCallback,
    EditTextFocusCallback,
    NoteGalleryAdapter.Callback,
    ReadOnlyStyledView {

    override var telemetryCallback: ReadOnlyStyledView.RecordTelemetryCallback? = null
    override var imageCallbacks: ReadOnlyStyledView.ImageCallbacks? = null
    override var ribbonCallbacks: ReadOnlyStyledView.RibbonCallbacks? = null

    private var sourceNote: Note? = null
    private var imageCaptureEnabled: Boolean = false
    private var microphoneButtonEnabled: Boolean = false
    private var isRestrictedCanvas: Boolean = NotesLibrary.getInstance().isRestricted()
    private var editMode = false
    private var boldState = false
    private var italicsState = false
    private var underlineState = false
    private var strikethroughState = false
    private var bulletedListState = false
    private val editSessionTimer = EventTimer()
    private var hasNewMedia = false

    private val noteGalleryAdapter = NoteGalleryAdapter()

    var themeOverride: NotesThemeOverride.StickyNoteCanvasThemeOverride? = null
    var microPhoneCallbacks: MicroPhoneCallbacks? = null

    private var noteBodyEditText: NotesEditText? = null
    private var editNoteFrameLayout: FrameLayout? = null
    private var noteContainer: RelativeLayout? = null
    private var timeStamp: ConstraintLayout? = null
    private var emailInfo: TextView? = null
    private var timestampText: TextView? = null
    private var cameraButtonTimestamp: ImageButton? = null
    private var microphoneButtonTimestamp: ImageButton? = null
    private var editNoteScrollView: NestedScrollView? = null
    private var noteGalleryRecyclerView: NestedRecyclerView? = null
    private var cameraButtonRibbon: ThemedAppCompatImageButton? = null
    private var microphoneButton: ThemedAppCompatImageButton? = null
    private var boldButton: ThemedAppCompatImageButton? = null
    private var italicButton: ThemedAppCompatImageButton? = null
    private var underlineButton: ThemedAppCompatImageButton? = null
    private var strikethroughButton: ThemedAppCompatImageButton? = null
    private var unorderedListButton: ThemedAppCompatImageButton? = null
    private var recordControls: CardView? = null
    private var playbackControls: CardView? = null
    private var stopRecordingIcon: ImageView? = null
    private var audioPlayIcon: ImageView? = null
    private var seekBar: SeekBar? = null
    private var currentTime: TextView? = null
    private var totalTime: TextView? = null
    private var menuImg: ImageView? = null
    private val handler = Handler()
    private var currentTimeRecordingView: TextView? = null
    private var startTime: Long = 0

    private var handler2: Handler = Handler()
    private var runnable: Runnable = object : Runnable {
        override fun run() {
            val elapsedMillis = System.currentTimeMillis() - startTime
            val seconds = (elapsedMillis / 1000) % 60
            val minutes = (elapsedMillis / (1000 * 60)) % 60
            val timeString = String.format("%02d:%02d", minutes, seconds)
            currentTimeRecordingView?.text = timeString

            handler.postDelayed(this, 1000) // Update every second
        }
    }

    private var doneButton: Button? = null
    private var cameraButtonOutsideSNCanvasRibbon: ImageButton? = null
    var reminderLabelComponent: ReminderLabelComponent? = null
    private lateinit var mediaPlayer: MediaPlayer
    private var isRecording = false


    init {
        inflate(context)
        setUpNoteGalleryRecyclerView()
        setEditNoteCardView()
        setScrollView()
        noteBodyEditText?.ribbonCallback = this
        noteBodyEditText?.focusCallback = this
        resetEditingMode()

        if (isRestrictedCanvas) {
            bindRestrictedCanvasButtons()
            setHintText()
        } else {
            configureViews()
            setFormattingPalette()
            bindNonRestrictedCanvasButtons()
        }
    }

    private fun setHintText() {
        if (isRestrictedCanvas && NotesLibrary.getInstance().experimentFeatureFlags.hintTextInSNCanvasEnabled) {
            noteBodyEditText?.setHint(R.string.sn_edit_hint_text)
        }
    }

    @Keep
    override fun getNotesEditText(): NotesEditText? = noteBodyEditText

    @Keep
    override fun getEditNoteLayout(): FrameLayout? = editNoteFrameLayout

    @Keep
    override fun getNoteContainerLayout(): RelativeLayout? = noteContainer

    private fun setEditNoteCardView() {
        editNoteFrameLayout?.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: Outline?) {
                view?.let {
                    outline?.setRoundRect(
                        0, 0, view.width,
                        (
                            view.height + resources.getDimensionPixelSize(
                                R
                                    .dimen.sn_editnotecard_corner_radius
                            )
                            ),
                        resources.getDimensionPixelSize(
                            R
                                .dimen.sn_editnotecard_corner_radius
                        ).toFloat()
                    )
                }
            }
        }
        editNoteFrameLayout?.clipToOutline = true
    }

    private fun inflate(context: Context) {
        @Suppress("UnsafeCast")
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val noteStyledView = inflater.inflate(R.layout.sn_note_styled_view_layout, this, true)

        noteBodyEditText = noteStyledView.findViewById(R.id.noteBodyEditText)
        editNoteFrameLayout = noteStyledView.findViewById(R.id.editNoteFrameLayout)
        noteContainer = noteStyledView.findViewById(R.id.noteContainer)
        editNoteScrollView = noteStyledView.findViewById(R.id.editNoteScrollView)
        noteGalleryRecyclerView = noteStyledView.findViewById(R.id.noteGalleryRecyclerView)

        if (isRestrictedCanvas) {
            doneButton = findViewById(R.id.doneButton)
            cameraButtonOutsideSNCanvasRibbon = findViewById(R.id.cameraButtonOutsideSNCanvasRibbon)
        } else {
            timeStamp = noteStyledView.findViewById(R.id.timestamp)
            timestampText = noteStyledView.findViewById(R.id.timestampText)
            cameraButtonTimestamp = noteStyledView.findViewById(R.id.cameraButtonTimestamp)
            microphoneButtonTimestamp = noteStyledView.findViewById(R.id.microphoneButtonTimestamp)
            cameraButtonRibbon = noteStyledView.findViewById(R.id.cameraButtonRibbon)
            microphoneButton = noteStyledView.findViewById(R.id.microphoneButton)
            boldButton = findViewById(R.id.boldButton)
            italicButton = findViewById(R.id.italicButton)
            underlineButton = findViewById(R.id.underlineButton)
            strikethroughButton = findViewById(R.id.strikethroughButton)
            unorderedListButton = findViewById(R.id.unorderedListButton)
            recordControls = findViewById(R.id.record_controls)
            playbackControls = findViewById(R.id.playback_controls)
            stopRecordingIcon = findViewById(R.id.stop_recording)
            audioPlayIcon = findViewById(R.id.audio_play_icon)
            seekBar = findViewById(R.id.audio_seek)
            currentTime = findViewById(R.id.currentTime)
            totalTime = findViewById(R.id.totalTime)
            currentTimeRecordingView = findViewById(R.id.currentTimeRecording)
            menuImg = findViewById(R.id.img_menu)
        }
    }

    private fun setScrollView() {
        editNoteScrollView?.let { noteBodyEditText?.setScrollView(it) }
    }

    private fun setTimeStamp(note: Note) {
        timestampText?.text = context.parseMillisToRFC1123String(note.documentModifiedAt)
    }

    private fun setEmailInfo(note: Note) {
        if (NotesLibrary.getInstance().showEmailInEditNote) {
            val email = NotesLibrary.getInstance().getEmailForNote(note)
            if (NotesLibrary.getInstance().isMultipleUsersSignedIn() && email.isNotEmpty()) {
                emailInfo?.text = email
                emailInfo?.visibility = View.VISIBLE
            } else {
                emailInfo?.visibility = View.GONE
            }
        }
    }

    private fun setUpNoteContext(note: Note) {
        val contextComponent: NoteContextComponent? = findViewById(R.id.noteContext)
        if (note.metadata.context != null) {
            contextComponent?.showNoteContext(
                noteContext = note.metadata.context,
                themeOverride = themeOverride
            )
        } else {
            contextComponent?.visibility = View.GONE
        }
    }

    /**
     * Send an Accessibility Event of type TYPE_ANNOUNCEMENT with the given message.
     * should be called with activity context if available
     */
    private fun sendAccessibilityAnnounce(announce: String) {
        val manager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (manager != null && manager.isEnabled) {
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            if (event != null) {
                event.text.add(announce)
                manager.sendAccessibilityEvent(event)
            }
        }
    }

    private fun setFormattingPalette() {
        boldButton?.let { boldButton ->
            prepareFormattingItem(
                boldButton,
                OnClickListener {
                    recordFormattingOptionTelemetry(
                        FormattingStyleType.Bold, FormattingToggleSource.System
                    )
                    noteBodyEditText?.toggleBold()
                    setAccessibilityAnnouncement(boldButton)
                }
            )
        }
        italicButton?.let { italicButton ->
            prepareFormattingItem(
                italicButton,
                OnClickListener {
                    recordFormattingOptionTelemetry(
                        FormattingStyleType.Italic, FormattingToggleSource.System
                    )
                    noteBodyEditText?.toggleItalic()
                    setAccessibilityAnnouncement(italicButton)
                }
            )
        }
        underlineButton?.let { underlineButton ->
            prepareFormattingItem(
                underlineButton,
                OnClickListener {
                    recordFormattingOptionTelemetry(
                        FormattingStyleType.Underline, FormattingToggleSource.System
                    )
                    noteBodyEditText?.toggleUnderline()
                    setAccessibilityAnnouncement(underlineButton)
                }
            )
        }
        strikethroughButton?.let { strikethroughButton ->
            prepareFormattingItem(
                strikethroughButton,
                OnClickListener {
                    recordFormattingOptionTelemetry(
                        FormattingStyleType.Strikethrough,
                        FormattingToggleSource.System
                    )
                    noteBodyEditText?.toggleStrikethrough()
                    setAccessibilityAnnouncement(strikethroughButton)
                }
            )
        }
    }

    private fun setAccessibilityAnnouncement(v: View) {
        if (!v.isSelected) {
            with(v) {
                // Screen reader only reads selected states, but not unselected states
                sendAccessibilityAnnounce(
                    v.contentDescription?.toString() + "," + context.resources.getString(
                        R.string.sn_unselected
                    )
                )
            }
        }
    }

    private fun prepareFormattingItem(formatOption: View, clickListener: View.OnClickListener) {
        formatOption.setOnClickListener(clickListener)
    }

    private fun recordFormattingOptionTelemetry(
        type: FormattingStyleType,
        source: FormattingToggleSource
    ) {
        telemetryCallback?.recordTelemetryEvent(
            EventMarkers.NoteContentActionTaken,
            Pair(NotesSDKTelemetryKeys.NoteProperty.NOTE_HAS_IMAGES, hasNewMedia.toString()),
            Pair(NotesSDKTelemetryKeys.NoteProperty.FORMATTING_STYLE_TYPE, type.name),
            Pair(NotesSDKTelemetryKeys.NoteProperty.FORMATTING_TOGGLE_SOURCE, source.name),
            Pair(
                NotesSDKTelemetryKeys.NoteProperty.ACTION,
                NoteContentActionType.NOTE_INLINE_STYLE_TOGGLED
            )
        )
    }

    private fun configureViews() {
        setToVisible(cameraButtonRibbon, imageCaptureEnabled)
        cameraButtonRibbon?.setImageResource(getInsertImageDrawable())
        cameraButtonTimestamp?.visibility =
            if (imageCaptureEnabled) View.VISIBLE else View.INVISIBLE
        cameraButtonTimestamp?.setImageResource(getInsertImageDrawable())

        setToVisible(microphoneButton, microphoneButtonEnabled)
    }

    private fun setupReminderLabelComponent(note: Note) {
        reminderLabelComponent = findViewById(R.id.reminderLabel)
        reminderLabelComponent?.setReminderLayout(note.metadata.reminder)
    }

    private fun setUpNoteGalleryRecyclerView() {
        val layoutManager = object : GridLayoutManager(context, ITEMS_IN_A_ROW) {
            override fun isAutoMeasureEnabled(): Boolean = true
        }

        noteGalleryRecyclerView?.layoutManager = layoutManager
        noteGalleryRecyclerView?.isNestedScrollingEnabled = false

        noteGalleryAdapter.setCallback(this)
        noteGalleryRecyclerView?.adapter = noteGalleryAdapter

        val horizontalDivider = DividerItemDecoration(context, DividerItemDecoration.HORIZONTAL)
        val verticalDivider = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        val horizontalDividerDrawable =
            ContextCompat.getDrawable(context, R.drawable.sn_note_gallery_item_divider)
        horizontalDividerDrawable?.let { horizontalDivider.setDrawable(it) }
        val verticalDividerDrawable =
            ContextCompat.getDrawable(context, R.drawable.sn_note_gallery_item_divider)
        verticalDividerDrawable?.let { verticalDivider.setDrawable(it) }
        noteGalleryRecyclerView?.addItemDecoration(horizontalDivider)
        noteGalleryRecyclerView?.addItemDecoration(verticalDivider)
    }

    private fun <T : View?> setToVisible(view: T, isVisible: Boolean) {
        when (isVisible) {
            true -> view?.visibility = View.VISIBLE
            false -> view?.visibility = View.GONE
        }
    }

    private fun bindRestrictedCanvasButtons() {
        cameraButtonOutsideSNCanvasRibbon?.setOnClickListener { onAddPhotoClick(ImageTrigger.Ribbon) }
        doneButton?.setOnClickListener {
            NotesLibrary.getInstance().sendCaptureNoteAction()
        }
    }

    private fun bindNonRestrictedCanvasButtons() {
        cameraButtonRibbon?.setOnClickListener { onAddPhotoClick(ImageTrigger.Ribbon) }
        cameraButtonTimestamp?.setOnClickListener { onAddPhotoClick(ImageTrigger.Canvas) }
        microphoneButtonTimestamp?.setOnClickListener {
            telemetryCallback?.recordTelemetryEvent(
                EventMarkers.DictationTriggered,
                Pair(HostTelemetryKeys.TRIGGER_POINT, Mode.View.toString())
            )
            recordControls?.visibility = VISIBLE
            microPhoneCallbacks?.onAudioRecordButtonClicked(isRecording)
            startTime = System.currentTimeMillis()
            handler2.post(runnable)
            isRecording = !isRecording
            // enableEditingMode(showSoftInput = false)
        }

        stopRecordingIcon?.setOnClickListener {
            recordControls?.visibility = GONE
            microPhoneCallbacks?.onAudioRecordButtonClicked(isRecording)
            isRecording = !isRecording
        }

        unorderedListButton?.setOnClickListener {
            telemetryCallback?.recordTelemetryEvent(
                EventMarkers.NoteContentActionTaken,
                Pair(NotesSDKTelemetryKeys.NoteProperty.NOTE_HAS_IMAGES, hasNewMedia.toString()),
                Pair(
                    NotesSDKTelemetryKeys.NoteProperty.FORMATTING_STYLE_TYPE,
                    FormattingStyleType.Bullet.name
                ),
                Pair(
                    NotesSDKTelemetryKeys.NoteProperty.FORMATTING_TOGGLE_SOURCE,
                    FormattingToggleSource.System.name
                ),
                Pair(
                    NotesSDKTelemetryKeys.NoteProperty.ACTION,
                    NoteContentActionType.NOTE_BLOCK_STYLE_TOGGLED
                )
            )
            noteBodyEditText?.toggleUnorderedList()
        }
    }

    private fun onAddPhotoClick(imageTrigger: ImageTrigger) {
        if (isContinuousClicking()) {
            return
        }
        imageCallbacks?.addPhoto(imageTrigger)
    }

    private fun setRibbonState() {
        if (isRestrictedCanvas) findViewById<ViewGroup>(R.id.outsideSNCanvasOptionToolbar)?.visibility =
            View.VISIBLE
        else if (editMode) findViewById<ViewGroup?>(R.id.optionToolbar)?.visibility = View.VISIBLE
        else findViewById<ViewGroup?>(R.id.optionToolbar)?.visibility = View.GONE
    }

    override fun updateFormatState(
        isBoldEnabled: Boolean,
        isItalicsEnabled: Boolean,
        isUnderlineEnabled: Boolean,
        isStrikeThroughEnabled: Boolean,
        isBulletedListEnabled: Boolean
    ) {
        boldState = isBoldEnabled
        italicsState = isItalicsEnabled
        underlineState = isUnderlineEnabled
        strikethroughState = isStrikeThroughEnabled
        bulletedListState = isBulletedListEnabled

        boldButton?.isSelected = isBoldEnabled && editMode
        italicButton?.isSelected = isItalicsEnabled && editMode
        underlineButton?.isSelected = isUnderlineEnabled && editMode
        strikethroughButton?.isSelected = isStrikeThroughEnabled && editMode
        unorderedListButton?.isSelected = isBulletedListEnabled && editMode

        boldButton?.isEnabled = editMode
        italicButton?.isEnabled = editMode
        underlineButton?.isEnabled = editMode
        strikethroughButton?.isEnabled = editMode
        unorderedListButton?.isEnabled = editMode
    }

    override fun setEditMode(isEditMode: Boolean): Boolean {
        if (isEditMode != editMode) {
            setEditingModeAndRibbonState(editModeVal = isEditMode)
            return true
        }

        return false
    }

    override fun isInEditMode(): Boolean = editMode

    @Keep
    override fun onReEntry() {
        noteBodyEditText?.setDocument()
        if (isInEditMode) {
            setCanvasEditStart()
            noteBodyEditText?.requestFocus()
        }
    }

    @Keep
    override fun onConfigurationChanged() {
        noteBodyEditText?.setDocument()
    }

    @Keep
    override fun onNavigatingAway() {
        if (isInEditMode) {
            setCanvasEditEndAndRecord()
            noteBodyEditText?.clearFocus()
        }
        noteBodyEditText?.removeEmptySpans()
    }

    private fun setCanvasEditStart() {
        if (!editSessionTimer.hasStarted()) {
            editSessionTimer.startTimer()
        }
    }

    private fun setCanvasEditEndAndRecord() {
        if (editSessionTimer.hasStarted()) {
            val editSessionTime = editSessionTimer.endTimer()
            telemetryCallback?.recordTelemetryEvent(
                EventMarkers.NoteEditSessionComplete,
                Pair(HostTelemetryKeys.TIME_TAKEN_IN_MS, editSessionTime.toString())
            )
        }
    }

    private fun setEditingModeAndRibbonState(editModeVal: Boolean) {
        if (!isInEditMode) setCanvasEditStart()
        else setCanvasEditEndAndRecord()

        val fDidSwitchMode: Boolean = editMode != editModeVal
        editMode = editModeVal

        timeStamp?.visibility = View.GONE

        if (editMode) noteBodyEditText?.removeLinks()
        else noteBodyEditText?.addLinks()

        noteBodyEditText?.isCursorVisible = editModeVal
        setRibbonState()

        if (fDidSwitchMode) ribbonCallbacks?.onEditModeChanged(editModeVal)

        if (!isRestrictedCanvas) {
            if (!editMode) timeStamp?.visibility = View.VISIBLE
            updateFormatState(
                boldState,
                italicsState,
                underlineState,
                strikethroughState,
                bulletedListState
            )
        }
    }

    fun setupNoteBodyCallbacks(callback: NotesEditTextCallback) {
        noteBodyEditText?.setNotesEditTextViewCallback(callback)
    }

    fun setupNoteInkCallback(callback: NotesEditInkCallback) {
        noteGalleryAdapter.setNotesEditInkViewCallback(callback)
    }

    fun resumeEditingMode(showSoftInput: Boolean = true) {
        if (isInEditMode) {
            enableEditingMode(showSoftInput)
        }
    }

    fun resetEditingMode(showSoftInput: Boolean = true) {
        setEditingModeAndRibbonState(!showSoftInput)
    }

    fun enableEditingMode(showSoftInput: Boolean = true) {
        noteBodyEditText?.requestFocus()
        if (showSoftInput) {
            showSoftInput()
        }
        setEditingModeAndRibbonState(editModeVal = true)
    }

    private fun showSoftInput() {
        @Suppress("UnsafeCast")
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        noteBodyEditText?.let {
            imm.showSoftInput(
                noteBodyEditText,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    fun isEmpty() = noteBodyEditText?.text.toString().isEmpty()

    @Keep
    override fun setNoteContent(note: Note) {
        /**
         *   If we were already showing a note and now we want to switch to a new note, we need to
         *   clear the locally cached data from the old note, we restore member data variables to
         *   their default state
         */
        if (note.localId != sourceNote?.localId) {
            noteBodyEditText?.onBeforeActiveNoteChanged()
            sourceNote = null
        }

        /**
         * This check is needed because setNoteInk calls notifyDataSetChanged(), which forces
         * the edit ink view to be recycled. If the user is currently drawing ink, the stroke
         * they are drawing will abruptly be cancelled. We only want to call setNoteInk when
         * there has been an actual update, so we check documentModifiedAt.
         */
        val shouldSetNoteInk = sourceNote?.let {
            it.documentModifiedAt < note.documentModifiedAt
        } ?: true

        sourceNote = note
        setTimeStamp(note)
        setEmailInfo(note)
        setupReminderLabelComponent(note)
        noteBodyEditText?.setNoteContent(note)
        setUpNoteContext(note)
        applyTheme()
        setNoteMedia(note)
        enableImageCapture(!note.document.readOnly)

        noteGalleryRecyclerView?.layoutParams?.height = if (note.isInkNote) {
            // The edit ink view in the gallery should take up all the space in the note
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            // The image gallery view should only take up as much space as it needs for the images
            ViewGroup.LayoutParams.WRAP_CONTENT
        }

        if (shouldSetNoteInk) setNoteInk(note)
    }

    private fun setNoteInk(note: Note) {
        noteGalleryAdapter.setInk(note.document, note.uiRevision.increaseRevision())
    }

    private fun formatDuration(duration: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // test
    private fun setNoteMedia(note: Note) {
        this.hasNewMedia = note.media.isNotEmpty()
        noteBodyEditText?.hasMedia = !note.isMediaListEmpty

        if (note.isVoiceNote) {
            playbackControls?.visibility = VISIBLE
            // show different UX
            var fileUrl = note.media[0].localUrl ?: return
            if (fileUrl.startsWith("file:///")) fileUrl = fileUrl.substring(7)
            else fileUrl = fileUrl.substring(5)
            val file = File(fileUrl)
            val uri = Uri.fromFile(file)
            mediaPlayer = MediaPlayer.create(context, uri)
            mediaPlayer.setOnPreparedListener {
                totalTime?.text = formatDuration(mediaPlayer.duration.toLong())
                seekBar?.max = mediaPlayer.duration
            }
            mediaPlayer.setOnCompletionListener {
                audioPlayIcon!!.setImageResource(R.drawable.ic_play_icon)
            }
            audioPlayIcon?.setOnClickListener {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                    audioPlayIcon!!.setImageResource(R.drawable.ic_play_icon)
                } else {
                    mediaPlayer.start()
                    audioPlayIcon!!.setImageResource(R.drawable.ic_pause_icon)
                }
            }

            menuImg?.setOnClickListener {
                showPopUpMenu()
            }
            // Set up SeekBar change listener
            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    // No action needed
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    // No action needed
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.let {
                        mediaPlayer.seekTo(it.progress)
                    }
                }
            })

            // Update the SeekBar and current time
            handler.postDelayed(object : Runnable {
                override fun run() {
                    if (mediaPlayer.isPlaying) {
                        seekBar?.progress = mediaPlayer.currentPosition
                        currentTime?.text = formatDuration(mediaPlayer.currentPosition.toLong())
                    }
                    handler.postDelayed(this, 100)
                }
            }, 0)
            //Toast.makeText(context, "Voice Note here", Toast.LENGTH_SHORT).show()
            return
        } else {
            playbackControls?.visibility = GONE
        }

        if (note.isInkNote) {
            // Since we are piggybacking off the gallery recycler view, we need to remove the padding
            // to allow the user to ink to the edges
            noteGalleryRecyclerView?.setPadding(0, 0, 0, 0)
        } else if (note.isMediaListEmpty) {
            noteGalleryRecyclerView?.visibility = View.GONE
            return
        }

        val layoutManager = noteGalleryRecyclerView?.layoutManager as GridLayoutManager
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // want ink notes to take up the whole width
                if (note.isInkNote) {
                    return ITEMS_IN_A_ROW
                }
                if (note.media.size % 2 == 0) {
                    return 1
                }
                return if (position == 0 && note.media.size > 1) ITEMS_IN_A_ROW else 1 // Required to resolve an accessibility issue (#185) where single image is declared as a item in list of 2.
            }
        }
        /* Required to resolve an accessibility issue (#185) where single image is declared as a item in list of 2.
        Accessibility Bug filed under:  https://github.com/microsoft-notes/accessibility-testing/issues/185 */
        layoutManager.spanCount = if (note.media.size == 1) 1 else ITEMS_IN_A_ROW
        noteGalleryRecyclerView?.layoutManager = layoutManager

        noteGalleryRecyclerView?.visibility = View.VISIBLE
        noteGalleryAdapter.setMedia(note.sortedMedia, note.color, imageCaptureEnabled)
    }

    private fun showPopUpMenu() {
        val popupView = LayoutInflater.from(context).inflate(R.layout.layout_popup, null)
        val option1 = popupView.findViewById<TextView>(R.id.option1)
        val option2 = popupView.findViewById<TextView>(R.id.option2)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        popupWindow.isFocusable = true
        popupWindow.setBackgroundDrawable(
            ContextCompat.getDrawable(
                context,
                android.R.color.white
            )
        )
        popupWindow.showAsDropDown(menuImg)
        option1.setOnClickListener {
           popupWindow.dismiss()
        }
        option2.setOnClickListener {
            popupWindow.dismiss()
        }
    }

    private fun enableImageCapture(imageCaptureEnabled: Boolean) {
        this.imageCaptureEnabled = imageCaptureEnabled
        setToVisible(cameraButtonRibbon, imageCaptureEnabled)
        cameraButtonTimestamp?.visibility =
            if (imageCaptureEnabled) View.VISIBLE else View.INVISIBLE
    }

    fun enableMicrophoneButton(microphoneButtonEnabled: Boolean) {
        this.microphoneButtonEnabled = microphoneButtonEnabled
        val isMicrophoneEnabled = !isRestrictedCanvas && microphoneButtonEnabled
        setToVisible(microphoneButton, isMicrophoneEnabled)
        setToVisible(microphoneButtonTimestamp, isMicrophoneEnabled)
    }

    fun applyTheme() {
        sourceNote?.let { setNoteColor(it.color, themeOverride) }

        // The null check is needed as applyTheme is called in ThemedView's init block, and even properties won't
        // be initialized by then! Is there a better way of setting this value?
        noteGalleryAdapter?.setThemeOverride(themeOverride)
    }

    private fun setNoteColor(
        color: Color,
        themeOverride: NotesThemeOverride.StickyNoteCanvasThemeOverride?
    ) {
        val textAndInkColorValue = if (themeOverride != null)
            ContextCompat.getColor(context, themeOverride.textAndInkColor) else null
        val metadataColorValue = if (themeOverride != null)
            ContextCompat.getColor(context, themeOverride.metadataColor) else null
        val hyperLinkColor = if (themeOverride != null)
            ContextCompat.getColor(
                context,
                R.color.hyperlink_color_dark
            ) else color.toLinkTextContextColor(context)
        val hintColor = if (themeOverride != null)
            ContextCompat.getColor(context, themeOverride.textHintColor) else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (themeOverride != null) {
                noteBodyEditText?.highlightColor = color.toDarkTextHighLightContextColor(context)
                noteBodyEditText?.textSelectHandleLeft?.setTint(
                    color.toDarkTextHandleContextColor(
                        context
                    )
                )
                noteBodyEditText?.textSelectHandleRight?.setTint(
                    color.toDarkTextHandleContextColor(
                        context
                    )
                )
                noteBodyEditText?.textSelectHandle?.setTint(
                    color.toDarkTextHandleContextColor(
                        context
                    )
                )
            } else {
                noteBodyEditText?.highlightColor = color.toLightTextHighLightContextColor(context)
                noteBodyEditText?.textSelectHandleLeft?.setTint(
                    color.toLightTextHandleContextColor(
                        context
                    )
                )
                noteBodyEditText?.textSelectHandleRight?.setTint(
                    color.toLightTextHandleContextColor(
                        context
                    )
                )
                noteBodyEditText?.textSelectHandle?.setTint(
                    color.toLightTextHandleContextColor(
                        context
                    )
                )
            }
        }
        val noteBackgroundColorId = if (themeOverride == null)
            color.toLighterColorResource() else color.toSNCardColorNightResource()
        val topBarColorValue = color.toTopBarContextColor(context, themeOverride)

        if (isRestrictedCanvas) {
            findViewById<ViewGroup?>(R.id.outsideSNCanvasOptionToolbar)?.background =
                ColorDrawable(ContextCompat.getColor(context, noteBackgroundColorId))
            setSNOutsideCanvasCCBItemsColor(color, topBarColorValue)
        }

        setNoteColor(
            noteColor = color,
            noteBackgroundColorId = noteBackgroundColorId,
            fontColor = textAndInkColorValue ?: color.getFontColor().toContextColor(context),
            linkFontColor = hyperLinkColor,
            inkColor = textAndInkColorValue ?: color.toInkContextColor(context),
            metadataColor = metadataColorValue ?: color.toMetadataColor(context),
            topBarColor = topBarColorValue,
            hintColor = hintColor ?: color.getFontColor().toSecondaryTextColor(context)
        )
    }

    private fun setNoteColor(
        noteColor: Color,
        noteBackgroundColorId: Int,
        fontColor: Int,
        linkFontColor: Int,
        inkColor: Int,
        metadataColor: Int,
        topBarColor: Int,
        hintColor: Int
    ) {
        setNoteBackgroundColor(noteBackgroundColorId)
        editNoteFrameLayout?.setBackgroundColor(topBarColor)
        setFontColor(fontColor, metadataColor, linkFontColor, hintColor)
        noteBodyEditText?.inkColor = inkColor
        if (!isRestrictedCanvas) {
            tintTimestampButtons(noteColor, metadataColor)
            setDividerColor(noteColor.toDividerContextColor(context))
        }
    }

    private fun setNoteBackgroundColor(noteColorResId: Int) {
        noteContainer?.setBackgroundResource(noteColorResId)
        setEditTextBackground(noteColorResId)
        timeStamp?.setBackgroundResource(noteColorResId)
    }

    @Suppress("UnsafeCast")
    private fun setEditTextBackground(noteColorResId: Int) {
        if (isHardwareKeyBoardAvailable()) {
            val layerDrawable = ContextCompat.getDrawable(
                context,
                R.drawable.sn_edittext_background
            ) as LayerDrawable
            val colorBG =
                layerDrawable.findDrawableByLayerId(R.id.card_content_bg_color) as GradientDrawable
            colorBG.setColor(ContextCompat.getColor(context, noteColorResId))
            noteBodyEditText?.background = layerDrawable
        } else {
            noteBodyEditText?.setBackgroundResource(noteColorResId)
        }
    }

    private fun setDividerColor(dividerColor: Int) {
        (findViewById<View?>(R.id.timestampDivider))?.setBackgroundColor(dividerColor)
    }

    private fun setFontColor(fontColor: Int, metadataColor: Int, linkColor: Int, hintColor: Int) {
        noteBodyEditText?.setTextColor(fontColor)
        noteBodyEditText?.setLinkTextColor(linkColor)
        noteBodyEditText?.setHintTextColor(hintColor)
        timestampText?.setTextColor(metadataColor)
        emailInfo?.setTextColor(metadataColor)
    }

    private fun tintTimestampButtons(color: Color, tint: Int) {
        cameraButtonTimestamp?.setBackgroundResource(color.toTimestampDrawable())
        microphoneButtonTimestamp?.setBackgroundResource(color.toTimestampDrawable())

        val iconCamera: Drawable? =
            VectorDrawableCompat.create(resources, getInsertImageDrawable(), null)
        iconCamera?.let {
            val wrappedIcon = DrawableCompat.wrap(it).mutate()
            DrawableCompat.setTint(wrappedIcon, tint)
            cameraButtonTimestamp?.setImageDrawable(wrappedIcon)
        }

        val iconMicrophone: Drawable? =
            VectorDrawableCompat.create(resources, R.drawable.sn_ic_microphone_24dp, null)
        iconMicrophone?.let {
            val wrappedIcon = DrawableCompat.wrap(it).mutate()
            DrawableCompat.setTint(wrappedIcon, tint)
            microphoneButtonTimestamp?.setImageDrawable(wrappedIcon)
        }
    }

    private fun setSNOutsideCanvasCCBItemsColor(color: Color, dividerColor: Int) {
        cameraButtonOutsideSNCanvasRibbon?.setBackgroundResource(color.toTimestampDrawable())
        if (themeOverride != null) {
            doneButton?.setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.sn_outside_canvas_ccb_icons_tint_dark
                )
            )
            cameraButtonOutsideSNCanvasRibbon?.setColorFilter(
                ContextCompat.getColor(
                    context,
                    R.color.sn_outside_canvas_ccb_icons_tint_dark
                )
            )
        } else {
            cameraButtonOutsideSNCanvasRibbon?.setColorFilter(
                ContextCompat.getColor(
                    context,
                    R.color.sn_outside_canvas_ccb_icons_tint_light
                )
            )
        }
        (findViewById<View?>(R.id.SNOutsideCanvasDivider))?.setBackgroundColor(dividerColor)
    }

    private fun getInsertImageDrawable(): Int =
        if (NotesLibrary.getInstance().withGalleryIconInsteadOfCamera)
            R.drawable.sn_ic_gallery_24dp else R.drawable.sn_ic_camera_24dp

    private fun isHardwareKeyBoardAvailable(): Boolean {
        if (context == null) {
            return false
        }
        return context.resources.configuration.keyboard != 1 || !isInTouchMode
    }

    fun prepareForNewImage() {
        clearFocusAndStopEditMode()
        editNoteScrollView?.fling(0)
        editNoteScrollView?.fullScroll(ScrollView.FOCUS_UP)
    }

    @Keep
    override fun onContextMenuClosed() {
        noteGalleryAdapter.menuDismissed()
    }

    // NoteGalleryAdapter.Callback
    override fun displayFullScreenImage(media: Media) {
        media.localUrl?.let {
            clearFocusAndStopEditMode()
            imageCallbacks?.openMediaInFullScreen(it, media.mimeType)
        }
    }

    override fun editAltText(media: Media) {
        clearFocusAndStopEditMode(hideKeyboard = false)
        imageCallbacks?.editAltText(media)
    }

    override fun deleteMedia(media: Media) {
        clearFocusAndStopEditMode()
        imageCallbacks?.deleteMedia(media)
    }

    fun insertTextToCurrentNote(text: String) {
        noteBodyEditText?.insertTextToCurrentNote(text)
    }

    private fun clearFocusAndStopEditMode(hideKeyboard: Boolean = true) {
        noteBodyEditText?.clearFocus()
        if (hideKeyboard) {
            val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(noteBodyEditText?.windowToken, 0)
        }
        setEditMode(isEditMode = false)
    }

    // EditTextFocusCallback
    override fun focusBeforeEditText() {
        noteGalleryRecyclerView?.requestFocus(FOCUS_UP)
    }

    override fun focusAfterEditText() {
        timeStamp?.requestFocus(FOCUS_DOWN)
    }

    fun getMicrophoneButtonView(): ImageView? = microphoneButton

    interface MicroPhoneCallbacks {
        fun onMicroPhoneButtonClicked()
        fun onAudioRecordButtonClicked(isRecording: Boolean)
    }
}
