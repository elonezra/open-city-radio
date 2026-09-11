package com.opencity.radio

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder

class RadioApplication : Application(), ImageLoaderFactory {
    val repository by lazy { ContentRepository(this) }
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()); add(ImageDecoderDecoder.Factory()) }.build()
}
