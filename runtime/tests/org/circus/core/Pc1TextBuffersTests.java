package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Current CHS4123A0 private-CPU vectors plus known-memory lifetime tests.
 * Does not execute a native process or claim frame/slot/layout integration. */
public final class Pc1TextBuffersTests {
    private static int count;
    private static final String[] IDS={Pc1TextBuffers.LIVE_BODY,Pc1TextBuffers.LIVE_SPEAKER,Pc1TextBuffers.REDRAW_BODY,Pc1TextBuffers.REDRAW_SPEAKER};
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Action{void run()throws Exception;}
    private static void rejects(Action action,String why)throws Exception{boolean rejected=false;try{action.run();}catch(IOException|IllegalArgumentException expected){rejected=true;}check(rejected,why);}
    private static byte[] ascii(String text){return text.getBytes(StandardCharsets.US_ASCII);}
    private static byte[] repeat(int value,int length){byte[] bytes=new byte[length];Arrays.fill(bytes,(byte)value);return bytes;}
    private static byte[] wire(Pc1TextBuffers buffers)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();buffers.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static Pc1TextBuffers restore(byte[] bytes)throws IOException{return Pc1TextBuffers.load(new DataInputStream(new ByteArrayInputStream(bytes)));}
    private static int knownOffset(int owner){int result=4;for(int n=0;n<owner;n++)result+=4+Pc1TextBuffers.capacity(IDS[n]);return result;}
    private static void intAt(byte[] bytes,int offset,int value){for(int n=0;n<4;n++)bytes[offset+n]=(byte)(value>>>(24-8*n));}
    private static Pc1TextBuffers patterned(String owner)throws IOException{byte[] bytes=wire(new Pc1TextBuffers());int ordinal=Arrays.asList(IDS).indexOf(owner),start=knownOffset(ordinal)+4,capacity=Pc1TextBuffers.capacity(owner);for(int n=0;n<capacity;n++)bytes[start+n]=(byte)(n*29+7);return restore(bytes);}
    private static String sha(byte[] bytes)throws Exception{StringBuilder text=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))text.append(String.format(Locale.ROOT,"%02x",value&255));return text.toString();}
    private static void nativeReference(String owner,byte[] first,byte[] second,String expected)throws Exception{
        Pc1TextBuffers buffers=patterned(owner);buffers.writeExpanded(owner,first);buffers.writeExpanded(owner,second);
        check(sha(buffers.fullBytes(owner)).equals(expected),"entire backing matches current4123A0 interpreted instructions for "+owner+" first length"+first.length);
        check(Arrays.equals(buffers.read(owner),second),"logical output matches native second write");
        check(buffers.unsignedByte(owner,second.length)==0&&buffers.unsignedByte(owner,second.length+1)==0,"both native terminating stores occur");
        check(Arrays.equals(wire(buffers),wire(restore(wire(buffers)))),"patterned stale suffix survives full portable save/load");
    }
    public static int run()throws Exception{
        count=0;Pc1TextBuffers buffers=new Pc1TextBuffers();
        check(Arrays.equals(IDS,Pc1TextBuffers.ownerIds()),"four stable IDs have fixed codec order");
        check(new HashSet<String>(Arrays.asList(IDS)).size()==4,"redraw and live addresses never alias owner IDs");
        check(IDS[0].equals("pc1.live.body.9d3af8")&&IDS[1].equals("pc1.live.speaker.9ccaa0")&&IDS[2].equals("pc1.redraw.418220.local400")&&IDS[3].equals("pc1.redraw.418220.local500"),"layout alias identity remains stable across copies/restores");
        for(int n=0;n<4;n++){
            String id=IDS[n];int capacity=n%2==0?1024:256;
            check(Pc1TextBuffers.capacity(id)==capacity&&buffers.fullBytes(id).length==capacity,"native destination capacity "+id);
            check(buffers.knownPrefixLength(id)==(n<2?capacity:0),"BSS known, fresh redraw stack unknown "+id);
            if(n<2){check(buffers.read(id).length==0,"live PE initial zero C string");check(buffers.unsignedByte(id,capacity-1)==0,"entire live virtual zero range is known");}
            else{rejects(()->buffers.read(id),"unknown redraw memory cannot produce a logical string");rejects(()->buffers.unsignedByte(id,0),"Java allocation zeros do not make stack byte0 known");}
            byte[] full=buffers.fullBytes(id);full[0]=99;check(buffers.fullBytes(id)[0]==0,"full backing clone cannot mutate owner");
        }
        String[] aliases=Pc1TextBuffers.ownerIds();aliases[0]="fake";check(Pc1TextBuffers.ownerIds()[0].equals(IDS[0]),"ID array is detached");
        rejects(()->Pc1TextBuffers.capacity("pc1.418090.scratch"),"encoded scratch is not an expanded destination owner");
        rejects(()->buffers.read(null),"null ID never silently selects live memory");
        buffers.beginRedraw();check(buffers.knownPrefixLength(IDS[2])==0&&buffers.knownPrefixLength(IDS[3])==1,"native redraw entry writes only speakerhead");
        check(buffers.read(IDS[3]).length==0&&buffers.knownBytes(IDS[3]).length==1,"head-only empty speaker does not imply known second zero");
        rejects(()->buffers.unsignedByte(IDS[3],1),"redraw second byte remains unknown before expansion");
        for(String id:IDS){buffers.writeExpanded(id,ascii("abcdef"));check(Arrays.equals(buffers.read(id),ascii("abcdef")),"each owner accepts expanded prefix independently");}
        byte[] snapshot=wire(buffers);buffers.writeExpanded(IDS[0],ascii("A"));
        for(int n=1;n<4;n++)check(Arrays.equals(buffers.read(IDS[n]),ascii("abcdef")),"livebody write never overwrites another destination");
        check(buffers.fullBytes(IDS[0])[3]=='d'&&buffers.fullBytes(IDS[0])[5]=='f',"shorter prefix retains prior native tail beyond twoNUL");
        check(buffers.knownPrefixLength(IDS[2])==8,"redraw prefix includes both terminators");
        buffers.writeExpanded(IDS[2],ascii("x"));check(buffers.knownPrefixLength(IDS[2])==8&&buffers.unsignedByte(IDS[2],3)=='d',"shorter same-entry redraw preserves known stale suffix");
        byte[] oldSource=buffers.knownBytes(IDS[2]);String pendingOwner=IDS[2];int pendingOffset=3;
        buffers.writeExpanded(IDS[2],ascii("UVWXYZ"));check(oldSource[pendingOffset]=='d'&&buffers.unsignedByte(pendingOwner,pendingOffset)=='X',"stable pending owner+offset reads latest same-owner overwrite, not captured Source bytes");
        byte[] beforeEntry=buffers.fullBytes(IDS[2]);buffers.beginRedraw();
        check(Arrays.equals(beforeEntry,buffers.fullBytes(IDS[2]))&&buffers.knownBytes(IDS[2]).length==0,"new redraw entry invalidates knowledge without fabricating stack clearing");
        buffers.writeExpanded(IDS[2],ascii("q"));check(buffers.knownPrefixLength(IDS[2])==3,"new shorter redraw does not promote prior invocation tail");
        check(buffers.fullBytes(IDS[2])[3]=='X',"unknown stale bytes may remain in portable raw backing");
        rejects(()->buffers.unsignedByte(IDS[2],3),"unknown stale tail is inaccessible to native pointer reads");
        byte[] known=buffers.knownBytes(IDS[2]);known[0]=12;check(buffers.unsignedByte(IDS[2],0)=='q',"layout source gets an immutable copy of known prefix");
        for(String id:new String[]{IDS[1],IDS[3]}){
            buffers.writeExpanded(id,ascii("speaker"));int size=buffers.knownPrefixLength(id);byte[] prior=buffers.fullBytes(id);
            if(id.equals(IDS[1]))buffers.clearLiveSpeakerHead();else buffers.clearRedrawSpeakerHead();
            check(buffers.read(id).length==0&&buffers.fullBytes(id)[1]=='p',"native speaker clear writes only byte0, never secondNUL "+id);
            check(buffers.knownPrefixLength(id)==size,"head clear retains known suffix");
            prior[0]=0;check(Arrays.equals(prior,buffers.fullBytes(id)),"head clear leaves all remaining raw bytes unchanged");
        }
        check(Arrays.equals(wire(buffers),wire(restore(wire(buffers)))),"P1B1 accepts actual head-only clear even when following byte is nonzero");
        for(String id:IDS){
            byte[] expanded=ascii("detached");buffers.writeExpanded(id,expanded);expanded[0]=1;
            check(buffers.read(id)[0]=='d',"expanded caller array never aliases owner "+id);
            byte[] read=buffers.read(id);read[0]=2;check(buffers.read(id)[0]=='d',"logical read is detached");
            byte[] nativeBuffer={'N',0,0};buffers.writeNativeBuffer(id,nativeBuffer);nativeBuffer[0]=3;check(buffers.read(id)[0]=='N',"native doubleNUL input is copied");
            int capacity=Pc1TextBuffers.capacity(id);buffers.writeExpanded(id,repeat('a',capacity-2));
            check(buffers.read(id).length==capacity-2&&buffers.unsignedByte(id,capacity-1)==0,"maximum owned expanded prefix reserves both native zeros");
            byte[] before=wire(buffers);rejects(()->buffers.writeExpanded(id,repeat('a',capacity-1)),"expanded overflow fails before native memory corruption");check(Arrays.equals(before,wire(buffers)),"expanded overflow cannot partly replace backing");
            for(byte[] invalid:new byte[][]{null,new byte[0],new byte[1],{'x',0},{'x',0,1},{0,'x',0,0},{0,0,0},new byte[capacity+1]}){
                byte[] prior=wire(buffers);rejects(()->buffers.writeNativeBuffer(id,invalid),"invalid exact native doubleNUL representation rejected");check(Arrays.equals(prior,wire(buffers)),"invalid native buffer leaves owner unchanged");
            }
            rejects(()->buffers.writeExpanded(id,new byte[]{'x',0,'y'}),"expanded prefix cannot contain earlyNUL");rejects(()->buffers.writeExpanded(id,null),"missing expanded result is explicit");
            rejects(()->buffers.unsignedByte(id,-1),"negative pointer offset cannot alias previous owner");rejects(()->buffers.unsignedByte(id,capacity),"pointer cannot cross native destination end");
        }
        Pc1TextBuffers restored=restore(snapshot);Pc1TextBuffers copy=restored.copy();copy.writeExpanded(IDS[0],ascii("copy"));check(Arrays.equals(restored.read(IDS[0]),ascii("abcdef")),"copy detaches byte backing");
        copy.beginRedraw();check(restored.knownPrefixLength(IDS[2])==8&&copy.knownPrefixLength(IDS[2])==0,"copy detaches known-prefix metadata");
        nativeReference(IDS[2],ascii("ABCDEFGHIJKLMNOP"),ascii("Z"),"55dd6ad0dc2c2b447784dc03a764d8935befbc7477ad1ed33577d4167b2dc80b");
        nativeReference(IDS[3],ascii("speaker-long"),new byte[0],"bd1a6bbf3d53b95bf6105ee08f0cae3b44c023964dfbdc56b7b7b012dd3d5c46");
        nativeReference(IDS[2],repeat('A',1022),ascii("XY"),"3a91234e7e4d2f76416237d38a4d86e06ccc270798c6ba14d6bbba598ae902b8");
        nativeReference(IDS[3],repeat('B',254),ascii("Q"),"09d00409fba2832af26479b0cc8b9bc10a49ff067da023694fcc6fdd6b0f33fd");
        byte[] saved=wire(restored);check(saved.length==2580&&saved[0]=='P'&&saved[1]=='1'&&saved[2]=='B'&&saved[3]=='1',"P1B1 saves known-prefix lengths and all2560 backingbytes");
        check(Arrays.equals(saved,wire(restore(saved))),"all four independent memory owners roundtrip exactly");
        ByteArrayOutputStream outer=new ByteArrayOutputStream();outer.write(saved);new DataOutputStream(outer).writeInt(0x12345678);DataInputStream input=new DataInputStream(new ByteArrayInputStream(outer.toByteArray()));Pc1TextBuffers.load(input);check(input.readInt()==0x12345678,"P1B1 consumes no bytes from the following snapshot owner");
        Pc1TextBuffers unknown=patterned(IDS[2]);byte[] unknownSaved=wire(unknown);Pc1TextBuffers restoredUnknown=restore(unknownSaved);check(Arrays.equals(unknownSaved,wire(restoredUnknown))&&restoredUnknown.knownBytes(IDS[2]).length==0,"unknown raw suffix preserved without becoming a layout source after load");
        rejects(()->restoredUnknown.read(IDS[2]),"restoring unknown backing cannot invent a terminator");
        byte[] broken=saved.clone();broken[3]='0';rejects(()->restore(broken),"unknown codec version rejected");
        for(int owner=0;owner<4;owner++)for(int length:new int[]{-1,Pc1TextBuffers.capacity(IDS[owner])+1}){byte[] bad=saved.clone();intAt(bad,knownOffset(owner),length);rejects(()->restore(bad),"known-prefix length must remain inside fixed owner");}
        for(int owner=0;owner<2;owner++){byte[] bad=saved.clone();intAt(bad,knownOffset(owner),1);rejects(()->restore(bad),"PE live memory cannot become partly unknown on load");}
        for(int end:new int[]{0,3,4,7,100,1031,1032,1291,2579}){byte[] shortWire=Arrays.copyOf(saved,end);rejects(()->restore(shortWire),"truncated backing/known metadata rejected");}
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 text-buffer assertions");}
}
