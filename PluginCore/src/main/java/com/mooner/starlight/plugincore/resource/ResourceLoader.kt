package com.mooner.starlight.plugincore.resource

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.InputStreamReader

class ResourceLoader {
    fun loadXml(path: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        val parser: XmlPullParser = factory.newPullParser()
        return parser
    }
}