package dev.mooner.starlight.plugincore.plugin

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PluginCategory.Serializer::class)
enum class PluginCategory(val id: String) {
    MESSENGER   ("messenger"),
    ENGINE      ("engine"),
    LIBRARY     ("library"),
    UTILITY     ("utility"),
    DEVTOOLS    ("devtools"),
    OTHER       ("other");

    companion object {
        val DEFAULT = OTHER

        fun fromId(id: String?): PluginCategory =
            entries.find { it.id.equals(id?.trim(), ignoreCase = true) } ?: DEFAULT
    }

    internal object Serializer : KSerializer<PluginCategory> {

        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("dev.mooner.starlight.plugincore.plugin.PluginCategory", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): PluginCategory =
            fromId(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: PluginCategory) =
            encoder.encodeString(value.id)
    }
}
