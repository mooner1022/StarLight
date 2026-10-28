package dev.mooner.starlight.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.net.toUri
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.chip.ChipGroup
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.BuildConfig
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import java.io.File

fun Context.requestManageStoragePermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val uri = "package:${BuildConfig.APPLICATION_ID}".toUri()

    startActivity(
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            uri
        )
    )
}

fun getLanguageByExtension(ext: String): DefaultEditorActivity.Language? =
    DefaultEditorActivity.Language.entries.firstOrNull { ext in it.fileExt }

fun String.toFile(): File =
    File(this)

private val FILE_NAME_REGEX = "(^[-_.A-Za-z0-9/]+$)".toRegex()

internal fun validateFileName(name: String, parent: File?): String? = when {
    name.isEmpty() -> translate {
        Locale.ENGLISH { "Enter a name for the file or folder." }
        Locale.KOREAN  { "폴더/파일의 이름을 입력해주세요." }
    }
    !FILE_NAME_REGEX.matches(name) -> translate {
        Locale.ENGLISH { "Only letters, numbers, -, _, . and / are allowed." }
        Locale.KOREAN  { "영문, 숫자, -, _, ., / 만 사용할 수 있어요." }
    }
    File(parent, name).exists() -> translate {
        Locale.ENGLISH { "A file with this name already exists." }
        Locale.KOREAN  { "이미 존재하는 이름이에요." }
    }
    else -> null
}


@SuppressLint("CheckResult")
fun Context.showNewFileDialog(root: File, directory: Boolean = false, onFileCreated: (file: File) -> Unit) {
    MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
        setCommonAttrs()
        customView(R.layout.dialog_editor_create_file)
        noAutoDismiss()
        findViewById<ChipGroup>(R.id.chip_group_file_type)
            .check(if (directory) R.id.chip_directory else R.id.chip_file)

        positiveButton(res = R.string.ok) {
            val nameInput: EditText = findViewById(R.id.edit_text_filename)!!
            val error = validateFileName(nameInput.text.toString(), root)
            if (error != null) {
                nameInput.error = error
                nameInput.requestFocus()
                return@positiveButton
            }

            val file = File(root, nameInput.text.toString())

            val chipGroup: ChipGroup = findViewById(R.id.chip_group_file_type)
            val selectedId = chipGroup.checkedChipId
            when (selectedId) {
                R.id.chip_file -> {
                    file.parentFile?.let { parent ->
                        if (!parent.exists())
                            parent.mkdirs()
                    }
                    file.createNewFile()
                }
                R.id.chip_directory ->
                    file.mkdirs()
                View.NO_ID -> {
                    val message = translate {
                        Locale.ENGLISH { "Choose what to create. (Folder/File)" }
                        Locale.KOREAN  { "생성할 종류를 선택해 주세요. (폴더/파일)" }
                    }
                    if (this@showNewFileDialog is Activity)
                        createFailurePeek(message, PeekAlert.Position.Top).peek()
                    else
                        Toast.makeText(this@showNewFileDialog, message, Toast.LENGTH_LONG).show()
                    return@positiveButton
                }
            }
            onFileCreated(file)
            it.dismiss()
        }
    }
}