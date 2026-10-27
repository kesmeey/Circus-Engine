package org.circus.graphics;
import android.graphics.Matrix;
public final class AffineTransform {
    final Matrix matrix;
    public AffineTransform(){matrix=new Matrix();}AffineTransform(Matrix matrix){this.matrix=new Matrix(matrix);}
}
