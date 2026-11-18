package com.userhunter.app.service

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Streams imported files line-by-line; does not retain the whole list in memory. */
internal class WordListReader(
    private val contentResolver: ContentResolver,
    private val uri: String
) {
    suspend fun streamFrom(startIndex: BigInteger, onLine: suspend (BigInteger, String) -> Boolean) =
        withContext(Dispatchers.IO) {
            val parsed = Uri.parse(uri)
            val stream = contentResolver.openInputStream(parsed) ?: return@withContext
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var index = BigInteger.ZERO
                while (index < startIndex) {
                    if (reader.readLine() == null) return@withContext
                    index += BigInteger.ONE
                }
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!onLine(index, line)) break
                    index += BigInteger.ONE
                }
            }
        }
}
