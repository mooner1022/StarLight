/*
 * TypedStringSerializer.kt created by Minki Moon(mooner1022) on 4/23/23, 11:43 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/*
 * Ddev.mooner.starlight.FooClass:{"foo": "bar"}
 * PString:foobar
 */

object TypedStringSerializer: KSerializer<TypedString<*>> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TypedString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TypedString<*> {
        val raw = decoder.decodeString()
        val descriptor = raw[0]
        if (descriptor != 'D' && descriptor != 'P')
            throw SerializationException("Invalid TypedString descriptor: $descriptor")

        return TODO()
    }

    override fun serialize(encoder: Encoder, value: TypedString<*>) {
        val descriptor = when(value) {
            is DynamicTypedString -> 'D'
            is PrimitiveTypedString -> 'P'
        }
        encoder.encodeString(descriptor + ":" + value.value)
    }
}