package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Tests original menus against read-only data and isolated save output. */
public final class DesktopFeatureAudit {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void settle(DesktopEngine e)throws Exception{DesktopSettingsAudit.settle(e);}
    private static void choose(DesktopEngine e,String action)throws Exception{e.choose(action);settle(e);}
    private static void shot(DesktopEngine e,Path out,String name)throws Exception{ImageIO.write(e.render(),"png",out.resolve(name+".png").toFile());}
    private static void pointerCheck(DesktopEngine e)throws Exception{
        DesktopEngine.Control c=e.controls().get(0);int x=(c.left+c.right)/2,y=(c.top+c.bottom)/2,pc=e.vm().offset();long ticket=e.ticket();
        e.pointerDown(x,y);e.render();check(e.vm().offset()==pc&&e.ticket()==ticket,"Press does not activate before release");e.pointerMove(-20,-20);e.pointerUp(-20,-20);check(e.vm().offset()==pc&&e.ticket()==ticket,"Dragging away cancels a button click");
        e.pointerDown(x,y);e.cancelPointer();e.pointerUp(x,y);check(e.vm().offset()==pc&&e.ticket()==ticket,"Focus loss cancels a held button");
    }
    private static void partialText(DesktopEngine e,Path out)throws Exception{
        int speedKey=e instanceof DesktopLegacy&&((DesktopLegacy)e).host() instanceof PcHost?13:26,old=e.vm().variables.read(99,speedKey);e.vm().variables.write(99,speedKey,0);
        String previous=e.body();boolean ready=false;for(int n=0;n<5000;n++){if(e.textWaiting()&&!e.body().equals(previous)&&e.body().length()>4){ready=true;break;}if(e.textWaiting())e.click(e.width/2,e.height/2,false);e.tick(1);for(int ch=0;ch<6;ch++)if(e.audioActive(ch)&&!e.audioLoop(ch))e.audioCompleted(ch,e.audioGeneration(ch));}
        check(ready,"Fresh text presentation reached");long duration=TextPlayback.duration(e.resources.profile.id,0,e.body().codePointCount(0,e.body().length()));e.tick(Math.max(1,duration/2));String visible=e.visibleBody();check(!visible.isEmpty()&&visible.length()<e.body().length(),"Native speed progressively reveals text");
        BufferedImage before=e.render();Path save=out.resolve("partial-text.sav");e.save(save);e.restore(save);BufferedImage after=e.render();check(visible.equals(e.visibleBody())&&Arrays.equals(before.getRGB(0,0,e.width,e.height,null,0,e.width),after.getRGB(0,0,e.width,e.height,null,0,e.width)),"Save restores partial text and exact pixels");
        int pc=e.vm().offset();e.click(e.width/2,e.height/2,false);check(e.vm().offset()==pc&&e.visibleBody().equals(e.body()),"First click finishes the sentence without advancing");e.vm().variables.write(99,speedKey,old);
    }
    public static void main(String[] args)throws Exception {
        Path out=Paths.get(args[1]);Files.createDirectories(out);System.setProperty("dc.saveRoot",out.resolve("saves").toString());
        try(DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),true)){
            DesktopEngine e=app.engine;settle(e);String profile=e.resources.profile.id;
            if(profile.equals("dcpc-2063")){
                choose(e,"window");choose(e,"name");PcHost h=(PcHost)((DesktopLegacy)e).host();check(h.nameInput.active,"Original name editor opens");
                choose(e,"@name:button:2");check(h.nameInput.pages()>=2,"Original alphabet/symbol pages");choose(e,"@name:button:12");check(h.nameInput.page==1,"Name page navigation");
                choose(e,"@name:button:3");choose(e,"@name:char:0");check(h.nameInput.group>0&&!h.nameInput.visibleRows().isEmpty(),"Original kanji index builds a character page");
                choose(e,"@name:field:4");e.typeText("测试");choose(e,"@name:button:7");check(h.nameInput.confirming,"Original name confirmation");shot(e,out,"name-confirm");choose(e,"@name:no");check(h.nameInput.active&&!h.nameInput.confirming,"Name confirmation cancellation returns to editor");
                byte[] edited=e.vm().snapshot();e.restore(edited);e.vm().restore(edited);h=(PcHost)((DesktopLegacy)e).host();check(h.nameInput.given.equals("测试"),"Name editing state roundtrip");shot(e,out,"name-editor");
                choose(e,"@name:button:7");choose(e,"@name:yes");h=(PcHost)((DesktopLegacy)e).host();check(h.givenName.equals("测试")&&!h.nameInput.active,"Confirmed name installed");
                ByteArrayOutputStream b=new ByteArrayOutputStream();h.savePersistent(new DataOutputStream(b));PcHost fresh=new PcHost(e.resources,e.codec);fresh.loadPersistent(new DataInputStream(new ByteArrayInputStream(b.toByteArray())));check(fresh.givenName.equals("测试"),"Player name persists between launches");
                choose(e,"name");choose(e,"@name:field:4");e.typeText("取消");choose(e,"@name:button:9");check(((PcHost)((DesktopLegacy)e).host()).givenName.equals("测试"),"Leaving name screen discards edits");
            }else if(profile.equals("dc2pc-5769"))choose(e,"03");
            pointerCheck(e);choose(e,DesktopSettingsAudit.actions(e).contains("sce_start")?"sce_start":"start");
            for(int pre=0;pre<8&&e.menuWaiting();pre++){List<String> actions=DesktopSettingsAudit.actions(e);if(actions.contains("sel01"))choose(e,"sel01");else if(actions.contains("sceselect01"))choose(e,"sceselect01");}
            int dialogues=0;String previous="";
            int turns=0;while(dialogues<20){if(turns++>5000)throw new IOException("Story did not advance "+e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset())+" "+DesktopSettingsAudit.actions(e));settle(e);String at=e.vm().scriptName()+":"+e.vm().offset();if(e.textWaiting()&&!at.equals(previous)){previous=at;dialogues++;}if(dialogues<20)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}
            e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().run(1000);String body=e.body(),file=e.vm().scriptName();int pc=e.vm().offset();
            e.audioPosition(0,123456,10000000);Path save=out.resolve("feature.sav");e.save(save);DesktopSave meta=DesktopSave.decode(Files.readAllBytes(save));check(meta.image()!=null&&meta.image().getWidth()==256,"Save thumbnail decodes");check(meta.created>0&&!meta.caption.isEmpty(),"Save date and caption stored");
            byte[] damaged=Files.readAllBytes(save);damaged[damaged.length/2]^=1;boolean rejected=false;try{DesktopSave.decode(damaged);}catch(IOException expected){rejected=true;}check(rejected,"Corrupt desktop save rejected");
            e.restore(save);check(body.equals(e.body())&&file.equals(e.vm().scriptName())&&pc==e.vm().offset(),"Save restores exact sentence and instruction");if(!e.audio(0).isEmpty())check(e.takeAudioResume(0)==123456,"Saved music cursor reaches platform");
            Dc2History history=e instanceof DesktopDd?((DesktopDd)e).host.history:((DesktopLegacy)e).host().history;check(!history.entries.isEmpty()&&history.entries.get(history.entries.size()-1).scene.length>0,"Backlog retains scene description");
            if(e instanceof DesktopDd)((DesktopDd)e).action(":backlog");else ((DesktopLegacy)e).action(":backlog");settle(e);shot(e,out,"backlog");if(e instanceof DesktopDd){e.hover(400,70);shot(e,out,"backlog-preview");}e.scroll(-1);e.render();check(e.menuWaiting(),"Original backlog opens and scrolls");if(!(e instanceof DesktopLegacy&&((DesktopLegacy)e).host() instanceof PcHost)){int top=e instanceof DesktopDd?80:112,bottom=e instanceof DesktopDd?412:412;e.pointerDown(960,top+10);e.pointerMove(960,top);check(history.cursor==0,"Backlog scrollbar drags to first entry");e.pointerMove(960,bottom);check(history.cursor==Math.max(0,history.entries.size()-4),"Backlog scrollbar drags to last entry");e.pointerUp(960,bottom);}if(!e.audio(0).isEmpty())e.audioPosition(0,2345678,10000000);e.click(0,0,true);settle(e);check(e.body().equals(body),"Backlog exit restores the caller sentence");if(!e.audio(0).isEmpty())check(e.takeAudioResume(0)==2345678,"Returning from a system screen retains the music cursor");e.restore(save);
            byte[] raw=e.snapshot();check(Arrays.equals(DesktopSave.decode(raw).state,raw),"Legacy raw VM saves remain readable");
            Path nativeSlot=e.saveDirectory.resolve("slot-4.sav");e.save(nativeSlot);e.restore(nativeSlot);if(e instanceof DesktopDd)((DesktopDd)e).action(":save");else {((DesktopLegacy)e).action(":menu");settle(e);if(DesktopSettingsAudit.actions(e).contains("save"))choose(e,"save");else if(DesktopSettingsAudit.actions(e).contains("s"))choose(e,"s");}settle(e);shot(e,out,"original-save-menu");check(e instanceof DesktopDd?((DesktopDd)e).host.ui.gridWaiting&&((DesktopDd)e).host.ui.saveGrid:((DesktopLegacy)e).host().gridWaiting&&((DesktopLegacy)e).host().saveGrid,"Original save UI renders stored metadata");check(e.thumbnails.containsKey(4),"Saved thumbnail refreshed after restore");e.restore(save);partialText(e,out);e.restore(save);
            if(args.length>2){e.restore(Paths.get(args[2]));check(!e.body().isEmpty(),"Released 0.9 save loads through host migration");e.render();Path migrated=out.resolve("migrated.sav");e.save(migrated);String oldBody=e.body();e.restore(migrated);check(e.body().equals(oldBody),"Migrated save writes and reloads current format");}
            shot(e,out,"restored-story");Files.write(out.resolve("result.json"),Json.text(Json.obj("profile",profile,"checks",checks,"dialogue_waits",dialogues,"original_exe_run",false,"pass",true)).getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("PASS "+checks+" desktop feature checks: "+profile);
        }
    }
}
