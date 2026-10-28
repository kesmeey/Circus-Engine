package org.circus.core;

import java.io.*;
import java.util.*;

/** Bounded cooperative implementation of the Unity CircusScript register/stack machine. */
public final class CircusScrVm {
    public interface Library {CircusScrScript script(String name)throws IOException;}
    public interface Host {
        boolean command(int command,Value[] args,CircusScrVm vm)throws IOException;
        int readFlag(int flag)throws IOException;
        void writeFlag(int flag,int value)throws IOException;
        default void afterTransfer(CircusScrVm vm)throws IOException{}
    }
    public enum Status {RUNNING,WAIT_CLICK,WAIT_TIME,WAIT_HOST,HALTED,FAULT}
    public static final class Value {
        public final boolean isString;public final int number;public final String string;
        public Value(int number){this.isString=false;this.number=number;this.string=null;}
        public Value(String string){if(string==null)throw new NullPointerException("string");this.isString=true;this.number=0;this.string=string;}
        /** CsData.Val remains zero for string arguments in the original managed runtime. */
        public int integer(){return number;}
        public String text()throws IOException{if(!isString)throw new IOException("Expected string CircusScr argument");return string;}
        @Override public String toString(){return isString?string:Integer.toString(number);}
    }
    private static final class Frame {
        CircusScrScript script;int pc,reg1,reg2,line;String source="";
        final ArrayList<Integer> expression=new ArrayList<Integer>();final ArrayList<Value> parameters=new ArrayList<Value>();
        Frame(CircusScrScript script){this.script=script;}
    }
    private final Library library;private final Host host;private Frame frame;
    private final ArrayList<Frame> calls=new ArrayList<Frame>();
    private Status status=Status.RUNNING;private String error="";private long remainingMillis,instructionsExecuted;
    public CircusScrVm(Library library,Host host,String entry)throws IOException{this.library=library;this.host=host;frame=new Frame(library.script(CircusScrScript.normalizeName(entry)));}
    public Status status(){return status;}public String error(){return error;}public String scriptName(){return frame.script.name;}public int offset(){return frame.pc;}public int line(){return frame.line;}public String sourceFile(){return frame.source;}
    public long remainingMillis(){return remainingMillis;}public long instructionsExecuted(){return instructionsExecuted;}public int callDepth(){return calls.size();}
    public int readFlag(int flag)throws IOException{return host.readFlag(flag);}public void writeFlag(int flag,int value)throws IOException{host.writeFlag(flag,value);}
    public void waitClick(){status=Status.WAIT_CLICK;}public void waitHost(){status=Status.WAIT_HOST;}public void waitTime(long ms){remainingMillis=Math.max(0,ms);status=remainingMillis==0?Status.RUNNING:Status.WAIT_TIME;}
    public void resume(){if(status==Status.WAIT_HOST||status==Status.WAIT_CLICK||status==Status.WAIT_TIME){status=Status.RUNNING;remainingMillis=0;}}
    public void advance(){if(status==Status.WAIT_CLICK)resume();}
    public void halt(){status=Status.HALTED;}
    public void elapse(long ms){if(status==Status.WAIT_TIME){remainingMillis=Math.max(0,remainingMillis-Math.max(0,ms));if(remainingMillis==0)status=Status.RUNNING;}}
    public void jump(String label)throws IOException{if(label==null||label.length()==0)return;frame.pc=frame.script.label(label);status=Status.RUNNING;remainingMillis=0;}
    public void changeScript(String name,String label,boolean quick,boolean forcePush)throws IOException{
        CircusScrScript next=library.script(CircusScrScript.normalizeName(name));int pc=label==null||label.length()==0?0:next.label(label);
        if(quick){if(calls.isEmpty()||forcePush){if(calls.size()>=256)throw new IOException("CircusScr call stack limit");calls.add(frame);}}else calls.clear();
        frame=new Frame(next);frame.pc=pc;status=Status.RUNNING;remainingMillis=0;
    }
    public void transfer(String name,boolean quick)throws IOException{changeScript(name,null,quick,false);}
    public void returnScript()throws IOException{if(calls.isEmpty())throw new IOException("CircusScr return without caller");frame=calls.remove(calls.size()-1);status=Status.RUNNING;remainingMillis=0;}
    public Status run(int budget){
        if(budget<1||budget>100000)throw new IllegalArgumentException("Instruction budget must be 1..100000");
        for(int n=0;n<budget&&status==Status.RUNNING;n++){
            int pc=frame.pc;String name=scriptName();
            try{if(pc==frame.script.instructions.length){status=Status.HALTED;break;}step();instructionsExecuted++;}
            catch(IOException|RuntimeException e){status=Status.FAULT;error=name+" @instruction "+pc+" (line "+frame.line+"): "+e.getMessage();}
        }return status;
    }
    private void step()throws IOException{
        if(frame.pc<0||frame.pc>=frame.script.instructions.length)throw new IOException("CircusScr instruction outside script");
        CircusScrScript.Instruction ins=frame.script.instructions[frame.pc++];int value=ins.value;
        switch(ins.opcode){
        case 0:frame.reg1=value;break;
        case 1:if(frame.expression.size()>=65536)throw new IOException("CircusScr expression stack limit");frame.expression.add(frame.reg1);break;
        case 2:if(frame.expression.isEmpty())throw new IOException("CircusScr expression stack underflow");frame.reg2=frame.expression.remove(frame.expression.size()-1);break;
        case 3:if(frame.parameters.size()>=65536)throw new IOException("CircusScr parameter stack limit");if(value==0)frame.parameters.add(new Value(frame.reg1));else{if(frame.reg1<0||frame.reg1>=frame.script.strings.length)throw new IOException("CircusScr string index out of bounds");frame.parameters.add(new Value(frame.script.strings[frame.reg1]));}break;
        case 4:function(ins);break;
        case 5:frame.reg1=readFlag(frame.reg1);break;
        case 6:writeFlag(value,frame.reg1);break;
        case 7:frame.reg1=frame.reg2+frame.reg1;break;
        case 8:frame.reg1=frame.reg2-frame.reg1;break;
        case 9:frame.reg1=frame.reg2*frame.reg1;break;
        case 10:if(frame.reg1==0)throw new IOException("CircusScr division by zero");if(frame.reg2==Integer.MIN_VALUE&&frame.reg1==-1)throw new IOException("CircusScr division overflow");frame.reg1=frame.reg2/frame.reg1;break;
        case 11:frame.reg1=frame.reg1!=0||frame.reg2!=0?1:0;break;
        case 12:frame.reg1=frame.reg1!=0&&frame.reg2!=0?1:0;break;
        case 13:frame.reg1|=frame.reg2;break;
        case 14:frame.reg1&=frame.reg2;break;
        case 15:frame.reg1=frame.reg2>=frame.reg1?1:0;break;
        case 16:frame.reg1=frame.reg2<=frame.reg1?1:0;break;
        case 17:frame.reg1=frame.reg2>frame.reg1?1:0;break;
        case 18:frame.reg1=frame.reg2<frame.reg1?1:0;break;
        case 19:frame.reg1=frame.reg1==frame.reg2?1:0;break;
        case 20:frame.reg1=frame.reg1!=frame.reg2?1:0;break;
        case 23:status=Status.HALTED;break;
        case 24:if(frame.reg1==0)frame.pc=frame.script.jumps[value];break;
        case 26:frame.line=value;break;
        case 27:frame.pc=frame.script.jumps[value];break;
        case 28:frame.source=frame.script.strings[value];break;
        default:throw new IOException("Unsupported CircusScr operator "+ins.opcode);
        }
    }
    private void function(CircusScrScript.Instruction ins)throws IOException{
        int argc=ins.argumentCount(),command=ins.command();if(frame.parameters.size()<argc)throw new IOException("CircusScr function parameter stack underflow");
        Value[] args=new Value[argc];for(int i=argc-1;i>=0;i--)args[i]=frame.parameters.remove(frame.parameters.size()-1);
        boolean handled;try{handled=host.command(command,args,this);}catch(IOException e){throw new IOException("CircusScr function "+command+" "+argumentSummary(args)+": "+e.getMessage(),e);}
        switch(command){
        case 37:require(args,1);if("advmenu".equals(args[0].text()))changeScript(args[0].text(),null,true,true);else throw new IOException("Unsupported CircusScr call "+args[0]);break;
        case 38:case 39:require(args,0);returnScript();if(command==38){if(readFlag((99<<16)+19)==4)jump("rnew");writeFlag((99<<16)+19,0);}break;
        case 59:status=Status.HALTED;break;
        case 74:require(args,2);writeFlag(args[0].integer(),args[1].integer());break;
        case 89:require(args,1);jump(args[0].text());break;
        case 134:case 148:require(args,1);changeScript(args[0].text(),null,command==134,false);break;
        case 135:case 149:require(args,2);changeScript(args[0].text(),args[1].text(),command==135,false);break;
        default:if(!handled)throw new IOException("Unimplemented CircusScr function "+command+" "+Arrays.toString(args));break;
        }
        if(command==37||command==38||command==39||command==134||command==135||command==148||command==149)host.afterTransfer(this);
    }
    private static String argumentSummary(Value[] args){StringBuilder out=new StringBuilder("[");for(int i=0;i<args.length;i++){if(i>0)out.append(", ");String value=args[i].toString();out.append(value.length()>120?value.substring(0,120)+"...":value);if(out.length()>1000){out.append("...");break;}}return out.append(']').toString();}
    private static void require(Value[] args,int count)throws IOException{if(args.length!=count)throw new IOException("CircusScr function requires "+count+" arguments, got "+args.length);}
    public void save(DataOutput out)throws IOException{
        out.writeInt(0x43535631);out.writeInt(status.ordinal());out.writeLong(remainingMillis);out.writeLong(instructionsExecuted);out.writeInt(calls.size());for(Frame f:calls)saveFrame(out,f);saveFrame(out,frame);
    }
    public void load(DataInput in)throws IOException{
        if(in.readInt()!=0x43535631)throw new IOException("Invalid CircusScr VM save");int s=in.readInt();if(s<0||s>=Status.values().length)throw new IOException("Invalid CircusScr status");long time=in.readLong(),executed=in.readLong();if(time<0||executed<0)throw new IOException("Invalid CircusScr counters");
        int count=bounded(in.readInt(),256);ArrayList<Frame> restored=new ArrayList<Frame>();for(int i=0;i<count;i++)restored.add(loadFrame(in));Frame current=loadFrame(in);
        calls.clear();calls.addAll(restored);frame=current;status=Status.values()[s];remainingMillis=time;instructionsExecuted=executed;error="";
    }
    private void saveFrame(DataOutput out,Frame f)throws IOException{
        out.writeUTF(f.script.name);out.writeInt(f.pc);out.writeInt(f.reg1);out.writeInt(f.reg2);out.writeInt(f.line);out.writeUTF(f.source);
        out.writeInt(f.expression.size());for(int v:f.expression)out.writeInt(v);out.writeInt(f.parameters.size());for(Value v:f.parameters){out.writeBoolean(v.isString);if(v.isString)out.writeUTF(v.string);else out.writeInt(v.number);}
    }
    private Frame loadFrame(DataInput in)throws IOException{
        Frame f=new Frame(library.script(CircusScrScript.normalizeName(in.readUTF())));f.pc=in.readInt();if(f.pc<0||f.pc>f.script.instructions.length)throw new IOException("Saved CircusScr position outside script");f.reg1=in.readInt();f.reg2=in.readInt();f.line=in.readInt();f.source=in.readUTF();
        int count=bounded(in.readInt(),65536);for(int i=0;i<count;i++)f.expression.add(in.readInt());count=bounded(in.readInt(),65536);for(int i=0;i<count;i++)f.parameters.add(in.readBoolean()?new Value(in.readUTF()):new Value(in.readInt()));return f;
    }
    private static int bounded(int n,int max)throws IOException{if(n<0||n>max)throw new IOException("Invalid CircusScr saved stack size");return n;}
}
