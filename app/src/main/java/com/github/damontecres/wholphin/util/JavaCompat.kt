@file:JvmName("JavaCompat")

package com.github.damontecres.wholphin.util

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset

/*
 * Replacements for newer Java APIs that are missing on older Android versions.
 *
 * The build rewrites calls to these in NewPipe Extractor to point here, see `CompatCallRewriter` in build.gradle.kts
 */

fun urlEncode(
    value: String,
    charset: Charset,
): String = URLEncoder.encode(value, charset.name())

fun urlDecode(
    value: String,
    charset: Charset,
): String = URLDecoder.decode(value, charset.name())
