package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Self-authored hidden HWND, pre-device timer, real staged DirectSound calls.
 *Decoder/VOICE/message consumers here are explicit fixtures, not full startup. */
public final class Pc1DirectSoundStartupAudit {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] raw(String s){return s.getBytes(StandardCharsets.US_ASCII);}
    private static Pc1Audio.Catalog catalog(){return new Pc1Audio.Catalog(){public int musicCount(){return 0;}public Pc1Audio.Entry music(int n)throws IOException{throw new IOException("unused");}public int soundCount(){return 0;}public Pc1Audio.Entry sound(int n)throws IOException{throw new IOException("unused");}};}
    private static Pc1NativeMemory bus()throws IOException{final byte[] settings=new byte[Pc1NativeSettings.SIZE];return new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){public int read(int n){return settings[n]&255;}public void write(int n,int value){settings[n]=(byte)value;}});}
    private static byte[] encoded(Pc1DirectSound.Snapshot s)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();s.save(new DataOutputStream(out));return out.toByteArray();}
    private static Pc1DirectSound.Snapshot decoded(byte[] b)throws IOException{return Pc1DirectSound.Snapshot.load(new DataInputStream(new ByteArrayInputStream(b)));}
    private static Pc1DirectSound.StartupLeaves unusedLeaves(){return new Pc1DirectSound.StartupLeaves(){public void initializeDecoderTables()throws IOException{throw new IOException("Not reached decoder fixture");}public void loadVoiceIndex()throws IOException{throw new IOException("Not reached VOICE fixture");}public void failureMessage(int text)throws IOException{throw new IOException("Not reached failure fixture");}};}
    private static byte[] format(){return new byte[]{1,0,1,0,0x22,0x56,0,0,0x44,(byte)0xac,0,0,2,0,16,0,0,0};}
    private static void word(byte[] b,int at,int value){b[at]=(byte)value;b[at+1]=(byte)(value>>>8);}
    private static void dword(byte[] b,int at,int value){for(int k=0;k<4;k++)b[at+k]=(byte)(value>>>(8*k));}
    private static Path silence(Path audioDll)throws IOException{
        Path allowed=audioDll.toAbsolutePath().normalize().getParent().getParent().getParent();Path build=Paths.get("build/native-rewrite").toAbsolutePath().normalize();Path file=allowed.resolve("silent-"+audioDll.toAbsolutePath().getParent().getFileName()+".wav");if(!file.normalize().startsWith(build)||!allowed.getFileName().toString().equals("pc1-directsound-staged-stage26")&&!allowed.getFileName().toString().equals("pc1-audio-alias-stage28"))throw new IOException("MCI fixture must stay inside its runtime-owned audit output");
        byte[] wav=new byte[44+2205*2];System.arraycopy(raw("RIFF"),0,wav,0,4);dword(wav,4,wav.length-8);System.arraycopy(raw("WAVEfmt "),0,wav,8,8);dword(wav,16,16);word(wav,20,1);word(wav,22,1);dword(wav,24,22050);dword(wav,28,44100);word(wav,32,2);word(wav,34,16);System.arraycopy(raw("data"),0,wav,36,4);dword(wav,40,wav.length-44);Files.write(file,wav);return file;
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Expected immutable audioDLL andwindowDLL");Path audioDll=Paths.get(args[0]),windowDll=Paths.get(args[1]);
        final Pc1DirectSound[] device=new Pc1DirectSound[1];final Pc1Audio[] control=new Pc1Audio[1];final int[] timers={0},mciNotify={0,0};
        Pc1WindowsWindow.NativeDispatcher dispatcher=new Pc1WindowsWindow.NativeDispatcher(){public Pc1WindowsWindow.Dispatch dispatch(long hwnd,int message,long wp,long lp)throws IOException{
            if(message==0x3b9){mciNotify[0]++;mciNotify[1]=(int)wp;return Pc1WindowsWindow.Dispatch.handled(0);}
            if(message==1&&device[0]!=null)device[0].borrowMainWindow(hwnd);
            if(message==0x113&&wp==2&&control[0]!=null){timers[0]++;control[0].handleNativeTimer2(Pc1NativeSettings.parse(new byte[Pc1NativeSettings.SIZE]));return Pc1WindowsWindow.Dispatch.handled(0);}
            return Pc1WindowsWindow.Dispatch.unhandled();
        }public int quitFlag(){return 0;}};
        try(Pc1WindowsWindow window=Pc1WindowsWindow.deferred(windowDll,dispatcher)){
            Pc1DirectSound.Snapshot beforeDevice,busySaved;Pc1Audio.State beforeControl;
            try(Pc1DirectSound out=Pc1DirectSound.deferred(audioDll,null)){
                device[0]=out;control[0]=new Pc1Audio(out,catalog());Pc1NativeMemory memory=bus();new Pc1CatalogMemory(memory);control[0].bindNativeMemory(memory);out.bindNativeMemory(memory);
                check(!out.pcmInitialized()&&out.initialization()[0]==Integer.MAX_VALUE,"Deferred does not call DirectSoundCreate");check(memory.readAddress(0x43dbe0)==255&&memory.readAddress(0xbc12c8)==0,"Backend native views expose real initial MM/outcount fields");
                for(int k=0;k<4;k++)memory.writeAddress(0x43dbe0+k,0x12345678>>>(k*8));boolean unregistered=false;try{out.capture(control[0]);}catch(IOException e){unregistered=e.getMessage().contains("retained actual timer graph");}check(unregistered,"Raw nonzero token alone never invents an actual MM registration at restore");for(int k=0;k<4;k++)memory.writeAddress(0x43dbe0+k,255);
                for(int k=0;k<4;k++)memory.writeAddress(0xbc1d64+k,0x87654321>>>(k*8));busySaved=out.capture(control[0]);for(int k=0;k<4;k++)memory.writeAddress(0xbc1d64+k,0);
                for(int k=0;k<4;k++)memory.writeAddress(0x43dbdc+k,-2>>>(k*8));check(out.serviceStreamForAudit(false,0)[3]==0,"Raw stream channel-2 sees zero BC12C8 alias before fillstate");for(int k=0;k<4;k++)memory.writeAddress(0x43dbdc+k,255);
                int[] streamAddresses={0xbbaaf8,0xbbab24,0xbbab50,0xbbab7c,0x43dbdc};for(int n=0;n<5;n++){int word=0x11223344+n;for(int k=0;k<4;k++)memory.writeAddress(streamAddresses[n]+k,word>>>(k*8));if(n<4)check(Binary.u32(control[0].state().rawStreamingBytes(),n*4)==Integer.toUnsignedLong(word),"Byte aliases share aligned little-endian stream word"+n);else check(control[0].state().words()[13]==word,"Streaming channel uses same fifth direct DWORD");for(int k=0;k<4;k++)memory.writeAddress(streamAddresses[n]+k,n==4?255:0);}
                check(out.streamState()[3]==0,"Native reads reset shared total without private counter shadow");
                long instance=window.moduleInstance();byte[] cls=raw("CircusPc1StagedAudioAudit");check(window.registerClass(instance,cls,0,window.loadCursor(0,32512))!=0,"Actual hidden window class");long hwnd=window.createWindow(0,cls,raw("PC1 staged audio audit"),0x80000000,0,0,64,64,instance);check(hwnd!=0,"Actual hidden main HWND");window.mainWindow(hwnd);check(window.windowInfo()[1]==0,"Audit window stays hidden");
                control[0].activation(true,0,0);check(control[0].timerToken()==2&&control[0].pcmReadyRaw()==0,"Main timer2 allocated before DSinit");Thread.sleep(175);out.drainServiceEvents();check(timers[0]==0,"Audio drain cannot steal main-window dispatch");window.pumpMessages();check(timers[0]==1&&control[0].activationRefreshes()==4,"Actual main HWND delivers pre-DS timer synchronously");
                beforeDevice=out.capture(control[0]);beforeControl=beforeDevice.control();check(Arrays.equals(encoded(beforeDevice),encoded(decoded(encoded(beforeDevice)))),"Pre-init backend detached codec exact");control[0].closeNative(out.shutdown());
            }
            device[0]=null;control[0]=null;check(window.windowInfo()[0]==1,"Audio close never destroys borrowed main HWND");
            try(Pc1DirectSound stage=Pc1DirectSound.stage(audioDll,window.hwnd(),null,beforeDevice)){
                device[0]=stage;check(!stage.pcmInitialized()&&stage.initialization()[0]==Integer.MAX_VALUE,"BSS snapshot stages without an eager device");control[0]=Pc1Audio.restore(stage,catalog(),beforeControl);check(control[0].pcmReadyRaw()==0,"BSS control installed before timer adoption");Thread.sleep(175);window.pumpMessages();check(timers[0]==2&&control[0].activationRefreshes()==3,"Restored pre-init timer uses same HWND consumer");control[0].closeNative(stage.shutdown());
            }
            device[0]=null;control[0]=null;
            try(Pc1DirectSound partial=Pc1DirectSound.stage(audioDll,window.hwnd(),null,busySaved)){
                Pc1Audio rawControl=Pc1Audio.restore(partial,catalog(),busySaved.control());Pc1NativeMemory memory=bus();rawControl.bindNativeMemory(memory);partial.bindNativeMemory(memory);int rawBusy=0;for(int k=0;k<4;k++)rawBusy|=memory.readAddress(0xbc1d64+k)<<(k*8);check(rawBusy==0x87654321,"Raw busy flag is preserved and does not masquerade as an executing callback during staged binding");rawControl.closeNative(partial.shutdown());
            }
            try(Pc1DirectSound aliases=Pc1DirectSound.deferred(audioDll,null)){
                Pc1Audio rawControl=new Pc1Audio(aliases,catalog());Pc1NativeMemory memory=bus();rawControl.bindNativeMemory(memory);aliases.bindNativeMemory(memory);for(int k=0;k<4;k++){memory.writeAddress(0x43dbdc+k,-2>>>(k*8));memory.writeAddress(0xbc12c8+k,k==0?7:0);}memory.writeAddress(0xbbab50,1);check(aliases.serviceStreamForAudit(false,0)[3]==0,"Nonzero BC12C8 pointer alias still skips when raw fillstate is nonzero");memory.writeAddress(0xbbab50,0);check(aliases.serviceStreamForAudit(false,0)[3]==3,"Nonzero count never masquerades as an owned physical COM pointer");check(aliases.drainServiceEvents().stream().anyMatch(e->e.kind==4&&e.token==3),"Unmapped physical alias reports a strict required leaf instead of silent skip");
            }
            Pc1DirectSound.Snapshot beforeArrays;
            try(Pc1DirectSound partial=Pc1DirectSound.deferred(audioDll,null)){
                partial.borrowMainWindow(window.hwnd());Pc1Audio rawControl=new Pc1Audio(partial,catalog());Pc1Audio.Initialization api=partial.initialization(unusedLeaves());
                check(api.directSoundCreate()==0&&api.setCooperativeLevel(2)==0,"Actual early device/cooperative prefix");check(api.createPrimary(20,-1)!=0,"Actual invalid primary HRESULT remains observable");
                check(partial.initialization()[14]==0,"Actual failed primary leaves null pointer in this device");boolean fault=false;try{api.setPrimaryFormat(format());}catch(IOException e){fault=e.getMessage().contains("absent primary");}check(fault&&rawControl.pcmReadyRaw()==0,"Ignored primary error faults only at actual null SetFormat boundary");
                check(api.createPrimary(20,0x4009)==0&&api.setPrimaryFormat(format())==0,"Known primary/format can continue after failed API prefix");
                check(partial.pcmInitialized()&&rawControl.pcmReadyRaw()==0,"Physical SetFormat does not invent native BC1D10 publication");beforeArrays=partial.capture(rawControl);rawControl.closeNative(partial.shutdown());
            }
            try(Pc1DirectSound partial=Pc1DirectSound.stage(audioDll,window.hwnd(),null,beforeArrays)){
                Pc1Audio rawControl=Pc1Audio.restore(partial,catalog(),beforeArrays.control());check(partial.pcmInitialized()&&rawControl.pcmReadyRaw()==0,"Partial physical graph restores while native ready remains raw0");rawControl.closeNative(partial.shutdown());
            }
            try(Pc1DirectSound out=Pc1DirectSound.deferred(audioDll,null)){
                device[0]=out;out.borrowMainWindow(window.hwnd());control[0]=new Pc1Audio(out,catalog());Pc1NativeMemory memory=bus();new Pc1CatalogMemory(memory);control[0].bindNativeMemory(memory);out.bindNativeMemory(memory);final List<String> leaves=new ArrayList<String>();
                Pc1DirectSound.StartupLeaves required=new Pc1DirectSound.StartupLeaves(){public void initializeDecoderTables()throws IOException{check(control[0].pcmReadyRaw()==1,"Ready published before required decoder leaf");leaves.add("decoder");}public void loadVoiceIndex(){leaves.add("voice");}public void failureMessage(int rawText){leaves.add("failure");}};
                boolean late=false;try{out.initialization(required).setPrimaryFormat(format());}catch(IOException e){late=e.getMessage().contains("absent primary");}check(late&&out.initialization()[0]==Integer.MAX_VALUE&&control[0].pcmReadyRaw()==0,"Null primary faults late without an invented device");
                control[0].writeNativeByte(0x43dbc0,50);int result=control[0].initializeNative(out.initialization(required));System.out.println("initialization="+Arrays.toString(out.initialization()));if(result==0)throw new IOException("Actual DirectSound unavailable; native failureprefix retained");check(leaves.equals(Arrays.asList("decoder","voice")),"Required decoder/VOICE leaves in actual init order");List<Pc1DirectSound.Result> journal=out.journal();check(journal.size()==5&&journal.get(0).operation.equals("initialize0")&&journal.get(1).operation.equals("initialize1")&&journal.get(2).operation.equals("initialize2")&&journal.get(3).operation.equals("initialize3")&&journal.get(4).operation.equals("initialize4"),"Actual staged DirectSound create/coop/primary/format/clear order");check(journal.get(3).b==68,"Mutable cbSize reads live primary/gap/channel/MM/table bytes through canonical bus");control[0].writeNativeByte(0x43dbc0,0);
                byte[] samples=new byte[2205*2];for(int n=0;n<samples.length;n++)samples[n]=(byte)(n*17);check(out.createBuffer(0,samples.length,0x40ea,format())==0,"Actual secondary buffer after staged init");check(out.writeBuffer(0,0,samples,0,samples.length)[0]==0,"Actual PCM transfer");check(out.setVolume(0,-10000)==0&&out.play(0,0)==0,"Controlled muted self-authored signal");Thread.sleep(20);check(out.query(0,0,0)[0]==0,"Actual staged device cursor");out.stop(0);out.release(0);
                Path silent=silence(audioDll);String target=silent.toString();int opened=out.mci(raw("open \""+target+"\""),false);System.out.println("silentMciOpen="+opened);check(opened==0,"Actual MCI opens only self-authored silent WAV");
                try{check(out.mci(raw("play \""+target+"\" notify"),true)==0,"MCI play notify uses borrowed actual main HWND");boolean active=false;try{out.capture(control[0]);}catch(IOException e){active=e.getMessage().contains("active MCI");}check(active,"Live MCI phase remains explicit snapshot reconstruction boundary");Thread.sleep(150);out.drainServiceEvents();check(mciNotify[0]==0,"Audio private drain never steals main MCI notification");window.pumpMessages();check(mciNotify[0]==1&&mciNotify[1]==1,"Actual queued MM_MCINOTIFY arrives through same main-window dispatcher");}finally{check(out.mci(raw("close \""+target+"\""),false)==0,"Actual MCI owned file/device close");}

                check(out.createBuffer(2,samples.length,0x40ea,format())==0&&out.setVolume(2,-10000)==0&&out.play(2,1)==0,"Controlled live buffer before native reinitialize");
                check(control[0].initializeNative(out.initialization(required))==1,"Native init may reenter without an invented initialized guard");int[] retired=out.retiredReferences();System.out.println("retired="+Arrays.toString(retired));check(retired[0]>=1&&retired[2]==1&&out.query(2,5,0)[1]==0,"REP pointer clear retains old COM references rather than releasing them");
                boolean graph=false;try{out.capture(control[0]);}catch(IOException e){graph=e.getMessage().contains("retained-reference snapshot");}check(graph,"Unimplemented retained COM graph reconstruction stays an explicit snapshot boundary");
                control[0].closeNative(out.shutdown());
            }
            device[0]=null;control[0]=null;check(window.windowInfo()[0]==1,"Staged device cleanup preserves shared main window");
        }
        System.out.println("Pc1DirectSoundStartupAudit PASS "+checks+"; actual hidden mainHWND/timer/DS, required decoder/VOICE fixtures, no original EXE");
    }
}
