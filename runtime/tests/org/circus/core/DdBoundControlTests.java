package org.circus.core;
import java.io.*;
import java.util.*;
/** Reference values come from current DD instruction interpretation, including x87, not Java formulas. */
public final class DdBoundControlTests {
 private static int count;private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
 private static final int[][] CONFIG={
{100,410,31,99007,0x41255555},
{0,101,4,10003,0x4206aaab},
{-100,100,7,96009,0x42055555},
{400,100,8,99009,0xc22b6db7},
{12,13,32767,99009,0x38000200},
{32760,-32760,30001,99009,0xc00bc6a8},
{0,0,1,99009,0xffc00000},
{0,1,1,99009,0x7f800000},
{0,0,0,0,0x80000000}
};
 private static final int[][] GOLDEN={
{0,0,-1,0,89},
{0,0,0,0,100},
{0,0,1,0,110},
{0,0,2,0,120},
{0,0,29,0,399},
{0,0,30,0,409},
{0,0,31,0,409},
{0,0,51,0,409},
{0,1,-1,17,6},
{0,1,0,17,17},
{0,1,1,17,27},
{0,1,2,17,37},
{0,1,29,17,316},
{0,1,30,17,326},
{0,1,31,17,326},
{0,1,51,17,326},
{0,2,99,0,0},
{0,2,100,0,0},
{0,2,101,0,0},
{0,2,255,0,15},
{0,2,409,0,29},
{0,2,410,0,30},
{0,2,411,0,30},
{0,2,99,9,0},
{0,2,100,9,0},
{0,2,101,9,0},
{0,2,109,9,0},
{0,2,110,9,0},
{0,2,255,9,15},
{0,2,409,9,29},
{0,2,410,9,30},
{0,2,411,9,30},
{1,0,-1,0,-33},
{1,0,0,0,0},
{1,0,1,0,33},
{1,0,2,0,67},
{1,0,3,0,101},
{1,0,4,0,101},
{1,0,24,0,101},
{1,1,-1,17,-16},
{1,1,0,17,17},
{1,1,1,17,50},
{1,1,2,17,84},
{1,1,3,17,118},
{1,1,4,17,118},
{1,1,24,17,118},
{1,2,-1,0,0},
{1,2,0,0,0},
{1,2,1,0,0},
{1,2,50,0,1},
{1,2,100,0,2},
{1,2,101,0,3},
{1,2,102,0,3},
{1,2,-1,9,0},
{1,2,0,9,0},
{1,2,1,9,0},
{1,2,9,9,0},
{1,2,10,9,0},
{1,2,50,9,1},
{1,2,100,9,2},
{1,2,101,9,3},
{1,2,102,9,3},
{2,0,-1,0,-133},
{2,0,0,0,-100},
{2,0,1,0,-66},
{2,0,2,0,-33},
{2,0,5,0,66},
{2,0,6,0,99},
{2,0,7,0,99},
{2,0,27,0,99},
{2,1,-1,17,-16},
{2,1,0,17,17},
{2,1,1,17,50},
{2,1,2,17,83},
{2,1,5,17,183},
{2,1,6,17,216},
{2,1,7,17,216},
{2,1,27,17,216},
{2,2,-101,0,0},
{2,2,-100,0,0},
{2,2,-99,0,0},
{2,2,0,0,3},
{2,2,99,0,5},
{2,2,100,0,6},
{2,2,101,0,6},
{2,2,-101,9,0},
{2,2,-100,9,0},
{2,2,-99,9,0},
{2,2,-91,9,0},
{2,2,-90,9,0},
{2,2,0,9,3},
{2,2,99,9,5},
{2,2,100,9,6},
{2,2,101,9,6},
{3,0,-1,0,442},
{3,0,0,0,400},
{3,0,1,0,357},
{3,0,2,0,314},
{3,0,6,0,142},
{3,0,7,0,99},
{3,0,8,0,99},
{3,0,28,0,99},
{3,1,-1,17,59},
{3,1,0,17,17},
{3,1,1,17,-25},
{3,1,2,17,-68},
{3,1,6,17,-240},
{3,1,7,17,-283},
{3,1,8,17,-283},
{3,1,28,17,-283},
{4,0,-1,0,11},
{4,0,0,0,12},
{4,0,1,0,12},
{4,0,2,0,12},
{4,0,32765,0,12},
{4,0,32766,0,12},
{4,0,32767,0,12},
{4,0,32787,0,12},
{4,1,-1,17,16},
{4,1,0,17,17},
{4,1,1,17,17},
{4,1,2,17,17},
{4,1,32765,17,17},
{4,1,32766,17,17},
{4,1,32767,17,17},
{4,1,32787,17,17},
{4,2,11,0,0},
{4,2,12,0,0},
{4,2,13,0,32766},
{4,2,14,0,32766},
{4,2,11,9,0},
{4,2,12,9,0},
{4,2,13,9,0},
{4,2,14,9,0},
{4,2,21,9,0},
{4,2,22,9,32766},
{5,0,-1,0,32762},
{5,0,0,0,32760},
{5,0,1,0,32757},
{5,0,2,0,32755},
{5,0,29999,0,-32757},
{5,0,30000,0,-32760},
{5,0,30001,0,-32760},
{5,0,30021,0,-32760},
{5,1,-1,17,19},
{5,1,0,17,17},
{5,1,1,17,14},
{5,1,2,17,12},
{5,1,29999,17,-65500},
{5,1,30000,17,-65503},
{5,1,30001,17,-65503},
{5,1,30021,17,-65503}
};
 private static Dc3Ui.Sprite sprite(int state){Dc3Ui.Sprite result=new Dc3Ui.Sprite();result.state=state;result.quad=new Dc3Scene.Quad();result.quad.resource="atlas";result.quad.x=77;result.quad.y=91;result.quad.sx=17;result.quad.sy=23;result.quad.width=31;result.quad.height=19;return result;}
 private static byte[] wire(DdBoundControl.Binding value)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();value.save(new DataOutputStream(out));return out.toByteArray();}
 public static int run()throws Exception{
  count=0;DdBoundControl.Binding[] bindings=new DdBoundControl.Binding[CONFIG.length];
  for(int i=0;i<CONFIG.length;i++){int[] c=CONFIG[i];bindings[i]=new DdBoundControl.Binding(c[0],c[1],c[2],c[3]);check(bindings[i].stepBits==c[4],"native FILD/FIDIV/FSTP32 configuration bits including invalid and negativezero setup="+i);}
  for(int[] row:GOLDEN){DdBoundControl.Binding b=bindings[row[0]];int actual;
   if(row[1]==0){Dc3Ui.Sprite s=sprite(100);Dc3Scene.Quad q=DdBoundControl.resolveQuad(b,s,row[2],99);actual=q.x+s.quad.width/2;check(q.y==91&&q.sx==17&&q.width==31&&s.quad.x==77,"thumb changes only a copied native x position");}
   else if(row[1]==1){Dc3Ui.Sprite s=sprite(105);Dc3Scene.Quad q=DdBoundControl.resolveQuad(b,s,row[2],1);actual=q.sx+q.width;check(q.x==77&&q.y==91&&q.sy==23&&s.quad.width==31,"active fill changes only a copied crop right edge");}
   else{Integer value=DdBoundControl.drag(b.withOffset(row[3]),row[2]);actual=value==null?Integer.MIN_VALUE:value;}
   check(actual==row[4],"current DD x87 instruction golden mode="+row[1]+" setup="+row[0]+" value="+row[2]+" expected="+row[4]+" actual="+actual);
  }
  DdBoundControl.Binding b=bindings[0];check(DdBoundControl.resolveQuad(b,sprite(100),30,99).x==394,"float32 step retains native center409 at the nominal410 endpoint");
  check(DdBoundControl.resolveQuad(b,sprite(101),30,2).x==394,"active thumb101 shares the exact center formula");
  check(DdBoundControl.resolveQuad(b,sprite(105),30,99).width==31,"idle99 does not update a bound fill crop");
  check(DdBoundControl.resolveQuad(b,sprite(104),30,2).width==31,"state104 has its own native branch and is not treated as a bound fill");
  check(DdBoundControl.adjust(b,0,-1)==null&&DdBoundControl.adjust(b,30,1)==null,"native keyboard endpoints suppress writes only on exact equality");
  check(DdBoundControl.adjust(b,-1,-1)==-2&&DdBoundControl.adjust(b,31,1)==32,"native keyboard arithmetic does not clamp out-of-range variable values");
  check(DdBoundControl.adjust(b,Integer.MIN_VALUE,-1)==Integer.MAX_VALUE,"native adjustment retains signed32 wrap before the owning variable setter");
  DdBoundControl.Binding copied=b.withOffset(9).withMusicSeek(true);byte[] saved=wire(copied);DdBoundControl.Binding loaded=DdBoundControl.Binding.load(new DataInputStream(new ByteArrayInputStream(saved)));check(Arrays.equals(saved,wire(loaded))&&loaded.leftOffset==9&&loaded.musicSeek&&loaded.extraRight==410&&loaded.bank()==99&&loaded.index()==7,"typed saved fields and exact float bits roundtrip independently of input/audio owners");
  DdBoundControl.Binding zero=DdBoundControl.Binding.zero();check(zero.stepBits==0&&new DdBoundControl.Binding(0,0,0,0).stepBits==0x80000000,"native memset zero differs from state5 configured negativezero");
  check(Arrays.equals(wire(zero),wire(DdBoundControl.Binding.load(new DataInputStream(new ByteArrayInputStream(wire(zero)))))),"unconfigured native binding is explicitly serializable without manufacturing count1");
  check(DdBoundControl.resolveQuad(zero,sprite(100),0,99).x==-15&&DdBoundControl.drag(zero,1)==-1,"native unconfigured modal clone uses actual zero fields with their original quirks");
  Dc3Ui.Sprite retained=sprite(105);retained.quad=DdBoundControl.resolveQuad(zero,retained,0,1);check(retained.quad.width==0,"a zero-config active fill changes the native owned crop to zero width");check(DdBoundControl.resolveQuad(zero,retained,0,99).width==0,"the owning common-frame retains its last crop when an idle frame skips the native update");
  byte[] damaged=saved.clone();damaged[28]=0x3f;damaged[29]=(byte)0x80;damaged[30]=damaged[31]=0;boolean rejected=false;try{DdBoundControl.Binding.load(new DataInputStream(new ByteArrayInputStream(damaged)));}catch(IOException expected){rejected=expected.getMessage().contains("float bits");}check(rejected,"saved state cannot inject a nonnative float step inconsistent with its configuration");
  boolean invalid=false;try{DdBoundControl.resolveQuad(bindings[6],sprite(100),0,99);}catch(IOException expected){invalid=expected.getMessage().contains("4550D0");}check(invalid,"nonfinite native CRT conversion is explicit unsupported rather than a fabricated zero");
  DdBoundControl.Binding signed=new DdBoundControl.Binding(0,10,2,-1001);check(signed.bank()==-1&&signed.index()==-1,"encoded variable decomposition follows native signed IDIV rather than unsigned bank extraction");
  return count;
 }
 public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" DD bound-control assertions");}
}
