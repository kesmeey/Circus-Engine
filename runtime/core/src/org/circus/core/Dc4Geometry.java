package org.circus.core;

import java.io.IOException;

/** PH B4/428958 fits only the explicitly marked background. */
public final class Dc4Geometry {
    public static final int WIDTH=1280,HEIGHT=720;
    private Dc4Geometry(){}
    public static final class Fit {
        public final int xBasisPoints,yBasisPoints;
        public final float scaleX,scaleY;
        public final double left,top;
        private Fit(int width,int height){
            // 4289AC..4289DE truncates the integer division before storing
            // float scale values. 4432E0 divides both axes by10000 only when
            // both are >=1000; the ordinary object scale otherwise uses100.
            xBasisPoints=12800000/width;yBasisPoints=7200000/height;
            float unit=xBasisPoints>=1000&&yBasisPoints>=1000?10000f:100f;
            scaleX=xBasisPoints/unit;scaleY=yBasisPoints/unit;
            // 444030(1): center using the scaled source rectangle, keeping
            // the CRX origin and the object's position/pivot separate.
            left=(WIDTH-width*(double)scaleX)/2;top=(HEIGHT-height*(double)scaleY)/2;
        }
    }
    public static Fit fit(int width,int height)throws IOException{
        if(width<=0||height<=0)throw new IOException("Invalid PH background geometry");
        return new Fit(width,height);
    }
}
