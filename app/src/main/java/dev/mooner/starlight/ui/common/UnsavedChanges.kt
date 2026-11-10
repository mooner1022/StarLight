/*
 * UnsavedChanges.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.common

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import dev.mooner.starlight.R

/**
 * Asks before a screen with a save button is left with changes that weren't saved, whether through the
 * back key or [backButton].
 *
 * [hasChanges] says whether there are any. [save] saves them and returns false when it couldn't, in which
 * case the screen stays open with what it told the user.
 */
fun ComponentActivity.confirmLeavingUnsaved(backButton: View, hasChanges: () -> Boolean, save: () -> Boolean) {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!hasChanges()) {
                finish()
                return
            }
            showSheet {
                title = getString(R.string.unsaved_title)
                message = getString(R.string.unsaved_message)
                icon = R.drawable.ic_round_edit_24
                positive(R.string.save) {
                    if (save())
                        finish()
                }
                negative(R.string.unsaved_discard) { finish() }
            }
        }
    })
    backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
}
