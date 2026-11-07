package org.circus.core;

import java.io.*;
import java.util.*;

/** Actual VM + canonical frame; literal expectations from currentCHS fetch/
 *handler instruction references. Probe host is only an explicit I/O boundary
 *journal, not a substitute implementation of native resource or render effects. */
public final class Pc1NativePcBindingTests {
    private static int count;
    private static void check(boolean value,String text){count++;if(!value)throw new AssertionError(text);}
    private interface Work{void run()throws Exception;}
    private static void reject(Work work,String text)throws Exception{boolean failed=false;try{work.run();}catch(IOException error){failed=String.valueOf(error.getMessage()).contains(text);}check(failed,"Reject "+text);}
    private static byte[] join(byte[]...pieces)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] piece:pieces)out.write(piece);return out.toByteArray();}
    private static byte[] words(int opcode,int...args){byte[] raw=new byte[1+2*args.length];raw[0]=(byte)opcode;for(int n=0;n<args.length;n++){raw[n*2+1]=(byte)args[n];raw[n*2+2]=(byte)(args[n]>>>8);}return raw;}
    private static byte[] text(int opcode,String value)throws IOException{return join(new byte[]{(byte)opcode},value.getBytes(Binary.SJIS),new byte[1]);}
    private static byte[] code(byte[]...body)throws IOException{return join(new byte[]{0,0,0,0,0x63,0x20},join(body),new byte[48]);}
    private static byte[] hex(String value){byte[] bytes=new byte[value.length()/2];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)Integer.parseInt(value.substring(n*2,n*2+2),16);return bytes;}
    private static final class Probe implements MesVm.ContextHost,MesVm.NativePcHost,MesVm.ScriptSystemHost,MesVm.ChoiceHost,MesVm.MenuHost {
        final Pc1NativeFrame frame=Pc1NativeFrame.zeroBss();final List<String> calls=new ArrayList<>();final List<Integer> fetches=new ArrayList<>();final String profile;
        Variables variables;MesScript script;boolean blocked,corruptOnSave;int reads,writes;String action="",pollAction="";MesVm.SystemReturn caller;Pc1NativeFrame suspended;
        Probe(){this("dcpc-2063");}Probe(String profile){this.profile=profile;}
        public Variables.NativeByteMemory nativeByteMemory(){return profile.equals("dcpc-2063")?frame:null;}
        public boolean signedNativeIndex(int bank){return profile.equals("dcpc-2063")&&bank==90;}
        public int read(int bank,int index)throws IOException{if(bank==90)return frame.readBank90(index);if(bank==99)return 0;throw new IOException("Probe has no bank "+bank);}
        public void write(int bank,int index,int value)throws IOException{if(bank==90){frame.writeBank90(index,value);return;}throw new IOException("Probe has no setter "+bank);}
        public int readNativeCodePc(){reads++;return frame.dword(Pc1NativeSlot.Dword.RAW_CODE_PC);}
        public void writeNativeCodePc(int pc){writes++;frame.dword(Pc1NativeSlot.Dword.RAW_CODE_PC,pc);}
        public void writeNativeArgument0(int value){frame.dword(Pc1NativeSlot.Dword.ARGUMENT0,value);}
        public void nativeHeaderLoaded(MesScript original)throws IOException{throw new IOException("PC-only probe has no native header owner");}
        public void beforeNativeScriptLoad(int opcode,String target){calls.add("beforeload"+opcode+":"+target+":"+readNativeCodePc());}
        public void attach(Variables variables){this.variables=variables;}
        public void position(MesScript script,int pc){this.script=script;}
        public void restored(MesScript script,int next){this.script=script;}
        public void fetched(MesScript.Instruction instruction){MesScript.Record r=instruction.record;frame.dword(Pc1NativeSlot.Dword.ARGUMENT0,r.args.length>0?r.args[0]:0);frame.dword(Pc1NativeSlot.Dword.ARGUMENT1,r.args.length>1?r.args[1]:0);if(r.opcode>=0x4a&&r.opcode<0x4e)frame.dword(Pc1NativeSlot.Dword.ARGUMENT1,r.bytes.length);fetches.add(r.offset);}
        public boolean beforeInstruction(Profile profile,int opcode){return blocked;}
        public boolean waiting(){return blocked;}
        public long waitTicket(){return 0;}
        public boolean signal(long ticket,MesVm.HostSignal signal){if(!blocked)return false;blocked=false;return true;}
        public void elapse(long milliseconds){}
        public boolean allowNamedJump(){return true;}
        public boolean beforeTransfer(int mode,String name){calls.add("load"+mode+":"+readNativeCodePc());return true;}
        public boolean command(String op,int[] args,byte[] bytes)throws IOException{
            calls.add(op+":"+readNativeCodePc());
            if(op.equals("pc.ui.wait")){blocked=true;return true;}
            return op.equals("pc.save_context")||op.equals("pc.resource_payload")||op.equals("dm.ui.text")||op.equals("pc.quad")||op.equals("host.command_with_string")||op.equals("pc.button_rect_payload")||op.equals("pc.grid.caption_payload")||op.equals("ui.button_text")||op.equals("pc.character_pair")||op.equals("pc.portrait_pair")||op.equals("native.no_effect");
        }
        public void enterSystemContext(MesScript script,int returnPc,int mode,byte[] target)throws IOException{calls.add("enter:"+readNativeCodePc());caller=new MesVm.SystemReturn(script.name,script.sha256,returnPc);suspended=frame.copy();}
        public MesVm.SystemReturn systemReturnPoint(){return caller;}
        public void leaveSystemContext()throws IOException{frame.copyFrom(suspended);caller=null;}
        public void addChoice(byte[] label,byte[] caption){calls.add("choice:"+readNativeCodePc()+":"+frame.dword(Pc1NativeSlot.Dword.ARGUMENT0));}
        public void selectChoice(byte[] label,int index){}
        public byte[] menuAction(long ticket,int button){return action.isEmpty()?null:action.getBytes(Binary.SJIS);}
        public void acceptMenuAction(int button){blocked=false;}
        public byte[] pollMenuAction(){if(pollAction.isEmpty())return null;byte[] raw=pollAction.getBytes(Binary.SJIS);pollAction="";blocked=false;return raw;}
        public void save(DataOutput out)throws IOException{Pc1NativeFrame image=frame.copy();if(corruptOnSave)image.dword(Pc1NativeSlot.Dword.RAW_CODE_PC,image.dword(Pc1NativeSlot.Dword.RAW_CODE_PC)+1);image.save(out);out.writeBoolean(blocked);}
        public void load(DataInput in)throws IOException{frame.copyFrom(Pc1NativeFrame.load(in));blocked=in.readBoolean();}
    }
    private static MesVm vm(Probe host,byte[] raw)throws IOException{return new MesVm(name->new MesScript(name,raw,Profiles.get(host.profile)),host,"START");}
    private static final String[] NATIVE={
        "bank90_pc_set|585a00aceb14000000|20|90|-5204|b4776db6c6d5d10b8340b855e475432a90c92fbc48abb0469651056edc4bc8ad",
        "ordinary_negative_index_pc|58000020f814000000|20|0|-2016|b6e5d8d76ef4d2a2fcaf020b3423a58cd6ebe71a903b58836ffaef4d8e7b2187",
        "ordinary_negative_bank_pc|58ecfff0ff14000000|20|-20|-16|1cc251a83845079fc98a9836ac2a5c0d3a01f357a792aa46ff764fbc6c78505a",
        "ordinary_normal|58000000002c010000|11|0|0|789726e07212b3bee0c86eea67f2fbcb9efd817fb942081d064551ffe90d124e",
        "bank90_pc_read_copy|59000000005a00aceb|11|0|0|85c01619d08d868a61387eb5a9053098204adaa255a430c4d014111a0a05f99d",
        "argument0_alias|585a00580663000000|11|99|1624|f1190688e6e9d49a2ce4afcb68cdc8433c9a49a1bce3d3f4d9593d8afbb9664a",
        "no_effect_mode|585a00aceb14000400|11|90|-5204|e68e297250f4b72f1b8d9df633263eb61bb9b763d1d19bc20e0b1bae03466b78",
        "sum_pc_read|5a00000000000000004e5a00aceb00000000020000|20|0|0|f06d5cabd543841d19b1dad7a4d3243d89645f00c9d6fe53207b4c449f336754",
        "sum_pc_destination|5a5a00aceb000000004e5a00aceb00000000020000|20|90|-5204|b4776db6c6d5d10b8340b855e475432a90c92fbc48abb0469651056edc4bc8ad",
        "compare_pc_literal|5b5a00aceb0b000000020000|14|1|-5204|c501e5076e6e039fff6020bb6f50143e75ff4ef3e97817db8ba36386db6bef82",
        "compare_unmatched_mode|5b5a00aceb0b000700020000|14|11|-5204|dd2e3b19ffdfa66153b3a6151144f2e8618e72da3f1544c785f7622437fb0218"
    };
    public static int run()throws Exception{
        count=0;
        for(String row:NATIVE){String[] c=row.split("\\|");Probe host=new Probe();MesVm vm=vm(host,code(hex(c[1])));check(vm.run(1)==MesVm.Status.RUNNING,"native machine vector executes one real fetch: "+c[0]);check(host.readNativeCodePc()==Integer.parseInt(c[2])&&vm.offset()==4+Integer.parseInt(c[2]),"frame PC remains authoritative after native handler: "+c[0]);check(host.frame.dword(Pc1NativeSlot.Dword.ARGUMENT0)==Integer.parseInt(c[3])&&host.frame.dword(Pc1NativeSlot.Dword.ARGUMENT1)==Integer.parseInt(c[4]),"native argument alias/result order: "+c[0]);check(Binary.sha256(host.frame.bytes()).equals(c[5]),"entire8004-byte image equals complete native fetch+handler golden: "+c[0]);}
        byte[] alias=code(words(0x58,90,-5204,20,0),words(0x58,0,0,11,0),words(0x58,0,1,42,0));Probe host=new Probe();MesVm vm=vm(host,alias);check(vm.run(2)==MesVm.Status.RUNNING&&vm.variables.read(0,0)==0&&vm.variables.read(0,1)==42,"bank90 changes the next executed PC and skips the intervening record");
        Probe inside=new Probe();MesVm insideVm=vm(inside,code(words(0x58,90,-5204,5,0)));check(insideVm.run(2)==MesVm.Status.RUNNING&&inside.readNativeCodePc()==8&&inside.frame.dword(Pc1NativeSlot.Dword.ARGUMENT0)==235&&inside.frame.dword(Pc1NativeSlot.Dword.ARGUMENT1)==5,"alias jump into an earlier word payload performs a new signed short fetch rather than cached nine-byte record");check(insideVm.run(1)==MesVm.Status.HALTED&&inside.readNativeCodePc()==8,"native STOP leaves its raw PC at the stop head");
        byte[] pending=vm.snapshot();Probe restored=new Probe();MesVm resumed=vm(restored,alias);resumed.restore(pending);check(Arrays.equals(pending,resumed.snapshot())&&resumed.offset()==vm.offset()&&restored.readNativeCodePc()==host.readNativeCodePc(),"newVM snapshot retains byte-exact canonical and projected cursors");
        Probe mismatch=new Probe();MesVm mismatchVm=vm(mismatch,alias);mismatch.corruptOnSave=true;byte[] inconsistent=mismatchVm.snapshot();Probe target=new Probe();MesVm targetVm=vm(target,alias);reject(()->targetVm.restore(inconsistent),"differs from canonical");
        Probe external=new Probe();MesVm externalVm=vm(external,alias);external.writeNativeCodePc(20);byte[] externalSave=externalVm.snapshot();Probe externalNext=new Probe();MesVm externalResumed=vm(externalNext,alias);externalResumed.restore(externalSave);check(externalResumed.offset()==24&&externalResumed.run(1)==MesVm.Status.RUNNING&&externalResumed.variables.read(0,1)==42,"snapshot reads current raw owner even before another VM instruction can mirror it");
        Probe blocked=new Probe();MesVm blockedVm=vm(blocked,code(words(0x58,0,0,1,0)));blocked.blocked=true;blocked.frame.dword(Pc1NativeSlot.Dword.ARGUMENT0,123);check(blockedVm.run(1)==MesVm.Status.WAIT_HOST&&blocked.readNativeCodePc()==2&&blocked.fetches.isEmpty()&&blocked.frame.dword(Pc1NativeSlot.Dword.ARGUMENT0)==123,"pre-fetch wait cannot advance PC or reset native arguments");blockedVm.signalHost(0,MesVm.HostSignal.ADVANCE);check(blockedVm.run(1)==MesVm.Status.RUNNING&&blocked.readNativeCodePc()==11,"released wait performs exactly one physical fetch");
        for(int opcode:new int[]{0x63,0x7f,0x80,0xff}){Probe defaults=new Probe();MesVm machine=vm(defaults,code(new byte[]{(byte)opcode,1,2,3,4,5,6,7,8}));check(machine.run(1)==MesVm.Status.RUNNING&&defaults.readNativeCodePc()==2+(opcode>=128?3:9)&&defaults.calls.isEmpty(),"real unsigned switch default retains signed physical fetch width "+opcode);}
        Probe marker=new Probe();MesVm markerVm=vm(marker,code(new byte[]{1,2,3,4,5,6,7,8,9}));marker.writeNativeCodePc(0);check(markerVm.run(1)==MesVm.Status.RUNNING&&marker.readNativeCodePc()==9&&marker.fetches.get(0)==4,"native known marker0 is fetched by the shared decoder as real63 default");
        Probe marker1=new Probe();MesVm marker1Vm=vm(marker1,code(new byte[]{1,2,3}));marker1.writeNativeCodePc(1);check(marker1Vm.run(1)==MesVm.Status.FAULT&&marker1.readNativeCodePc()==4&&!marker1Vm.error().contains("outside"),"known marker1 reaches its real20 handler boundary after short fetch instead of being rejected as unknown source bytes");
        Probe unsupported=new Probe();MesVm unsupportedVm=vm(unsupported,code(new byte[]{0x10,1,2}));check(unsupportedVm.run(1)==MesVm.Status.FAULT&&unsupported.readNativeCodePc()==5&&(unsupportedVm.error().contains("native.unmodelled")||unsupportedVm.error().contains("Unsupported opcode 0x10")),"defined but unknown00..62 handler still faults after its real fetch");
        Probe stale=new Probe();MesVm staleVm=vm(stale,code(new byte[]{1,0,0}));stale.writeNativeCodePc(999);check(staleVm.run(1)==MesVm.Status.FAULT&&stale.readNativeCodePc()==999&&stale.fetches.isEmpty()&&staleVm.error().contains("currently known"),"unknown stale native buffer suffix is neither zero-filled nor clamped");
        Probe longCode=new Probe();MesVm longVm=vm(longCode,code(new byte[100010]));longCode.writeNativeCodePc(100000);check(longVm.run(1)==MesVm.Status.FAULT&&longCode.fetches.isEmpty(),"native100000-byte loaded-code capacity remains a separate bound from file size");
        Probe fragment=new Probe();MesVm fragmentVm=vm(fragment,new byte[]{0,0,0,0,0x63,0x20,0});check(fragmentVm.run(1)==MesVm.Status.FAULT&&fragment.fetches.isEmpty()&&fragment.readNativeCodePc()==2&&fragmentVm.error().contains("known loaded code"),"STOP still fetches two native operand bytes; unknown stale suffix cannot become fabricated zero frame arguments");
        Probe phase=new Probe();MesVm phaseVm=vm(phase,code(new byte[]{0x1b,1,0,0x4b,'A',0},new byte[]{0x25,0,0,0x4c,'B',0},words(0x5c,1,2,3,4),new byte[]{0x4b,'C',0},words(0x53,1,2,3,4),words(0x52,5,6,0x40,0)));check(phaseVm.run(4)==MesVm.Status.RUNNING&&phase.calls.equals(Arrays.asList("pc.save_context:5","pc.resource_payload:14","dm.ui.text:23","pc.quad:36"))&&phase.readNativeCodePc()==44,"1B post-tail,25 pre-tail,5C post-tail and53 marker/words phases are native-ordered");
        Probe pairs=new Probe();MesVm pairVm=vm(pairs,code(text(0x33,"A"),text(0x46,"B")));check(pairVm.run(1)==MesVm.Status.RUNNING&&pairs.readNativeCodePc()==5&&pairs.calls.size()==2,"native33 borrows optional46 string without consuming its next fetch");check(pairVm.run(1)==MesVm.Status.RUNNING&&pairs.readNativeCodePc()==8&&pairs.fetches.size()==2&&pairs.frame.dword(Pc1NativeSlot.Dword.ARGUMENT0)==0,"borrowed46 still performs its own native string fetch and argument reset");
        Probe choices=new Probe();MesVm choiceVm=vm(choices,code(text(0x45,"pick"),new byte[]{0x4d,33,34,35,0}));check(choiceVm.run(1)==MesVm.Status.RUNNING&&choices.calls.equals(Collections.singletonList("choice:8:42"))&&choices.readNativeCodePc()==13,"45 manually consumes caption but exposes byte-count-times14 through canonical C0");
        byte[] branch=code(words(0x5b,0,0,1,0),new byte[]{0x7f,7,0},words(0x58,0,0,11,0),new byte[]{0x1f,1,7},words(0x58,0,1,42,0));Probe branchHost=new Probe();MesVm branchVm=vm(branchHost,branch);check(branchVm.run(1)==MesVm.Status.RUNNING&&branchHost.readNativeCodePc()==26&&branchHost.frame.dword(Pc1NativeSlot.Dword.ARGUMENT0)==0,"5B skips its raw3-byte companion without treating its marker as a framed opcode");check(branchVm.run(2)==MesVm.Status.HALTED&&branchVm.variables.read(0,0)==0&&branchVm.variables.read(0,1)==42,"native conditional scan writes its selected target rather than stale fallthrough");
        byte[] jumps=code(text(0x3b,"there"),words(0x58,0,0,11,0),text(0x32,"there"),words(0x58,0,1,42,0));Probe jumpHost=new Probe();MesVm jumpVm=vm(jumpHost,jumps);check(jumpVm.run(1)==MesVm.Status.RUNNING&&jumpHost.readNativeCodePc()==18,"required label jump installs native label-head cursor");check(jumpVm.run(3)==MesVm.Status.HALTED&&jumpVm.variables.read(0,0)==0&&jumpVm.variables.read(0,1)==42,"label fetch and target body continue from authoritative PC");
        byte[] menus=code(new byte[]{0x0a,0,0},text(0x32,"next"),words(0x58,0,0,7,0));Probe menu=new Probe();MesVm menuVm=vm(menu,menus);check(menuVm.run(1)==MesVm.Status.WAIT_HOST&&menu.readNativeCodePc()==5,"menu wait retains consumed nativePC");menu.action="next";check(menuVm.activateMenu(0,1)&&menu.readNativeCodePc()==5&&menuVm.run(3)==MesVm.Status.HALTED&&menuVm.variables.read(0,0)==7,"menu activation commits native target before resumed fetch");
        Probe timed=new Probe();MesVm timedVm=vm(timed,menus);timedVm.run(1);timed.pollAction="next";check(timedVm.run(1)==MesVm.Status.RUNNING&&timed.readNativeCodePc()==11&&timed.fetches.get(1)==9,"polled menu label commits target through the same native cursor path");
        Probe call=new Probe();Map<String,byte[]> calls=new HashMap<>();calls.put("START",code(new byte[]{0x26,0,0,0,'N','E','X','T',0},words(0x58,0,0,7,0)));calls.put("NEXT",code(new byte[]{0x1c,0,0}));MesVm callVm=new MesVm(name->new MesScript(name,calls.get(name),Profiles.get("dcpc-2063")),call,"START");check(callVm.run(1)==MesVm.Status.RUNNING&&call.calls.equals(Collections.singletonList("enter:11"))&&callVm.scriptName().equals("NEXT")&&call.readNativeCodePc()==2,"26 captures after owned tail consumption then loader initializes target PC");check(callVm.run(1)==MesVm.Status.RUNNING&&callVm.scriptName().equals("START")&&call.readNativeCodePc()==11,"1C explicit caller control flow replaces menuPC rather than keeping stale next");check(callVm.run(2)==MesVm.Status.HALTED&&callVm.variables.read(0,0)==7,"returned caller runs once");
        for(int transfer:new int[]{0x36,0x37}){Probe loading=new Probe();MesVm.Library library=name->{loading.calls.add("lookup:"+name);if(name.equals("START"))return new MesScript(name,code(text(transfer,"MISSING")),Profiles.get("dcpc-2063"));throw new FileNotFoundException(name);};MesVm missingVm=new MesVm(library,loading,"START");loading.calls.clear();check(missingVm.run(1)==MesVm.Status.FAULT&&loading.readNativeCodePc()==11,"failed target load retains its consumed source string PC");check(loading.calls.equals(transfer==0x36?Arrays.asList("beforeload54:MISSING:11","lookup:MISSING"):Collections.singletonList("lookup:MISSING")),"raw36 marks old caller before failing I/O; raw37 does not invoke that native consumer");}
        Probe later=new Probe("dc2pc-5769");byte[] laterScript=code(words(0x5c,0,0,7,0));laterScript[4]=0x69;laterScript[5]=0x57;MesVm laterVm=vm(later,laterScript);check(laterVm.run(1)==MesVm.Status.RUNNING&&laterVm.variables.read(0,0)==7&&later.writes==0&&later.reads==0,"NativePcHost capability on shared host cannot bind a non-PC1 profile to this frame PC contract");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 authoritative PC binding assertions");}
}
