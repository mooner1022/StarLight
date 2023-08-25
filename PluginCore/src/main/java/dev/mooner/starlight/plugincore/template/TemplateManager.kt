/*
 * TemplateManager.kt created by Minki Moon(mooner1022) on 22. 1. 3. 오후 6:27
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.template

import dev.mooner.starlight.plugincore.project.Project

class TemplateManager {

    private val templates: MutableSet<Template> = hashSetOf()

    fun addTemplate(template: Template) {
        templates += template
    }

    fun formatCode(project: Project, code: String): String {
        var buffer: String = code
        for (template in templates) {
            buffer = buffer.replace("", template.name)
        }
        //TODO: Add
        return ""
    }
}