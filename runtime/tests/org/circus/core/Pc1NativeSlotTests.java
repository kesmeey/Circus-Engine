package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Native slot image tests, not load/reconstruction tests. The small private
 *instruction interpreter below uses current CHS41A9E2..41AA1A machine bytes
 *(SHA7988971ff38f6b0fb2f21827b1ba38c3c7fe5cc824420b179ea9d9a838872057).
 *No target instructions, target executable or OS/audio/graphics code is run. */
public final class Pc1NativeSlotTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private interface Action{void run()throws Exception;}
    private static void rejects(Action action,String message)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=expected.getMessage().contains(message);}check(failed,"reject "+message);}
    private static void put(byte[] b,int at,int value){for(int n=0;n<4;n++)b[at+n]=(byte)(value>>>(n*8));}
    private static int get(byte[] b,int at){return (b[at]&255)|((b[at+1]&255)<<8)|((b[at+2]&255)<<16)|((b[at+3]&255)<<24);}
    private static byte[] image(int extra){byte[] b=new byte[8004+extra];for(int n=0;n<b.length;n++)b[n]=(byte)(n*73+19);put(b,0,1);return b;}
    private static byte[] hex(String s){byte[] b=new byte[s.length()/2];for(int n=0;n<b.length;n++)b[n]=(byte)Integer.parseInt(s.substring(n*2,n*2+2),16);return b;}
    /** Whitelist interpreter over a private bytearray. Open/read are modeled
     *only as read-count=min(file bytes,8004); exact original admission/copy
     *branches are interpreted, rather than reusing production field constants. */
    private static byte[] nativeAdmission(byte[] file)throws Exception{
        byte[] code=hex("81ff441f0000740c5f83c8ff5e81c404010000c3833d003f9d0001740c5f83c8ff5e81c404010000c3b9d1070000be003f9d00bf485e9d00f3a5");
        int pc=0,edi=Math.min(file.length,8004),ecx=0,esi=0;boolean equal=false;
        for(int steps=0;steps<40;steps++){
            int op=code[pc]&255;
            if(op==0x81&&(code[pc+1]&255)==0xff){equal=edi==get(code,pc+2);pc+=6;}
            else if(op==0x74){pc+=2+(equal?code[pc+1]:0);}
            else if(op==0x5f||op==0x5e){pc++;}
            else if(op==0x83&&(code[pc+1]&255)==0xc8){pc+=3;}
            else if(op==0x81&&(code[pc+1]&255)==0xc4){pc+=6;}
            else if(op==0xc3)return null;
            else if(op==0x83&&(code[pc+1]&255)==0x3d){
                if(get(code,pc+2)!=0x9d3f00||file.length<4)throw new AssertionError("native probe backing");equal=get(file,0)==code[pc+6];pc+=7;
            }
            else if(op==0xb9){ecx=get(code,pc+1);pc+=5;}
            else if(op==0xbe){esi=get(code,pc+1);pc+=5;}
            else if(op==0xbf){edi=get(code,pc+1);pc+=5;}
            else if(op==0xf3&&(code[pc+1]&255)==0xa5){
                if(esi!=0x9d3f00||edi!=0x9d5e48||ecx!=0x7d1)throw new AssertionError("native probe copy owners");return Arrays.copyOf(file,4*ecx);
            }
            else throw new AssertionError("unhandled native probe opcode @"+pc);
        }
        throw new AssertionError("native probe did not finish");
    }
    private static void admission()throws Exception{
        byte[] full=image(0);rejects(()->Pc1NativeSlot.parse(null),"at least8004");
        for(int n=0;n<8004;n++){
            final byte[] shortFile=Arrays.copyOf(full,n);check(nativeAdmission(shortFile)==null,"machine branch rejects short read "+n);
            rejects(()->Pc1NativeSlot.parse(shortFile),"at least8004");
        }
        for(int signature:new int[]{0,2,-1,0x01000001,0x00000100,Integer.MIN_VALUE}){
            byte[] invalid=full.clone();put(invalid,0,signature);check(nativeAdmission(invalid)==null,"machine branch exact DWORD signature");
            rejects(()->Pc1NativeSlot.parse(invalid),"signature DWORD");
        }
        for(int extra:new int[]{0,1,4,8004}){
            byte[] input=image(extra),before=input.clone();Pc1NativeSlot slot=Pc1NativeSlot.parse(input);
            check(Arrays.equals(nativeAdmission(input),slot.frameBytes()),"native copy is first8004 bytes");
            check(slot.frameBytes().length==8004&&slot.originalLength()==input.length,"native frame and total lengths differ");
            check(Arrays.equals(slot.originalBytes(),before),"original full input roundtrip");
            check(Arrays.equals(slot.trailingBytes(),Arrays.copyOfRange(before,8004,before.length)),"trailing preserved, not native frame");
            Arrays.fill(input,(byte)0);check(Arrays.equals(slot.originalBytes(),before),"parse owns defensive clone");
            byte[] returned=slot.originalBytes();Arrays.fill(returned,(byte)0);returned=slot.frameBytes();Arrays.fill(returned,(byte)0);returned=slot.trailingBytes();Arrays.fill(returned,(byte)0);
            check(Arrays.equals(slot.originalBytes(),before),"raw byte views cannot mutate backing");
        }
    }
    private static void fields()throws Exception{
        byte[] input=image(7);Pc1NativeSlot slot=Pc1NativeSlot.parse(input);
        // Independently named native live addresses, not copied enum offsets.
        Pc1NativeSlot.Dword[] names={Pc1NativeSlot.Dword.SIGNATURE,Pc1NativeSlot.Dword.LOGICAL_MUSIC,Pc1NativeSlot.Dword.PACKED_SOUND,Pc1NativeSlot.Dword.RAW_CODE_PC,Pc1NativeSlot.Dword.CURRENT_CHECKPOINT,Pc1NativeSlot.Dword.LOG_COUNT,Pc1NativeSlot.Dword.WAIT_KIND,
            Pc1NativeSlot.Dword.BACKGROUND_X,Pc1NativeSlot.Dword.BACKGROUND_Y,Pc1NativeSlot.Dword.ACTOR1_X,Pc1NativeSlot.Dword.ACTOR1_Y,Pc1NativeSlot.Dword.ACTOR2_X,Pc1NativeSlot.Dword.ACTOR2_Y,Pc1NativeSlot.Dword.PENDING_COUNT,
            Pc1NativeSlot.Dword.CACHED_SCENARIO_READ,Pc1NativeSlot.Dword.PARAGRAPH_MODE,Pc1NativeSlot.Dword.HEAD_Y,Pc1NativeSlot.Dword.EFFECT_KIND,Pc1NativeSlot.Dword.EFFECT_STRENGTH,Pc1NativeSlot.Dword.ARGUMENT0,Pc1NativeSlot.Dword.ARGUMENT1};
        int[] live={0x9d8588,0x9d8a0c,0x9d8a10,0x9d8a14,0x9d8a18,0x9d8a1c,0x9d8a20,0x9d96bc,0x9d96c0,0x9d96c4,0x9d96c8,0x9d96cc,0x9d96d0,0x9d96e4,0x9da4a8,0x9da4ac,0x9da4b0,0x9da4b4,0x9da4bc,0x9da4c0,0x9da4c4};
        for(int n=0;n<live.length;n++)check(slot.dword(names[n])==get(input,live[n]-0x9d8588),"native field address "+names[n]);
        Pc1NativeSlot.StringField[] stringNames={Pc1NativeSlot.StringField.SCRIPT,Pc1NativeSlot.StringField.BACKGROUND,Pc1NativeSlot.StringField.ACTOR1,Pc1NativeSlot.StringField.ACTOR2,Pc1NativeSlot.StringField.OVERLAY1,Pc1NativeSlot.StringField.OVERLAY2,Pc1NativeSlot.StringField.RESOURCE3,Pc1NativeSlot.StringField.RESOURCE4,Pc1NativeSlot.StringField.RESOURCE5,Pc1NativeSlot.StringField.ACTION0,Pc1NativeSlot.StringField.ACTION1};
        int[] stringLive={0x9d858c,0x9d860c,0x9d868c,0x9d870c,0x9d878c,0x9d880c,0x9d888c,0x9d890c,0x9d898c,0x9d95dc,0x9d95fc};
        for(int n=0;n<stringNames.length;n++){int offset=stringLive[n]-0x9d8588,size=n<9?128:32;check(Arrays.equals(slot.rawCell(stringNames[n]),Arrays.copyOfRange(input,offset,offset+size)),"native string cell address "+stringNames[n]);}
        check(slot.rawDword(8000)==get(input,8000),"last unknown frame DWORD remains accessible");
        check(slot.rawDword(1)==get(input,1),"raw unaligned DWORD view");
        check(slot.rawBytes(8004,0).length==0,"empty frame-end slice");
        rejects(()->slot.rawDword(8001),"outside owned");rejects(()->slot.rawBytes(Integer.MAX_VALUE,4),"outside owned");rejects(()->slot.rawBytes(0,-1),"outside owned");rejects(()->slot.rawBytes(8004,1),"outside owned");
        rejects(()->slot.dword(null),"missing DWORD");rejects(()->slot.rawCString(null),"missing string");
        byte[] ordinary=slot.ordinaryBytes();check(ordinary.length==1000,"ordinary owner exact1000");
        for(int b=0;b<10;b++)for(int n=0;n<100;n++)check(slot.ordinary(b,n)==(input[0x9d91f4-0x9d8588+b*100+n]&255),"ordinary field byte");
        Arrays.fill(ordinary,(byte)0);check(Arrays.equals(slot.ordinaryBytes(),Arrays.copyOfRange(input,0xc6c,0x1054)),"ordinary view clone");
        rejects(()->slot.ordinary(10,0),"outside0..9");rejects(()->slot.ordinary(9,100),"outside0..9");
        int[] log=slot.rawLogWords();check(log.length==1000,"log owner exact1000 words");
        for(int n=0;n<log.length;n++){int at=0x9d8a24-0x9d8588+2*n;check(log[n]==((input[at]&255)|((input[at+1]&255)<<8)),"log LE ushort including inactive words");}
        log[0]=0;check(slot.rawLogWords()[0]!=0,"log view clone");
        for(int n=0;n<60;n++){
            int[] record=slot.pendingRecordDwords(n);check(record.length==8,"native pending32-byte record");
            for(int word=0;word<8;word++)check(record[word]==get(input,0x9d96e8-0x9d8588+32*n+4*word),"pending DWORD including inactive records");
            record[0]=0;check(slot.pendingRecordDwords(n)[0]!=0,"pending view clone");
        }
        rejects(()->slot.pendingRecordDwords(-1),"outside owned60");rejects(()->slot.pendingRecordDwords(60),"outside owned60");
        for(int rawCount:new int[]{-1,1001,Integer.MAX_VALUE}){byte[] malformed=input.clone();put(malformed,0x494,rawCount);Pc1NativeSlot accepted=Pc1NativeSlot.parse(malformed);check(accepted.dword(Pc1NativeSlot.Dword.LOG_COUNT)==rawCount,"admission keeps raw unsafe log count");rejects(accepted::activeLogWords,"log count exceeds");check(Arrays.equals(accepted.originalBytes(),malformed),"unsafe count raw roundtrip");}
        for(int rawCount:new int[]{0,1,1000}){byte[] valid=input.clone();put(valid,0x494,rawCount);check(Pc1NativeSlot.parse(valid).activeLogWords().length==rawCount,"optional active log count");}
        for(int rawCount:new int[]{-1,61,Integer.MAX_VALUE}){byte[] malformed=input.clone();put(malformed,0x115c,rawCount);Pc1NativeSlot accepted=Pc1NativeSlot.parse(malformed);check(accepted.dword(Pc1NativeSlot.Dword.PENDING_COUNT)==rawCount,"admission keeps unsafe pending count");rejects(accepted::activePendingCount,"pending count exceeds");}
        for(int rawCount:new int[]{0,1,60}){byte[] valid=input.clone();put(valid,0x115c,rawCount);check(Pc1NativeSlot.parse(valid).activePendingCount()==rawCount,"optional active pending count");}
    }
    private static void strings()throws Exception{
        for(Pc1NativeSlot.StringField field:Pc1NativeSlot.StringField.values()){
            byte[] input=new byte[8004+1];Arrays.fill(input,(byte)0xe3);put(input,0,1);int length=field.cellBytes+3;input[field.offset+length]=0;
            Pc1NativeSlot slot=Pc1NativeSlot.parse(input);byte[] raw=slot.rawCString(field);
            check(raw.length==length&&raw[0]==(byte)0xe3,"native C scan crosses cell "+field);
            check(slot.rawCell(field).length==field.cellBytes,"separate raw cell view "+field);
            Arrays.fill(raw,(byte)0);byte[] cell=slot.rawCell(field);Arrays.fill(cell,(byte)0);check(slot.rawCString(field)[0]==(byte)0xe3,"C/cell view clone "+field);
            Arrays.fill(input,field.offset,8004,(byte)0xff);input[8004]=0;Pc1NativeSlot noNul=Pc1NativeSlot.parse(input);
            rejects(()->noNul.rawCString(field),"beyond owned8004");check(Arrays.equals(noNul.originalBytes(),input),"noNUL accepted losslessly; trailing NUL not frame "+field);
            input[8003]=0;check(Pc1NativeSlot.parse(input).rawCString(field).length==8003-field.offset,"last owned byte NUL usable "+field);
        }
        byte[] input=image(0);input[4]=0;check(Pc1NativeSlot.parse(input).rawCString(Pc1NativeSlot.StringField.SCRIPT).length==0,"empty raw script admitted, not resolved");
    }
    private static void metadata()throws Exception{
        byte[] input=image(13),before=input.clone();Pc1NativeSlot slot=Pc1NativeSlot.parse(input);
        int[] nativeAddresses={0x9ced1a,0x9ced2c,0x9ced2d,0x9ced2e,0x9ced2f,0x9ced30,0x9ced31};
        int[] values={6,0xe7,11,29,23,59,(2026+0x30)&255};
        Pc1NativeSlot patched=slot.withNativeSaveMetadata(6,0xe7,2026,11,29,23,59);byte[] expected=input.clone();put(expected,0x9cdd28-0x9cdd28,1);
        for(int n=0;n<nativeAddresses.length;n++)expected[nativeAddresses[n]-0x9cdd28]=(byte)values[n];
        check(Arrays.equals(patched.originalBytes(),expected),"404E70 native absolute-address seven-byte reference");
        check(Arrays.equals(slot.originalBytes(),before),"metadata patch immutable original");
        Set<Integer> allowed=new HashSet<>();for(int address:nativeAddresses)allowed.add(address-0x9cdd28);byte[] patchedBytes=patched.originalBytes();
        for(int n=0;n<input.length;n++)if(!allowed.contains(n))check(patchedBytes[n]==input[n],"unknown/padding/inactive/trailing byte unchanged "+n);
        check(Arrays.equals(patched.trailingBytes(),slot.trailingBytes()),"metadata preserves trailing, native frame excludes it");
        for(int year:new int[]{0,207,208,255,256,65535})check(Pc1NativeSlot.parse(input).withNativeSaveMetadata(255,254,year,1,1,0,0).ordinary(9,25)==((year+0x30)&255),"native year addition wraps byte");
        rejects(()->slot.withNativeSaveMetadata(256,0,2026,1,1,0,0),"metadata fields");rejects(()->slot.withNativeSaveMetadata(0,-1,2026,1,1,0,0),"metadata fields");rejects(()->slot.withNativeSaveMetadata(0,0,65536,1,1,0,0),"metadata fields");rejects(()->slot.withNativeSaveMetadata(0,0,2026,0,1,0,0),"metadata fields");
    }
    private static byte[] saveFields(Pc1FrameFields fields)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();fields.save(new DataOutputStream(out));return out.toByteArray();}
    private static byte[] saveVariables(Variables variables)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();variables.save(new DataOutputStream(out));return out.toByteArray();}
    private static byte[] savePersistent(PcHost host)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();host.savePersistent(new DataOutputStream(out));return out.toByteArray();}
    private static byte[] saveTextBuffers(Pc1TextBuffers buffers)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();buffers.save(new DataOutputStream(out));return out.toByteArray();}
    private static PcHost ownerHost()throws IOException{
        final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<>(Arrays.asList("AdvData/DAT/NAMES.DAT","AdvData/GRP/NAMES.DAT"));}
            public byte[] read(String path,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture limit");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+length);}};}
        });PcHost host=Pc1TestDefaults.bind(new PcHost(new CircusResources(files,Profiles.get("dcpc-2063")),TextCodec.identity()));host.attach(new Variables(host,host.resources.profile.variableModel));return host;
    }
    private static void nativeOwners()throws Exception{
        int[] words=new int[1000];for(int n=0;n<words.length;n++)words[n]=(n*1777+0x8543)&0xffff;words[0]=0x8007;words[1]=0xffff;words[2]=0x8000;
        int[] expectedWords=words.clone();Pc1TextLog rawLog=Pc1TextLog.fromNative(65535,2,words);Arrays.fill(words,0);
        check(rawLog.count()==2&&rawLog.currentCheckpoint()==65535,"factory retains typed count/current checkpoint independently");
        check(Arrays.equals(rawLog.rawWords(),expectedWords)&&rawLog.pendingRawWord()==0x8000,"factory deep-copies all stale/page-bit words without append");
        int[] view=rawLog.rawWords();Arrays.fill(view,0);check(Arrays.equals(rawLog.rawWords(),expectedWords),"new log has no raw array alias");
        ByteArrayOutputStream logBytes=new ByteArrayOutputStream();rawLog.save(new DataOutputStream(logBytes));Pc1TextLog logDecoded=Pc1TextLog.load(new DataInputStream(new ByteArrayInputStream(logBytes.toByteArray())));
        check(logDecoded.count()==2&&logDecoded.currentCheckpoint()==65535&&Arrays.equals(logDecoded.rawWords(),expectedWords),"native log typed codec preserves1000 cells including inactive suffix");
        Pc1TextLog full=Pc1TextLog.fromNative(0,1000,expectedWords);check(full.count()==1000&&Arrays.equals(full.rawWords(),expectedWords),"full-capacity log projection allowed, without reading next alias word");rejects(()->full.onWait07(0,0),"next native word aliases");
        for(int checkpoint:new int[]{-1,65536,Integer.MAX_VALUE})check(Pc1TextLog.fromNative(checkpoint,0,expectedWords).currentCheckpoint()==checkpoint,"raw checkpoint DWORD is preserved by narrow projection");
        for(int length:new int[]{0,999,1001})rejects(()->Pc1TextLog.fromNative(0,0,new int[length]),"all1000 raw words");rejects(()->Pc1TextLog.fromNative(0,0,null),"all1000 raw words");
        for(int rawCount:new int[]{-1,1001,Integer.MAX_VALUE}){Pc1TextLog narrow=Pc1TextLog.fromNative(0,rawCount,expectedWords);check(narrow.count()==rawCount,"raw count DWORD is retained without inventing adjacent authority");rejects(narrow::pendingRawWord,"no authority");}
        for(int badWord:new int[]{-1,65536,Integer.MAX_VALUE}){int[] invalid=expectedWords.clone();invalid[999]=badWord;rejects(()->Pc1TextLog.fromNative(0,0,invalid),"native raw word");}

        byte[] input=image(5);put(input,0x490,65535);put(input,0x494,2);put(input,0x1f20,255);put(input,0x1f28,0x89abcdef);put(input,0x498,0x87654321);put(input,0x1f38,0x12345678);put(input,0x1f3c,0xfedcba98);put(input,0x48c,0x76543210);
        for(int n=0;n<1000;n++){input[0x49c+n*2]=(byte)expectedWords[n];input[0x49d+n*2]=(byte)(expectedWords[n]>>>8);}
        byte[] original=input.clone();Pc1NativeSlot slot=Pc1NativeSlot.parse(input);Pc1FrameFields projected=Pc1FrameFields.fromNativeSlot(slot);
        check(projected.currentCheckpoint()==65535&&projected.textLog().count()==2&&Arrays.equals(projected.textLog().rawWords(),expectedWords),"slot raw log enters typed field owner without rebuilding entries");
        check(projected.cachedScenarioRead==255&&projected.headY==0x89abcdef&&projected.waitKind==0x87654321&&projected.argument0==0x12345678&&projected.argument1==0xfedcba98,"slot cache and all raw signed32 fields map exactly");
        Pc1TextLog detached=projected.textLog();detached.clearLog();detached.onCheckpoint04(0);check(projected.textLog().count()==2&&projected.currentCheckpoint()==65535&&Arrays.equals(projected.textLog().rawWords(),expectedWords),"immutable frame fields expose only a copied log");
        byte[] encoded=saveFields(projected);DataInputStream fieldInput=new DataInputStream(new ByteArrayInputStream(encoded));Pc1FrameFields decoded=Pc1FrameFields.load(fieldInput);
        check(fieldInput.available()==0&&Arrays.equals(encoded,saveFields(decoded)),"native subset uses explicit P1D2 narrow codec without extra raw-slot bytes");

        PcHost host=ownerHost();Variables variableOwner=host.variables;for(int b=0;b<10;b++)for(int n=0;n<100;n++)host.variables.write(b,n,(b*100+n*17)&255);host.variables.appendHistory();host.variables.write(0,0,254);
        host.write(100,3,45);host.write(90,6,77);host.pc1.textLog.onCheckpoint03(8);host.pc1.cachedScenarioRead(1);host.pc1.headY(2);host.pc1.waitKind(7);host.pc1.argument0(3);host.pc1.argument1(4);host.pc1.historySelection(123456);
        host.body="live body";host.speaker="live speaker";host.checkpoint=27;host.checkpointLabel="live checkpoint";host.logicalMusic(19);host.audio[0]="live music";host.audioActive[0]=true;host.audioGeneration[0]=23;
        host.scene.slots[1]="live background";host.ui.actions[0]="live action";host.ui.palette[2]=0xff123456;host.history.append("live","entry","voice","caller",29);host.history.active=true;host.history.selected=0;
        host.pc1.textBuffers().writeExpanded(Pc1TextBuffers.LIVE_BODY,new byte[]{65,66});host.pc1.textBuffers().writeExpanded(Pc1TextBuffers.LIVE_SPEAKER,new byte[]{67});
        byte[] oldFields=saveFields(Pc1FrameFields.capture(host)),oldVariables=saveVariables(host.variables),oldPersistent=savePersistent(host),oldText=saveTextBuffers(host.pc1.textBuffers());Pc1TextLog liveLog=host.pc1.textLog;
        Pc1FrameFields.fromNativeSlot(slot);check(Arrays.equals(saveFields(Pc1FrameFields.capture(host)),oldFields),"factory alone never applies host fields");
        decoded.apply(host);check(host.pc1.textLog==liveLog&&Arrays.equals(saveFields(Pc1FrameFields.capture(host)),encoded),"apply updates only typed fields using the existing final log object");
        for(int n=0;n<1000;n++)check(host.pc1.textLog.rawWords()[n]==expectedWords[n],"codec/apply retains inactive ushort "+n);
        check(host.variables==variableOwner&&Arrays.equals(saveVariables(host.variables),oldVariables),"ordinary, shadow and variable history stay live byte-for-byte");
        check(Arrays.equals(savePersistent(host),oldPersistent)&&host.read(90,6)==77,"persistent and scratch owners do not roll back");
        check(host.pc1.historySelection()==123456&&host.history.active&&host.history.selected==0&&host.history.entries.size()==1&&host.history.entries.get(0).body.equals("entry"),"external selection and portable history owners remain live");
        check(Arrays.equals(saveTextBuffers(host.pc1.textBuffers()),oldText)&&host.body.equals("live body")&&host.speaker.equals("live speaker")&&host.checkpoint==27&&host.checkpointLabel.equals("live checkpoint"),"global raw text and portable display/checkpoint adapters remain live");
        check(host.logicalMusic()==19&&host.audio[0].equals("live music")&&host.audioActive[0]&&host.audioGeneration[0]==23,"logical and physical audio are outside field projection");
        check(host.scene.slots[1].equals("live background")&&host.ui.actions[0].equals("live action")&&host.ui.palette[2]==0xff123456,"graphics and UI/palette owners remain live");
        check(Arrays.equals(slot.originalBytes(),original)&&slot.dword(Pc1NativeSlot.Dword.RAW_CODE_PC)==0x76543210,"factory/codec/apply never rewrites original slot or saved rawPC");
        rejects(()->Pc1FrameFields.fromNativeSlot(null),"require a native slot");
        int[][] rawFields={{0x490,-1},{0x490,65536},{0x494,-1},{0x494,1001},{0x1f20,-1},{0x1f20,256},{0x1f20,0x01000001}};
        for(int[] pair:rawFields){byte[] image=original.clone();put(image,pair[0],pair[1]);Pc1NativeSlot accepted=Pc1NativeSlot.parse(image);Pc1FrameFields rawView=Pc1FrameFields.fromNativeSlot(accepted);check((pair[0]==0x490?rawView.currentCheckpoint():pair[0]==0x494?rawView.textLog().count():rawView.cachedScenarioRead)==pair[1],"subset retains each raw DWORD instead of inventing a range gate");check(Arrays.equals(accepted.originalBytes(),image),"raw scalar projection does not reject/truncate its original slot");}
    }
    private static void projection()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] table=new byte[8];put(table,0,1);put(table,4,2);out.write(table);out.write(new byte[]{0x63,0x20,0x1d,9,8,0,0,0});
        MesScript caller=new MesScript("caller",out.toByteArray(),Profiles.get("dcpc-2063"));byte[] input=image(0);input[4]=0;
        put(input,0x48c,0xfedcba98);put(input,0x490,0);put(input,0x494,0);put(input,0x1f20,5);put(input,0x498,0x88776655);put(input,0x484,17);put(input,0x1f38,0x12345678);put(input,0x1f3c,0x87654321);
        Pc1NativeSlot slot=Pc1NativeSlot.parse(input);Pc1SlotProjection projected=slot.projection(caller,33);
        check(projected.resumeCodePc==5&&projected.resumeFilePc==13&&projected.wait==Pc1SlotProjection.Wait.SYSTEM_COMMAND,"checkpoint derives resume, not raw saved PC");
        check(projected.loadedFields.rawCodePc==0xfedcba98&&projected.loadedFields.argument0==0x12345678&&projected.loadedFields.argument1==0x87654321,"native rawPC/args retained independent of header operands");
        check(projected.loadedFields.logicalMusic==17&&projected.logicalMusicDuringReconstruction==33,"saved/live logical music distinct");
        Pc1FrameFields fields=Pc1FrameFields.fromNativeSlot(slot);check(fields.argument0==projected.loadedFields.argument0&&fields.argument1==projected.loadedFields.argument1&&fields.argument0!=projected.peekOperand0&&fields.argument1!=projected.peekOperand1,"native1D header prewrites cannot replace restored saved arguments");
        check(fields.waitKind==0x88776655&&fields.waitKind!=projected.wait.nativeValue,"saved raw wait and native load-projected wait remain separate owners");
        check(Arrays.equals(slot.originalBytes(),input),"projection does not rewrite candidate/frame");
        rejects(()->slot.projection(null,0),"independently resolved");put(input,0x490,-1);Pc1NativeSlot invalid=Pc1NativeSlot.parse(input);rejects(()->invalid.projection(caller,0),"checkpoint index outside");
    }
    public static int run()throws Exception{count=0;admission();fields();strings();metadata();nativeOwners();projection();return count;}
    /** Optional independently supplied real native slots; reads only, never
     *writes originals. No synthetic image is reported as a real-game sample. */
    public static void main(String[] args)throws Exception{
        int checks=run(),samples=0;for(String arg:args){Path path=Paths.get(arg);byte[] bytes=Files.readAllBytes(path);Pc1NativeSlot slot=Pc1NativeSlot.parse(bytes);check(Arrays.equals(bytes,slot.originalBytes()),"actual native slot exact roundtrip");StringBuilder sha=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))sha.append(String.format("%02x",b&255));System.out.println("actual read-only slot "+path.toAbsolutePath()+" bytes="+bytes.length+" sha256="+sha+" byteExact=true");samples++;}
        System.out.println("Pc1NativeSlotTests "+checks+" synthetic/reference checks PASS; actual native slot samples="+samples);
    }
}
