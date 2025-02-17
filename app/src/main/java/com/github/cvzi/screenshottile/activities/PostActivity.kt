package com.github.cvzi.screenshottile.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.util.Size
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.cvzi.screenshottile.App
import com.github.cvzi.screenshottile.NOTIFICATION_ACTION_RENAME_INPUT
import com.github.cvzi.screenshottile.R
import com.github.cvzi.screenshottile.ToastType
import com.github.cvzi.screenshottile.utils.*


class PostActivity : GenericPostActivity() {
    companion object {
        private const val TAG = "PostActivity"

        /**
         * New Intent that opens a single image
         *
         * @param context    Context
         * @param uri   Uri   of image
         * @return The intent
         */
        fun newIntentSingleImage(
            context: Context,
            uri: Uri,
            parentFolderUri: Uri? = null,
            highlight: Int? = null
        ): Intent {
            val intent = Intent(context, PostActivity::class.java)
            intent.putExtra(NOTIFICATION_ACTION_RENAME_INPUT, uri.toString())
            if (highlight != null) {
                intent.putExtra(HIGHLIGHT, highlight)
            }
            if (parentFolderUri != null) {
                intent.putExtra(PARENT_FOLDER_URI, parentFolderUri.toString())
            }
            return intent
        }

        fun newIntentSingleImageBitmap(
            context: Context,
            uri: Uri,
            parentFolderUri: Uri? = null,
            highlight: Int? = null
        ): Intent {
            val intent = Intent(context, PostActivity::class.java)
            intent.putExtra(NOTIFICATION_ACTION_RENAME_INPUT, uri.toString())
            intent.putExtra(BITMAP_FROM_LAST_SCREENSHOT, true)
            if (highlight != null) {
                intent.putExtra(HIGHLIGHT, highlight)
            }
            if (parentFolderUri != null) {
                intent.putExtra(PARENT_FOLDER_URI, parentFolderUri.toString())
            }
            return intent
        }
    }

