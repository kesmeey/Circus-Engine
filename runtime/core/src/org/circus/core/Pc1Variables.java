package org.circus.core;

import java.io.IOException;

/** CurrentCHS411910/411B10. All bytes are borrowed from the canonical bus;
 *this owns no settings/register mirror, catalog or random generator. */
public final class Pc1Variables {
    public static final int ORDINARY=0x9d91f4,BANK90=0x9d9e68,BANK97=0x454dac,BANK98=0x454fac,BANK100=0x454d48;
    public interface State {int read(int address)throws IOException;void write(int address,int value)throws IOException;}
    /** Exact external callees, reached only after their proved memory prefix.
     *Defaults are required-leaf faults, never empty successful backends. */
    public interface Leaves {
        default int[] localSystemTime()throws IOException{throw required("GetLocalTime /4118F0");}
        default int musicIdle41C820(int argument)throws IOException{throw required("41C820");}
        default int voiceIdle41CBF0(int argument)throws IOException{throw required("41CBF0");}
        default int soundIdle41C960(int argument)throws IOException{throw required("41C960");}
        default void refreshVolumes41CD00()throws IOException{throw required("41CD00");}
        default void requestMusic41C4B0(int track,int mode)throws IOException{throw required("41C4B0");}
        default void stopMusic41C650(int keepRequest)throws IOException{throw required("41C650");}
        default void armAudioTimer4070E0()throws IOException{throw required("4070E0");}
        /**Whole4027E0 must read its live source after its own actual file-open
         *prefix; do not submit an early immutable settings copy here. */
        default void writeSettings4027E0()throws IOException{throw required("4027E0");}
    }
    private static IOException required(String address){return new IOException("PC1 native variable required external leaf "+address);}
    private final State memory;private final Leaves leaves;private int depth;private Pc1NativeMemory nativeBus;
    public Pc1Variables(State memory,Leaves leaves)throws IOException{if(memory==null)throw new IOException("PC1 variables require canonical memory");this.memory=memory;this.leaves=leaves==null?new Leaves(){}:leaves;}
    public Pc1Variables(final Pc1NativeMemory memory,Leaves leaves)throws IOException{this(memory==null?null:new State(){public int read(int address)throws IOException{return memory.readAddress(address);}public void write(int address,int value)throws IOException{memory.writeAddress(address,value);}},leaves);nativeBus=memory;}
    public void requireBus(Pc1NativeMemory bus)throws IOException{if(bus==null||bus!=nativeBus)throw new IOException("PC1 variable consumer requires bound canonical native memory");}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 variable snapshot during native callback/local stack");}
    private int b(int address)throws IOException{return memory.read(address)&255;}
    private int d(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=b(address+n)<<(n*8);return value;}
    private void d(int address,int value)throws IOException{for(int n=0;n<4;n++)memory.write(address+n,value>>>(n*8));}
    private static int address99(int index){switch(index){
        case 0:return 0xbb8420;case 2:return 0xbb8428;case 3:return 0x43028c;case 4:return 0x94c61c;
        case 5:return 0x454d18;case 6:return 0x454d1c;case 7:return 0x454d04;case 8:return 0x454d0c;case 9:return 0x454d14;
        case 10:return 0x454ce8;case 11:return 0x454ce0;case 12:return 0x454cf0;case 13:return 0x454cdc;case 14:return 0x454cfc;
        case 15:return 0x454d08;case 16:return 0x454ce4;case 17:return 0x454cf8;case 18:return 0x454cec;case 20:return 0x454cf4;
        case 22:return 0x94c628;case 254:return 0x968a00;case 253:return 0x4305fc;case 252:return 0x43d41c;case 251:return 0x9da4a8;case 243:return 0x43d470;
        default:return 0;
    }}
    public int read(int bank,int index)throws IOException{
        depth++;try{
            if(bank==100)return b(BANK100+index);
            if(bank==99){int address=address99(index);if(address!=0)return d(address);switch(index){
                case 21:int[] time=leaves.localSystemTime();if(time==null||time.length!=8)throw new IOException("PC1 GetLocalTime requires eight actual WORD outputs");return time[4]&65535;
                case 250:return leaves.musicIdle41C820(0)!=0?1:0;
                case 249:return leaves.voiceIdle41CBF0(0)!=0?1:0;
                case 248:return leaves.soundIdle41C960(0)!=0?1:0;
                case 247:return d(0x94c624)==0?1:0;
                default:return 0;
            }}
            if(bank==98)return b(BANK98+index);if(bank==97)return b(BANK97+index);if(bank==90)return b(BANK90+index);
            //Both comparisons are SIGNED and have no native lower bound.
            return bank<10&&index<100?b(ORDINARY+bank*100+index):0;
        }finally{depth--;}
    }
    /**Void native API: ignored EAX byproducts are not invented success values. */
    public void write(int bank,int index,int value)throws IOException{
        depth++;try{
            if(bank==100){memory.write(BANK100+index,value&255);leaves.writeSettings4027E0();return;}
            if(bank==99){switch(index){
                case 0:d(0xbb8420,value);return;
                case 5:case 6:case 8:case 9:case 10:case 11:case 12:case 13:case 15:case 16:case 17:case 18:case 20:d(address99(index),value);return;
                case 7:d(0x454d04,value);leaves.refreshVolumes41CD00();return;
                case 14:
                    d(0x454cfc,value);
                    if(value==0){leaves.stopMusic41C650(0);leaves.armAudioTimer4070E0();return;}
                    int track=d(0x9d8a0c);if(track==-1)return;int mode=d(0xbb8438);leaves.requestMusic41C4B0(track,mode);leaves.armAudioTimer4070E0();return;
                case 19:d(0x9b9f1c,value);return;
                case 254:d(0x968a00,value);return;
                case 243:d(0x43d470,value==255?-1:value);return;
                default:return;
            }}
            if(bank==98){memory.write(BANK98+index,value&255);return;}if(bank==97){memory.write(BANK97+index,value&255);return;}if(bank==90){memory.write(BANK90+index,value&255);return;}
            if(bank<10&&index<100)memory.write(ORDINARY+bank*100+index,value<0?0:value>255?255:value);
        }finally{depth--;}
    }
}
