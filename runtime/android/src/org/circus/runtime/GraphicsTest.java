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
        scrollCreditsCrop();checks++;
        out.flush();clip.flush();sprite.flush();read.flush();return checks;
    }
    private static void scrollCreditsCrop(){
        // Distinct page/row colors expose scaling, gaps and repeated rows at the seam.
        BufferedImage current=new BufferedImage(1024,600,BufferedImage.TYPE_INT_ARGB),next=new BufferedImage(1024,600,BufferedImage.TYPE_INT_ARGB),viewport=new BufferedImage(1024,576,BufferedImage.TYPE_INT_ARGB);
        try{
            int[] rows=new int[1024*600];for(int y=0;y<600;y++)java.util.Arrays.fill(rows,y*1024,(y+1)*1024,0xff100000|y);current.setRGB(0,0,1024,600,rows,0,1024);
            for(int y=0;y<600;y++)java.util.Arrays.fill(rows,y*1024,(y+1)*1024,0xff200000|y);next.setRGB(0,0,1024,600,rows,0,1024);
            Graphics2D g=viewport.createGraphics();g.drawImage(current,0,0,1024,600,0,0,1024,600,null);
            require(viewport.getRGB(0,0)==0xff100000&&viewport.getRGB(1023,575)==(0xff100000|575),"600-row image clips at viewport bottom without scaling");
            for(int offset:new int[]{24,300,599}){
                g.setColor(Color.BLACK);g.fillRect(0,0,1024,576);int split=600-offset;
                g.drawImage(current,0,0,1024,split,0,offset,1024,600,null);
                g.drawImage(next,0,split,1024,600,0,0,1024,offset,null);
                require(viewport.getRGB(0,0)==(0xff100000|offset),"credits current-page source row "+offset);
                if(split<576)require(viewport.getRGB(1023,split-1)==(0xff100000|599)&&viewport.getRGB(1023,split)==0xff200000,"credits page join has no gap or repeated row "+offset);
                int expected=575<split?(0xff100000|(offset+575)):(0xff200000|(575-split));
                require(viewport.getRGB(1023,575)==expected,"credits next page is clipped at viewport bottom "+offset);
            }
        }finally{current.flush();next.flush();viewport.flush();}
    }
    private static void require(boolean value,String name){if(!value)throw new AssertionError("Android graphics: "+name);}
}
