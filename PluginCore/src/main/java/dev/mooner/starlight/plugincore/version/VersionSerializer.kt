package dev.mooner.starlight.plugincore.version

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object VersionSerializer: KSerializer<Version> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.mooner.starlight.plugincore.version.Version", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Version {
        return Version.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Version) {
        encoder.encodeString(value.toString())
    }
}