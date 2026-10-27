package org.circus.graphics;
import android.graphics.*;

/** Canvas implementation of the small drawing contract used by the shared UI.
 * Clips are held in device coordinates so restoring a clip can expand it again. */
public final class Graphics2D {
    private final Canvas canvas;private final Matrix matrix=new Matrix();private Shape clip;
    private Color color=Color.BLACK;private Font font=new Font("sans-serif",0,12);private AlphaComposite composite=AlphaComposite.SrcOver;private boolean filter;
    Graphics2D(Bitmap image){canvas=new Canvas(image);}
    public void dispose(){}
    public void setColor(Color color){this.color=color;}public void setFont(Font font){this.font=font;}
    public void setComposite(Composite value){if(!(value instanceof AlphaComposite))throw new IllegalArgumentException("Unsupported composite");composite=(AlphaComposite)value;}
    public Composite getComposite(){return composite;}
    public void setRenderingHint(int key,int value){if(key==RenderingHints.KEY_INTERPOLATION)filter=value==RenderingHints.VALUE_INTERPOLATION_BILINEAR;}
    public AffineTransform getTransform(){return new AffineTransform(matrix);}public void setTransform(AffineTransform transform){matrix.set(transform.matrix);}
    public void translate(double x,double y){matrix.preTranslate((float)x,(float)y);}public void scale(double x,double y){matrix.preScale((float)x,(float)y);}
    public Shape getClip(){return clip==null?null:new Shape(clip.path);}public void setClip(Shape shape){clip=shape==null?null:new Shape(shape.path);}
    public void clipRect(int x,int y,int width,int height){Path path=new Path();if(width>0&&height>0)path.addRect(x,y,x+width,y+height,Path.Direction.CW);path.transform(matrix);if(clip!=null)path.op(clip.path,Path.Op.INTERSECT);clip=new Shape(path);}
    private Paint paint(boolean image){Paint p=new Paint();p.setFilterBitmap(filter);p.setColor(color.argb);p.setAlpha(Math.round((image?255:color.argb>>>24)*composite.alpha));p.setTextSize(font.size);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));return p;}
    public FontMetrics getFontMetrics(){Paint p=paint(false);p.setAntiAlias(true);return new FontMetrics(p);}
    private int begin(){int count=canvas.save();if(clip!=null)canvas.clipPath(clip.path);canvas.concat(matrix);return count;}
    public void fillRect(int x,int y,int w,int h){if(w<=0||h<=0)return;int count=begin();try{canvas.drawRect(x,y,x+w,y+h,paint(false));}finally{canvas.restoreToCount(count);}}
    public void drawRect(int x,int y,int w,int h){int count=begin();try{Paint p=paint(false);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);canvas.drawRect(x+.5f,y+.5f,x+w+.5f,y+h+.5f,p);}finally{canvas.restoreToCount(count);}}
    public void drawString(String text,int x,int y){int count=begin();try{Paint p=paint(false);p.setAntiAlias(true);canvas.drawText(text,x,y,p);}finally{canvas.restoreToCount(count);}}
    public boolean drawImage(BufferedImage image,int x,int y,Object observer){return drawImage(image,x,y,x+image.getWidth(),y+image.getHeight(),0,0,image.getWidth(),image.getHeight(),observer);}
    public boolean drawImage(BufferedImage image,int x,int y,int w,int h,Object observer){return drawImage(image,x,y,x+w,y+h,0,0,image.getWidth(),image.getHeight(),observer);}
    public boolean drawImage(BufferedImage image,int x1,int y1,int x2,int y2,int sx1,int sy1,int sx2,int sy2,Object observer){
        if(x1==x2||y1==y2||sx1==sx2||sy1==sy2)return true;int count=begin();try{canvas.translate(x1,y1);canvas.scale((x2-x1)/(float)(sx2-sx1),(y2-y1)/(float)(sy2-sy1));canvas.translate(-sx1,-sy1);canvas.clipRect(Math.min(sx1,sx2),Math.min(sy1,sy2),Math.max(sx1,sx2),Math.max(sy1,sy2));canvas.drawBitmap(image.bitmap,0,0,paint(true));}finally{canvas.restoreToCount(count);}return true;
    }
}
