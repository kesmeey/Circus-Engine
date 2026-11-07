package org.circus.core;

import java.io.*;
import java.util.*;

/** Current CHS41BC60/41BD50 and linked420BE5/4252C0 initial C locale.
 *Rows are complete raw64byte allocations, not normalized names or spans. */
public final class Pc1VoiceIndex {
    private final int count;
    private final byte[] original,sorted;
    private final int[] originalIds;
    private Pc1VoiceIndex(int count,byte[] rows,int locale)throws IOException{
        locale(locale);if(count<0||(long)count*64>Integer.MAX_VALUE||rows==null||rows.length!=(long)count*64)throw new IOException("PC1 voice allocation/read backing not fully known; wrapped/short malloc tail cannot be invented");
        this.count=count;original=rows.clone();sorted=rows.clone();originalIds=new int[count];for(int n=0;n<count;n++)originalIds[n]=n;sort();
    }
    private static void locale(int value)throws IOException{if(value!=0)throw new IOException("PC1 voice CRT locale branch is not bound to its native locale owner");}
    public static Pc1VoiceIndex fromRawTable(int count,byte[] rows,int nativeLocaleFlag)throws IOException{return new Pc1VoiceIndex(count,rows,nativeLocaleFlag);}
    /** Read only the header/table; no whole-archive allocation or payload span
     *inference. Native ignores short counts, leaving unowned malloc bytes: gate. */
    public static Pc1VoiceIndex read(Archive.Source archive,int nativeLocaleFlag)throws IOException{
        byte[] head=archive.read(0,4);if(head.length!=4)throw new IOException("PC1 voice count partial backing not owned");long count=Binary.u32(head,0),bytes=count*64;if(count>Integer.MAX_VALUE||bytes>Integer.MAX_VALUE)throw new IOException("PC1 voice wrapped/oversized allocation owner");byte[] rows=archive.read(4,(int)bytes);if(rows.length!=bytes)throw new IOException("PC1 voice table short read leaves unowned heap suffix");return fromRawTable((int)count,rows,nativeLocaleFlag);
    }
    public int count(){return count;}
    public byte[] originalRows(){return original.clone();}
    public byte[] sortedRows(){return sorted.clone();}
    public int originalRow(int sortedIndex)throws IOException{row(sortedIndex);return originalIds[sortedIndex];}
    public byte[] rowBytes(int index)throws IOException{row(index);return Arrays.copyOfRange(sorted,index*64,index*64+64);}
    public byte[] rawName(int index)throws IOException{row(index);int end=index*64;while(end<sorted.length&&sorted[end]!=0)end++;if(end==sorted.length)throw new IOException("PC1 voice strcmpi source leaves allocated table");return Arrays.copyOfRange(sorted,index*64,end);}
    public long offset(int index)throws IOException{row(index);return Binary.u32(sorted,index*64+60);}
    private void row(int index)throws IOException{if(index<0||index>=count)throw new IOException("PC1 voice native lookup probes unowned row:"+index+" count="+count);}
    private static int fold(int b){return b>='A'&&b<='Z'?b+32:b;}
    private int compare(int a,int b)throws IOException{return compare(sorted,a*64,sorted,b*64);}
    private static int compare(byte[] a,int pa,byte[] b,int pb)throws IOException{
        for(;;pa++,pb++){if(pa<0||pa>=a.length||pb<0||pb>=b.length)throw new IOException("PC1 voice strcmpi source leaves known C-string backing");int x=fold(a[pa]&255),y=fold(b[pb]&255);if(x!=y)return x<y?-1:1;if(x==0)return 0;}
    }
    /**425210 initial C locale, unsigned bytes, firstNUL ends comparison. */
    public static boolean prefix(byte[] value,byte[] prefix,int n,int nativeLocaleFlag)throws IOException{
        locale(nativeLocaleFlag);for(int at=0;at<n;at++){int x=at<value.length?value[at]&255:0,y=at<prefix.length?prefix[at]&255:0;if(x==0||y==0)return x==y;if(fold(x)!=fold(y))return false;}return true;
    }
    private void swap(int a,int b){if(a==b)return;for(int n=0;n<64;n++){byte t=sorted[a*64+n];sorted[a*64+n]=sorted[b*64+n];sorted[b*64+n]=t;}int id=originalIds[a];originalIds[a]=originalIds[b];originalIds[b]=id;}
    /** Exact linked CRT partition/shortsort swap order, including ties. */
    private void sort()throws IOException{
        if(count<2)return;ArrayDeque<int[]> stack=new ArrayDeque<>();int lo=0,hi=count-1;
        for(;;){int size=hi-lo+1;if(size<=8){while(hi>lo){int max=lo;for(int n=lo+1;n<=hi;n++)if(compare(n,max)>0)max=n;swap(max,hi--);}}
            else{swap(lo+size/2,lo);int low=lo,high=hi+1;for(;;){do{low++;}while(low<=hi&&compare(low,lo)<=0);do{high--;}while(high>lo&&compare(high,lo)>=0);if(high<low)break;swap(low,high);}swap(high,lo);
                if((high-lo)*64-1>=(hi-low)*64){if(lo+1<high)stack.push(new int[]{lo,high-1});if(low<hi){lo=low;continue;}}
                else{if(low<hi)stack.push(new int[]{low,hi});if(lo+1<high){hi=high-1;continue;}}
            }
            if(stack.isEmpty())return;int[] next=stack.pop();lo=next[0];hi=next[1];
        }
    }
    /**425210 over addressed sources; do not pre-copy or synthesize the NUL.
     *Caller supplies the same canonical memory and fixed argument pointers. */
    public interface Converter {int lowerCase42534C(int unsignedByte)throws IOException;}
    public static boolean prefix(Pc1NativePaths.Memory memory,int left,int right,int count,int nativeLocaleFlag)throws IOException{return prefix(memory,left,right,count,nativeLocaleFlag,b->{throw new IOException("PC1 voice non-C locale42534C converter required");});}
    public static boolean prefix(Pc1NativePaths.Memory memory,int left,int right,int count,int nativeLocaleFlag,Converter converter)throws IOException{
        for(int n=0;n<count;n++){int a=memory.read(left+n)&255,b=memory.read(right+n)&255;if(a==0||b==0)return a==b;if(nativeLocaleFlag==0){a=fold(a);b=fold(b);}else{int convertedRight=converter.lowerCase42534C(b);a=converter.lowerCase42534C(a);b=convertedRight;}if(a!=b)return false;}return true;
    }
    private static int liveLength(Pc1NativePaths.Memory memory,int source)throws IOException{for(int n=0;n<1024*1024;n++)if((memory.read(source+n)&255)==0)return n;throw new IOException("PC1 voice raw source leaves known scan backing");}
    /**41BD50 addressed route: scan first separator, then strlen and forward
     *DWORD/remainder copy into the actual80B local before forcing extension.
     *No admission-time CString or cached literal is manufactured. */
    public long lookupRaw(final Pc1NativePaths.Memory memory,int rawName,final int rawPcmExtension,int nativeLocaleFlag)throws IOException{
        locale(nativeLocaleFlag);if(memory==null)throw new IOException("PC1 voice raw lookup memory missing");int source=rawName,n=0,b=memory.read(source)&255;while(b!=0&&b!='\\'){if(++n>=1024*1024)throw new IOException("PC1 voice first separator exceeds known scan backing");source++;b=memory.read(source)&255;}if((memory.read(source)&255)=='\\')source++;
        int count=liveLength(memory,source)+1;final int local=0xe1000000;final byte[] scratch=new byte[80];final boolean[] known=new boolean[80];final Pc1NativePaths.Memory localMemory=new Pc1NativePaths.Memory(){
            public int read(int at)throws IOException{long offset=(at&0xffffffffL)-(local&0xffffffffL);if(offset>=0&&offset<80){if(!known[(int)offset])throw new IOException("PC1 voice lookup reads unknown native stack suffix");return scratch[(int)offset]&255;}return memory.read(at);}
            public void write(int at,int value)throws IOException{long offset=(at&0xffffffffL)-(local&0xffffffffL);if(offset<0||offset>=80)throw new IOException("PC1 voice lookup writes beyond native80B stack owner");scratch[(int)offset]=(byte)value;known[(int)offset]=true;}
        };int at=0;for(;at+4<=count;at+=4){int word=0;for(int k=0;k<4;k++)word|=(memory.read(source+at+k)&255)<<(k*8);for(int k=0;k<4;k++)localMemory.write(local+at+k,word>>>(k*8));}for(;at<count;at++)localMemory.write(local+at,memory.read(source+at));
        Pc1NativePaths.forceExtension(localMemory,local,rawPcmExtension);int lo=0,hi=this.count;while(lo<=hi){int mid=lo+(hi-lo)/2;row(mid);int cmp=compare(scratch,0,sorted,mid*64);if(cmp==0)return offset(mid);if(cmp<0)hi=mid-1;else lo=mid+1;}return 0;
    }
    /** The raw lookup function applies no VOICE prefix filter. No backslash
     *means the empty suffix. The caller's80byte stack buffer is a real bound. */
    public long lookup(byte[] rawName,byte[] rawPcmExtension,int nativeLocaleFlag)throws IOException{
        locale(nativeLocaleFlag);if(rawName==null||rawPcmExtension==null)throw new IOException("PC1 voice lookup raw argument missing");int end=0;while(end<rawName.length&&rawName[end]!=0)end++;int start=0;while(start<end&&rawName[start]!='\\')start++;if(start<end)start++;
        byte[] suffix=Arrays.copyOfRange(rawName,start,end);if(suffix.length+1>80)throw new IOException("PC1 voice lookup copies beyond original80byte stack owner");final byte[] scratch=new byte[80];System.arraycopy(suffix,0,scratch,0,suffix.length);
        Pc1NativePaths.Memory memory=new Pc1NativePaths.Memory(){public int read(int address)throws IOException{if(address>=0&&address<80)return scratch[address]&255;int at=address-256;if(at>=0&&at<=rawPcmExtension.length)return at==rawPcmExtension.length?0:rawPcmExtension[at]&255;throw new IOException("PC1 voice extension leaves original80byte scratch/known literal");}public void write(int address,int value)throws IOException{if(address<0||address>=80)throw new IOException("PC1 voice extension writes beyond original80byte stack owner");scratch[address]=(byte)value;}};
        Pc1NativePaths.forceExtension(memory,0,256);int lo=0,hi=count;while(lo<=hi){int mid=lo+(hi-lo)/2;row(mid);int cmp=compare(scratch,0,sorted,mid*64);if(cmp==0)return offset(mid);if(cmp<0)hi=mid-1;else lo=mid+1;}return 0;
    }
}
