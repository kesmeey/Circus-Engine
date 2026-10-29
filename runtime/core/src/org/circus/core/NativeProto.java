package org.circus.core;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Bounded protobuf wire reader for the verified Unity native saves. No object deserialization. */
public final class NativeProto {
    private NativeProto() {}
    private static final int MAX_FIELDS=200000,MAX_DEPTH=32;
    private static final class Budget {int fields;long bytes,limit;Budget(long limit){this.limit=limit;}}
    public static Message framed(byte[] bytes,int maxBytes)throws IOException {
        if(bytes==null||bytes.length<4||bytes.length>maxBytes)throw new IOException("Native protobuf file size limit");
        long size=(bytes[0]&255L)|((bytes[1]&255L)<<8)|((bytes[2]&255L)<<16)|((bytes[3]&255L)<<24);
        if(size!=bytes.length-4)throw new IOException("Native protobuf length prefix mismatch");
        return parse(Arrays.copyOfRange(bytes,4,bytes.length),new Budget(4L*maxBytes),0);
    }
    public static final class Message {
        private final List<Field> fields;
        private Message(List<Field> value){fields=Collections.unmodifiableList(value);}
        public List<Field> fields(){return fields;}
        public List<Field> fields(int number){List<Field> out=new ArrayList<>();for(Field f:fields)if(f.number==number)out.add(f);return Collections.unmodifiableList(out);}
        public Field optional(int number)throws IOException {Field found=null;for(Field f:fields)if(f.number==number){if(found!=null)throw new IOException("Repeated native protobuf field "+number);found=f;}return found;}
        public Field required(int number)throws IOException {Field f=optional(number);if(f==null)throw new IOException("Missing native protobuf field "+number);return f;}
    }
    public static final class Field {
        public final int number,wire;
        private final long value;private final byte[] data;private final Budget budget;private final int depth;private Message parsed;
        private Field(int number,int wire,long value,byte[] data,Budget budget,int depth){this.number=number;this.wire=wire;this.value=value;this.data=data;this.budget=budget;this.depth=depth;}
        private void require(int type)throws IOException{if(wire!=type)throw new IOException("Native protobuf field "+number+" wire type "+wire+", expected "+type);}
        public int int32()throws IOException {require(0);int n=(int)value;if(value!=n&&value!=(n&0xffffffffL))throw new IOException("Native protobuf int32 overflow");return n;}
        public long int64()throws IOException {require(0);return value;}
        public float float32()throws IOException {require(5);float n=Float.intBitsToFloat((int)value);if(!Float.isFinite(n))throw new IOException("Non-finite native protobuf float");return n;}
        public double float64()throws IOException {require(1);double n=Double.longBitsToDouble(value);if(!Double.isFinite(n))throw new IOException("Non-finite native protobuf double");return n;}
        public byte[] bytes()throws IOException {require(2);return data.clone();}
        public String string(int maxBytes)throws IOException {
            require(2);if(data.length>maxBytes)throw new IOException("Native protobuf string size limit");
            try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();}
            catch(CharacterCodingException e){throw new IOException("Invalid native protobuf UTF-8",e);}
        }
        public Message message()throws IOException {require(2);if(parsed==null)parsed=parse(data,budget,depth+1);return parsed;}
    }
    private static Message parse(byte[] data,Budget budget,int depth)throws IOException {
        if(depth>MAX_DEPTH)throw new IOException("Native protobuf nesting limit");
        Reader in=new Reader(data);List<Field> out=new ArrayList<>();
        while(in.at<data.length){
            if(++budget.fields>MAX_FIELDS)throw new IOException("Native protobuf field count limit");
            long tag=in.varint();if(tag<=0||tag>0xffffffffL)throw new IOException("Invalid native protobuf tag");int number=(int)(tag>>>3),wire=(int)(tag&7);if(number==0)throw new IOException("Native protobuf zero field");
            long value=0;byte[] bytes=null;
            switch(wire){case 0:value=in.varint();break;case 1:value=in.fixed(8);break;case 5:value=in.fixed(4);break;
                case 2:long size=in.varint();if(size<0||size>data.length-in.at)throw new IOException("Truncated native protobuf bytes");budget.bytes+=size;if(budget.bytes>budget.limit)throw new IOException("Native protobuf allocation limit");bytes=Arrays.copyOfRange(data,in.at,in.at+(int)size);in.at+=(int)size;break;
                default:throw new IOException("Unsupported native protobuf wire type "+wire);
            }
            out.add(new Field(number,wire,value,bytes,budget,depth));
        }
        return new Message(out);
    }
    private static final class Reader {
        final byte[] data;int at;Reader(byte[] data){this.data=data;}
        int octet()throws IOException{if(at==data.length)throw new EOFException("Truncated native protobuf");return data[at++]&255;}
        long varint()throws IOException{long value=0;for(int n=0;n<10;n++){int b=octet();if(n==9&&(b&0xfe)!=0)throw new IOException("Native protobuf varint overflow");value|=(long)(b&127)<<(7*n);if((b&128)==0)return value;}throw new IOException("Native protobuf varint length");}
        long fixed(int n)throws IOException{long v=0;for(int i=0;i<n;i++)v|=(long)octet()<<(8*i);return v;}
    }
}
