package org.circus.graphics;
import android.graphics.*;
import java.io.*;
public final class ImageIO {
    public static BufferedImage read(InputStream stream)throws IOException{BitmapFactory.Options options=new BitmapFactory.Options();options.inMutable=true;options.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap bitmap=BitmapFactory.decodeStream(stream,null,options);if(bitmap==null)throw new IOException("Invalid saved preview image");return new BufferedImage(bitmap);}
    public static boolean write(BufferedImage image,String format,OutputStream out)throws IOException{if(!format.equalsIgnoreCase("png"))throw new IOException("Only PNG preview output is supported");if(!image.bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Could not encode preview");return true;}
}
