package org.circus.core;

import java.io.*;
import java.util.*;

/** Self-authored DC2 Windows frame admission, independently specified native
 * checkpoint projections and guarded ordinary-text import plans. */
public final class Dc2NativeSlotTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private interface Action {void run()throws Exception;}
    private static void reject(Action action,String why)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed,why);}
    private static void put(byte[] b,int at,int value){for(int n=0;n<4;n++)b[at+n]=(byte)(value>>>(n*8));}
    private static void string(byte[] b,int at,String value)throws IOException{byte[] text=value.getBytes(Binary.SJIS);System.arraycopy(text,0,b,at,text.length);b[at+text.length]=0;}
    static byte[] fixture()throws IOException{
        byte[] b=new byte[Dc2NativeSlot.FRAME_BYTES];put(b,0,1);string(b,4,"chapter");string(b,0x84,"BG/test");put(b,0x484,3);put(b,0x488,0x105);put(b,0x48c,0x345);put(b,0x490,1);put(b,0x494,2);put(b,0x498,7);
        b[0x49e]=1;b[0xc6c]=31;b[0xc6c+999]=(byte)255;put(b,0x1f24,1);put(b,0x1f28,462);put(b,0x1f2c,6);put(b,0x1f34,2);put(b,0x1f38,123);put(b,0x1f3c,-42);return b;
    }
    static MesScript caller(int opcode)throws IOException{
        byte[] tail=opcode==0x34?new byte[]{0x34,'V',0,0x4b,'A',0,7,0,0,0,0,0}:opcode==0x4b?new byte[]{0x4b,'A',0,7,0,0,0,0,0}:new byte[]{(byte)opcode,9,12,0,0,0};
        byte[] b=new byte[12+5+tail.length];put(b,0,2);put(b,4,2);put(b,8,5);b[12]=0x63;b[13]=0x33;b[14]=3;System.arraycopy(tail,0,b,17,tail.length);return new MesScript("chapter",b,Profiles.get("dc2-3363"));
    }
    public static int run()throws Exception{
        checks=0;byte[] source=fixture();Dc2NativeSlot slot=Dc2NativeSlot.parse(source);source[4]='X';
        check(slot.scriptName().equals("chapter"),"parse owns original bytes independently");check(slot.originalLength()==8004&&slot.frameBytes().length==8004&&slot.trailingBytes().length==0,"native frame length");
        check(slot.resource(Dc2NativeSlot.StringField.BACKGROUND).equals("BG/test"),"DC2 background path is normalized separately from text");
        check(slot.dword(Dc2NativeSlot.Dword.RAW_CODE_PC)==0x345&&slot.dword(Dc2NativeSlot.Dword.CURRENT_CHECKPOINT)==1,"raw stored cursor remains accessible independently");
        check(slot.ordinary(0,0)==31&&slot.ordinary(9,99)==255,"DC2 getter-bound ordinary bytes use unsigned values");byte[] ordinary=slot.ordinaryBytes();ordinary[0]=0;check(slot.ordinary(0,0)==31,"ordinary getter is a copy");
        check(Arrays.equals(slot.activeLogWords(),new int[]{0,1})&&slot.logWords().length==1000,"active and full ushort log views differ");
        for(int op:new int[]{0x34,0x4b}){
            MesScript code=caller(op);Dc2NativeSlot.Plan p=slot.normalTextPlan(code);
            check(p.script.equals("chapter")&&p.sha256.equals(code.sha256),"independent caller identity is bound to plan");
            check(p.savedRawCodePc==0x345&&p.savedWaitKind==7,"plan keeps stored diagnostic fields");
            check(p.checkpointIndex==1&&p.checkpointCodePc==5&&p.checkpointFilePc==17&&p.checkpointOpcode==op,"checkpoint table index drives file cursor");
            check(p.resumeCodePc==5&&p.resumeFilePc==17&&p.nativeWaitKind==-1&&p.candidateFlag==0,"34/4B replay from checkpoint, never saved rawPC");
            check(p.argument0==123&&p.argument1==-42,"whole-frame copy retains saved argument fields");
        }
        for(int op:new int[]{7,0x1a,0x1c}){
            Dc2NativeSlot.Plan p=slot.projection(caller(op));check(p.resumeCodePc==8&&p.resumeFilePc==20,"known short header advances exactly3 bytes");
            check(p.nativeWaitKind==(op==7?7:op==0x1c?0x1c:-1)&&p.candidateFlag==(op==0x1a?9:0),"known header native wait/borrowed flag projection");
            check(p.argument0==123&&p.argument1==-42,"header1C argument writes are overwritten by subsequent whole-frame copy");
            reject(()->slot.normalTextPlan(caller(op)),"non-text reconstruction requires additional native owners");
        }
        byte[] trailing=Arrays.copyOf(fixture(),8011);Arrays.fill(trailing,8004,8011,(byte)0x5a);Dc2NativeSlot extra=Dc2NativeSlot.parse(trailing);check(Arrays.equals(extra.originalBytes(),trailing)&&extra.trailingBytes().length==7&&extra.frameBytes().length==8004,"trailing bytes archived without entering frame backing");
        byte[] detached=extra.originalBytes();detached[0]=0;check(extra.dword(Dc2NativeSlot.Dword.SIGNATURE)==1,"original getter cannot mutate parsed frame");
        reject(()->Dc2NativeSlot.parse(new byte[8003]),"short slot rejected");reject(()->Dc2NativeSlot.parse(new byte[8004]),"signature rejected");reject(()->slot.ordinary(10,0),"ordinary bank bound");reject(()->slot.ordinary(0,-1),"ordinary index bound");
        byte[] bad=fixture();put(bad,0x494,1001);Dc2NativeSlot badLog=Dc2NativeSlot.parse(bad);reject(()->badLog.activeLogWords(),"malformed count survives raw parse but cannot enter active view");
        bad=fixture();put(bad,0x490,2);Dc2NativeSlot wrongIndex=Dc2NativeSlot.parse(bad);reject(()->wrongIndex.projection(caller(0x34)),"checkpoint index checked against caller table");
        bad=fixture();put(bad,0x115c,1);Dc2NativeSlot pending=Dc2NativeSlot.parse(bad);reject(()->pending.normalTextPlan(caller(0x34)),"pending raster batch not silently discarded");
        bad=fixture();put(bad,0x498,9);Dc2NativeSlot choice=Dc2NativeSlot.parse(bad);reject(()->choice.normalTextPlan(caller(0x34)),"choice wait not ordinary text");
        bad=fixture();put(bad,0x1f2c,2);Dc2NativeSlot rain=Dc2NativeSlot.parse(bad);reject(()->rain.normalTextPlan(caller(0x34)),"unsupported rain producer is rejected");
        bad=fixture();bad[0x49e]=0;Dc2NativeSlot mismatch=Dc2NativeSlot.parse(bad);reject(()->mismatch.normalTextPlan(caller(0x34)),"last log and current checkpoint must match");
        bad=fixture();bad[0x49f]=(byte)0x80;Dc2NativeSlot tagged=Dc2NativeSlot.parse(bad);check(tagged.normalTextPlan(caller(0x34)).checkpointIndex==1&&tagged.activeLogWords()[1]==0x8001,"history flag retained while checkpoint identity masks8000");
        bad=fixture();string(bad,0x84,"../escape");Dc2NativeSlot traversal=Dc2NativeSlot.parse(bad);reject(()->traversal.normalTextPlan(caller(0x34)),"resource cannot escape selected game");
        bad=fixture();Arrays.fill(bad,0x84,0x104,(byte)'A');Dc2NativeSlot crossing=Dc2NativeSlot.parse(bad);check(crossing.rawCString(Dc2NativeSlot.StringField.BACKGROUND).length==128,"raw native C read retains cross-cell result");reject(()->crossing.normalTextPlan(caller(0x34)),"portable import requires reviewed cell fit");
        bad=fixture();Arrays.fill(bad,0x84,8004,(byte)1);Dc2NativeSlot noNul=Dc2NativeSlot.parse(bad);reject(()->noNul.rawCString(Dc2NativeSlot.StringField.BACKGROUND),"C read cannot leave loaded8004 backing");
        bad=fixture();bad[0x84]=(byte)0x81;bad[0x85]=0;Dc2NativeSlot invalidText=Dc2NativeSlot.parse(bad);reject(()->invalidText.normalTextPlan(caller(0x34)),"invalid CP932 path rejected");
        bad=fixture();put(bad,0x48c,Integer.MAX_VALUE);check(Dc2NativeSlot.parse(bad).normalTextPlan(caller(0x34)).resumeFilePc==17,"unused saved rawPC is not trusted or clamped into execution");
        MesScript code=caller(0x34);MesScript different=new MesScript("other",new byte[]{0,0,0,0,0x63,0x33,0,0,0},code.profile);reject(()->slot.projection(different),"wrong named caller rejected");
        reject(()->slot.projection(caller(3)),"unreviewed checkpoint opcode rejected");
        byte[] beyond=new byte[100028];put(beyond,0,2);put(beyond,4,2);put(beyond,8,100005);beyond[12]=0x63;beyond[13]=0x33;beyond[100017]=0x4b;beyond[100018]=65;MesScript large=new MesScript("chapter",beyond,Profiles.get("dc2-3363"));reject(()->slot.projection(large),"file bytes beyond native100000 read cannot stand in for native code");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc2NativeSlotTests "+run()+" PASS");}
}
