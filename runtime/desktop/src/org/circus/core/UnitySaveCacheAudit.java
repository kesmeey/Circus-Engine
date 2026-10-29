package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import javax.imageio.ImageIO;

/** Save integrity and old menu-snapshot regressions against original game scripts.
 * All writes stay under the explicitly supplied, newly created output directory. */
public final class UnitySaveCacheAudit {
    private final UnityCircusResources data;
    private final Path output;
    private final StringBuilder log=new StringBuilder();
    private DesktopUnity engine;
    private int checks;
    private UnitySaveCacheAudit(UnityCircusResources data,Path output){this.data=data;this.output=output;}
    private DesktopUnity create(Path root)throws Exception {
        Constructor<DesktopUnity> constructor=DesktopUnity.class.getDeclaredConstructor(UnityCircusResources.class,boolean.class,Path.class,boolean.class);
        constructor.setAccessible(true);return constructor.newInstance(data,true,root,true);
    }
    private void check(boolean value,String description)throws IOException {if(!value)throw new IOException(description);checks++;log.append("PASS ").append(description).append('\n');}
    private void release()throws Exception {if(engine!=null){Method method=DesktopUnity.class.getDeclaredMethod("releaseImages");method.setAccessible(true);method.invoke(engine);engine=null;}}
    private boolean action(String name){for(DesktopEngine.Control control:engine.controls())if(control.action.equals(name))return true;return false;}
    private void tick()throws Exception {
        for(int channel=0;channel<6;channel++)if(engine.audioActive(channel)&&!engine.audioLoop(channel))engine.audioCompleted(channel,engine.audioGeneration(channel));
        if(!engine.movie().isEmpty())engine.mediaCompleted(engine.ticket());engine.tick(1000);
        if(engine.halted())throw new IOException("Unexpected script halt");
    }
    private void menu(String script)throws Exception {
        for(int n=0;n<1000;n++){tick();if(engine.menuWaiting()){if(engine.scriptName().equals(script))return;throw new IOException("Expected "+script+", got "+engine.scriptName());}}
        throw new IOException("Menu timeout "+script);
    }
    private void story()throws Exception {
        for(int n=0;n<1000;n++){tick();if(engine.textWaiting())return;if(engine.menuWaiting()){if(action("return"))engine.choose("return");else throw new IOException("Unexpected story-return menu "+engine.scriptName());}}
        throw new IOException("Story timeout");
    }
    private void openSave()throws Exception {engine.click(1,1,true);menu("advmenu");engine.choose("save");menu("save");}
    private void writeSlot(int number)throws Exception {
        if(!engine.menuWaiting()||!engine.scriptName().equals("save"))openSave();
        Dc3rHost.Button selected=null;for(Dc3rHost.Button button:engine.host.buttons())if(button.action.equals("selsave")&&button.flagValue==number){selected=button;break;}
        if(selected==null)throw new IOException("Original save slot unavailable "+number);
        engine.click((selected.left+selected.right)/2,(selected.top+selected.bottom)/2,false);
        for(int n=0;n<1000;n++){tick();if(engine.menuWaiting()){if(action(":y")){engine.choose(":y");continue;}if(action("return"))return;}}
        throw new IOException("Save completion timeout");
    }
    private Path slot(int number){return engine.saveDirectory.resolve("slot-"+number+".sav");}
    private void saveImage(BufferedImage image,Path target)throws IOException {try(OutputStream out=Files.newOutputStream(target)){if(!ImageIO.write(image,"png",out))throw new IOException("Cannot encode audit image");}finally{image.flush();}}
    private static boolean samePixels(BufferedImage first,BufferedImage second){return first.getWidth()==second.getWidth()&&first.getHeight()==second.getHeight()&&Arrays.equals(first.getRGB(0,0,first.getWidth(),first.getHeight(),null,0,first.getWidth()),second.getRGB(0,0,second.getWidth(),second.getHeight(),null,0,second.getWidth()));}
    private void seed(Path source)throws Exception {engine=create(output.resolve("phone-saves"));engine.restore(source);engine.tick(0);check(engine.textWaiting(),"seed restores an original stable dialogue");}
    private void record(Path seed)throws Exception {
        seed(seed);BufferedImage scene=engine.preview();try{Files.write(output.resolve("legacy-thumbnail.png"),DesktopSave.thumbnail(scene,256,144));}finally{scene.flush();}
        String script=engine.scriptName(),body=engine.body();int offset=engine.scriptOffset();
        openSave();Files.write(output.resolve("legacy-menu.uni"),engine.snapshot());saveImage(engine.render(),output.resolve("legacy-menu.png"));
        writeSlot(0);check(DesktopSave.decode(Files.readAllBytes(slot(0))).preview.length>0,"legacy original SAVE emitted thumbnail");
        story();check(engine.scriptName().equals(script)&&engine.scriptOffset()==offset&&engine.body().equals(body),"legacy menu returns to exact story");
        Files.write(output.resolve("legacy-slot.sav"),Files.readAllBytes(slot(0)));
    }
    private void verify(Path seed,Path legacy)throws Exception {
        seed(seed);String first=engine.body();openSave();writeSlot(0);writeSlot(1);
        check(engine.host.availableSaves.containsKey(0)&&engine.host.availableSaves.containsKey(1),"saving a second slot retains both original LOAD entries");
        check(engine.host.availableSaves.get(0).caption.equals(first)&&engine.host.availableSaves.get(1).caption.equals(first),"new slot labels match their saved dialogue");
        byte[] firstOne=Files.readAllBytes(slot(1));story();engine.click(1,1,false);story();String next=engine.body();check(!next.equals(first),"overwrite fixture advances to a different original dialogue");
        writeSlot(0);check(engine.host.availableSaves.get(0).caption.equals(next)&&engine.host.availableSaves.get(1).caption.equals(first),"overwriting one slot refreshes its label and preserves other slots");
        check(Arrays.equals(firstOne,Files.readAllBytes(slot(1))),"saving another slot leaves existing save bytes unchanged");
        byte[] corrupt=firstOne.clone();corrupt[corrupt.length-33]^=1;Files.write(slot(1),corrupt);writeSlot(0);
        check(Arrays.equals(corrupt,Files.readAllBytes(slot(1))),"an unrelated damaged slot neither blocks nor gets rewritten by saving");
        boolean rejected=false;try{engine.restore(slot(1));}catch(IOException expected){rejected=expected.getMessage().contains("checksum");}
        check(rejected,"loading a damaged cached slot still verifies its checksum");Files.write(slot(1),firstOne);
        DesktopSave replacement=DesktopSave.decode(firstOne);replacement.caption="Externally imported replacement with a distinct caption";replacement.created+=60000;
        Files.write(slot(1),replacement.encode());Files.setLastModifiedTime(slot(1),FileTime.fromMillis(System.currentTimeMillis()+2000));engine.restore(engine.snapshot());
        check(engine.host.availableSaves.get(1).caption.equals(replacement.caption),"restore detects externally replaced slot metadata");
        Files.delete(slot(1));engine.restore(engine.snapshot());check(!engine.host.availableSaves.containsKey(1),"restore removes externally deleted slots from original LOAD availability");
        story();Set<Integer> available=new HashSet<>(engine.host.availableSaves.keySet());if(!action(":qs")&&action(":n"))engine.choose(":n");check(action(":qs"),"original message toolbar exposes quick save");engine.choose(":qs");
        check(Files.isRegularFile(engine.saveDirectory.resolve("quick.sav"))&&engine.host.availableSaves.keySet().equals(available),"quick save creates its own file without inventing a numbered slot");
        DesktopSave quick=DesktopSave.decode(Files.readAllBytes(engine.saveDirectory.resolve("quick.sav")));check(quick.caption.equals(next),"quick save retains current story caption");
        release();engine=create(output.resolve("legacy-load-saves"));engine.restore(Files.readAllBytes(legacy.resolve("legacy-menu.uni")));
        check(engine.menuWaiting()&&engine.scriptName().equals("save"),"0.13.9 nested menu snapshot retains original SAVE controls");
        BufferedImage actual=engine.render(),expected=ImageIO.read(legacy.resolve("legacy-menu.png").toFile());try{check(samePixels(actual,expected),"0.13.9 nested menu snapshot restores exact rendered pixels");}finally{actual.flush();expected.flush();}
        writeSlot(0);DesktopSave saved=DesktopSave.decode(Files.readAllBytes(slot(0)));actual=saved.image();expected=ImageIO.read(legacy.resolve("legacy-thumbnail.png").toFile());try{check(actual!=null&&samePixels(actual,expected),"old menu preview remains pixel-identical when saved by updated runtime");}finally{if(actual!=null)actual.flush();expected.flush();}
        story();engine.restore(legacy.resolve("legacy-slot.sav"));engine.tick(0);check(engine.textWaiting()&&engine.body().equals(first),"0.13.9 story save remains playable after updated menu save");
        BufferedImage scene=engine.preview();byte[] expectedPreview;try{expectedPreview=DesktopSave.thumbnail(scene,256,144);}finally{scene.flush();}
        openSave();writeSlot(0);actual=DesktopSave.decode(Files.readAllBytes(slot(0))).image();expected=ImageIO.read(new ByteArrayInputStream(expectedPreview));try{check(actual!=null&&samePixels(actual,expected),"new reduced retained preview matches original thumbnail pixels");}finally{if(actual!=null)actual.flush();expected.flush();}
    }
    private void verifyLongHistory(Path oldMenu,Path oldStory)throws Exception {
        release();engine=create(output.resolve("long-history-saves"));engine.restore(oldStory);engine.tick(0);
        String body=engine.body(),script=engine.scriptName();int offset=engine.scriptOffset(),history=engine.host.history.size();
        check(history==1000,"legacy compatibility fixture reaches the retained 1000-line history limit");
        engine.restore(Files.readAllBytes(oldMenu));check(engine.menuWaiting()&&engine.scriptName().equals("save")&&engine.host.history.size()==history,"0.13.9 large nested menu restores its complete history");
        writeSlot(0);engine.restore(slot(0));engine.tick(0);
        check(engine.textWaiting()&&engine.scriptName().equals(script)&&engine.scriptOffset()==offset&&engine.body().equals(body),"saving a restored old large menu retains exact underlying story position");
        check(engine.host.history.size()==history&&engine.host.canRewindHistory(0),"old large menu save retains earlier rewind checkpoints");
        engine.host.rewindHistory(0,engine.script);check(engine.textWaiting()&&engine.host.history.size()==1,"earliest old-history checkpoint still rewinds after resaving");
    }
    public static void main(String[] args)throws Exception {
        if(args.length<4||args.length>7)throw new IllegalArgumentException("Usage: UnitySaveCacheAudit record|verify GAME OUTPUT SEED [LEGACY_FIXTURE [LARGE_MENU LARGE_STORY]]");
        Path output=Paths.get(args[2]);if(Files.exists(output))throw new IOException("Use a fresh audit output directory");Files.createDirectories(output);
        UnitySaveCacheAudit audit=new UnitySaveCacheAudit(new UnityCircusResources(new GameResources(new LocalGameStorage(Paths.get(args[1])))),output);
        try{if(args[0].equals("record"))audit.record(Paths.get(args[3]));else if(args[0].equals("verify")&&(args.length==5||args.length==7)){audit.verify(Paths.get(args[3]),Paths.get(args[4]));if(args.length==7)audit.verifyLongHistory(Paths.get(args[5]),Paths.get(args[6]));}else throw new IllegalArgumentException("Invalid audit mode");}
        catch(Throwable error){audit.log.append("FAIL ").append(error).append('\n');throw error;}
        finally{audit.release();audit.log.append("CHECKS ").append(audit.checks).append("; original script paths, simulated media completion and elapsed time\n");Files.write(output.resolve("result.txt"),audit.log.toString().getBytes("UTF-8"));System.out.print(audit.log);}
    }
}
