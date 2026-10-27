package org.circus.runtime;
import org.circus.graphics.*;
import java.io.*;

/** Pixel contracts needed by the shared renderer, tested on Android Canvas. */
final class GraphicsTest {
    static int run()throws Exception {
        int checks=0;BufferedImage out=new BufferedImage(12,12,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();
        g.setColor(Color.BLACK);g.fillRect(0,0,12,12);g.translate(2,3);g.scale(2,2);g.setColor(Color.WHITE);g.fillRect(1,1,1,1);
        require(out.getRGB(4,5)==0xffffffff&&out.getRGB(3,5)==0xff000000,"translate then scale");checks++;
        BufferedImage clip=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);g=clip.createGraphics();g.clipRect(0,0,4,8);Shape before=g.getClip();g.clipRect(0,0,2,8);g.setColor(Color.WHITE);g.fillRect(0,0,8,8);g.setClip(before);g.setColor(new Color(255,0,0));g.fillRect(2,0,6,8);
        require(clip.getRGB(0,1)==0xffffffff&&clip.getRGB(3,1)==0xffff0000&&clip.getRGB(4,1)==0,"nested clip restoration");checks++;
        g.setClip(null);g.setColor(Color.WHITE);g.fillRect(4,0,4,8);require(clip.getRGB(7,1)==0xffffffff,"clear clip");checks++;
        BufferedImage sprite=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);sprite.setRGB(0,0,0xffff0000);sprite.setRGB(1,0,0xff0000ff);
        g.setColor(new Color(0,0,0,0));g.drawImage(sprite,0,4,4,6,1,0,2,1,null);require(clip.getRGB(1,5)==0xff0000ff,"source rectangle and image alpha independent of pen");checks++;
        g.setComposite(AlphaComposite.SrcOver.derive(.5f));g.drawImage(sprite,4,4,null);int pixel=clip.getRGB(4,4);require((pixel>>>24)==255&&((pixel>>8)&255)>=126&&((pixel>>8)&255)<=128,"source-over opacity");checks++;
        g.setComposite(AlphaComposite.SrcOver);g.drawImage(sprite,7,6,5,7,0,0,2,1,null);require(clip.getRGB(5,6)==0xff0000ff&&clip.getRGB(6,6)==0xffff0000,"mirrored destination");checks++;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(clip,"png",bytes);BufferedImage read=ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));require(java.util.Arrays.equals(clip.getRGB(0,0,8,8,null,0,8),read.getRGB(0,0,8,8,null,0,8)),"PNG pixel roundtrip");checks++;
        out.flush();clip.flush();sprite.flush();read.flush();return checks;
    }
    private static void require(boolean value,String name){if(!value)throw new AssertionError("Android graphics: "+name);}
}
