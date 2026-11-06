/*
 * FileSerializer.kt created by Minki Moon(mooner1022) on 22. 12. 26. 오후 3:27
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File

class FileSerializer: KSerializer<File> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.io.File", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): File {
        return File(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: File) {
        encoder.encodeString(value.absolutePath)
    }
}