    private val prefManager = App.getInstance().prefManager
    private var recentFolders: ArrayList<RecentFolder> = ArrayList()
    private lateinit var startForPickFolder: ActivityResultLauncher<Intent>

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post)

        intent?.run {
            val imagePath = getStringExtra(NOTIFICATION_ACTION_RENAME_INPUT)
            val tryLastBitmap = getBooleanExtra(BITMAP_FROM_LAST_SCREENSHOT, false)
            val lastBitmap = if (tryLastBitmap) {
                App.getInstance().lastScreenshot
            } else {
                null
            }
            val parentFolderUri = getStringExtra(PARENT_FOLDER_URI)?.let { Uri.parse(it) }
            if (imagePath?.isNotBlank() == true) {
                Uri.parse(imagePath)?.let { imageUri ->
                    SingleImage(imageUri, parentFolderUri = parentFolderUri).apply {
                        loadImageInThread(
                            contentResolver,
                            Size(200, 400),
                            lastBitmap,
                            { singleImageLoaded ->
                                runOnUiThread {
                                    singleImage = singleImageLoaded
                                    showSingleImage(singleImageLoaded)
                                }
                            },
                            { error ->
                                runOnUiThread {
                                    Log.e(TAG, "Failed to load image: $error")
                                    findViewById<ImageView>(R.id.imageView).setImageResource(android.R.drawable.stat_notify_error)
                                    findViewById<TextView>(R.id.textViewFileName).text =
                                        "Failed to load image"
                                    findViewById<TextView>(R.id.textViewFileSize).text = ""
                                }
                            })
                    }
                }
            }

            when (getIntExtra(HIGHLIGHT, -1)) {
                HIGHLIGHT_FILENAME ->
                    findViewById<TextView>(R.id.textViewFileName).setTextColor(getColor(R.color.highlightColor))

                HIGHLIGHT_FOLDER ->
                    findViewById<TextView>(R.id.textViewFileFolder).setTextColor(getColor(R.color.highlightColor))

                else -> {
                    // Do nothing
                }
            }

        }

        findViewById<Button>(R.id.buttonShare).setOnClickListener {
            shareIntent?.let { intent ->
                openIntent(intent)
            }
        }
        findViewById<Button>(R.id.buttonEdit).setOnClickListener {
            editIntent?.let { intent ->
                openIntent(intent)
            }
        }
        findViewById<Button>(R.id.buttonPhotoEditor).setOnClickListener {
            photoEditorIntent?.let { intent ->
                openIntent(intent)
            }
        }
        findViewById<Button>(R.id.buttonCrop).setOnClickListener {
            cropIntent?.let { intent ->
                openIntent(intent)
            }
        }

        findViewById<Button>(R.id.buttonDelete).setOnClickListener {
            singleImage?.let { singleImageLoaded ->
                if (deleteImage(this, singleImageLoaded.uri)) {
                    toastMessage(
                        R.string.screenshot_deleted,
                        ToastType.ACTIVITY,
                        Toast.LENGTH_SHORT
                    )
                    // Show delete icon and close activity
                    findViewById<ImageView>(R.id.imageView).setImageResource(android.R.drawable.ic_menu_delete)
                    findViewById<TextView>(R.id.textViewFileName).setText(R.string.screenshot_deleted)
                    findViewById<TextView>(R.id.textViewFileSize).text = "0"
                    it.postDelayed({
                        finish()
                    }, 1000L)
                } else {
                    toastMessage(
                        R.string.screenshot_delete_failed,
                        ToastType.ACTIVITY
                    )
                }
            }
        }
        findViewById<Button>(R.id.buttonRename).setOnClickListener {
            singleImage?.let { singleImageLoaded ->
                rename(singleImageLoaded)
            }
        }
        findViewById<Button>(R.id.buttonMove).setOnClickListener {
            val lastDir = prefManager.getRecentFolders().firstOrNull()?.uri
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && lastDir != null) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, lastDir)
                }
                if (resolveActivity(packageManager) != null) {
                    startForPickFolder.launch(Intent.createChooser(this, "Choose directory"))
                }
            }
        }
        startForPickFolder =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.let { intent ->
                        val uri = intent.data
                        val takeFlags: Int = intent.flags and
                                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        @SuppressLint("WrongConstant")
                        if (uri != null && contentResolver != null) {
                            contentResolver?.takePersistableUriPermission(uri, takeFlags)
                            singleImage?.let { singleImageLoaded ->
                                move(singleImageLoaded, uri)
                            }
                        }
                    }
                }
            }
        findViewById<EditText>(R.id.editTextNewName).setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                singleImage?.let { singleImageLoaded ->
                    rename(singleImageLoaded)
                }
            }
            return@setOnEditorActionListener false
        }
        findViewById<RecyclerView>(R.id.recyclerViewRecentFolders).apply {
            layoutManager = LinearLayoutManager(context)
            recentFolders.clear()
            recentFolders.addAll(prefManager.getRecentFolders())
            adapter = RecentFoldersAdapter(this@PostActivity, recentFolders).apply {
                onTextClickListener = { _: View, index: Int ->
                    singleImage?.let { singleImageLoaded ->
                        move(singleImageLoaded, recentFolders[index].uri)
                    }
                }
                onDeleteClickListener = { _: View, index: Int ->
                    prefManager.run {
                        removeRecentFolder(recentFolders[index])
                        updateData(getRecentFolders())
                        invalidate()
                    }
                }
            }
        }
        findViewById<RecyclerView>(R.id.recyclerViewSuggestions).apply {
            layoutManager = LinearLayoutManager(context)
            suggestions.clear()
            suggestions.addAll(prefManager.getFileNameSuggestions())
            adapter = SuggestionsAdapter(suggestions).apply {
                onTextClickListener = { _: View, index: Int ->
                    this@PostActivity.findViewById<EditText>(R.id.editTextNewName)
                        .setText(suggestions[index].value)
                }
                onDeleteClickListener = { _: View, index: Int ->
                    prefManager.run {
                        removeFileName(suggestions[index])
                        updateData(getFileNameSuggestions())
                        invalidate()
                    }
                }
                onStarClickListener = { _: View, index: Int ->
                    prefManager.run {
                        removeFileName(suggestions[index])
                        addStarredFileName(suggestions[index].value)
                        updateData(getFileNameSuggestions())
                    }
                }
            }
        }
        findViewById<ImageButton>(R.id.imageButtonSaveSuggestion).setOnClickListener {
            saveNewSuggestions()
        }
        findViewById<EditText>(R.id.editTextAddSuggestion).setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                saveNewSuggestions()
            }
            return@setOnEditorActionListener false
        }

        findViewById<TextView>(R.id.textViewDateIso).setOnClickListener {
            findViewById<EditText>(R.id.editTextNewName)
                .setText((it as TextView).text)
        }
        findViewById<TextView>(R.id.textViewDateLocal).setOnClickListener {
            findViewById<EditText>(R.id.editTextNewName)
                .setText((it as TextView).text)
        }

    }

    override fun onRestoreInstanceState(mSavedInstanceState: Bundle) {
        super.onRestoreInstanceState(mSavedInstanceState)
        savedInstanceState = mSavedInstanceState
    }

    override fun restoreSavedInstanceValues() {
        savedInstanceState?.run {
            getString("editText_${R.id.editTextNewName}", null)?.let {
                findViewById<EditText>(R.id.editTextNewName).setText(it)
            }
            getString("editText_${R.id.editTextAddSuggestion}", null)?.let {
                findViewById<EditText>(R.id.editTextAddSuggestion).setText(it)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            "editText_${R.id.editTextNewName}",
            findViewById<EditText>(R.id.editTextNewName)?.text.toString()
        )
        outState.putString(
            "editText_${R.id.editTextAddSuggestion}",
            findViewById<EditText>(R.id.editTextAddSuggestion)?.text.toString()
        )
        super.onSaveInstanceState(outState)
    }
}
