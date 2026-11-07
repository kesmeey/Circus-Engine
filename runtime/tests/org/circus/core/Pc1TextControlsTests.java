package org.circus.core;

import java.io.*;
import java.util.*;
import java.util.zip.InflaterInputStream;

/**Actual fixed-body byte goldens, including unknown-neighbor failure prefixes.*/
public final class Pc1TextControlsTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static final class Memory implements Pc1Compositor.State {
        final int[][] ranges;final int[] scalars;final byte[][] raw,fields;
        final List<String> events=new ArrayList<>();int[][] callback;
        Memory(int[][] ranges,int[] scalars,int wait,int prefix,int full,int current,int dictionary)throws IOException{
            this.ranges=ranges;this.scalars=scalars;raw=new byte[ranges.length][];fields=new byte[scalars.length][4];
            for(int n=0;n<raw.length;n++){raw[n]=new byte[ranges[n][1]];for(int b=0;b<raw[n].length;b++){int address=ranges[n][0]+b;raw[n][b]=(byte)(17+address*37+(address>>>8)*11);}}
            for(int a:scalars)writeInt(a,0);
            for(int[] pair:new int[][]{{0x9d8a20,wait},{0xbb8450,prefix},{0x9b8e1c,full},{0x9689ec,current},{0x43d434,dictionary},{0x4305f4,0x95af28}})writeInt(pair[0],pair[1]);
        }
        private int read(int a)throws IOException{for(int n=0;n<ranges.length;n++)if(a>=ranges[n][0]&&a-ranges[n][0]<raw[n].length)return raw[n][a-ranges[n][0]]&255;for(int n=0;n<scalars.length;n++)if(a>=scalars[n]&&a-scalars[n]<4)return fields[n][a-scalars[n]]&255;throw new IOException("unowned text-controls fixture byte"+Integer.toHexString(a));}
        public void writeByte(int a,int value)throws IOException{for(int n=0;n<ranges.length;n++)if(a>=ranges[n][0]&&a-ranges[n][0]<raw[n].length){raw[n][a-ranges[n][0]]=(byte)value;return;}for(int n=0;n<scalars.length;n++)if(a>=scalars[n]&&a-scalars[n]<4){fields[n][a-scalars[n]]=(byte)value;return;}throw new IOException("unowned text-controls fixture byte"+Integer.toHexString(a));}
        public int readInt(int a)throws IOException{int value=0;for(int n=0;n<4;n++)value|=read(a+n)<<(n*8);return value;}
        public void writeInt(int a,int v)throws IOException{for(int n=0;n<4;n++)writeByte(a+n,v>>>(n*8));}
        void buttons(int mode)throws IOException{events.add(Arrays.toString(new int[]{mode,readInt(0x4305f4),readInt(0x9689ec),readInt(0x43d434),readInt(0x94fa28)}));for(int[] row:callback)writeInt(row[0],row[1]);}
        String digest()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] bytes:raw)out.write(bytes);for(int a:scalars)for(int b=0;b<4;b++)out.write(read(a+b));return Binary.sha256(out.toByteArray());}
    }
    public static int run()throws Exception{
        count=0;DataInputStream in=new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(DATA))));int[][] ranges=new int[in.readInt()][2];for(int[] row:ranges){row[0]=in.readInt();row[1]=in.readInt();}int[] scalars=new int[in.readInt()];for(int n=0;n<scalars.length;n++)scalars[n]=in.readInt();
        for(int cases=in.readInt();cases>0;cases--){Memory memory=new Memory(ranges,scalars,in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt());memory.callback=new int[in.readInt()][2];for(int[] row:memory.callback){row[0]=in.readInt();row[1]=in.readInt();}int[][] program=new int[in.readInt()][2];for(int[] row:program){row[0]=in.readInt();row[1]=in.readInt();}String digest=in.readUTF();List<String> events=new ArrayList<>();for(int n=in.readInt();n>0;n--){int[] event=new int[5];for(int b=0;b<5;b++)event[b]=in.readInt();events.add(Arrays.toString(event));}boolean expectedFault=in.readInt()!=0,fault=false;Pc1TextControls controls=new Pc1TextControls(memory,memory::buttons);
            try{for(int[] row:program)if(row[0]==0)controls.restore(row[1]);else controls.activate();}catch(IOException failure){fault=true;}
            check(fault==expectedFault,"native text-controls owner fault");check(digest.equals(memory.digest()),"native text-controls full byte image/prefix");check(events.equals(memory.events),"native text-controls callback order/live state");
        }
        check(in.read()==-1,"native text-controls vector consumed exactly");return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" text-controls machine checks");}
    private static final String DATA="eNq9WC2MXkUUnbdd2J9WgMBBaEIClXfmzs8d16QVSBJaQYJg/m4qEJXIJkACCBRb0zYYBA4sqUGSYBAoFLICgahoYBM43/6Uj6/dvm7ytS+5me+9vJ0zd965555ZY8xLZu/d28aYTYw/YTyPcR/jObN3ZduY6aLZu/r24f3Va8a8973Z++6CMb/9aG59ed6YbYy37htz5ry59TXuX/nd3P4Bf/fyDXP7528Px/3rxrz1gbnz6WfGvH7Z3Pnml4P37ryBceMG5n7NXHrhPuIBYt9cetEgNhHb5qu/Lpibn99D/IH4E3Ef8QCxb25+YRCbiG1z95PLiCuI9xHXEB8iriM+QryDtb6KvO7hN5m7HwPjVw/cN/85uszhtXEUi2syx9dFGb5kdt55zSURBQm2FsbDwUql5Zgl2VC1OS81KHfm6qO6UXOtgY4negq0ab1oS7OfkFu00Y4kNo0eY2UnMlxXkaBRSZNGn7RycqTRkSsuDOdbI82WyA+tp0Cb1o525oC3J+fmxHYaPeeIH6GHKCo8OgVm4R44j5ai79RqtyW5XkqPkkNoQ23w5RRo0/rQcG3Nc7LYmAJbaVFDK1LVx1yBElsowYtL2YZWXXQuD+dk2Fxq6yxWVNMI6RRo01rQpoOAFhyhbSyhbs3zdORRo0gdXHL2w2XS4UILkpNToRAs915THFydxuwI78YSOWWmzs0u5zvP0zWgPcz3eGdX8p3h7hBp3Lpa6U4opeKtFykh1mxRDTkyFCBn29gOYo/HvTnrXJQYuq20ku8Md9eAtpzvmZV8d+b57Eu1gRx7jQWb2qJLpTXxKj4Nq8ly0m5br7kM253vpYqS70Ga7WzdUr4783xeA9qTvu/OPJ85qkLsOrgFlknWmCB5LXYduXCqkESuKUj3lUmZe+M6slgqSkP/x+edeT6vAW053+kx+c7wOXdsZLHZe6AopKIn28U37KzDLieUl7YeVEtLqYZShIt6ri05H/FsJd8ZPq8BbTnfzZV8d+f5rMmVYrnGgc4cS4bwK2frqsQ6bKHCo7nqrA19ZLXcvK8N5ZSCxXdKy3zenefzOtGeQouHrZhdxAYXPDUnEEZLKUYqY8QuwbXUsetYjQiIhp4wKgfvlRMK6hRo09rRZnhah8/N0kIHRZm8C3BcOrCIUIcbjbhgg0vUpq7aURol5kRVOmvhzKdAm9aKtnWEsKq900PEi95bTG59yOhfDIMJDNiRRqOILyWUFJoP1HqS4EOFP2swlDAxlXJAUzgJben580GbHkUD+Qkm2MMW19h7gT2hENOAIbYB5IephrGGmvGiMJIbyaNGameXHOqGH852cLo5QJqesILdR1fQKWWqDt/J9wbTEOCGuAstdAeF1iPysviGybeSO/yg9wTH0DN5y264lRWcW0J53ArOProC9snnlBtZgrzlRSFUtK4eXUwssZGkirNCcwWi5wS+RhSdHV+mQfIdL824M8+mBiAoClRGYyyY0JW4ENpRm/VWyMYGX5ggvClQqRAk5UFhVAww/35F0WfY9AzRpueKtvtc0c4+U7RVTh7G4dl6a3FmB3H+XlEFGoszEKsdgZIqrBcOhThKBA2hEKoVsudlNEbTis1zoBFYI2WsEQZ1pUo2VxjzuBVNB/9dOOzkG4/uR2ytRxQul+KFKsmQkGB/BK0ywtyiqUBZCK/Y0dSia9pIoeTmcSqKRR6i/IdwXDEn+YedpfvlzrDK/YVvalqbeu2eJSW/aNijQONw2qrFpVCcJddTIecCTltjcWDuDWdOZFJbdjN7cwLqGvbkRP/0L8vXfh8=";
}
