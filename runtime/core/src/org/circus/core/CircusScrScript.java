package org.circus.core;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Unity-era CircusScr1.0 bytecode. Addresses are instruction indices, not byte offsets. */
public final class CircusScrScript {
    public static final String SIGNATURE="CircusScr1.0";
    public static final class Instruction {
        public final int index,opcode,value;
        Instruction(int index,int opcode,int value){this.index=index;this.opcode=opcode;this.value=value;}
        public int argumentCount(){return value>>>16;}
        public int command(){return value&255;}
    }
    public final String name;
    public final Instruction[] instructions;
    public final String[] strings,preloadScripts;
    public final int[] jumps;
    public final Map<String,Integer> labels;
    public final boolean primaryBundle;
    public CircusScrScript(String name,byte[] data)throws IOException {
        if(data==null||data.length>32*1024*1024)throw new IOException("CircusScr size limit");
        this.name=normalizeName(name);Reader r=new Reader(data);
        if(!SIGNATURE.equals(new String(r.bytes(12),StandardCharsets.US_ASCII)))throw new IOException("Invalid CircusScr1.0 signature");
        int count=r.count(1000000,"instructions");if((long)count*8>r.remaining())throw new IOException("Truncated CircusScr instructions");
        instructions=new Instruction[count];for(int i=0;i<count;i++){int op=r.integer(),v=r.integer();if(op<0||op>28)throw new IOException("Invalid CircusScr opcode "+op+" at "+i);instructions[i]=new Instruction(i,op,v);}
        strings=new String[r.count(1000000,"strings")];for(int i=0;i<strings.length;i++)strings[i]=r.string();
        int n=r.count(1000000,"labels");Map<String,Integer> map=new LinkedHashMap<String,Integer>();
        for(int i=0;i<n;i++){String label=r.string();int target=r.integer();target(target);if(map.put(label,target)!=null)throw new IOException("Duplicate CircusScr label: "+label);}
        labels=Collections.unmodifiableMap(map);jumps=new int[r.count(1000000,"jumps")];for(int i=0;i<jumps.length;i++){jumps[i]=r.integer();target(jumps[i]);}
        preloadScripts=new String[r.count(1000000,"preloads")];for(int i=0;i<preloadScripts.length;i++)preloadScripts[i]=r.string();
        int flag=r.octet();if(flag>1)throw new IOException("Invalid CircusScr bundle flag");primaryBundle=flag!=0;
        if(r.remaining()!=0)throw new IOException("Trailing CircusScr data: "+r.remaining());
        for(Instruction ins:instructions){
            if((ins.opcode==24||ins.opcode==27)&&(ins.value<0||ins.value>=jumps.length))throw new IOException("Invalid CircusScr jump index at "+ins.index);
            if(ins.opcode==28&&(ins.value<0||ins.value>=strings.length))throw new IOException("Invalid CircusScr source index at "+ins.index);
            if(ins.opcode==4&&(ins.argumentCount()>1024||(ins.value&0xff00)!=0))throw new IOException("Invalid CircusScr function descriptor at "+ins.index);
        }
    }
    private void target(int target)throws IOException{if(target<0||target>instructions.length)throw new IOException("CircusScr branch outside script: "+target);}
    public int label(String name)throws IOException{Integer result=labels.get(name);if(result==null)throw new IOException("Missing CircusScr label "+name+" in "+this.name);return result;}
    public static String normalizeName(String name)throws IOException {
        if(name==null)throw new IOException("Missing CircusScr name");name=name.replace('\\','/');if(name.toLowerCase(Locale.ROOT).endsWith(".txt"))name=name.substring(0,name.length()-4);
        if(name.length()==0||name.length()>240||name.contains("/")||name.contains("..")||name.indexOf(0)>=0)throw new IOException("Unsafe CircusScr name");return name.toLowerCase(Locale.ROOT);
    }
    private static final class Reader {
        final byte[] data;int position;
        Reader(byte[] data){this.data=data;}
        int remaining(){return data.length-position;}
        int octet()throws IOException{if(position>=data.length)throw new EOFException("Truncated CircusScr");return data[position++]&255;}
        int integer()throws IOException{return octet()|octet()<<8|octet()<<16|octet()<<24;}
        int count(int max,String type)throws IOException{int n=integer();if(n<0||n>max)throw new IOException("CircusScr "+type+" count out of bounds");return n;}
        byte[] bytes(int n)throws IOException{if(n<0||n>remaining())throw new EOFException("Truncated CircusScr data");byte[] value=Arrays.copyOfRange(data,position,position+n);position+=n;return value;}
        String string()throws IOException{
            int length=0;for(int shift=0;;shift+=7){if(shift>28)throw new IOException("Invalid CircusScr string length");int b=octet();if(shift==28&&(b&0xf8)!=0)throw new IOException("CircusScr string length overflow");length|=(b&127)<<shift;if((b&128)==0)break;}
            if(length>4*1024*1024)throw new IOException("CircusScr string size limit");byte[] utf=bytes(length);
            try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(utf)).toString();}catch(CharacterCodingException e){throw new IOException("Invalid CircusScr UTF-8",e);}
        }
    }
}
