package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Complete419D30/419E40 private-CPU call/field journals; no Windows or pixels.
 * Golden rows come from native-fade-vectors.json, not the Java implementation. */
public final class Pc1LoadFadeTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Action{void run()throws Exception;}
    private static void rejects(Action action,String why)throws Exception{boolean failed=false;try{action.run();}catch(IOException|IllegalArgumentException|IllegalStateException expected){failed=true;}check(failed,why);}
    // direction,id,journalSHA,result,C4,BB8434,94C698,430284,pumpCalls.
    private static final Object[][] GOLDEN={
        {"IN","speed0","66bb096b1e3d4cc97e7d8e85abcaf5928f068e5fd9764412d81861cdeee3fb63",0,16,0,0,40,64},
        {"IN","speed1","751b9e5fc845ec79a38f38697a16397c884adf850d94c8a41954ab6abf32800b",0,16,0,0,40,8},
        {"IN","speed2","9c4e4880fa7a25959682d526a354e3ac121ab209816fd1c2ac0c3ee1c68f9f83",0,16,0,0,40,4},
        {"IN","speed3","552f819d1fc80ecc1388aa9f029f23720e667e8b46dd5ae5102da7554dd747a2",0,16,0,0,40,2},
        {"IN","ctrl","b80c47dc61aebd53d6af73bea8c6bfd3ded0b3991452b2bc2584ec18794f178f",0,16,0,0,40,2},
        {"IN","rapid","b80c47dc61aebd53d6af73bea8c6bfd3ded0b3991452b2bc2584ec18794f178f",0,16,0,0,40,2},
        {"IN","unknown_speed","670264fbfef73a2a4532dfc6e6c5e1a31a6059cc5ca6e5431db91932531463a3",0,16,0,0,40,16},
        {"IN","wrap","4340293722534f9875837b817bc40346a10872483aeabd3c40be5a02e75883c6",0,16,0,0,40,16},
        {"IN","large_elapsed","bc15f4ad51dff76f15926bc9902be1cd8ffc5e3a6e2402bca5ae4d4618aba4ab",0,16,0,0,40,2},
        {"IN","late_frames","1932f0084ffa69eebd14939dbcc5ec5e3b183cecbf991cadb43236fc47d14023",0,16,0,0,40,4},
        {"IN","capture_high","5aac3cf8dd5dde45c118b593b8edd55248792021e4b15c62c6ed85edc733ea35",0,20,0,0,123,0},
        {"IN","speed_changes","418a2beb1dae137f992db13c51f7c5210a785c08eaa050b4939db3dfbf3e4a7d",0,16,0,0,40,3},
        {"IN","arg1_fifteen","edafb6395a72a1611c7c6737ab5b1b0939fa2e4a9e448f8d23f61aa2b2e8d9a1",0,16,0,0,40,2},
        {"IN","arg1_high","f9d33c3e122741af525d3b235193966b3ea8210020490d7815c81d591dc7084e",0,20,0,0,40,1},
        {"IN","arg1_negative","8e81ea2a1acd469577b39819099d7c4f9842bee57312d1217966a6bbc665ec4c",0,16,0,0,40,6},
        {"IN","flags","145f4c9c5f6583f4293734946bfb8512e301c7e41dcdc69f7def062b15c772fe",0,16,0,0,40,2},
        {"IN","capture_fail","814bc8bdd171bd1b311e75d2fc31638d4d6bf335b93c29b4eba6ebf6a16f885d",0,16,0,0,40,2},
        {"IN","interrupt_first","363b60f31a997bf15df3b93b1d6fad30900e36210b4104930819d90f4d9fe2e8",1,1,1,1,40,1},
        {"IN","interrupt_final","929ec604b2d80cc1f63f10ef4494c3bbf32694fc9b5112bee9034a10786e3b50",1,16,1,1,40,2},
        {"IN","interrupt_stalled","b42b3511ea2c4616653f76c2aefbebd58ddf61480a1a4c5bca3b3228184246b4",1,1,1,1,40,3},
        {"IN","interrupt_fields","f67bd1dde94f1635c623c31ae2992a8f30bc393953a74c831bec5dd65d31dd0f",1,16,7,9,40,1},
        {"OUT","speed0","76bbc2c228b10fb9546f8b4c094d04044695067f74719776fd5fff32ebc06f74",0,16,1,0,40,64},
        {"OUT","speed1","ec72966e7ccdb4f94cd695b51f67175d0d9e65704b3eae15090d32d4a8d86892",0,16,1,0,40,8},
        {"OUT","speed2","35a05ab042bad9c70181cccbfc4dfeee17eed56b2a818610c946152ca7928b43",0,16,1,0,40,4},
        {"OUT","speed3","7b5bb4a1be904417d87ef4112ee35fd74497b85a9f17a47fe72600b7e2a1f98f",0,16,1,0,40,2},
        {"OUT","ctrl","07155b5f8441892c2abc59ab2a68af2bff869e72417f95fa78d355c9c2fd09e6",0,16,1,0,40,2},
        {"OUT","rapid","07155b5f8441892c2abc59ab2a68af2bff869e72417f95fa78d355c9c2fd09e6",0,16,1,0,40,2},
        {"OUT","unknown_speed","e46325ad740522889319ec50c62d277711956f4d811239c035a688a9ee41d425",0,16,1,0,40,16},
        {"OUT","wrap","2f51b7c2b66c1b4de2ff46a705d6d21d6de8000137ff78cc3645b73d2df637c0",0,16,1,0,40,16},
        {"OUT","large_elapsed","3b584496eca7c705155fd3e1f0c44ccc91c4bd3a5f86e3409572cf2fc712fca0",0,16,1,0,40,2},
        {"OUT","late_frames","168c51373756d3c5f1baba4c69a987d561d4fd0e2d3ab227e81ebe92715baffa",0,16,1,0,40,4},
        {"OUT","capture_high","8b89cd02307222ede1ba9df50a2cb86a56fae0a7952032390fd86c2e6abf4fab",0,20,1,0,123,0},
        {"OUT","speed_changes","ca95caadbf095947fd1fd5dab8146e066248076267b256170a84c1f5b3409c2d",0,16,1,0,40,3},
        {"OUT","arg1_fifteen","dbbff40faeb424aaad8ebd76259d8bd87a350fa6dec6d317c91dc798c000d2e6",0,16,1,0,40,2},
        {"OUT","arg1_high","9bb65da9c6c614da5a17fcb71744dad5165b8096ad5eb1942629e4261a24ae56",0,20,1,0,40,1},
        {"OUT","arg1_negative","370a0d9d176b71a0183ad592ad637036632d7c62be9671a6395ca0ddf8aa4449",0,16,1,0,40,6},
        {"OUT","flags","5645662455541f720cbf889a1cea9dabff28a17a7cf87309a7c3dbade98df4d8",0,16,7,0,40,2},
        {"OUT","capture_fail","386f3c68fa08fd3e16f6bc3fa61ea33dc5ebc1b06d00d698bcf9165d5b083b68",0,16,1,0,40,2},
        {"OUT","interrupt_first","2ee1b1b28dd6c05123209c527172c400c8f5375d35f6420eab9c6aba3ebc56b1",1,1,1,1,40,1},
        {"OUT","interrupt_final","c665bae2235df51fe38458baea09acaa23e551d07b2fe272863f4fed9fa7567b",1,16,1,1,40,2},
        {"OUT","interrupt_stalled","9be349851fbbc980d9febf74840f27d15f2b8ec0cb3a821b9c50df993fab5880",1,1,1,1,40,3},
        {"OUT","interrupt_fields","3256b6794ae284c9cb98ef94d3c6838f71b20dbfd939a8445d8fc58073b40448",1,16,7,9,40,1},
    };
    private static final class Fixture implements Pc1LoadFade.Host {
        final String id;final List<String> events=new ArrayList<>();int argument=99,fade=37,loop=41,delay=123,speed,now,delta=40,pumps,handle=17;boolean ctrl,rapid;
        String failStage="";Pc1LoadFade observed;boolean snapshotRejected,copyRejected,reentryRejected;
        Fixture(String id){this.id=id;
            if(id.startsWith("speed")&&id.length()==6){speed=id.charAt(5)-'0';if(speed==0)delta=10;}
            else if(id.equals("ctrl"))ctrl=true;else if(id.equals("rapid"))rapid=true;
            else if(id.equals("unknown_speed"))speed=4;
            else if(id.equals("wrap")){speed=2;delta=10;now=-16;}
            else if(id.equals("large_elapsed")){speed=3;delta=Integer.MIN_VALUE;}
            else if(id.equals("late_frames")){speed=2;delta=1000;}
            else if(id.equals("arg1_negative")||id.equals("flags")||id.equals("capture_fail")||id.equals("interrupt_final")||id.equals("interrupt_fields"))speed=3;
            else if(id.equals("interrupt_stalled"))delta=0;
            if(id.equals("capture_fail"))handle=-1;
        }
        Fixture copy(){Fixture out=new Fixture(id);out.events.addAll(events);out.argument=argument;out.fade=fade;out.loop=loop;out.delay=delay;out.speed=speed;out.now=now;out.delta=delta;out.pumps=pumps;out.handle=handle;out.ctrl=ctrl;out.rapid=rapid;return out;}
        public int argument1(){return argument;}
        public void argument1(int value){argument=value;events.add("arg1:"+value);}
        public int speed(){events.add("speed:"+speed);return speed;}
        public boolean skipRequested(){boolean skip=ctrl||rapid;events.add("skip:"+(skip?1:0));return skip;}
        public void fadeFlag(int value){fade=value;events.add("fade:"+value);}
        public void loopFlag(int value){loop=value;events.add("loop:"+value);}
        public void schedulerDelay(int milliseconds){delay=milliseconds;events.add("delay:"+milliseconds);}
        public int tickCount(){events.add("tick:"+Integer.toUnsignedString(now));return now;}
        private void failure(String stage)throws IOException{if(failStage.equals(stage))throw new IOException("fixture "+stage);}
        public int capture(Pc1LoadFade.Direction direction)throws IOException{events.add("capture:"+direction+":"+handle);failure("capture");if(id.equals("capture_high"))argument1(20);return handle;}
        public void draw(int capturedHandle,int weight)throws IOException{events.add("draw:"+capturedHandle+":"+weight);failure("draw");
            if(observed!=null){try{wire(observed);}catch(IOException expected){snapshotRejected=true;}try{observed.copy();}catch(IOException expected){copyRejected=true;}try{observed.step(this);}catch(IOException expected){reentryRejected=true;}}
        }
        public void present()throws IOException{events.add("present");failure("present");}
        public int pumpMessages()throws IOException{
            events.add("pump:enter");failure("pump");pumps++;now+=delta;
            if(pumps==1){
                if(id.equals("speed_changes")){events.add("callback:speed:3");speed=3;}
                if(id.equals("arg1_fifteen"))argument1(15);if(id.equals("arg1_high"))argument1(20);if(id.equals("arg1_negative"))argument1(-20);
                if(id.equals("interrupt_fields"))argument1(16);
                if(id.equals("flags")||id.equals("interrupt_fields")){fadeFlag(7);loopFlag(9);}
            }
            int result=id.equals("interrupt_first")&&pumps==1||id.equals("interrupt_final")&&pumps==2||id.equals("interrupt_stalled")&&pumps==3||id.equals("interrupt_fields")&&pumps==1?1:0;
            events.add("pump:return:"+result);return result;
        }
        public void release(int capturedHandle)throws IOException{events.add("release:"+capturedHandle);failure("release");}
    }
    private static byte[] wire(Pc1LoadFade fade)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();fade.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static Pc1LoadFade restore(byte[] bytes)throws IOException{return Pc1LoadFade.load(new DataInputStream(new ByteArrayInputStream(bytes)));}
    private static String digest(List<String> events)throws Exception{byte[] hash=MessageDigest.getInstance("SHA-256").digest(String.join("\n",events).getBytes(StandardCharsets.US_ASCII));StringBuilder out=new StringBuilder();for(byte value:hash)out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();}
    private static void finish(Pc1LoadFade fade,Fixture host)throws IOException{for(int n=0;fade.active();n++){if(n>200)throw new IOException("fixture step budget");fade.step(host);}}
    private static void checkResult(Pc1LoadFade fade,Fixture host,Object[] reference)throws Exception{
        String label=reference[0]+"/"+reference[1];check(digest(host.events).equals(reference[2]),"entire native field/call journal "+label+" got"+digest(host.events));
        check(fade.nativeResult()==(Integer)reference[3],"native return0/1 "+label);check(host.argument==(Integer)reference[4],"external argument1 alias "+label);
        check(host.fade==(Integer)reference[5]&&host.loop==(Integer)reference[6],"normal/interrupt native flags "+label);check(host.delay==(Integer)reference[7]&&host.pumps==(Integer)reference[8],"native scheduler and complete pump count "+label);
    }
    public static int run()throws Exception{
        count=0;
        check(Pc1LoadFade.Direction.IN.captureName.equals("fadein()")&&Pc1LoadFade.Direction.OUT.captureName.equals("fadeout()"),"capture names are exact current CHS PE strings");
        for(Object[] reference:GOLDEN){
            Fixture host=new Fixture((String)reference[1]);Pc1LoadFade fade=Pc1LoadFade.begin(Pc1LoadFade.Direction.valueOf((String)reference[0]),host);boolean ready=false,pump=false;
            // Every fixture resumes once at each available continuation kind.
            for(int n=0;;n++){
                if(fade.phase()==Pc1LoadFade.Phase.READY&&!ready||fade.phase()==Pc1LoadFade.Phase.PUMP&&!pump||!fade.active()){
                    byte[] saved=wire(fade);Pc1LoadFade next=restore(saved);Fixture resumed=host.copy();check(Arrays.equals(saved,wire(next)),"local phase codec exact roundtrip");finish(next,resumed);checkResult(next,resumed,reference);
                    Pc1LoadFade copied=fade.copy();Fixture copyHost=host.copy();finish(copied,copyHost);check(digest(copyHost.events).equals(reference[2]),"copy continuation emits no repeated capture/draw/pump");
                    if(fade.phase()==Pc1LoadFade.Phase.READY)ready=true;if(fade.phase()==Pc1LoadFade.Phase.PUMP)pump=true;
                }
                if(!fade.active())break;if(n>200)throw new AssertionError("fixture budget");fade.step(host);
            }
            checkResult(fade,host,reference);check(fade.releaseWasCalled()==((Integer)reference[3]==0),"interrupt retains capture while normal path releases even token-1");
            int size=host.events.size();Pc1LoadFade.Phase phase=fade.phase();check(fade.step(host)==phase&&host.events.size()==size,"terminal continuation is side-effect free");
        }
        for(Pc1LoadFade.Direction direction:Pc1LoadFade.Direction.values()){
            Fixture host=new Fixture("speed3");host.delta=39;Pc1LoadFade fade=Pc1LoadFade.begin(direction,host);check(fade.step(host)==Pc1LoadFade.Phase.PUMP&&host.argument==8,"first native frame owns39ms without advancing its counter again");
            host.delta=1;check(fade.step(host)==Pc1LoadFade.Phase.READY&&host.argument==8,"40th millisecond releases frame only after its second pump");host.delta=39;fade.step(host);
            check(fade.phase()==Pc1LoadFade.Phase.PUMP&&host.argument==16&&!fade.releaseWasCalled(),"final progress16 still owns its full40ms");byte[] saved=wire(fade);Fixture restoredHost=host.copy();Pc1LoadFade restored=restore(saved);restoredHost.delta=1;restored.step(restoredHost);
            check(restored.phase()==Pc1LoadFade.Phase.COMPLETE&&restoredHost.events.stream().filter(e->e.startsWith("draw:")).count()==2,"mid-final-frame restore does not redraw or recapture final submission");
            int release=restoredHost.events.indexOf("release:17"),lastPump=restoredHost.events.lastIndexOf("pump:return:0");check(lastPump<release&&restoredHost.events.get(lastPump+1).startsWith("tick:"),"last successful pump and unsigned clock check precede native cleanup");
            check(wire(restored).length==14,"P1E1 contains only direction,phase,localhandle and localstartTick");
        }
        Fixture callbackHost=new Fixture("speed0");Pc1LoadFade callback=Pc1LoadFade.begin(Pc1LoadFade.Direction.OUT,callbackHost);callbackHost.observed=callback;callback.step(callbackHost);
        check(callbackHost.snapshotRejected&&callbackHost.copyRejected&&callbackHost.reentryRejected&&callback.phase()==Pc1LoadFade.Phase.PUMP,"draw callback cannot serialize/reenter a partially committed function");
        Fixture aliasHost=new Fixture("speed0");Pc1LoadFade alias=Pc1LoadFade.begin(Pc1LoadFade.Direction.IN,aliasHost);byte[] saved=wire(alias);Fixture external=aliasHost.copy();external.argument=15;Pc1LoadFade next=restore(saved);next.step(external);
        check(external.argument==16&&aliasHost.argument==0&&external.events.contains("draw:17:16"),"local codec/copy never restores a hidden progress over the external C4 owner");rejects(()->alias.nativeResult(),"active continuation has no native completed result");
        for(String stage:new String[]{"draw","present","pump","release"}){
            Fixture bad=new Fixture("speed3");Pc1LoadFade value=Pc1LoadFade.begin(Pc1LoadFade.Direction.IN,bad);bad.failStage=stage;rejects(()->finish(value,bad),"host failure propagates "+stage);
            check(value.phase()==Pc1LoadFade.Phase.FAILED,"unknown partial host submission cannot resume as native success");int size=bad.events.size();rejects(()->value.step(bad),"failed continuation cannot repeat native resource effects");check(bad.events.size()==size,"failed step cannot retry callbacks");rejects(()->wire(value),"failed callback state has no safe resumable codec");
        }
        byte[] valid=wire(next);ByteArrayOutputStream outer=new ByteArrayOutputStream();outer.write(valid);new DataOutputStream(outer).writeInt(777);DataInputStream input=new DataInputStream(new ByteArrayInputStream(outer.toByteArray()));Pc1LoadFade.load(input);check(input.readInt()==777,"fade codec consumes no external field/resource owner");
        for(int n:new int[]{0,3,4,5,6,9,13}){byte[] cut=Arrays.copyOf(valid,n);rejects(()->restore(cut),"truncated local continuation rejected");}
        for(int[] corrupt:new int[][]{{3,'0'},{4,2},{4,255},{5,0},{5,5},{5,255}}){byte[] bad=valid.clone();bad[corrupt[0]]=(byte)corrupt[1];rejects(()->restore(bad),"unknown local version/direction/phase rejected");}
        rejects(()->Pc1LoadFade.begin(null,new Fixture("speed0")),"missing direction rejected");rejects(()->Pc1LoadFade.begin(Pc1LoadFade.Direction.IN,null),"missing host rejected");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 native load-fade assertions");}
}
