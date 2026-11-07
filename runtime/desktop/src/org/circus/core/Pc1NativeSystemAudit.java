package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Actual PC1 START/MES/CRX with portable input/media completion; never executes its EXE. */
public final class Pc1NativeSystemAudit {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void media(DesktopLegacy e)throws IOException{
        for(int n=0;n<6;n++)if(!e.audioLoop(n))e.audioCompleted(n,e.audioGeneration(n));
        e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.vm().elapse(Math.max(1,e.host().remainingMillis()));
    }
    private static void story(DesktopLegacy e)throws Exception{
        for(int n=0;n<30000;n++){
            MesVm.Status state=e.vm().run(1);if(state==MesVm.Status.FAULT)throw new IOException(e.vm().error());
            if(e.host().textWaiting&&!e.body().isEmpty())return;
            if(state!=MesVm.Status.WAIT_HOST)continue;
            if(e.menuWaiting()){
                boolean found=false;for(String action:new String[]{"03","window","start","sce_start"}){for(int index=0;index<e.host().ui.buttons.size();index++)if(e.host().ui.buttons.get(index).action.equals(action)){e.vm().activateMenu(e.ticket(),index);found=true;break;}if(found)break;}
                if(!found)throw new IOException("Unexpected startup menu "+DesktopSettingsAudit.actions(e));
            }else{if(((PcHost)e.host()).hasTimedInput()&&!e.host().ui.actions[1].isEmpty()){e.vm().activateMenu(e.ticket(),-2);continue;}media(e);e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}
        }throw new IOException("PC1 story wait budget exhausted at "+e.vm().scriptName()+"@0x"+Integer.toHexString(e.vm().offset())+" status="+e.vm().status()+" remaining="+e.host().remainingMillis()+" audioMask="+e.host().audioMask()+" save="+e.host().saveRequest+" load="+e.host().loadRequest+" waiting="+e.host().waiting()+" movie="+e.host().movie+" text="+e.host().textWaiting+" presentation="+e.host().presentationWaiting+" return="+e.host().returnRequested+" credit="+((PcHost)e.host()).pcCredits.active+" ui="+e.host().ui.waiting);
    }
    private static void menu(DesktopLegacy e)throws Exception{
        for(int n=0;n<5000;n++){
            MesVm.Status state=e.vm().run(1);if(state==MesVm.Status.FAULT)throw new IOException(e.vm().error());
            if(e.menuWaiting())return;media(e);
        }throw new IOException("PC1 system menu budget exhausted");
    }
    private static int[] pixels(DesktopLegacy e,Path output)throws IOException{
        BufferedImage image=e.render();try{if(output!=null)ImageIO.write(image,"png",output.toFile());return image.getRGB(0,0,e.width,e.height,null,0,e.width);}finally{image.flush();}
    }
    private static String pixelHash(int[] pixels)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);for(int pixel:pixels)out.writeInt(pixel);return Binary.sha256(bytes.toByteArray());}
    private static Pc1Runtime nativeText(DesktopLegacy e){return ((PcHost)e.host()).pc1;}
    private static Object back(DesktopLegacy e,Path output,int entry,int[] menuPixels)throws Exception{
        e.click(0,0,true);
        for(int n=0;n<5000;n++){
            MesVm.Status state=e.vm().run(1);if(state==MesVm.Status.FAULT)throw new IOException(e.vm().error());
            if(nativeText(e).returning())break;
            if(!e.session.inSystem())throw new IOException("PC1 actual1C committed without an observable native40ms return phase");
            media(e);if(n==4999)throw new IOException("PC1 native1C return budget exhausted at "+e.vm().scriptName()+"@0x"+Integer.toHexString(e.vm().offset()));
        }
        int pc=e.vm().offset(),firstProgress=nativeText(e).returnProgress(),speed=e.host().read(99,12);String script=e.vm().scriptName();
        check(e.session.inSystem()&&e.vm().status()==MesVm.Status.WAIT_HOST&&firstProgress>0&&firstProgress<=16,"Actual1C owns a pending caller return phase before commit");
        int[] first=pixels(e,output.resolve("return-"+entry+"-first.png"));
        check(!e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE),"ADVANCE is rejected during the actual1C transfer");
        e.click(0,0,false);e.click(0,0,true);check(e.modalKey(java.awt.event.KeyEvent.VK_ENTER)&&e.modalKey(java.awt.event.KeyEvent.VK_PAGE_UP),"Desktop click and modal keys are owned by the pending native return");
        check(e.vm().run(1)==MesVm.Status.WAIT_HOST&&e.vm().offset()==pc&&e.vm().scriptName().equals(script)&&nativeText(e).returnProgress()==firstProgress&&Arrays.equals(first,pixels(e,null)),"Blocked return input cannot advance caller PC, phase or rendered pixels");
        e.vm().elapse(39);check(nativeText(e).returning()&&nativeText(e).returnProgress()==firstProgress&&Arrays.equals(first,pixels(e,null)),"First feedback frame lasts39ms without an early40ms commit");
        byte[] pending=e.vm().snapshot();Files.write(output.resolve("return-"+entry+"-pending.cvs2"),pending);
        DesktopLegacy restored=new DesktopLegacy(e.resources,e.codec,true);
        // Session.restore deliberately retains the account's live settings and
        // unlocks. Give the separate engine that same owner through its public
        // persistence codec; keep its own framebuffer capture callback.
        ByteArrayOutputStream persistent=new ByteArrayOutputStream();e.host().savePersistent(new DataOutputStream(persistent));restored.host().loadPersistent(new DataInputStream(new ByteArrayInputStream(persistent.toByteArray())));restored.restore(pending);
        byte[] decoded=restored.vm().snapshot();int[] decodedPixels=pixels(restored,null);boolean byteExact=Arrays.equals(pending,decoded),pixelExact=Arrays.equals(first,decodedPixels);
        if(!byteExact||!pixelExact||!nativeText(restored).returning()){
            Files.write(output.resolve("return-"+entry+"-restored.cvs2"),decoded);pixels(restored,output.resolve("return-"+entry+"-restored.png"));int difference=-1;for(int n=0;n<Math.min(pending.length,decoded.length);n++)if(pending[n]!=decoded[n]){difference=n;break;}
            Files.write(output.resolve("return-"+entry+"-restore-diagnostic.json"),Json.text(Json.obj("byte_exact",byteExact,"pixel_exact",pixelExact,"returning",nativeText(restored).returning(),"first_byte_difference",difference,"before_bytes",pending.length,"after_bytes",decoded.length,"source_progress",nativeText(e).returnProgress(),"restored_progress",nativeText(restored).returnProgress())).getBytes("UTF-8"));
        }
        check(byteExact&&nativeText(restored).returning()&&pixelExact,"Pending actual1C snapshot restores a new VM/host with matching live settings, exact phase bytes and raster");
        check(!restored.vm().signalHost(restored.ticket(),MesVm.HostSignal.ADVANCE),"Restored pending actual1C also blocks ADVANCE");
        List<Object> phases=new ArrayList<>();phases.add(Json.obj("progress",firstProgress,"elapsed_ms",39,"pixels_sha256",pixelHash(first)));
        int feedbackFrames=1,elapsed=39;boolean finalHold=false;
        for(int frame=0;frame<17&&nativeText(e).returning();frame++){
            int progress=nativeText(e).returnProgress();long tick=frame==0?1:40;
            if(progress==16&&frame!=0){int[] held=pixels(e,null);e.vm().elapse(39);restored.vm().elapse(39);elapsed+=39;
                check(nativeText(e).returning()&&nativeText(restored).returning()&&nativeText(e).returnProgress()==16&&Arrays.equals(held,pixels(e,null))&&Arrays.equals(held,pixels(restored,null)),"Progress16 feedback still owns its final40ms before the exact copy");tick=1;finalHold=true;}
            else if(progress==16)finalHold=true;
            e.vm().elapse(tick);restored.vm().elapse(tick);elapsed+=tick;
            MesVm.Status state=e.vm().run(1),again=restored.vm().run(1);if(state==MesVm.Status.FAULT||again==MesVm.Status.FAULT)throw new IOException("Actual1C continuation "+e.vm().scriptName()+"@0x"+Integer.toHexString(e.vm().offset())+": "+e.vm().error()+" / "+restored.vm().error());
            check(nativeText(e).returning()==nativeText(restored).returning()&&e.vm().offset()==pc&&restored.vm().offset()==pc,"Original/restored transfer keeps caller cursor until its wait resumes");
            int[] current=pixels(e,null);check(Arrays.equals(current,pixels(restored,null))&&Arrays.equals(e.vm().snapshot(),restored.vm().snapshot()),"Original/restored native40ms phase has byte-identical snapshot and full800x600 raster");
            if(nativeText(e).returning()){feedbackFrames++;int next=nativeText(e).returnProgress();check(next>progress&&next<=16,"Native return progresses through feedback frames");phases.add(Json.obj("progress",next,"elapsed_ms",elapsed,"pixels_sha256",pixelHash(current)));if(next==16)pixels(e,output.resolve("return-"+entry+"-progress16.png"));}
        }
        check(!nativeText(e).returning()&&!nativeText(restored).returning()&&!e.session.inSystem()&&!restored.session.inSystem()&&finalHold,"Actual/restored1C commit only after the final40ms interval");
        int[] finalPixels=pixels(e,null);check(!Arrays.equals(menuPixels,finalPixels)&&!Arrays.equals(first,finalPixels),"Actual system menu returns through visible intermediate pixels, rather than an instantaneous caller repaint");
        return Json.obj("speed",speed,"feedback_frames",feedbackFrames,"scheduled_elapsed_ms",elapsed,"pending_snapshot_bytes",pending.length,"menu_pixels_sha256",pixelHash(menuPixels),"first_feedback_sha256",pixelHash(first),"returned_pixels_sha256",pixelHash(finalPixels),"phases",phases);
    }
    public static void main(String[] args)throws Exception{
        if(args.length<2||args.length>3)throw new IOException("GAME OUTPUT [static-table EXE]");
        Path output=Paths.get(args[1]);Files.createDirectories(output);System.setProperty("dc.saveRoot",output.resolve("saves").toString());
        GameResources files=new GameResources(new LocalGameStorage(Paths.get(args[0])),args.length==3?args[2]:"@none");
        CircusResources data=new CircusResources(files,Profiles.get("dcpc-2063"));DesktopLegacy e=new DesktopLegacy(data,GameText.detect(files,data.profile),true);
        checks=0;story(e);String script=e.vm().scriptName(),body=e.body();int pc=e.vm().offset();byte[] baseline=e.vm().snapshot();
        check(e.host() instanceof PcHost&&((PcHost)e.host()).pc1!=null,"Actual PC1 startup reaches first story wait with typed host");
        pixels(e,output.resolve("story.png"));List<Object> rows=new ArrayList<>();
        for(int entry=0;entry<3;entry++){
            e.restore(baseline);PcHost h=(PcHost)e.host();int flag=h.pc1.saves().flag();Pc1Frame prior=h.pc1.saves().candidate();Variables identity=e.vm().variables;
            if(entry==0)e.click(0,0,true);else e.action(entry==1?":menu":":option");
            check(e.session.inSystem()&&e.vm().variables==identity,"Actual platform event enters typed context without replacing live Variables");
            check(h.pc1.system().returnPoint().pc==pc&&h.pc1.system().returnPoint().script.equals(script),"Input entry stores the actual consumed story PC");
            check(flag==0?h.pc1.saves().candidate().returnPoint().pc==pc:h.pc1.saves().candidate()==prior,"Candidate refresh follows native flag rather than borrowing system frame");
            menu(e);int[] before=pixels(e,output.resolve("menu-"+entry+".png"));
            e.vm().variables.write(0,99,179);byte[] snapshot=e.vm().snapshot();
            if(entry==0){e.restore(snapshot);check(Arrays.equals(snapshot,e.vm().snapshot())&&Arrays.equals(before,pixels(e,null)),"Actual CRX system menu and hidden caller survive a new VM/host snapshot byte and pixel comparison");}
            Variables returning=e.vm().variables;PcHost returningHost=(PcHost)e.host();Object returnTrace=back(e,output,entry,before);
            check(e.vm().variables==returning&&e.host()==returningHost,"Actual1C retains its live VM/host pair");
            check(e.vm().scriptName().equals(script)&&e.vm().offset()==pc&&e.body().equals(body)&&e.host().textWaiting,"Actual1C restores caller display and exact waiting cursor");
            check(e.vm().variables.read(0,99)==179,"Menu ordinary write survives the actual script1C");
            pixels(e,output.resolve("returned-"+entry+".png"));rows.add(Json.obj("entry",entry==0?"right/Esc":entry==1?"toolbar menu":"toolbar option","candidate_flag",flag,"return_pc",pc,"menu_snapshot_bytes",snapshot.length,"native_return",returnTrace));
        }
        for(String action:new String[]{":qs",":ql"}){
            Path probe=e.saveDirectory.resolve("slot-1.sav");byte[] previous=Files.exists(probe)?Files.readAllBytes(probe):null;
            try{
                if(action.equals(":ql"))e.save(probe); // Explicit portable fixture, never a native slot claim.
                boolean refused=false;try{e.action(action);}catch(IOException expected){refused=expected.getMessage().contains("not implemented");}check(refused,"Native PC1 quick slot refuses an unmapped slot reconstruction bridge");
            }finally{if(previous==null)Files.deleteIfExists(probe);else Files.write(probe,previous);}
        }
        e.restore(baseline);
        for(int n=0;n<2;n++){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);story(e);}
        PcHost historyHost=(PcHost)e.host();int historyPc=e.vm().offset(),logCount=historyHost.pc1.textLog().count();String historyScript=e.vm().scriptName(),currentBody=e.body();Variables historyMemory=e.vm().variables;Pc1Frame historyCandidate=historyHost.pc1.saves().candidate();
        check(logCount>=3&&historyHost.pc1.waitKind()==7,"Actual three-message path owns a native03 log and07 wait");
        check(e.modalKey(java.awt.event.KeyEvent.VK_PAGE_UP)&&historyHost.pc1.historySelection()==logCount-2,"Actual desktop PgUp selects the previous native log entry");
        check(!e.session.inSystem()&&e.vm().variables==historyMemory&&e.vm().offset()==historyPc&&e.vm().scriptName().equals(historyScript),"History input neither replaces the VM nor changes its live PC");
        check(historyHost.pc1.saves().candidate()==historyCandidate&&historyHost.history.entries.isEmpty(),"History preserves the native save candidate and does not create portable rewind entries");
        int[] historyPixels=pixels(e,output.resolve("history.png"));byte[] historySnapshot=e.vm().snapshot();e.restore(historySnapshot);
        check(Arrays.equals(historySnapshot,e.vm().snapshot())&&Arrays.equals(historyPixels,pixels(e,null)),"Selected raw log and glyph journal roundtrip through a fresh VM and raster comparison");
        check(e.modalKey(java.awt.event.KeyEvent.VK_PAGE_DOWN)&&!((PcHost)e.host()).pc1.historyActive()&&e.body().equals(currentBody),"PgDown redraws the current raw message with the current live substitutions");
        e.action(":up");check(((PcHost)e.host()).pc1.historyActive()&&!e.session.inSystem(),"Original history toolbar action uses the live native cursor");e.click(0,0,true);
        check(!((PcHost)e.host()).pc1.historyActive()&&e.vm().offset()==historyPc&&e.host().textWaiting,"Right closes native history without consuming07");
        check(e.missingImages.isEmpty(),"Observed story/system frames resolve all referenced actual CRX files");
        Files.write(output.resolve("result.json"),Json.text(Json.obj("checks",checks,"entries",rows,"caller_script",script,"caller_pc",pc,"scope","Actual original START/MES/CRX with simulated input,40ms time and media completion; native input entry and actual1C intermediate/final raster phases, blocked ADVANCE, synthetic menu ordinary mutation and pending new-VM byte/pixel snapshot roundtrip; no native EXE execution or original pixel/timing comparison; native slots deliberately rejected","complete_engine_fidelity_verified",false)).getBytes("UTF-8"));
        System.out.println("PASS "+checks+" actual PC1 system entry/return assertions");
    }
}
