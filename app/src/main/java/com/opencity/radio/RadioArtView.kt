package com.opencity.radio

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Resolution-independent fallback artwork: no protected game art is bundled in the code. */
class RadioArtView(context: Context) : View(context) {
    var accent = Color.rgb(101,233,255)
    var secondary = Color.rgb(243,107,205)
    var title = "OPEN CITY"
    var frequency = "RADIO"
    var landscape = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(Color.rgb(9,11,26))
        paint.shader = RadialGradient(w*.5f,h*.34f,w*.65f,intArrayOf(Color.argb(105,104,30,126),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,paint); paint.shader = null
        val radius = min(w*.27f,h*.35f)
        paint.shader = LinearGradient(0f,h*.08f,0f,h*.6f,intArrayOf(accent,secondary),null,Shader.TileMode.CLAMP)
        c.drawCircle(w*.5f,h*.35f,radius,paint); paint.shader = null
        paint.color = Color.rgb(9,11,26)
        for(i in 0..8) c.drawRect(w*.5f-radius,h*.35f+i*radius*.09f,w*.5f+radius,h*.35f+i*radius*.09f+2+i*.8f,paint)
        paint.color = Color.argb(85,71,174,218); paint.strokeWidth = 1.5f
        for(i in -8..8) c.drawLine(w*.5f+i*20,h*.65f,w*.5f+i*w*.16f,h,paint)
        for(i in 0..7) { val y = h*.65f+h*.35f*(i/7f).pow(2); c.drawLine(0f,y,w,y,paint) }
        // Neon frame, sized from the viewport rather than raster screenshots.
        val path = Path().apply { moveTo(w*.12f,h*.28f); lineTo(w*.88f,h*.28f); lineTo(w*.5f,h*.8f); close() }
        paint.style = Paint.Style.STROKE
        for(stroke in listOf(18f,8f,2.5f)) {
            paint.strokeWidth = stroke; paint.color = accent; paint.alpha = if(stroke>8) 22 else if(stroke>3) 60 else 255
            c.drawPath(path,paint)
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255
        paint.typeface = Typeface.create("sans-serif-condensed",Typeface.BOLD_ITALIC)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(w*.115f,h*.19f)
        while(paint.measureText(title)>w*.9f) paint.textSize -= 1f
        paint.setShadowLayer(14f,0f,0f,secondary); paint.color = Color.rgb(255,223,106)
        c.drawText(title,w*.5f,h*.51f,paint)
        paint.textSize = min(w*.105f,h*.16f); paint.color = accent
        paint.setShadowLayer(13f,0f,0f,accent); c.drawText(frequency,w*.5f,h*.68f,paint); paint.clearShadowLayer()
    }
}
class DialView(context: Context) : View(context) {
    var accent = Color.CYAN
    var frequency = "105.6"
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w=width.toFloat(); val h=height.toFloat()
        p.color=Color.rgb(150,143,171); p.strokeWidth=2f
        for(i in 0..50) { val x=w*.05f+w*.9f*i/50; c.drawLine(x,h*.25f,x,h*(if(i%5==0).65f else .48f),p) }
        val f=frequency.toFloatOrNull() ?: 98f; val x=w*.05f+w*.9f*((f-88)/20).coerceIn(0f,1f)
        p.color=accent; p.strokeWidth=5f; c.drawLine(x,h*.12f,x,h*.72f,p)
        p.textAlign=Paint.Align.CENTER; p.textSize=h*.19f
        c.drawText("88       92       96       100       104       108 FM",w*.5f,h*.97f,p)
    }
}
