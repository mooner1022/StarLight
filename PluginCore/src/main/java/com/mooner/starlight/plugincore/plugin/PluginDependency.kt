package com.mooner.starlight.plugincore.plugin

import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.version.Version
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PluginDependencySerializer::class)
class PluginDependency {

    companion object {
        const val VERSION_ANY = "*"
    }

    val pluginId: String
    val supportedVersion: String
    val isOptional: Boolean

    constructor(
        pluginId: String,
        supportedVersion: String = VERSION_ANY,
        isOptional: Boolean
    ) {
        this.pluginId = pluginId
        this.supportedVersion = supportedVersion
        this.isOptional = isOptional
    }

    constructor(dependency: String) {
        val versionIndex = dependency.indexOf("@")
        if (versionIndex == -1) {
            this.pluginId = dependency
            this.supportedVersion = VERSION_ANY
        } else {
            this.pluginId = dependency.substring(0, versionIndex)
            val version = dependency.substring(versionIndex + 1).replace("?", "")
            this.supportedVersion =
                if (version == VERSION_ANY) VERSION_ANY
                else {
                    if (Version.check(version))
                        version
                    else {
                        Logger.w("Failed to parse supportedVersion from plugin: $pluginId")
                        VERSION_ANY
                    }
                }
        }
        this.isOptional = dependency.endsWith("?")
    }

    override fun toString(): String {
        return (
                if (supportedVersion == VERSION_ANY)
                    pluginId
                else
                    "$pluginId@$supportedVersion"
                ) + if (isOptional) "?" else ""
    }
}

object PluginDependencySerializer: KSerializer<PluginDependency> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.mooner.starlight.plugincore.plugin.PluginDependency", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): PluginDependency {
        return PluginDependency(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: PluginDependency) {
        encoder.encodeString(value.toString())
    }
}