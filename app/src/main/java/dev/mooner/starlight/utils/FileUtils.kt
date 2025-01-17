package dev.mooner.starlight.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.chip.ChipGroup
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.BuildConfig
import dev.mooner.starlight.R
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import java.io.File

fun Context.requestManageStoragePermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")

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


@SuppressLint("CheckResult")
fun Context.showNewFileDialog(root: File, onFileCreated: (file: File) -> Unit) {
    MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
        setCommonAttrs()
        customView(R.layout.dialog_editor_create_file)
        noAutoDismiss()

        positiveButton(res = R.string.ok) {
            val nameInput: EditText = findViewById(R.id.edit_text_filename)!!
            if (nameInput.text.isEmpty()) {
                nameInput.error = "폴더/파일의 이름을 입력해주세요."
                nameInput.requestFocus()
                return@positiveButton
            }
            if (!"(^[-_.A-Za-z0-9/]+\$)".toRegex().matches(nameInput.text.toString())) {
                nameInput.error = "허용되지 않는 문자가 포함되어 있어요."
                nameInput.requestFocus()
                return@positiveButton
            }

            val file = File(root, nameInput.text.toString())
            if (file.exists()) {
                nameInput.error = "이미 존재하는 이름이에요."
                nameInput.requestFocus()
                return@positiveButton
            }

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
                    if (this@showNewFileDialog is Activity)
                        createFailurePeek("생성할 종류를 선택해 주세요. (폴더/파일)", PeekAlert.Position.Top).peek()
                    else
                        Toast.makeText(this@showNewFileDialog, "생성할 종류를 선택해 주세요. (폴더/파일)", Toast.LENGTH_LONG).show()
                    return@positiveButton
                }
            }
            onFileCreated(file)
            it.dismiss()
        }
    }
}