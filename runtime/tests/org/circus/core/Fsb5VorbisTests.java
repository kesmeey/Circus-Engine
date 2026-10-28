package org.circus.core;

import java.io.*;
import java.util.*;

/** Container-only fixtures: known public codec setup, self-authored packet bytes, no game audio. */
public final class Fsb5VorbisTests {
    private static int assertions;
    private interface Action{void run()throws Exception;}
    private static void check(boolean value,String name){assertions++;if(!value)throw new AssertionError(name);}
    private static void reject(Action action,String name)throws Exception{try{action.run();throw new AssertionError(name);}catch(IOException expected){assertions++;}}
    private static void le(ByteArrayOutputStream out,long n,int count){for(int i=0;i<count;i++)out.write((int)(n>>>(8*i)));}
    private static byte[] fsb(int crc,int samples,byte[] audio)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{'F','S','B','5'});le(out,1,4);le(out,1,4);le(out,16,4);le(out,0,4);le(out,audio.length,4);le(out,15,4);out.write(new byte[32]);le(out,1|8<<1|(long)samples<<34,8);le(out,11<<25|4<<1,4);le(out,crc,4);out.write(audio);return out.toByteArray();
    }
    private static List<byte[]> packets(byte[] ogg)throws Exception{
        List<byte[]> packets=new ArrayList<>();ByteArrayOutputStream packet=new ByteArrayOutputStream();int at=0,page=0;boolean eos=false;long previousGranule=0;
        while(at<ogg.length){int start=at;check(ogg[at]=='O'&&ogg[at+1]=='g'&&ogg[at+2]=='g'&&ogg[at+3]=='S',"Ogg page signature");int flags=ogg[at+5]&255;long granule=0;for(int i=0;i<8;i++)granule|=(long)(ogg[at+6+i]&255)<<(i*8);check(granule>=previousGranule,"Ogg monotonic granule");previousGranule=granule;check((ogg[at+18]&255)==page++,"Ogg sequence");int expected=(int)Binary.u32(ogg,at+22),segments=ogg[at+26]&255;at+=27;int[] lengths=new int[segments];for(int i=0;i<segments;i++)lengths[i]=ogg[at++]&255;for(int length:lengths){packet.write(ogg,at,length);at+=length;if(length<255){packets.add(packet.toByteArray());packet.reset();}}int crc=0;for(int i=start;i<at;i++){int b=i>=start+22&&i<start+26?0:ogg[i]&255;crc^=b<<24;for(int j=0;j<8;j++)crc=(crc<<1)^((crc<0)?0x04c11db7:0);}check(crc==expected,"Ogg CRC");if((flags&4)!=0){check(at==ogg.length,"EOS is final page");eos=true;}if(page==1)check((flags&2)!=0,"Ogg BOS");}
        check(eos,"Ogg EOS");return packets;
    }
    public static int run()throws Exception{
        assertions=0;int crc=(int)2216268436L;byte[] fixture=fsb(crc,128,new byte[]{1,0,0,1,0,0,0,0});byte[] ogg=Fsb5Vorbis.toOgg(fixture);List<byte[]> p=packets(ogg);
        check(p.size()==5,"Three Vorbis headers and two audio packets");check(p.get(0).length==30&&p.get(0)[0]==1&&p.get(0)[11]==1,"Vorbis identification channels");check(Arrays.equals(p.get(3),new byte[]{0})&&Arrays.equals(p.get(4),new byte[]{0}),"Compressed packets remain byte-exact");
        check(Arrays.equals(ogg,Fsb5Vorbis.toOgg(fsb(crc,128,new byte[]{1,0,0,1,0,0}))),"Unpadded packet ends with identical EOS and sample count");
        check(Arrays.equals(ogg,Fsb5Vorbis.toOgg(fsb(crc,128,new byte[]{1,0,0,1,0,0,0}))),"Single zero alignment byte preserves final EOS and sample count");
        reject(()->Fsb5Vorbis.toOgg(fsb(crc,128,new byte[]{1,0,0,1,0,0,1})),"Nonzero truncated packet size is not padding");
        reject(()->Fsb5Vorbis.toOgg(new byte[10]),"Short FSB5 header");reject(()->Fsb5Vorbis.toOgg(fsb(123,128,new byte[]{1,0,0,1,0,0})),"Unknown setup is not guessed");
        reject(()->Fsb5Vorbis.toOgg(fsb(crc,128,new byte[]{20,0,0})),"Truncated audio packet");reject(()->Fsb5Vorbis.toOgg(fsb(crc,128,new byte[]{0,0})),"Empty audio stream");
        reject(()->Fsb5Vorbis.toOgg(fsb(crc,128,new byte[]{1,0,1})),"Non-audio Vorbis packet");reject(()->Fsb5Vorbis.toOgg(fsb(crc,10000,new byte[]{1,0,0,1,0,0})),"Impossible final sample count");
        byte[] damaged=fixture.clone();damaged[68]=8;damaged[69]=(byte)255;reject(()->Fsb5Vorbis.toOgg(damaged),"FSB metadata length bounds");
        byte[] audio=new byte[2+255+3];audio[0]=(byte)255;audio[1]=0;audio[257]=1;audio[258]=0;audio[259]=0;List<byte[]> longPackets=packets(Fsb5Vorbis.toOgg(fsb(crc,128,audio)));check(longPackets.get(3).length==255,"Exact 255-byte packet terminator lacing");
        return assertions;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS FSB5 Vorbis assertions="+run());}
}
