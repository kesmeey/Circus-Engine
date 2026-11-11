package org.circus.core;

import java.io.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

/** Self-authored scene bytes exercise PH lifecycle and state through the real host. */
public final class Dc4SceneTests {
    private static int n;
    private static void check(boolean ok,String why){n++;if(!ok)throw new AssertionError(why);}
    private static byte[] raw(String s){return s.getBytes(Binary.SJIS);}
    private static byte[] black(int width,int height)throws Exception{
        ByteArrayOutputStream packed=new ByteArrayOutputStream();try(DeflaterOutputStream z=new DeflaterOutputStream(packed)){byte[] row=new byte[width*3+1];for(int y=0;y<height;y++)z.write(row);}
        byte[] h=new byte[20];h[0]='C';h[1]='R';h[2]='X';h[3]='G';h[8]=(byte)width;h[9]=(byte)(width>>>8);h[10]=(byte)height;h[11]=(byte)(height>>>8);h[12]=2;h[14]=1;
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(h);out.write(packed.toByteArray());return out.toByteArray();
    }
    private static Dc4Host host()throws Exception{
        Map<String,byte[]> f=new HashMap<>();f.put("AdvData/GRP/NAMES.DAT",new byte[20]);f.put("AdvData/MES/START.MES",new byte[]{0,0,0,0,(byte)0xb6,(byte)0xab,0,0,0,0});byte[] image=black(1920,1080);
        for(String path:new String[]{"OWNED","BG/BLACK","SYSTEM/COMMENTBACK","SYSTEM/COMUCHIP","SYSTEM/COMUSTAMP","SYSTEM/EFFECT"})f.put("AdvData/GRP/"+path+".CRX",image);
        GameResources data=new GameResources(new GameResources.Storage(){public Set<String> paths(){return f.keySet();}public byte[] read(String p,int limit)throws IOException{byte[] b=f.get(p);if(b==null)throw new FileNotFoundException(p);if(b.length>limit)throw new IOException("fixture size limit");return b.clone();}public Archive.Source source(String p)throws IOException{byte[] b=read(p,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long at,int count)throws IOException{Binary.range(at,count,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+count);}};}});
        Profile profile=Profiles.get("dc4ph-abb6");Dc4Host h=new Dc4Host(new CircusResources(data,profile),TextCodec.identity(),false);h.attach(new Variables(h,profile.variableModel));return h;
    }
    public static int run()throws Exception{
        n=0;Dc4Host h=host();Dc4SceneCommands s=new Dc4SceneCommands();
        s.command(h,"ph.scene.resource",new int[]{1},raw("OWNED"));s.command(h,"ph.scene.center",new int[]{0,0},new byte[0]);s.command(h,"ph.scene.background",new int[0],raw("OWNED"));s.command(h,"ph.scene.fit_background",new int[4],new byte[0]);s.prepare(h);h.scene.publish();
        check(h.scene.slots[1].x==-320&&h.scene.slots[1].y==-180,"PH37 centers the original source rectangle");
        check(h.scene.slots[1].centered,"PH B4 fit survives the shared publish boundary");
        Dc4Geometry.Fit fit=Dc4Geometry.fit(1920,1080);double x=h.scene.slots[1].x+960-960*fit.scaleX-fit.left,y=h.scene.slots[1].y+540-540*fit.scaleY-fit.top;
        check(Math.abs(x)<.0001&&Math.abs(y)<.0001,"Native source-half pivot cancels the37/B4 raw center");
        h.baseCommand("scene.background_move",new int[]{11,22,1,0},new byte[0]);check(h.scene.slots[1].centered&&h.scene.slots[1].x==11&&h.scene.slots[1].y==22,"PH89 changes position without clearing B4 mode");
        s.command(h,"ph.scene.resource",new int[]{1},raw("OWNED"));s.command(h,"ph.scene.background",new int[0],raw("OWNED"));s.prepare(h);h.scene.publish();check(!h.scene.slots[1].centered&&h.scene.slots[1].x==0,"A new ordinary background clears prior native fit");
        s.command(h,"scene.character_transform",new int[]{128,0,0,0},new byte[0]);check(h.scene.slots[4].x==128,"PH actor position remains pixels");
        s.command(h,"scene.character_transform",new int[]{100,640,360,16},new byte[0]);check(h.scene.slots[4].scaleX==100&&h.scene.slots[4].pivotX==640,"PH actor scale and explicit pivot are separate from background fit");
        s.command(h,"scene.character_transform",new int[]{0,0,0,128},new byte[0]);check(h.scene.pending[4].equals("-")&&h.scene.pending[9].equals("-"),"PH negative flag clears actor plus its companion");
        s.command(h,"scene.character_transform",new int[]{0,0,0,255},new byte[0]);check(h.scene.pending[4].equals("-"),"PH255 transform is the native retained state");
        s.command(h,"ph.scene.comments",new int[]{99,2,0,0},new byte[0]);check(s.commentType==2&&h.phSpeech.commentType()==2&&!s.commentsVisible,"Original START initializes a dormant type2 communication controller");
        h.phSpeech.command(h,"ph.comment.text",new int[0],raw("owned caption"));check(h.phSpeech.commentCount()==1,"Type2 initialization admits native caption queue data");
        s.command(h,"ph.scene.comments",new int[]{7,0,0,0},new byte[0]);check(s.commentsVisible,"PH AB7 enables queued communication display");
        s.command(h,"ph.scene.comments",new int[]{0,0,0,0},new byte[0]);check(!s.commentsVisible&&h.phSpeech.commentCount()==1,"PH AB0 hides the type2 panel while retaining its queue");
        s.command(h,"ph.scene.comments",new int[]{1,0,0,0},new byte[0]);check(s.commentsVisible&&h.phSpeech.commentCount()==1,"PH AB1 type2 view reset retains source messages");
        ByteArrayOutputStream saved=new ByteArrayOutputStream();s.save(new DataOutputStream(saved));Dc4SceneCommands restored=new Dc4SceneCommands();restored.load(new DataInputStream(new ByteArrayInputStream(saved.toByteArray())));check(restored.commentsVisible&&restored.commentType==2,"PH scene snapshot retains independent communication state");
        s.command(h,"ph.scene.comments",new int[]{-1,0,0,0},new byte[0]);check(!s.commentsVisible&&h.phSpeech.commentCount()==0,"PH AB-1 clears the native communication queue");
        s.command(h,"ph.scene.resource",new int[]{22},raw("SYSTEM/EFFECT"));s.command(h,"ph.scene.effect",new int[]{1,2},new byte[0]);s.prepare(h);check(h.scene.quads.size()==1&&h.scene.quads.get(0).width==732,"Portable PH screen effect uses the actual prepared atlas rectangle");s.elapse(h,400);check(h.scene.quads.isEmpty(),"Approximate PH screen effect has finite native-derived lifetime");
        try{s.command(h,"ph.scene.gamma",new int[]{30,0,0,0},new byte[0]);throw new AssertionError("Unreviewed gamma silently succeeded");}catch(IOException expected){n++;}
        h.scene.slots[1].x=91;h.scene.slots[1].y=32;s.command(h,"ph.scene.background",new int[0],raw(":n"));
        check(s.cameraLatch&&!h.scene.novelMode,"PH49:n retains the native camera latch without selecting an empty novel message template");
        s.command(h,"ph.scene.resource",new int[]{1},raw("OWNED"));check(h.scene.slots[1].x==91&&h.scene.slots[1].y==32&&!h.scene.novelMode,"PH:n preserves the background object only during image binding");
        ByteArrayOutputStream latchBytes=new ByteArrayOutputStream();s.save(new DataOutputStream(latchBytes));Dc4SceneCommands latchCopy=new Dc4SceneCommands();latchCopy.load(new DataInputStream(new ByteArrayInputStream(latchBytes.toByteArray())));check(latchCopy.cameraLatch,"PH scene snapshots preserve the native camera latch separately from message mode");
        s.command(h,"ph.scene.background",new int[0],raw(":r"));s.command(h,"ph.scene.resource",new int[]{1},raw("OWNED"));check(!s.cameraLatch&&!h.scene.novelMode&&h.scene.slots[1].x==0,"PH49:r restores normal background-object reset while retaining regular dialogue");
        return n;
    }
}
