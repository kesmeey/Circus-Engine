package org.circus.core;
import java.io.*;import java.util.IdentityHashMap;

/** Current CHS4125B0 control loop, routing the LIVE412654 target, not an opcode
 *catalog. Only machine-verified handler targets are internal to this owner. */
public final class Pc1RawDispatch {
 public static final int TABLE=0x415754,TAIL=0x41570f;
 public interface State extends Pc1Fetch.State{
  default int readInt32(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=(read(address+n)&255)<<(8*n);return value;}
  default void writeInt32(int address,int value)throws IOException{for(int n=0;n<4;n++)write(address+n,value>>>(8*n));}
 }
 public interface VariableAccess{int read(int bank,int index)throws IOException;void write(int bank,int index,int value)throws IOException;}
 public enum Flow{COMMON_TAIL,RETURN_ZERO_WITHOUT_TAIL,RETURN_ONE_WITHOUT_TAIL}
 public interface Leaves{
  default void releaseGraphics411080(int selector,int mode)throws IOException{throw required(0x411080);}
  default void error411050(int rawMessage)throws IOException{throw required(0x411050);}
  default int transition41A0B0(int handle,int argument)throws IOException{throw required(0x41a0b0);}
  /** Required for every actual target outside the internal proved subset,
   *including a mutated table pointing between known handler entrypoints. */
  default Flow handler(int target,Registers registers,Pc1Fetch.Frame frame)throws IOException{throw required(target);}
 }
 private static IOException required(int address){return new IOException("PC1 raw dispatch requires native target0x"+Integer.toHexString(address));}
 /** Cached register values at412647; local arguments preserve byte knowledge
  *without requiring unused unknown native stack values to become Java ints. */
 public static final class Registers{
  public final int opcode,guardOpcode,sourcePc,codeCursor,device,arg0,arg1;private final Pc1Fetch.Frame frame;private final Pc1Fetch.Dispatch dispatch;
  private Registers(Pc1Fetch.Frame frame,Pc1Fetch.Dispatch d)throws IOException{this.frame=frame;dispatch=d;opcode=d.signedOpcode;guardOpcode=d.guardOpcode;sourcePc=d.sourcePc;codeCursor=d.cachedCodeCursor();device=d.device;arg0=d.argument(0);arg1=d.argument(1);}
  public int argument2()throws IOException{return dispatch.cachedArgument(2);}public int argument3()throws IOException{return dispatch.cachedArgument(3);}
  public int argumentByte(int index,int byteIndex)throws IOException{return dispatch.cachedArgumentByte(index,byteIndex);}
  public Pc1GlyphCache.Source source(){return frame.source();}
 }
 public static final class Result{
  public final int target;public final Registers registers;public final boolean loop;private final int result;
  private Result(int target,Registers registers,boolean loop,int result){this.target=target;this.registers=registers;this.loop=loop;this.result=result;}
  public int nativeResult()throws IOException{if(loop)throw new IOException("PC1 native invocation has not returned");return result;}
 }
 private final State memory;private final Pc1Fetch fetch;private final VariableAccess variables;private final Leaves leaves;private Pc1NativeMemory nativeBus;private int depth;private final IdentityHashMap<Pc1Fetch.Frame,Pc1Fetch.Dispatch> pending=new IdentityHashMap<>();
 public Pc1RawDispatch(State memory,VariableAccess variables,Leaves leaves)throws IOException{if(memory==null)throw new IOException("PC1 raw dispatcher requires canonical live bus");this.memory=memory;fetch=new Pc1Fetch(memory);this.variables=variables;this.leaves=leaves==null?new Leaves(){}:leaves;}
 public Pc1RawDispatch(final Pc1NativeMemory bus,final Pc1Variables variables,Leaves leaves)throws IOException{this(bus==null?null:new State(){public int read(int at)throws IOException{return bus.readAddress(at);}public void write(int at,int value)throws IOException{bus.writeAddress(at,value);}public int readInt32(int at)throws IOException{return bus.readInt32(at);}public void writeInt32(int at,int value)throws IOException{bus.writeInt32(at,value);}},variables==null?null:new VariableAccess(){public int read(int bank,int index)throws IOException{return variables.read(bank,index);}public void write(int bank,int index,int value)throws IOException{variables.write(bank,index,value);}},leaves);if(variables!=null)variables.requireBus(bus);nativeBus=bus;}
 public void requireBus(Pc1NativeMemory bus)throws IOException{if(bus==null||bus!=nativeBus)throw new IOException("PC1 raw dispatcher requires same canonical bus");}
 public void requireBoundary()throws IOException{if(depth!=0||!pending.isEmpty())throw new IOException("PC1 raw dispatcher is inside an unresolved native fetch/handler continuation");}
 /** False is the real WAIT14 return0; it must not publish a fetched opcode. */
 public boolean begin(Pc1Fetch.Frame frame)throws IOException{if(frame==null||frame.active()||pending.containsKey(frame))throw new IOException("PC1 raw dispatcher entry frame is unresolved");pending.put(frame,null);boolean begun=fetch.enter(frame);pending.remove(frame);return begun;}
 public Pc1Fetch.Dispatch next(Pc1Fetch.Frame frame)throws IOException{if(frame==null||!frame.atNextBoundary()||pending.containsKey(frame))throw new IOException("PC1 raw dispatcher next frame is unresolved");pending.put(frame,null);Pc1Fetch.Dispatch dispatch=fetch.next(frame);pending.put(frame,dispatch);return dispatch;}
 public Result step(Pc1Fetch.Frame frame)throws IOException{return execute(frame,next(frame));}
 public Result execute(Pc1Fetch.Frame frame,Pc1Fetch.Dispatch dispatch)throws IOException{
  if(frame==null||dispatch==null||!frame.atDispatch()||!dispatch.belongsTo(frame)||pending.get(frame)!=dispatch)throw new IOException("PC1 raw dispatcher requires its current fetched frame");depth++;
  try{Registers regs=new Registers(frame,dispatch);int target=fetch.jumpTarget(dispatch);Flow flow=handler(target,regs,frame);Result result;if(flow==null)throw new IOException("PC1 native handler did not provide its actual continuation");if(flow==Flow.RETURN_ONE_WITHOUT_TAIL){fetch.interruptedReturn(frame);result=new Result(target,regs,false,1);}else if(flow==Flow.RETURN_ZERO_WITHOUT_TAIL){fetch.directReturnZero(frame);result=new Result(target,regs,false,0);}else{boolean loop=fetch.commonTail(frame);result=new Result(target,regs,loop,0);}pending.remove(frame);return result;}finally{depth--;}
 }
 private int g(int at)throws IOException{return memory.readInt32(at);}private void g(int at,int value)throws IOException{memory.writeInt32(at,value);}
 private int word(int at)throws IOException{return(memory.read(at)&255)|(memory.read(at+1)&255)<<8;}
 private void word(int at,int value)throws IOException{memory.write(at,value);memory.write(at+1,value>>>8);}
 private int readVariable(int bank,int index)throws IOException{if(variables==null)throw required(0x411910);return variables.read(bank,index);}
 private void writeVariable(int bank,int index,int value)throws IOException{if(variables==null)throw required(0x411b10);variables.write(bank,index,value);}
 /**4115D0's first buffer argument is truly unused. Marker bytes are signed,
  *but the caller's depth DWORD is compared as supplied without normalization.*/
 private void scanBlock(int cursor,int depth)throws IOException{
  for(;;){int opcode=(byte)memory.read(Pc1Fetch.CODE_BASE+cursor);if(opcode<0x2d){if(opcode!=0)cursor+=3;if(opcode==0x1f&&(byte)memory.read(Pc1Fetch.CODE_BASE+cursor-1)==depth){g(Pc1Fetch.PC,cursor);return;}}else if(opcode<0x4e){int length=0;while((memory.read(Pc1Fetch.CODE_BASE+cursor+1+length)&255)!=0)length++;cursor+=length+2;}else cursor+=9;if(opcode==0){leaves.error411050(0x43d8e4);g(Pc1Fetch.WAIT,1);return;}}
 }
 private Flow handler(int target,Registers r,Pc1Fetch.Frame f)throws IOException{
  switch(target){
   case TAIL:return Flow.COMMON_TAIL;
   case 0x41265b:leaves.releaseGraphics411080(-1,0);g(Pc1Fetch.WAIT,0);break;
   case 0x412817:g(Pc1Fetch.WAIT,14);break;
   case 0x412885:for(int n=0;n<250;n++)g(Pc1Variables.ORDINARY+n*4,0);break;
   case 0x41343c:{int value=g(0x9553e8);g(0xbb843c,value);g(0xbb8440,value);break;}
   case 0x41354e:g(0x430608,r.arg0);g(0x430604,r.arg1);break;
   case 0x412fe0:g(0xbb8444,128);break;
   case 0x412d6e:g(0xbb8430,1);break;
   case 0x413c61:if(r.arg0==0)scanBlock(r.codeCursor,r.arg1);break;
   case 0x415395:{int value=r.arg0+(r.arg1<<8);g(Pc1Fetch.ARGUMENT0,value);g(0x9d8a18,value);break;}
   case 0x415341:{int count=g(0x9d8a1c),value=r.arg0+(r.arg1<<8);g(Pc1Fetch.ARGUMENT0,value);int at=0x9d8a24+count*2;if(word(at)==0x8000)word(at,value|0x8000);else word(at,word(Pc1Fetch.ARGUMENT0));int next=g(0x9d8a1c)+1;g(0x9d8a18,g(Pc1Fetch.ARGUMENT0));g(0x9d8a1c,next);break;}
   case 0x4139f6:{int mode=r.argument3();if(mode==0)writeVariable(r.arg0,r.arg1,r.argument2());else if(mode==1){int old=readVariable(r.arg0,r.arg1),index=g(Pc1Fetch.ARGUMENT1),bank=g(Pc1Fetch.ARGUMENT0);writeVariable(bank,index,old+r.argument2());}else if(mode==2){int old=readVariable(r.arg0,r.arg1),bank=g(Pc1Fetch.ARGUMENT0),value=old-r.argument2(),index=g(Pc1Fetch.ARGUMENT1);writeVariable(bank,index,value);}break;}
   case 0x413a61:{int value=readVariable(r.argument2(),r.argument3()),index=g(Pc1Fetch.ARGUMENT1),bank=g(Pc1Fetch.ARGUMENT0);writeVariable(bank,index,value);break;}
   case 0x413a83:{int opcode=(byte)memory.read(Pc1Fetch.CODE_BASE+r.codeCursor),sum=0;f.setArgument3(0);f.setArgument2(opcode);int bank=r.arg0,index=r.arg1;if(opcode!=2){do{int first=fetch.signedWord(Pc1Fetch.CODE_BASE+g(Pc1Fetch.PC)+1),second=fetch.signedWord(Pc1Fetch.CODE_BASE+g(Pc1Fetch.PC)+3);g(Pc1Fetch.PC,g(Pc1Fetch.PC)+9);sum+=readVariable(first,second);int pc=g(Pc1Fetch.PC);f.setArgument3(sum);opcode=(byte)memory.read(Pc1Fetch.CODE_BASE+pc);f.setArgument2(opcode);}while(opcode!=2);bank=g(Pc1Fetch.ARGUMENT0);index=g(Pc1Fetch.ARGUMENT1);}writeVariable(bank,index,sum);break;}
   case 0x41416d:{int x=r.arg0,y=r.arg1;if(r.argument2()!=0){x+=g(0xbb8460);y+=g(0xbb8464);}g(0xbb8460,x);g(0xbb8464,y);break;}
   case 0x4142f6:if(r.argument2()!=0){g(0x9d96bc,r.arg0);g(0x9d96c0,r.arg1);}else{int visible=g(0xbb844c);g(Pc1Fetch.WAIT,0x5e);g(0x9cca8c,r.argument3());g(0x9da4c8,0);if(visible!=0&&leaves.transition41A0B0(g(0x43d418),1)!=0)return Flow.RETURN_ONE_WITHOUT_TAIL;}break;
   case 0x413b97:{int left=readVariable(r.arg0,r.arg1);g(Pc1Fetch.ARGUMENT0,left);int low=r.argumentByte(3,0),mode=(low&128)!=0?low&127:r.argument3(),right=0;if((low&128)!=0){right=r.argument2();right=readVariable(right>>8,right&255);left=g(Pc1Fetch.ARGUMENT0);f.setArgument2(right);}else if(mode>=0&&mode<=5)right=r.argument2();int pass=left;switch(mode){case 0:pass=left==right?1:0;break;case 1:pass=left!=right?1:0;break;case 2:pass=left>right?1:0;break;case 3:pass=left<right?1:0;break;case 4:pass=left>=right?1:0;break;case 5:pass=left<=right?1:0;break;default:break;}if(mode>=0&&mode<=5)g(Pc1Fetch.ARGUMENT0,pass);int pc=g(Pc1Fetch.PC),signedDepth=(byte)memory.read(Pc1Fetch.CODE_BASE+pc+1);f.setArgument3(signedDepth);pc+=3;g(Pc1Fetch.PC,pc);if(pass==0)scanBlock(pc,signedDepth);break;}
   default:return leaves.handler(target,r,f);
  }
  return Flow.COMMON_TAIL;
 }
}
