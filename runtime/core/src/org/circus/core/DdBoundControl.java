package org.circus.core;

import java.io.*;

/** DD ff119f... 412F70/416840..416BB5. Pure binding arithmetic, no input/audio owners. */
public strictfp final class DdBoundControl {
    private DdBoundControl(){}
    public static final class Binding {
        public final int left,right,extraRight,count,encoded,leftOffset,stepBits;
        public final boolean musicSeek;
        /** Native state5 args0..4: left, right, detents, bank, index. */
        public Binding(int left,int right,int count,int encoded)throws IOException{
            this(left,right,right,count,encoded,0,step(left,right,count),false);
        }
        /** 417530/4175F0 memset of an unconfigured builder uses positive zero bits. */
        public static Binding zero(){return new Binding(0,0,0,0,0,0,0,false);}
        private Binding(int left,int right,int extraRight,int count,int encoded,int leftOffset,int stepBits,boolean musicSeek){this.left=left;this.right=right;this.extraRight=extraRight;this.count=count;this.encoded=encoded;this.leftOffset=leftOffset;this.stepBits=stepBits;this.musicSeek=musicSeek;}
        private static int step(int left,int right,int count)throws IOException{
            // 42B56E's producer uses signed16 script words. FILD/FIDIV retain exact
            // integer operands; FSTP32 rounds only the quotient, not those operands.
            if(left<Short.MIN_VALUE||left>Short.MAX_VALUE||right<Short.MIN_VALUE||right>Short.MAX_VALUE||count<Short.MIN_VALUE||count>Short.MAX_VALUE)throw new IOException("DD bound configuration requires native signed16 script operands");
            if(right==left&&count==1)return 0xffc00000; // masked x87 invalid's negative indefinite.
            return Float.floatToRawIntBits((float)((double)(right-left)/(double)(count-1)));
        }
        public int bank(){return encoded/1000;}
        public int index(){return encoded%1000;}
        public float step(){return Float.intBitsToFloat(stepBits);}
        public Binding withOffset(int offset){return new Binding(left,right,extraRight,count,encoded,offset,stepBits,musicSeek);}
        public Binding withMusicSeek(boolean enabled){return new Binding(left,right,extraRight,count,encoded,leftOffset,stepBits,enabled);}
        public void save(DataOutput out)throws IOException{out.writeInt(0x44444231);for(int value:new int[]{left,right,extraRight,count,encoded,leftOffset,stepBits})out.writeInt(value);out.writeBoolean(musicSeek);}
        public static Binding load(DataInput in)throws IOException{
            if(in.readInt()!=0x44444231)throw new IOException("DD bound state version");int left=in.readInt(),right=in.readInt(),extra=in.readInt(),count=in.readInt(),encoded=in.readInt(),offset=in.readInt(),bits=in.readInt();boolean seek=in.readBoolean();
            // No setter other than412F70 changes the float; memset gives the one
            // positive-zero exception to the configured negative-zero quotient.
            int expected=step(left,right,count);boolean zero=left==0&&right==0&&extra==0&&count==0&&encoded==0;
            if(bits!=expected&&!(zero&&bits==0))throw new IOException("DD bound float bits do not match native configuration");
            return new Binding(left,right,extra,count,encoded,offset,bits,seek);
        }
    }
    private static int integer(double value)throws IOException{
        // 4550D0 stores f64 then CVTTSD2SI on its SSE2 branch. Exceptional and
        // out-of-range conversion differs from the other CRT CPU path: do not guess.
        if(Double.isNaN(value)||Double.isInfinite(value)||value<=-2147483649.0||value>=2147483648.0)throw new IOException("Unsupported DD bound native4550D0 nonfinite/out-of-range conversion");
        return (int)value;
    }
    private static double coordinate(Binding binding,int value,int origin){int last=binding.count-1;return (double)binding.step()*(value<last?value:last)+origin;}
    /** Native thumb100/101 and active fill states>104; visibility belongs to415C60's caller.
     * The common-frame owner must retain the returned position/crop: idle99 skips a
     * fill update and keeps its last crop, rather than restoring its builder geometry.
     * UI+394 presentation origin is applied after this native local position. */
    public static Dc3Scene.Quad resolveQuad(Binding binding,Dc3Ui.Sprite sprite,int value,int buttonState)throws IOException{
        Dc3Scene.Quad result=sprite.quad.copy();
        if(sprite.state==100||sprite.state==101)result.x=integer(coordinate(binding,value,binding.left))-result.width/2;
        else if(sprite.state>104&&buttonState!=99)result.width=integer(coordinate(binding,value,result.sx))-result.sx;
        return result;
    }
    /** Input5/6 only skip an exact endpoint; invalid values are not silently clamped. */
    public static Integer adjust(Binding binding,int value,int direction){
        if(direction!= -1&&direction!=1)throw new IllegalArgumentException("DD bound direction must be -1 or1");
        return direction<0?(value==0?null:Integer.valueOf(value-1)):(value==binding.count-1?null:Integer.valueOf(value+1));
    }
    /** Native press2/input7-up path. null means the strict tail threshold performs no write. */
    public static Integer drag(Binding binding,int mouseX)throws IOException{
        if(mouseX<=binding.left+binding.leftOffset)return 0;
        if(mouseX>=binding.right)return binding.count-1;
        if(!((double)mouseX<(double)(binding.count-1)*binding.step()+binding.left+binding.leftOffset))return null;
        // The original division subtracts left, deliberately not leftOffset.
        return integer((double)(mouseX-binding.left)/binding.step());
    }
}
