package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Repackages FSB5 Vorbis packets into Ogg without decoding or altering the compressed audio. */
public final class Fsb5Vorbis {
    private Fsb5Vorbis(){}
    private static volatile Map<Integer,byte[]> headers;
    private static final int[] RATES={0,8000,11000,11025,16000,22050,24000,32000,44100,48000};
    private static final int[] CRC=new int[256];
    static{for(int i=0;i<256;i++){int crc=i<<24;for(int j=0;j<8;j++)crc=(crc<<1)^((crc<0)?0x04c11db7:0);CRC[i]=crc;}}
    private static byte[] setup(int crc)throws IOException{
        Map<Integer,byte[]> known=headers;
        if(known==null)synchronized(Fsb5Vorbis.class){known=headers;if(known==null){
            try(InputStream raw=Fsb5Vorbis.class.getResourceAsStream("fsb5-vorbis-headers.bin.gz")){
                if(raw==null)throw new IOException("Missing bundled FSB5 Vorbis setup headers");
                UnityBinary r=new UnityBinary(Binary.read(new GZIPInputStream(raw),2*1024*1024),true);if(!new String(r.bytes(4),StandardCharsets.US_ASCII).equals("FVH1"))throw new IOException("FSB5 setup table signature");
                known=new HashMap<>();int n=r.count(10000,"Vorbis setup");for(int i=0;i<n;i++){int key=r.i32(),size=r.count(65536,"Vorbis setup bytes");known.put(key,r.bytes(size));}headers=known;
            }
        }}
        byte[] bytes=known.get(crc);if(bytes==null)throw new IOException("Unknown FSB5 Vorbis setup CRC "+Integer.toUnsignedString(crc));return bytes;
    }
    public static byte[] toOgg(byte[] fsb)throws IOException{
        if(fsb.length>64*1024*1024)throw new IOException("FSB5 conversion input limit");
        UnityBinary r=new UnityBinary(fsb,true);if(!new String(r.bytes(4),StandardCharsets.US_ASCII).equals("FSB5"))throw new IOException("Expected FSB5 audio");
        int version=r.i32(),count=r.i32(),sampleHeaderBytes=r.i32(),nameBytes=r.i32(),dataBytes=r.i32(),codec=r.i32();
        if((version!=0&&version!=1)||count!=1||codec!=15||sampleHeaderBytes<8||nameBytes<0||dataBytes<0)throw new IOException("Unsupported FSB5 audio header");r.skip(version==0?36:32);
        long dataStart=(long)r.position+sampleHeaderBytes+nameBytes;Binary.range(dataStart,dataBytes,fsb.length);int headerEnd=r.position+sampleHeaderBytes;if(headerEnd<r.position||headerEnd>fsb.length)throw new IOException("FSB5 sample header bounds");
        long packed=r.i64();boolean more=(packed&1)!=0;int rateId=(int)(packed>>>1&15),channels=(int)(packed>>>5&1)+1,rate=rateId<RATES.length?RATES[rateId]:0;long offset=(packed>>>6&0xfffffffL)*16,samples=packed>>>34;int crc=0;boolean haveSetup=false;
        for(int chunks=0;more;chunks++){
            if(chunks>=1024||r.position>headerEnd-4)throw new IOException("FSB5 metadata chunk limit");int word=r.i32(),size=word>>>1&0xffffff,type=word>>>25;more=(word&1)!=0;if(size>headerEnd-r.position)throw new IOException("Truncated FSB5 metadata");int next=r.position+size;
            if(type==1){if(size!=1)throw new IOException("FSB5 channel metadata");channels=r.u8();}
            else if(type==2){if(size!=4)throw new IOException("FSB5 frequency metadata");rate=r.i32();}
            else if(type==11){if(size<4)throw new IOException("FSB5 Vorbis metadata");crc=r.i32();haveSetup=true;}
            r.position=next;
        }
        if(!haveSetup||channels<1||channels>8||rate<1000||rate>384000||offset>dataBytes||samples==0)throw new IOException("Invalid FSB5 Vorbis metadata");
        byte[] setup=setup(crc);boolean[] modes=blockModes(setup,channels);int modeBits=ilog(modes.length-1);
        Ogg out=new Ogg();ByteArrayOutputStream id=new ByteArrayOutputStream();id.write(1);id.write("vorbis".getBytes(StandardCharsets.US_ASCII));le(id,0,4);id.write(channels);le(id,rate,4);le(id,0,12);id.write(0xb8);id.write(1);out.packet(id.toByteArray(),0,true,false);
        ByteArrayOutputStream comment=new ByteArrayOutputStream();comment.write(3);comment.write("vorbis".getBytes(StandardCharsets.US_ASCII));byte[] vendor="Circus Runtime".getBytes(StandardCharsets.US_ASCII);le(comment,vendor.length,4);comment.write(vendor);le(comment,0,4);comment.write(1);out.packet(comment.toByteArray(),0,false,false);out.packet(setup,0,false,false);
        int at=(int)(dataStart+offset),end=(int)(dataStart+dataBytes),previous=0,packets=0;long granule=0;
        while(at<end){if(end-at<2)throw new EOFException("FSB5 packet size");int length=(fsb[at]&255)|(fsb[at+1]&255)<<8;at+=2;if(length==0||length==65535)break;if(length>end-at)throw new EOFException("FSB5 Vorbis packet");
            byte[] packet=Arrays.copyOfRange(fsb,at,at+length);at+=length;Bits bits=new Bits(packet);if(bits.read(1)!=0)throw new IOException("FSB5 packet is not Vorbis audio");int mode=bits.read(modeBits);if(mode>=modes.length)throw new IOException("Invalid Vorbis packet mode");int block=modes[mode]?2048:256;if(previous!=0)granule+=(previous+block)/4;previous=block;
            // Unity's FSB writer aligns the data section with zeros. A complete final
            // packet can leave exactly one padding byte, too short for a size field.
            boolean last=at==end||(end-at==1&&fsb[at]==0)||(end-at>=2&&((fsb[at]==0&&fsb[at+1]==0)||((fsb[at]&255)==255&&(fsb[at+1]&255)==255)));
            if(last&&samples>granule)throw new IOException("FSB5 sample count exceeds decoded packet duration");
            out.packet(packet,last?samples:granule,false,last);packets++;if(last)break;
        }
        if(packets==0)throw new IOException("Empty FSB5 Vorbis stream");return out.bytes.toByteArray();
    }
    private static int ilog(int value){return value==0?0:32-Integer.numberOfLeadingZeros(value);}
    private static void le(OutputStream out,long value,int bytes)throws IOException{for(int i=0;i<bytes;i++)out.write(i<8?(int)(value>>>(8*i)):0);}
    private static final class Ogg {
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();int sequence;
        void packet(byte[] packet,long granule,boolean bos,boolean eos)throws IOException{
            int at=0;boolean continued=false,done=false;
            while(!done){int remaining=packet.length-at,segments=Math.min(255,remaining/255+1),payload=Math.min(remaining,segments*255);done=segments>remaining/255;
                if((long)bytes.size()+27+segments+payload>64*1024*1024)throw new IOException("FSB5 Ogg conversion output limit");
                ByteArrayOutputStream page=new ByteArrayOutputStream(27+segments+payload);page.write(new byte[]{'O','g','g','S',0});page.write((continued?1:0)|(bos?2:0)|(done&&eos?4:0));le(page,done?granule:-1,8);le(page,0x43524353,4);le(page,sequence++,4);le(page,0,4);page.write(segments);int left=payload;for(int i=0;i<segments;i++){int n=Math.min(left,255);page.write(n);left-=n;}page.write(packet,at,payload);byte[] data=page.toByteArray();int crc=0;for(byte b:data)crc=(crc<<8)^CRC[(crc>>>24)^(b&255)];for(int i=0;i<4;i++)data[22+i]=(byte)(crc>>>(8*i));bytes.write(data);at+=payload;continued=true;bos=false;
            }
        }
    }
    private static final class Bits {
        final byte[] bytes;long bit;Bits(byte[] bytes){this.bytes=bytes;}
        int read(int n)throws IOException{if(n<0||n>32||bit+n>(long)bytes.length*8)throw new EOFException("Vorbis setup bits");int v=0;for(int i=0;i<n;i++,bit++)v|=((bytes[(int)(bit>>>3)]>>>(bit&7))&1)<<i;return v;}
        void skip(long n)throws IOException{if(n<0||bit+n>(long)bytes.length*8)throw new EOFException("Vorbis setup span");bit+=n;}
    }
    /** Parse only enough setup syntax to identify packet window sizes; no audio decoder is required. */
    private static boolean[] blockModes(byte[] setup,int channels)throws IOException{
        if(setup.length<8||setup[0]!=5||!new String(setup,1,6,StandardCharsets.US_ASCII).equals("vorbis"))throw new IOException("Invalid Vorbis setup packet");Bits b=new Bits(setup);b.skip(56);int books=b.read(8)+1;
        for(int book=0;book<books;book++){
            if(b.read(24)!=0x564342)throw new IOException("Vorbis codebook sync");int dimensions=b.read(16),entries=b.read(24);if(dimensions==0||entries==0)throw new IOException("Vorbis codebook geometry");
            if(b.read(1)!=0){int length=b.read(5)+1,current=0;while(current<entries){if(length++>32)throw new IOException("Vorbis ordered codebook lengths");int n=b.read(ilog(entries-current));if(n>entries-current)throw new IOException("Vorbis codebook count");current+=n;}}
            else{boolean sparse=b.read(1)!=0;for(int i=0;i<entries;i++)if(!sparse||b.read(1)!=0)b.skip(5);}
            int lookup=b.read(4);if(lookup>2)throw new IOException("Vorbis codebook lookup type");if(lookup!=0){b.skip(64);int valueBits=b.read(4)+1;b.skip(1);long values;
                if(lookup==2)values=(long)entries*dimensions;
                else{int root=(int)Math.floor(Math.pow(entries,1.0/dimensions));while(power(root+1,dimensions,entries)<=entries)root++;while(power(root,dimensions,entries)>entries)root--;values=root;}
                b.skip(values*valueBits);
            }
        }
        int times=b.read(6)+1;for(int i=0;i<times;i++)if(b.read(16)!=0)throw new IOException("Vorbis time transform");
        int floors=b.read(6)+1;for(int f=0;f<floors;f++){int type=b.read(16);if(type==0){b.skip(8+16+16+6+8);b.skip((b.read(4)+1)*8L);}else if(type==1){int partitions=b.read(5),max=-1;int[] classes=new int[partitions];for(int i=0;i<partitions;i++){classes[i]=b.read(4);max=Math.max(max,classes[i]);}int[] dimensions=new int[max+1];for(int c=0;c<=max;c++){dimensions[c]=b.read(3)+1;int subclasses=b.read(2);if(subclasses!=0)b.skip(8);b.skip((1<<subclasses)*8L);}b.skip(2);int range=b.read(4);for(int c:classes)b.skip((long)dimensions[c]*range);}else throw new IOException("Vorbis floor type");}
        int residues=b.read(6)+1;for(int i=0;i<residues;i++){if(b.read(16)>2)throw new IOException("Vorbis residue type");b.skip(72);int classes=b.read(6)+1;b.skip(8);int[] cascade=new int[classes];for(int c=0;c<classes;c++){int low=b.read(3),high=b.read(1)!=0?b.read(5):0;cascade[c]=high<<3|low;}for(int c:cascade)b.skip(Integer.bitCount(c)*8L);}
        int mappings=b.read(6)+1;for(int m=0;m<mappings;m++){if(b.read(16)!=0)throw new IOException("Vorbis mapping type");int submaps=b.read(1)!=0?b.read(4)+1:1;if(b.read(1)!=0){int coupling=b.read(8)+1;b.skip((long)coupling*2*ilog(channels-1));}if(b.read(2)!=0)throw new IOException("Vorbis mapping reserved bits");if(submaps>1)b.skip(channels*4L);b.skip(submaps*24L);}
        boolean[] modes=new boolean[b.read(6)+1];for(int i=0;i<modes.length;i++){modes[i]=b.read(1)!=0;if(b.read(16)!=0||b.read(16)!=0||b.read(8)>=mappings)throw new IOException("Vorbis mode parameters");}if(b.read(1)!=1)throw new IOException("Vorbis setup framing");return modes;
    }
    private static long power(int base,int exp,int limit){long value=1;for(int i=0;i<exp;i++){value*=base;if(value>limit)return (long)limit+1;}return value;}
}
