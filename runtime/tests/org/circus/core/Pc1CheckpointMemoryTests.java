package org.circus.core;
import java.io.*;
import java.util.*;

/** Raw411670 count/table ownership, including stale tails and known adjacent
 *aliasing. The byte-read prefix is explicit, not successful file IO. */
public final class Pc1CheckpointMemoryTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] dword(int value){return new byte[]{(byte)value,(byte)(value>>>8),(byte)(value>>>16),(byte)(value>>>24)};}
    public static int run()throws Exception{
        checks=0;Pc1NativeFrame frame=Pc1NativeFrame.zeroBss();Pc1NativeMemory bus=new Pc1NativeMemory(frame,Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){public int read(int p){throw new AssertionError("Unexpected settings alias");}public void write(int p,int v){throw new AssertionError("Unexpected settings alias");}});Pc1CheckpointMemory table=new Pc1CheckpointMemory(bus);Pc1LayoutMemory layout=new Pc1LayoutMemory(bus);
        check(table.count()==0&&Arrays.equals(table.tableBytes(),new byte[4000]),"PE BSS initial count/table are known zero");
        table.applyCountRead(dword(0x12345678));table.applyCountRead(new byte[]{9,8});check(table.count()==0x12340809,"Short count read preserves high stale bytes");table.applyCountRead(new byte[0]);check(table.count()==0x12340809,"Failed/empty count read does not zero-fill");
        table.applyCountRead(dword(0x40000001));check(table.requestedTableBytes()==4,"Native count*4 DWORD overflow is preserved");table.applyCountRead(dword(-1));check(table.requestedTableBytes()==-4,"Raw negative requested count is left toCRT owner");
        byte[] initial=new byte[4000];for(int n=0;n<initial.length;n++)initial[n]=(byte)(n*37+11);table.applyTableRead(initial);table.applyTableRead(new byte[]{1,2,3});byte[] expected=initial.clone();System.arraycopy(new byte[]{1,2,3},0,expected,0,3);check(Arrays.equals(expected,table.tableBytes()),"Short table read preserves every inactive/stale byte");table.applyCountRead(dword(0));check(table.offsetForLogWord(0x8000)==0x7a030201,"Consumer masks7FFF and reads even with loaded count0");
        layout.writeI32(Pc1InlineText.RUBY_GAP,0x12345678);check(table.offsetForLogWord(1000)==0x12345678,"Checkpoint1000 aliases next known ruby-gap owner, not false table bounds");
        byte[] over=new byte[4004];Arrays.fill(over,(byte)0x5a);table.applyTableRead(over);check(layout.readI32(Pc1InlineText.RUBY_GAP)==0x5a5a5a5a,"Oversized actual read prefix writes adjacent known layout bytes");boolean failed=false;try{table.applyTableRead(new byte[4005]);}catch(IOException missing){failed=missing.getMessage().contains("unowned");}check(failed&&table.offsetForLogWord(0)==0&&layout.readI32(Pc1InlineText.RUBY_GAP)==0,"Unknown neighbor fault preserves native forward-write prefix");
        byte[] file={1,0,0,0,2,0,0,0,0x63,0x20,0,0,0};MesScript source=new MesScript("SELF",file,Profiles.get("dcpc-2063"));source.headerOffsets[0]=12345;table.applyTableRead(initial);table.loadSource(source);check(table.count()==1&&table.offsetForLogWord(0)==2,"Successful source reads original header bytes rather than mutableheaderOffsets DTO");check(table.tableBytes()[4]==initial[4]&&table.tableBytes()[3999]==initial[3999],"Source load never clears stale inactive rows");
        ByteArrayOutputStream out=new ByteArrayOutputStream();table.save(new DataOutputStream(out));byte[] encoded=out.toByteArray();table.applyCountRead(dword(99));table.applyTableRead(new byte[]{7});table.restore(Pc1CheckpointMemory.loadImage(new DataInputStream(new ByteArrayInputStream(encoded))));ByteArrayOutputStream again=new ByteArrayOutputStream();table.save(new DataOutputStream(again));check(Arrays.equals(encoded,again.toByteArray()),"4004 raw owned bytes codec exact roundtrip");
        failed=false;try{Pc1CheckpointMemory.loadImage(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(encoded,encoded.length-1))));}catch(EOFException truncated){failed=true;}check(failed,"Truncated raw image cannot silently zero-fill");
        return checks;
    }
}
