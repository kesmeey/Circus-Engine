"""One-time mechanical port of MIT GARbro's integer transform; input kept in build/."""
from pathlib import Path
import re
root=Path(__file__).resolve().parent.parent
source=(root/'build/AudioPCM-reference.cs').read_text(encoding='utf-8-sig')
start=source.index('        int[] dword_43A214')
body=source[start:source.rfind('    }')]
body=body.replace('static readonly','static final').replace('.Length','.length').replace('var table','short[] table')
body=body.replace('(uint)dword_6996C8[v8]','Integer.toUnsignedLong(dword_6996C8[v8])')
body=body.replace('uint v88 = (uint)(v6 + 3) >> 2;','int v88 = (v6 + 3) >>> 2;')
body=body.replace('uint v89 = (uint)(v79 + 1) >> 1;','int v89 = (v79 + 1) >>> 1;')
body=body.replace('(ulong)','').replace(' >> 12',' >>> 12')
body=body.replace('byte v7 = input[v1+0x800];','int v7 = input[v1+0x800]&255;').replace('byte v8 = input[v1];','int v8 = input[v1]&255;')
body=body.replace('LittleEndian.ToUInt16','Binary.u16').replace('LittleEndian.ToInt16 (output, v6)','(short)Binary.u16(output,v6)')
body=body.replace('LittleEndian.Pack ((short)v11, output, v6);','output[v6]=(byte)v11;output[v6+1]=(byte)(v11>>8);')
body=body.replace('int output_size) //','int output_size)throws IOException //').replace('byte[] input, int a1)','byte[] input, int a1)throws IOException')
license=source[:source.index('using System;')]
head='''package org.circus.core;
import java.io.*;
import java.util.*;
import java.util.zip.*;
/** Integer XPCM transform ported from GARbro; retains integer wrap and unsigned shifts. */
final class XpcmTransform {
    static byte[] decode(byte[] data,int size,int mode,int extra)throws IOException {
        if(extra<0||extra>3)throw new IOException("XPCM transform parameters");
        long packed=Binary.u32(data,28);Binary.range(32,packed,data.length);
        int expected=((size+8127)/8128)*8192;
        byte[] encoded=new byte[expected];
        if(mode==3){Inflater z=new Inflater();try{z.setInput(data,32,(int)packed);int n=0;while(!z.finished()&&n<encoded.length){int got=z.inflate(encoded,n,encoded.length-n);if(got==0&&!z.finished())throw new IOException("Truncated XPCM spectrum");n+=got;}if(n!=expected||!z.finished()||z.getRemaining()!=0)throw new IOException("XPCM spectrum length mismatch");}catch(DataFormatException e){throw new IOException("XPCM deflate",e);}finally{z.end();}}
        else if(mode==1){int src=32,end=32+(int)packed,dst=0,flag=0;while(src<end){flag>>=1;if((flag&256)==0)flag=(data[src++]&255)|0xff00;if((flag&1)!=0){if(src>=end||dst>=expected)throw new IOException("XPCM literal bounds");encoded[dst++]=data[src++];}else{if(src>=end)break;int ctl=data[src++]&255,count,back;if(ctl>=192){if(src>=end)throw new IOException("XPCM short match");back=(data[src++]&255)|((ctl&3)<<8);count=4+((ctl>>2)&15);}else if((ctl&128)!=0){back=ctl&31;count=2+((ctl>>5)&3);if(back==0){if(src>=end)throw new IOException("XPCM short offset");back=data[src++]&255;}}else if(ctl==127){if(src+4>end)throw new IOException("XPCM long match");count=2+Binary.u16(data,src);back=Binary.u16(data,src+2);src+=4;}else{if(src+2>end)throw new IOException("XPCM match");back=Binary.u16(data,src);src+=2;count=ctl+4;}if(back<=0||back>dst||count>expected-dst)throw new IOException("XPCM match bounds");for(int n=0;n<count;n++){encoded[dst]=encoded[dst-back];dst++;}}}if(dst!=expected)throw new IOException("XPCM spectrum length");}
        else throw new IOException("XPCM compression");
        XpcmTransform decoder=new XpcmTransform();for(int n=0;n<16;n++)decoder.dword_43A214[n]=(int)Binary.u32(unk_43A254,extra*64+n*4);
        byte[] output=new byte[size+8192];decoder.DecodeV1(output,encoded,size);return Arrays.copyOf(output,size);
    }
'''
(root/'runtime/core/src/org/circus/core/XpcmTransform.java').write_text(license+head+body+'}\n',encoding='utf8')
