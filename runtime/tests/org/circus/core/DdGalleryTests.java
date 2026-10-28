package org.circus.core;
import java.io.*;
import java.util.*;

/** Synthetic catalogue: checks native unlock timing without proprietary assets. */
final class DdGalleryTests {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static int run()throws Exception{
        checks=0;ByteArrayOutputStream names=new ByteArrayOutputStream();
        String[][] tables={{},{},{},{"bg\\test_a","bg\\test_b","bg\\test_c"},{"chapter_a","chapter_b","chapter_c"}};
        for(String[] table:tables){names.write(table.length);names.write(new byte[3]);for(String name:table)names.write(Arrays.copyOf(name.getBytes(Binary.SJIS),32));}
        byte[] data=names.toByteArray();GameResources.Storage storage=new GameResources.Storage(){public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}public byte[] read(String p,int limit){return data.clone();}public Archive.Source source(String p){return new Archive.Source(){public long size(){return data.length;}public byte[] read(long offset,int size)throws IOException{Binary.range(offset,size,data.length);return Arrays.copyOfRange(data,(int)offset,(int)offset+size);}};}};
        CircusResources resources=new CircusResources(new GameResources(storage),Profiles.get("dc3dd-a5a8"));
        Dc3Host h=new Dc3Host(resources,TextCodec.identity(),true);h.attach(new Variables(h));
        h.command("image.load",new int[]{1},"BG/TEST_A.CRX".getBytes(Binary.SJIS));
        check(h.read(97,0)==0,"DD preload alone cannot unlock an unseen CG");
        h.command("scene.background",new int[0],"BG/TEST_A.CRX".getBytes(Binary.SJIS));
        check(h.read(97,0)==1&&h.read(97,1)==0,"DD displayed CG resolves exact catalogue index, case and extension");
        h.gallery.image(h.state,"BG/TEST_B_EXTRA");check(h.read(97,1)==0,"DD CG prefix is not a catalogue match");
        long revision=h.state.progressRevision;h.gallery.image(h.state,"bg\\test_a");check(h.state.progressRevision==revision,"Repeated display does not dirty persistent storage");
        byte[] code={0,0,0,0,(byte)0xa8,(byte)0xa5,0,0,0,0};
        h.position(new MesScript("chapter_a",code,resources.profile),7);check(h.read(98,0)==0,"Entering a DD chapter does not mark it completed");
        h.beforeTransfer(0,"chapter_b");check(h.read(98,0)==1&&h.read(98,1)==0,"DD transfer unlocks outgoing chapter, not incoming chapter");
        Dc3Host recovered=new Dc3Host(resources,TextCodec.identity(),true);recovered.scene.pending[1]="bg\\test_c";recovered.scene.published.layers[1].resource="bg\\test_a";
        recovered.history.append("","line A","","chapter_a",10);Dc3Scene.Frame seen=new Dc3Scene.Frame();seen.layers[1].resource="bg\\test_b";ByteArrayOutputStream frame=new ByteArrayOutputStream();seen.save(new DataOutputStream(frame));recovered.history.entries.get(0).scene=frame.toByteArray();
        recovered.gallery.recover(recovered);check(recovered.read(97,0)==1&&recovered.read(97,1)==1&&recovered.read(97,2)==0,"Legacy recovery uses retained displayed frames and excludes queued preloads");
        check(recovered.read(98,0)==0,"A lone history chapter is not proof of completion");
        recovered.history.append("","line B","","chapter_b",10);recovered.gallery.recover(recovered);check(recovered.read(98,0)==1&&recovered.read(98,1)==0&&recovered.read(98,2)==0,"History crossing recovers only the completed predecessor");
        Dc3State global=new Dc3State(true);global.write(99,7,37);global.write(97,2,1);global.mergeGallery(recovered.state);
        check(global.read(97,0)==1&&global.read(97,1)==1&&global.read(97,2)==1&&global.read(99,7)==37,"Gallery merge preserves newer flags and current preferences");
        byte[] saved=global.settingsSnapshot();Dc3State restarted=new Dc3State(true);restarted.restoreSettings(saved);check(restarted.read(97,1)==1&&restarted.read(98,0)==1&&restarted.read(98,1)==0,"Existing persistent layout retains exact earned flags");
        return checks;
    }
}
