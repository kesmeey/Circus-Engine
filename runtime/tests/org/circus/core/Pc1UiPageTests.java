package org.circus.core;

import java.io.*;

/**Complete current PE page-copy bodies against actual private-instruction goldens.*/
public final class Pc1UiPageTests {
    private static final int[][] RANGES={{0x4305f4,24},{0x94f8dc,0x95af28-0x94f8dc},{0x9689e4,40}};
    private static final class Bytes implements Pc1InlineText.Memory {
        final byte[][] raw=new byte[RANGES.length][];
        Bytes(int seed){for(int n=0;n<raw.length;n++){raw[n]=new byte[RANGES[n][1]];for(int b=0;b<raw[n].length;b++){int address=RANGES[n][0]+b;raw[n][b]=(byte)(seed+address*37+(address>>>8)*11);}}}
        public int read(int address)throws IOException{for(int n=0;n<raw.length;n++)if(address>=RANGES[n][0]&&address-RANGES[n][0]<raw[n].length)return raw[n][address-RANGES[n][0]]&255;throw new IOException("unowned page-test byte"+Integer.toHexString(address));}
        public void write(int address,int value)throws IOException{for(int n=0;n<raw.length;n++)if(address>=RANGES[n][0]&&address-RANGES[n][0]<raw[n].length){raw[n][address-RANGES[n][0]]=(byte)value;return;}throw new IOException("unowned page-test byte"+Integer.toHexString(address));}
        String digest()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] bytes:raw)out.write(bytes);return Binary.sha256(out.toByteArray());}
    }
    public static int run()throws Exception{int checks=0;for(String row:GOLDENS.split("\n")){String[] parts=row.split("\\|");Bytes memory=new Bytes(Integer.parseInt(parts[0]));for(String operation:parts[1].split(",")){if(operation.equals("C"))Pc1UiPage.capture(memory);else Pc1UiPage.restore(memory);}if(!memory.digest().equals(parts[2]))throw new AssertionError("actual page-copy bytes "+row);checks++;}return checks;}
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" actual native page-copy programs");}
    private static final String GOLDENS=
        "0|C|8df46d47342d557e633a94728544990122044170ebc8d039724fdc2690ea7ee9\n"+
        "0|R|64d4bb09c854d8b4e47340ea7b93d199db00520ed28268711e05568f9320cdbd\n"+
        "0|C,R|8df46d47342d557e633a94728544990122044170ebc8d039724fdc2690ea7ee9\n"+
        "0|R,C|64d4bb09c854d8b4e47340ea7b93d199db00520ed28268711e05568f9320cdbd\n"+
        "0|C,C|8df46d47342d557e633a94728544990122044170ebc8d039724fdc2690ea7ee9\n"+
        "0|R,R|64d4bb09c854d8b4e47340ea7b93d199db00520ed28268711e05568f9320cdbd\n"+
        "1|C|982ea4692cf1db2d6b68b460b95bda76bf29e993f019dc08c365dffb571833fe\n"+
        "1|R|b708e0c5b664090e4a0668c59419a42665076ff971b150012d6bedb234dab4b4\n"+
        "1|C,R|982ea4692cf1db2d6b68b460b95bda76bf29e993f019dc08c365dffb571833fe\n"+
        "1|R,C|b708e0c5b664090e4a0668c59419a42665076ff971b150012d6bedb234dab4b4\n"+
        "1|C,C|982ea4692cf1db2d6b68b460b95bda76bf29e993f019dc08c365dffb571833fe\n"+
        "1|R,R|b708e0c5b664090e4a0668c59419a42665076ff971b150012d6bedb234dab4b4\n"+
        "17|C|c27d673ce26550c474ec1434a4273f50bb78b3f63bd8357cf99b5525d9973f16\n"+
        "17|R|213073484b6c24dfa9532aa8139bddcc1f5bdaf2ea748849b4d4ba7e2ee229b3\n"+
        "17|C,R|c27d673ce26550c474ec1434a4273f50bb78b3f63bd8357cf99b5525d9973f16\n"+
        "17|R,C|213073484b6c24dfa9532aa8139bddcc1f5bdaf2ea748849b4d4ba7e2ee229b3\n"+
        "17|C,C|c27d673ce26550c474ec1434a4273f50bb78b3f63bd8357cf99b5525d9973f16\n"+
        "17|R,R|213073484b6c24dfa9532aa8139bddcc1f5bdaf2ea748849b4d4ba7e2ee229b3\n"+
        "127|C|4f8a8b49c2290e64402fd4f6aece5599447156a6643c5d859cb5523efda0f1cf\n"+
        "127|R|abcd2aa255e5f04823aac42df62949cd2b887de7d03541a0877328117932dfe9\n"+
        "127|C,R|4f8a8b49c2290e64402fd4f6aece5599447156a6643c5d859cb5523efda0f1cf\n"+
        "127|R,C|abcd2aa255e5f04823aac42df62949cd2b887de7d03541a0877328117932dfe9\n"+
        "127|C,C|4f8a8b49c2290e64402fd4f6aece5599447156a6643c5d859cb5523efda0f1cf\n"+
        "127|R,R|abcd2aa255e5f04823aac42df62949cd2b887de7d03541a0877328117932dfe9\n"+
        "128|C|929485944a940b231a203ab9ace57b032ef0a25d095bbec3d8819c6c96bab871\n"+
        "128|R|f693f1a9d51991d88e551531bf7f2151564d4090e88a4cd9ffa1ff5319cd4a71\n"+
        "128|C,R|929485944a940b231a203ab9ace57b032ef0a25d095bbec3d8819c6c96bab871\n"+
        "128|R,C|f693f1a9d51991d88e551531bf7f2151564d4090e88a4cd9ffa1ff5319cd4a71\n"+
        "128|C,C|929485944a940b231a203ab9ace57b032ef0a25d095bbec3d8819c6c96bab871\n"+
        "128|R,R|f693f1a9d51991d88e551531bf7f2151564d4090e88a4cd9ffa1ff5319cd4a71\n"+
        "255|C|b9eacd013e78569d1899c901dc2efe6d69e84f11754c4b6ea58f06f4ad8c6dc6\n"+
        "255|R|05af040665f6fff93dd76b4f13c18ca3a867814dd3395d74056657cfb09aeca6\n"+
        "255|C,R|b9eacd013e78569d1899c901dc2efe6d69e84f11754c4b6ea58f06f4ad8c6dc6\n"+
        "255|R,C|05af040665f6fff93dd76b4f13c18ca3a867814dd3395d74056657cfb09aeca6\n"+
        "255|C,C|b9eacd013e78569d1899c901dc2efe6d69e84f11754c4b6ea58f06f4ad8c6dc6\n"+
        "255|R,R|05af040665f6fff93dd76b4f13c18ca3a867814dd3395d74056657cfb09aeca6";
}
