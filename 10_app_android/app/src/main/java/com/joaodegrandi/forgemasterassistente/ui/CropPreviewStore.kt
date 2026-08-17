package com.joaodegrandi.forgemasterassistente.ui

import android.graphics.Bitmap

object CropPreviewStore {
    private var bitmap: Bitmap? = null

    @Synchronized
    fun replace(preview: Bitmap) {
        bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
        bitmap = preview
    }

    @Synchronized
    fun take(): Bitmap? = bitmap?.takeUnless(Bitmap::isRecycled).also { bitmap = null }

    @Synchronized
    fun clear() {
        bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
        bitmap = null
    }
}
