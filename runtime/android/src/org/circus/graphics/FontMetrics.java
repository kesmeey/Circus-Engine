package org.circus.graphics;
import android.graphics.Paint;
public final class FontMetrics {
    private final Paint paint;FontMetrics(Paint paint){this.paint=new Paint(paint);}
    public int stringWidth(String text){return Math.round(paint.measureText(text));}
    public int charWidth(char ch){return stringWidth(String.valueOf(ch));}
}
