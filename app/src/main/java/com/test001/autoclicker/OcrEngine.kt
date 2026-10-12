package com.test001.autoclicker

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * ML Kit on-device text recognition. Reused — originally shipped in
 * panelforsale080-byte/AutoJoystick commit c7c8bee (later improved).
 *
 * Returns list of (text, bounding-box) so the caller can decide what to
 * tap on — e.g. "PLAY", "CLAIM", "OK", or any user-supplied keyword.
 */
object OcrEngine {

    data class Hit(val text: String, val box: Rect)

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Recognize all text on screen, return matched (text, Rect) for anything containing [keyword]. */
    fun recognize(bitmap: Bitmap, keyword: String, onResult: (List<Hit>) -> Unit) {
        try {
            val img = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(img)
                .addOnSuccessListener { result ->
                    val hits = ArrayList<Hit>()
                    val kw = keyword.trim().lowercase()
                    for (block in result.textBlocks) {
                        val box = block.boundingBox ?: continue
                        val txt = block.text.trim()
                        if (kw.isEmpty() || txt.lowercase().contains(kw)) {
                            hits.add(Hit(txt, Rect(box)))
                        }
                    }
                    Log.i("T1OCR", "frames=${result.textBlocks.size} hits=${hits.size} kw='$kw'")
                    onResult(hits)
                }
                .addOnFailureListener { e ->
                    Log.w("T1OCR", "ocr fail: ${e.message}")
                    onResult(emptyList())
                }
        } catch (t: Throwable) { onResult(emptyList()) }
    }
}
