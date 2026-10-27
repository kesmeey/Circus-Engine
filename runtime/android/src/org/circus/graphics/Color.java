package org.circus.graphics;
public final class Color {
    public static final Color BLACK=new Color(0xff000000,true),WHITE=new Color(0xffffffff,true);
    final int argb;
    public Color(int value,boolean alpha){argb=alpha?value:0xff000000|value;}
    public Color(int r,int g,int b){this(r,g,b,255);}public Color(int r,int g,int b,int a){argb=android.graphics.Color.argb(a,r,g,b);}
    public Color(float r,float g,float b,float a){this(Math.round(r*255),Math.round(g*255),Math.round(b*255),Math.round(a*255));}
}
