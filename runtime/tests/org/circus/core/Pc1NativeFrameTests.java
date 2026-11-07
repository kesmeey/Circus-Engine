package org.circus.core;

import java.io.*;
import java.util.*;

/** Canonical byte aliases, unknown preservation, and actual native getter/
 *writer/constructor private-instruction goldens. Does not attach a running VM. */
public final class Pc1NativeFrameTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private interface Work {void run()throws Exception;}
    private static void reject(Work work,String text)throws Exception{boolean failed=false;try{work.run();}catch(IOException error){failed=text==null||String.valueOf(error.getMessage()).contains(text);}check(failed,"Reject "+text);}
    private static byte[] pattern(){byte[] raw=new byte[8004];for(int n=0;n<raw.length;n++)raw[n]=(byte)(n*73+(n>>3)+17);return raw;}
    private static byte[] saved(Pc1NativeFrame frame)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();frame.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    // Each row: bank/index/value/readBefore/readAfter/full-frame SHA256.
    // Expected values are literal output from complete current411910/411B10
    // instruction interpretation on the private patterned8004-byte array.
    private static final String[] NATIVE={
        "90,-6368,291,17,35,2b6567449263d292c58387c9769161b0b388c83c5726afd7d1d964ffac25ad59",
        "90,-6367,-1,90,255,d0ef4a803587cf0fabd1265c45ec830a20dccf2aaa4fa74a827d21bc6aba5a08",
        "90,-3188,256,106,0,8b5cab6ca4e644d04dd45d655f758f9890a6e86f3f1a2a9051cd68f40a402aa9",
        "90,-1,2309737967,195,239,652e6ed7a85765902f2c79597329faa32c216182a5ffb5202501db6c794cccce",
        "90,0,0,13,0,65869a2190a358b9c140d037a148603dd4c75fcc02c3e50e3ad99c9856b1ad27",
        "90,99,999,84,231,e99429debe8f1ae45597c94940cc510331c8e088a9c42530f2ce6a897e739263",
        "90,100,-999,157,25,5eae4c4accddc300f14a3a151dff32d58c92fcc6daffe4d6d6cba75ce70349f9",
        "90,1023,1,67,1,78e7ab74d96be0f933a6b61b6c04ae06e603abc6d3c00f3d28903a965439d68d",
        "90,1024,65,141,65,8960dbf3ac279fc417c7230cc84da9b416f1eb986cd909e2a8cc64e456f6e421",
        "90,1599,0,203,0,52e96182f068cd1b583e8304c9c530262b244483e970d232e34425977c5241c3",
        "90,1600,291,21,35,73c4fad5c9c6fb592fa75972baa00957bae24fdbe7ceced527585039152f06fe",
        "90,1603,255,240,255,6129a169f9b770c173912172d61ab4259321e83e7f5a982a661ec8a204ed207e",
        "90,1604,2,57,2,33926e0d3d9c62e1997fd0915e9de9b98344d53793b3777ace2fa0015fe939d3",
        "90,1608,-1,94,255,499d9358e816420e28d799788a69ca866a424084892bcd61de569bd9529cc013",
        "90,1624,52,240,52,26185e798e07acce25f474c185ef7b7eaaaa08c60c752892cf9f6b7ae100069e",
        "90,1628,18,20,18,3922da12e3827e3f2533ceb08499c202be8eedb884db30a0796d90fcd1624e6a",
        "90,1631,1110,239,86,6cdcc34832f3c27d8012448cbd1ac899e49c85c35c05a429da74e3711ea4f2ba",
        "90,1635,-256,20,0,5d6bd3efc3d9993483409817edbab2b8679415ab9fcff2eed96441edffe4bbf4",
        "0,0,-1,106,0,8b5cab6ca4e644d04dd45d655f758f9890a6e86f3f1a2a9051cd68f40a402aa9",
        "0,99,256,177,255,fc712b870b2fc8afeb975eaa6dc9676c4bed77e735a8d2cb5cb3cebe06fc71f0",
        "9,99,2147483647,198,255,7586596481c8d34f3ef0b6af94a005c298ddbf7971e21830eaa27238eb44f74b",
        "0,-1,333,33,255,e02ae50724303a13d860a665b3d37771c967d24b5e2b1f3613b1e7fe87148e58",
        "-1,0,-2147483648,218,0,50d213a1b60f2540bcce721f0ebeb650d9b713dd503b395602889091fde1401c",
        "-31,-60,42,199,42,2e11f030455aec44f1be52f23bfc518485b3eb16cda849eeca02630f4b368aad",
        "-1073741824,0,234,106,234,4c422d695bed042d4d1def42f893d72c5775df9c8bbef2e93f9c361a838f8f32",
        "0,100,7,0,0,24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768",
        "10,0,8,0,0,24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768",
        "-1,100,9,0,0,24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768",
        "9,99,128,198,128,20e2e3b5210e3124cd6e42c00914bc526693ea54ee85d48e6dac661cd1522599"
    };
    public static int run()throws Exception{
        count=0;
        Pc1NativeFrame empty=Pc1NativeFrame.zeroBss(),init=Pc1NativeFrame.initializedDefault();
        check(Arrays.equals(empty.bytes(),new byte[8004]),"zeroBss is a complete PE virtual-zero image, not native ready defaults");
        check(Binary.sha256(init.bytes()).equals("c46064fd2c932209007377e4c9caf1f44ea5bfff3eb738e5e253d6a408cc32e0"),"complete411480 constructor image matches native instruction reference");
        check(init.readI32(0)==1&&init.readI32(0x484)==-1&&init.readI32(0x498)==0&&init.readI32(0x1f28)==0,"native ctor does not invent loader wait/head=-1");
        for(String row:NATIVE){String[] cells=row.split(",");int bank=Integer.parseInt(cells[0]),index=Integer.parseInt(cells[1]),value=(int)Long.parseLong(cells[2]);Pc1NativeFrame frame=Pc1NativeFrame.fromBytes(pattern());
            check((bank==90?frame.readBank90(index):frame.readNativeOrdinary(bank,index))==Integer.parseInt(cells[3]),"native full getter golden before write "+bank+":"+index);
            if(bank==90)frame.writeBank90(index,value);else frame.writeNativeOrdinary(bank,index,value);
            check((bank==90?frame.readBank90(index):frame.readNativeOrdinary(bank,index))==Integer.parseInt(cells[4]),"native full getter golden after writer "+bank+":"+index);
            check(Binary.sha256(frame.bytes()).equals(cells[5]),"native writer changes exactly the machine-reference bytes "+bank+":"+index);
        }
        byte[] raw=pattern();Pc1NativeFrame frame=Pc1NativeFrame.fromBytes(raw);raw[0]^=1;check(frame.readU8(0)==17,"fromBytes clones its input");
        for(int n=0;n<8004;n++)check(frame.readBank90(n-6368)==(patternValue(n)&255),"bank90 signed address aliases every owned frame byte");
        for(int n=0;n<8004;n++)frame.writeBank90(n-6368,n*19+256);byte[] expected=new byte[8004];for(int n=0;n<8004;n++)expected[n]=(byte)(n*19+256);
        check(Arrays.equals(frame.bytes(),expected),"bank90 writes wrap directly into the single complete canonical backing");
        frame.writeI32(Pc1FrameLayout.CACHED_SCENARIO_READ,0x12345678);check(frame.readBank90(1600)==0x78&&frame.readBank90(1603)==0x12,"cache DWORD bytes are the same bank90 aliases");
        frame.writeBank90(1603,0xff);check(frame.readI32(Pc1FrameLayout.CACHED_SCENARIO_READ)==0xff345678,"bank90 can create raw cache values outside the old0..255 typed restriction");
        frame.writeI32(Pc1FrameLayout.ARGUMENT1,0x12345678);frame.writeBank90(1631,0x89);check(frame.readI32(Pc1FrameLayout.ARGUMENT1)==0x89345678,"frame C4 is a live bank90 DWORD alias");
        frame.writeU16(8002,0x123456);check(frame.readU16(8002)==0x3456,"unaligned/boundary raw uint16 stores retain low16 in little endian");
        frame.writeI32(1,0x89abcdef);check(frame.readI32(1)==0x89abcdef&&frame.readU8(1)==0xef&&frame.readU16(2)==0xabcd,"raw integer views impose no fake alignment rule");
        for(int n=0;n<1000;n++){frame.writeOrdinary(n/100,n%100,n-256);check(frame.readOrdinary(n/100,n%100)==Math.max(0,Math.min(255,n-256)),"normal ordinary writes saturate independently of bank90 truncation");}
        frame=Pc1NativeFrame.fromBytes(pattern());byte[] before=frame.bytes();frame.clearOrdinaryBytes();for(int n=0;n<8004;n++)check(frame.readU8(n)==(n>=0xc6c&&n<0x1054?0:before[n]&255),"ordinary memset preserves raw log and every other frame byte");
        for(int n=0;n<1000;n++){frame.rawLogWord(n,0x8000|n);check(frame.rawLogWord(n)==(0x8000|n),"all inactive/raw log words retain paragraph high bit");}
        for(int n=0;n<60;n++)for(int d=0;d<8;d++){frame.pendingDword(n,d,Integer.MIN_VALUE+n*8+d);check(frame.pendingDword(n,d)==Integer.MIN_VALUE+n*8+d,"pending raw DWORDs retain all bits and inactive entries");}
        frame.writeI32(Pc1FrameLayout.LOG_COUNT,-1);frame.writeI32(Pc1FrameLayout.PENDING_COUNT,Integer.MAX_VALUE);check(frame.readI32(Pc1FrameLayout.LOG_COUNT)==-1&&frame.readI32(Pc1FrameLayout.PENDING_COUNT)==Integer.MAX_VALUE,"raw owner does not reject unknown count bits at image admission");
        Pc1NativeFrame copy=frame.copy();copy.writeU8(777,99);check(frame.readU8(777)!=99,"copy makes a distinct frame owner");copy.copyFrom(frame);check(Arrays.equals(copy.bytes(),frame.bytes()),"full memcpy preserves every inactive/unknown byte");byte[] exported=frame.bytes();exported[778]^=1;check(frame.readU8(778)!=(exported[778]&255),"bytes getter does not expose mutable backing");
        byte[] wire=saved(frame);check(wire.length==8008&&Pc1NativeFrame.load(new DataInputStream(new ByteArrayInputStream(wire))).readI32(Pc1FrameLayout.PENDING_COUNT)==Integer.MAX_VALUE,"P1N1 keeps complete raw bytes without range projection");check(Arrays.equals(wire,saved(Pc1NativeFrame.load(new DataInputStream(new ByteArrayInputStream(wire))))),"raw codec is byte-exact including unknown fields");
        Pc1NativeFrame strings=Pc1NativeFrame.fromBytes(pattern());byte[] tail=strings.bytes();strings.writeCString(4,new byte[]{65,66});check(Arrays.equals(strings.rawCString(4),new byte[]{65,66})&&strings.readU8(6)==0&&strings.readU8(7)==(tail[7]&255),"native strcpy writes one terminator and keeps stale suffix");
        byte[] crossing=new byte[129];Arrays.fill(crossing,(byte)67);strings.writeCString(4,crossing);check(strings.rawCString(4).length==129&&strings.readU8(4+129)==0,"native strings may cross adjacent128-byte cells within known frame");strings.writeCString(4,new byte[0]);check(strings.readU8(4)==0&&strings.readU8(5)==67,"empty C string only clears its head");
        byte[] external={9,8,7};strings.writeBytes(8001,external);external[0]=0;check(strings.readU8(8001)==9,"raw writes do not retain an input array alias");byte[] piece=strings.rawSlice(8001,3);piece[0]=0;check(strings.readU8(8001)==9,"raw slice is detached");strings.writeCString(8003,new byte[0]);check(strings.rawCString(8003).length==0,"last owned byte can contain the complete empty native C string");
        byte[] slotRaw=pattern();slotRaw[0]=1;slotRaw[1]=slotRaw[2]=slotRaw[3]=0;byte[] extended=Arrays.copyOf(slotRaw,8011);Arrays.fill(extended,8004,8011,(byte)33);Pc1NativeFrame slotFrame=Pc1NativeFrame.fromSlot(Pc1NativeSlot.parse(extended));check(Arrays.equals(slotFrame.bytes(),slotRaw),"fromSlot takes exact8004 known memory and excludes trailing file bytes");
        final Pc1NativeFrame checked=strings;byte[] atomic=checked.bytes();reject(()->checked.writeCString(8003,new byte[]{1}),"outside owned8004");reject(()->checked.writeCString(2,new byte[]{1,0,2}),"contains NUL");reject(()->checked.writeBytes(8003,new byte[2]),"outside owned8004");check(Arrays.equals(atomic,checked.bytes()),"invalid writes fail before changing any canonical bytes");
        reject(()->checked.readBank90(-6369),"unowned neighboring");reject(()->checked.writeBank90(1636,1),"unowned neighboring");reject(()->checked.readNativeOrdinary(-32,0),"unowned neighboring");reject(()->checked.readOrdinary(0,-1),"typed reference");reject(()->checked.readI32(8001),"outside owned8004");reject(()->checked.rawLogWord(1000),"1000 entries");reject(()->checked.pendingDword(60,0),"60x8");
        final byte[] unterminated=new byte[8004];Arrays.fill(unterminated,(byte)1);reject(()->Pc1NativeFrame.fromBytes(unterminated).rawCString(4),"beyond owned8004");reject(()->Pc1NativeFrame.fromBytes(new byte[8003]),"exactly8004");reject(()->Pc1NativeFrame.load(new DataInputStream(new ByteArrayInputStream(new byte[8008]))),"format");reject(()->Pc1NativeFrame.load(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(wire,wire.length-1)))),null);
        return count;
    }
    private static int patternValue(int n){return n*73+(n>>3)+17;}
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 canonical native-frame assertions");}
}
