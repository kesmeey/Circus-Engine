package org.circus.core;
import java.io.*;
/** Portable provenance of DC2's borrowed9FE0E0 frame. No fabricated raw8004 image.
 * BE8B94 is independent of PC1's candidate owner. The current admitted corpus
 * never executes native1A; normal loader resets its zero flag,50 leaves it alone. */
public final class Dc2BorrowedState {
 private static final int FORMAT=0x44324231;
 int flag,kind,sourceOffset,pc;long serial;String script="",sha="";
 private transient int pending;
 public static final class SaveMetadata {
  public final int year,month,day,hour,minute;
  public SaveMetadata(int y,int m,int d,int h,int min)throws IOException{if(y<0||y>65535||m<1||m>12||d<1||d>31||h<0||h>23||min<0||min>59)throw new IOException("DC2 local save time");year=y;month=m;day=d;hour=h;minute=min;}
 }
 void produce(MesScript code,int source,int next,int producer)throws IOException{
  if(code==null||!code.profile.id.equals("dc2-3363")||!code.executableOffset(next)||producer<1||producer>4||serial==Long.MAX_VALUE)throw new IOException("DC2 borrowed producer identity");
  kind=producer;sourceOffset=source;pc=next;script=code.name;sha=code.sha256;serial++;pending=producer;
 }
 int pending(){return pending;}void captured(){pending=0;}
 void loaded(int mode){if(mode==1||mode==3)flag=0;}
 boolean matches(Dc2BorrowedState other){return other!=null&&serial==other.serial&&kind==other.kind&&sourceOffset==other.sourceOffset&&pc==other.pc&&script.equals(other.script)&&sha.equals(other.sha);}
 public int producerKind(){return kind;}public int producerOffset(){return sourceOffset;}public int continuation(){return pc;}public String script(){return script;}public long generation(){return serial;}
 public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(flag);out.writeInt(kind);out.writeInt(sourceOffset);out.writeInt(pc);out.writeLong(serial);out.writeUTF(script);out.writeUTF(sha);}
 public void load(DataInput in)throws IOException{
  if(in.readInt()!=FORMAT)throw new IOException("DC2 borrowed provenance version");int f=in.readInt(),k=in.readInt(),src=in.readInt(),next=in.readInt();long gen=in.readLong();String name=in.readUTF(),digest=in.readUTF();
  if(f!=0||k<0||k>4||gen<0||k==0&&(gen!=0||src!=0||next!=0||!name.isEmpty()||!digest.isEmpty())||k!=0&&(gen==0||next<0||src< -1||!digest.matches("[0-9a-f]{64}")))throw new IOException("DC2 borrowed provenance state");if(k!=0)Binary.safePath(name);
  flag=f;kind=k;sourceOffset=src;pc=next;serial=gen;script=name;sha=digest;pending=0;
 }
 void reset(){flag=kind=sourceOffset=pc=pending=0;serial=0;script=sha="";}
}
