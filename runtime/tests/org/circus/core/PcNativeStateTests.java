package org.circus.core;

import java.io.*;
import java.util.*;

/** PC2/SC/FL native getter/default/layout regression. Expected bytes come from
 * reviewed PE templates and native147-case getter/setter vectors, not runtime output. */
public final class PcNativeStateTests {
    private static int checks;
    private static final String[] IDS={"dc2pc-5769","dc2sc-3b69","dc2fl-9c69"};
    private static final int[][] SIZES={{100,1500,1500,100,1500},{100,1024,1024,80,1024},{100,640,1024,152,1024}};
    private static final int[] BANKS={100,97,98,95,90},OLD_SIZES={100,1600,1600,128,100};
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private interface Attempt {void run()throws Exception;}
    private static void fails(Attempt work,String fragment)throws Exception{
        try{work.run();throw new AssertionError("Expected native state failure: "+fragment);}catch(IOException expected){check(expected.getMessage().contains(fragment),expected.getMessage());}
    }
    private static void le32(ByteArrayOutputStream out,int value){for(int n=0;n<4;n++)out.write(value>>>(8*n));}
    private static byte[] names(){
        ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<4;n++)le32(out,0);le32(out,3);
        for(String name:new String[]{"START","A","B"}){byte[] row=new byte[188];byte[] raw=name.getBytes(Binary.SJIS);System.arraycopy(raw,0,row,0,raw.length);out.write(row,0,row.length);}return out.toByteArray();
    }
    private static CircusResources resources(String id)throws IOException{
        final byte[] names=names();
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
            public byte[] read(String path,int limit)throws IOException{if(names.length>limit)throw new IOException("Fixture read limit");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int count)throws IOException{Binary.range(at,count,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+count);}};}
        });return new CircusResources(files,Profiles.get(id));
    }
    private static PcHost host(CircusResources data){PcHost h=(PcHost)Dc2Host.create(data,TextCodec.identity());h.attach(new Variables(h,data.profile.variableModel));return h;}
    private static MesScript script(CircusResources data,String name)throws IOException{
        int version=data.profile.version;return new MesScript(name,new byte[]{0,0,0,0,(byte)version,(byte)(version>>>8),0,0,0},data.profile);
    }
    private static byte[] save(PcHost h,boolean persistent)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream stream=new DataOutputStream(out);if(persistent)h.savePersistent(stream);else h.save(stream);return out.toByteArray();}
    private static int marker(byte[] bytes)throws IOException{
        int found=-1;for(int n=0;n+4<=bytes.length;n++)if(bytes[n]==0x50&&bytes[n+1]==0x43&&bytes[n+2]==0x50&&bytes[n+3]==0x33){if(found>=0)throw new IOException("Ambiguous PCP3 fixture marker");found=n;}
        if(found<0)throw new IOException("Missing PCP3 fixture marker");return found;
    }
    private static final class Wire {
        byte[][] banks=new byte[5][],tails=new byte[5][];int flags,random;String family,given;
        Wire(byte[] bytes)throws IOException{
            int start=marker(bytes);DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,start,bytes.length-start));check(in.readInt()==0x50435033,"wire PCP3 tag");
            for(int n=0;n<5;n++){banks[n]=new byte[in.readInt()];in.readFully(banks[n]);}
            for(int n=0;n<5;n++){tails[n]=new byte[in.readInt()];in.readFully(tails[n]);}
            flags=in.readInt();random=in.readInt();family=in.readUTF();given=in.readUTF();check(in.available()==0,"PCP3 consumes exact persistent suffix");
        }
    }
    private static int oldByte(int bank,int index,int seed){return (bank*37+index*13+seed)&255;}
    private static byte[] legacy(byte[] current,int version,int seed)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(current,0,marker(current));DataOutputStream stream=new DataOutputStream(out);stream.writeInt(version);
        for(int bank=0;bank<5;bank++)for(int at=0;at<OLD_SIZES[bank];at++)stream.writeByte(oldByte(bank,at,seed));
        stream.writeInt(0x13579bdf);stream.writeInt(0xf0e1d2c3);if(version==0x50435032){stream.writeUTF("Fam");stream.writeUTF("Name");}return out.toByteArray();
    }
    private static void defaults(CircusResources data,int family)throws Exception{
        PcHost h=host(data);check(h.pc1==null&&h.second,"family owns no PC1 memory");
        int[] values={1,1,25,25,29,1,8,1,0,1,1,8,family==1?1:0,1};
        for(int n=0;n<values.length;n++)check(h.read(99,5+n)==values[n],data.profile.id+" native99:"+(5+n));
        check(h.read(99,20)==1,"native effect default20");
        for(int n=0;n<SIZES[family][3];n++)check(h.read(95,n)==0,"native95 default byte"+n);
        check(h.ui.palette[11]==0xffff7f7f,"PC palette index11 GRB template");
        h.dc2LocalHour=()->0;check(h.read(99,21)==0,"native physical midnight");h.dc2LocalHour=()->23;check(h.read(99,21)==23,"native physical late hour");
        for(int n=0;n<BANKS.length;n++){
            final int bank=BANKS[n],size=SIZES[family][n];
            check(h.read(bank,size-1)==0,"known native span final byte "+bank);fails(()->h.read(bank,size),"byte-bank span");fails(()->h.write(bank,size,1),"byte-bank span");
            h.write(bank,size-1,-1);check(h.read(bank,size-1)==255,"native special low8 "+bank);h.write(bank,size-1,256);check(h.read(bank,size-1)==0,"native special low8 wrap "+bank);
        }
        Wire wire=new Wire(save(h,true));for(int n=0;n<5;n++){check(wire.banks[n].length==SIZES[family][n],"PCP3 native bank length "+BANKS[n]);check(wire.tails[n].length==0,"new PCP3 has no legacy tail "+BANKS[n]);}
    }
    private static void cache(CircusResources data)throws Exception{
        PcHost h=host(data);h.write(98,0,7);h.loadedInitial(script(data,"START"),6);check(h.read(99,251)==7,"initial load samples seen value rather than catalog0");
        h.write(98,0,255);check(h.read(99,251)==7,"cached251 ignores later seen writes");
        h.write(98,1,19);h.write(98,2,77);h.beforeTransfer(1,"A");check(h.read(99,251)==19&&h.read(98,1)==19&&h.read(90,1)==0,"mode1 samples old byte before any body mark");
        h.restored(script(data,"A"),6);h.write(98,1,0);h.command("text.body",new int[0],"body".getBytes(Binary.SJIS));
        check(h.read(98,1)==1&&h.read(90,1)==1&&h.read(99,251)==19,"body marks98 and90 together without changing cache");
        h.beforeTransfer(2,"B");h.restored(script(data,"B"),6);check(h.read(99,251)==19&&h.read(98,2)==77&&h.read(90,2)==0,"mode2 retains cache and does not mark target");
        h.beforeTransfer(3,"B");check(h.read(99,251)==77&&h.read(98,2)==77&&h.read(90,2)==0,"mode3 samples unsigned prior seen byte");
        byte[] snapshot=save(h,false);PcHost restored=host(data);restored.load(new DataInputStream(new ByteArrayInputStream(snapshot)));restored.restored(script(data,"B"),6);
        check(restored.read(99,251)==77&&Arrays.equals(snapshot,save(restored,false)),"whole native host snapshot retains cached byte");
        h.command("text.body",new int[0],"again".getBytes(Binary.SJIS));check(h.read(98,2)==1&&h.read(90,2)==1&&h.read(99,251)==77,"body does not confuse arbitrary native seen value with its new mark");
        h.beforeTransfer(1,"missing");check(h.read(99,251)==0,"unknown native scene caches0");
        h.write(98,2,255);h.beforeTransfer(3,"B");check(h.read(99,251)==255,"cached native byte remains unsigned255");
    }
    private static void variables(CircusResources data,boolean wide)throws Exception{
        PcHost h=host(data);Variables v=h.variables;check(data.profile.variableModel==(wide?Variables.Model.PC_WIDE:Variables.Model.PC_BYTE),"native model identity");
        v.write(0,0,-1);v.write(9,99,300);check(v.read(0,0)==0&&v.read(9,99)==255,"ordinary native signed saturation");
        v.write(100,0,170);v.write(10,0,12);check(v.read(100,0)==170&&v.read(10,0)==0,"no ordinary10 persistent alias");
        v.write(206,96,0x89abcdef);
        check(v.read(206,96)==(wide?0x89abcdef:0),"native wide signed BE32 value or absent SC view");
        check(v.read(6,96)==(wide?0x89:0)&&v.read(6,97)==(wide?0xab:0)&&v.read(6,98)==(wide?0xcd:0)&&v.read(6,99)==(wide?0xef:0),"native BE32 writes exactly four ordinary bytes");
        v.write(206,97,0x11223344);check(v.read(206,97)==0&&v.read(6,99)==(wide?0xef:0),"wide index97 does not cross ordinary bank boundary");
        v.write(306,0,0x1234);v.write(400,0,0x1234);check(v.read(306,0)==0&&v.read(400,0)==0&&v.read(100,0)==170,"no BE16 or persistent word view");
        ByteArrayOutputStream raw=new ByteArrayOutputStream();v.save(new DataOutputStream(raw));Variables restored=new Variables(h,data.profile.variableModel);restored.load(new DataInputStream(new ByteArrayInputStream(raw.toByteArray())));
        check(restored.read(9,99)==255&&restored.read(206,96)==(wide?0x89abcdef:0),"ordinary snapshot retains family-specific view");
    }
    private static void wires(CircusResources data,int family)throws Exception{
        PcHost h=host(data);for(int n=0;n<5;n++){h.write(BANKS[n],0,31+n);h.write(BANKS[n],SIZES[family][n]-1,211+n);}
        byte[] original=save(h,true);PcHost restored=host(data);restored.loadPersistent(new DataInputStream(new ByteArrayInputStream(original)));check(Arrays.equals(original,save(restored,true)),"PCP3 public persistent roundtrip");
        for(int n=0;n<5;n++)check(restored.read(BANKS[n],0)==31+n&&restored.read(BANKS[n],SIZES[family][n]-1)==211+n,"PCP3 preserves both boundaries "+BANKS[n]);
        for(int version:new int[]{0x50435031,0x50435032}){
            PcHost old=host(data);String familyName=old.familyName,givenName=old.givenName;byte[] bytes=legacy(original,version,family+3);
            old.loadPersistent(new DataInputStream(new ByteArrayInputStream(bytes)));Wire migrated=new Wire(save(old,true));
            check(migrated.flags==0x13579bdf&&migrated.random==0xf0e1d2c3,"legacy flags and CRT state survive migration");
            check(migrated.family.equals(version==0x50435032?"Fam":familyName)&&migrated.given.equals(version==0x50435032?"Name":givenName),"legacy name presence follows PCP version");
            for(int n=0;n<5;n++){
                check(migrated.banks[n].length==SIZES[family][n],"migrated native active bank extent");
                for(int at=0;at<migrated.banks[n].length;at++)check((migrated.banks[n][at]&255)==(at<OLD_SIZES[n]?oldByte(n,at,family+3):0),"legacy bank copy or zero extension "+n+":"+at);
                int tail=Math.max(0,OLD_SIZES[n]-SIZES[family][n]);check(migrated.tails[n].length==tail,"legacy tail extent "+n);
                for(int at=0;at<tail;at++)check((migrated.tails[n][at]&255)==oldByte(n,SIZES[family][n]+at,family+3),"legacy excess byte retained "+n+":"+at);
            }
            byte[] migratedBytes=save(old,true);PcHost second=host(data);second.loadPersistent(new DataInputStream(new ByteArrayInputStream(migratedBytes)));check(Arrays.equals(migratedBytes,save(second,true)),"legacy tails survive subsequent PCP3 roundtrip");
            PcHost copied=host(data);copied.copyPersistentFrom(old);Wire copiedWire=new Wire(save(copied,true));for(int n=0;n<4;n++)check(Arrays.equals(migrated.banks[n],copiedWire.banks[n])&&Arrays.equals(migrated.tails[n],copiedWire.tails[n]),"persistent copy retains bank and opaque tail "+n);
            check(Arrays.equals(migrated.tails[4],copiedWire.tails[4]),"persistent copy retains legacy scratch tail without replacing live scratch");
        }
        byte[] bad=original.clone();int start=marker(bad);bad[start+7]++;fails(()->host(data).loadPersistent(new DataInputStream(new ByteArrayInputStream(bad))),"layout mismatch");
    }
    public static int run()throws Exception{
        checks=0;for(int n=0;n<IDS.length;n++){CircusResources data=resources(IDS[n]);defaults(data,n);cache(data);variables(data,n!=1);wires(data,n);}return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PcNativeStateTests "+run()+" PASS");}
}
