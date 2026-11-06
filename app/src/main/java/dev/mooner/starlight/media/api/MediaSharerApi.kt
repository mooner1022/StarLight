/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.api

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project

/** Gives each project its own `MediaSharer` global, which logs to the project. */
class MediaSharerApi : Api<MediaSharer>() {

    override val name: String = NAME

    override val objects: List<ApiObject> =
        getApiObjects<MediaSharer>()

    override val instanceClass: Class<MediaSharer> =
        MediaSharer::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        MediaSharer(project)

    companion object {
        /** The plugin's name; unlike the original module's `MediaSender`, so scripts still requiring it don't clash. */
        const val NAME = "MediaSharer"
    }
}
