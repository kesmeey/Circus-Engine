package org.circus.core;

import java.io.*;
import java.security.*;
import java.util.*;

/** Complete native getter/setter machine goldens across the three explicitly
 *owned regions. Gaps and adjacent globals are deliberately not invented. */
public final class Pc1NativeMemoryTests {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static byte[] pattern(int size,int seed){byte[] bytes=new byte[size];for(int n=0;n<size;n++)bytes[n]=(byte)(n*73+(n>>3)+seed);return bytes;}
    private static String sha(byte[] bytes)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    // BEGIN ACTUAL MACHINE VECTORS
    private static final String[] VECTORS={
        "-2321|44|300|61|255|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|d1b22d1bca31933d40c7a1498a91def2c15b0cfb7238190b021b783e49b536e0|b3ca8b32c32d2b9eb65522454fd4ed4090d51b75a7c86466ef90bcc318dda884",
        "-2321|45|-1|134|0|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|2cff6fbbd4e259204d4420572fa745ff5226527915c740c7d8a4b1fae6722707|b3ca8b32c32d2b9eb65522454fd4ed4090d51b75a7c86466ef90bcc318dda884",
        "-2321|86|73|60|73|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|63302a0bc63e78b1970d725153f1cce73f04a74606fb2751e626c3055e183cc0|b3ca8b32c32d2b9eb65522454fd4ed4090d51b75a7c86466ef90bcc318dda884",
        "-1321|43|128|103|128|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|034ac224285aa51f55c64acaf82c4d3f065ebe88c354edad471c95bb5540bdc9|b3ca8b32c32d2b9eb65522454fd4ed4090d51b75a7c86466ef90bcc318dda884",
        "-57849|40|-1|29|0|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|230a17ee0709a28779bb12f300fcbe8b916e0636e4e7fbf44f30253ca24035f7|246b6f866f6f1c77159a264b250144906882baf50c13af2f1bf71f9ad4e7b91b",
        "-57848|52|300|27|255|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|230a17ee0709a28779bb12f300fcbe8b916e0636e4e7fbf44f30253ca24035f7|84beca7a781b07d5f2e7a53ee50e20feedf41e4628cbb86d5b4d96bd964f864f",
        "-57832|87|19|34|19|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|230a17ee0709a28779bb12f300fcbe8b916e0636e4e7fbf44f30253ca24035f7|4795d178d14660bf92eec90b791bdd411291899299fd4b93b4c67427e28adc0d",
        "-1|99|333|33|255|e02ae50724303a13d860a665b3d37771c967d24b5e2b1f3613b1e7fe87148e58|230a17ee0709a28779bb12f300fcbe8b916e0636e4e7fbf44f30253ca24035f7|b3ca8b32c32d2b9eb65522454fd4ed4090d51b75a7c86466ef90bcc318dda884"
    };
    // END ACTUAL MACHINE VECTORS
    public static int run()throws Exception{
        checks=0;check(VECTORS.length==8,"All native known-region alias vectors present");
        for(String vector:VECTORS){String[] f=vector.split("\\|",-1);int bank=Integer.parseInt(f[0]),index=Integer.parseInt(f[1]),value=Integer.parseInt(f[2]);
            Pc1NativeFrame frame=Pc1NativeFrame.fromBytes(pattern(8004,17));Pc1CodeBuffer code=Pc1CodeBuffer.zeroBss();code.writeBytes(0,pattern(100000,61));final byte[] settings=pattern(1748,29);final int[] stores={0};
            Pc1NativeMemory memory=new Pc1NativeMemory(frame,code,new Pc1NativeMemory.SettingsBytes(){public int read(int offset){return settings[offset]&255;}public void write(int offset,int value){stores[0]++;settings[offset]=(byte)value;}});
            check(memory.readNativeOrdinary(bank,index)==Integer.parseInt(f[3]),"Actual native getter before write "+bank+":"+index);
            memory.writeNativeOrdinary(bank,index,value);
            check(memory.readNativeOrdinary(bank,index)==Integer.parseInt(f[4]),"Actual native getter after saturating write "+bank+":"+index);
            check(sha(frame.bytes()).equals(f[5]),"Full live frame native hash "+bank+":"+index);
            check(sha(code.bytes()).equals(f[6]),"Full100000-byte code owner native hash "+bank+":"+index);
            check(sha(settings).equals(f[7]),"Full1748-byte settings owner native hash "+bank+":"+index);
            int address=Pc1FrameLayout.LIVE_BASE+Pc1FrameLayout.ORDINARY_OFFSET+bank*100+index;
            memory.writeBank90(address-(Pc1FrameLayout.LIVE_BASE+Pc1FrameLayout.BANK90_OFFSET),511);
            check(memory.readAddress(address)==255,"Bank90 raw low8 reaches the same known owner");
            check(stores[0]==(address>=Pc1NativeMemory.SETTINGS_BASE&&address<Pc1NativeMemory.SETTINGS_BASE+1748?2:0),"Raw ordinary/bank90 aliases do not synthesize bank100 persistence effects");
        }
        Pc1NativeFrame frame=Pc1NativeFrame.zeroBss();Pc1NativeMemory memory=new Pc1NativeMemory(frame,Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){public int read(int offset){throw new AssertionError("Unexpected settings read");}public void write(int offset,int value){throw new AssertionError("Unexpected settings write");}});
        check(memory.readNativeOrdinary(10,0)==0&&memory.readNativeOrdinary(0,100)==0,"Native upper comparisons return zero before addressing");
        memory.writeNativeOrdinary(-1,100,12);check(Arrays.equals(frame.bytes(),new byte[8004]),"Failed native upper check does not write an alias");
        try{memory.readAddress(Pc1NativeMemory.CODE_BASE+100000);throw new AssertionError("Invented adjacent global");}catch(IOException expected){check(expected.getMessage().contains("unowned"),"Code end does not invent the next global");}
        try{memory.writeBank90(-6369,1);throw new AssertionError("Invented frame gap");}catch(IOException expected){check(expected.getMessage().contains("unowned"),"Unknown memory between real owners remains strict");}
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS Pc1NativeMemoryTests "+run());}
}
