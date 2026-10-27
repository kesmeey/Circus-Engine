package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reference platform state for the explicitly separate, original demo profile. */
public final class DemoHost implements MesVm.Host {
    public static final class Choice {
        public final int[] rect;public final byte[] action;
        Choice(int[] r,byte[] a){rect=Arrays.copyOf(r,4);action=a.clone();}
        public String caption(){return new String(action,StandardCharsets.UTF_8);}
    }
    public String scene="",text="",audio="";public int audioGeneration,selected=-1;
    public final List<String> backlog=new ArrayList<>();public final List<Choice> choices=new ArrayList<>();
    public int read(int b,int i)throws IOException{if(b==99&&i==245)return selected;throw new IOException("Demo has no special variable "+b+":"+i);}
    public void write(int b,int i,int v)throws IOException{if(b==99&&i==245){selected=v==255?-1:v;return;}throw new IOException("Demo has no special variable "+b+":"+i);}
    public boolean allowNamedJump(){return true;}
    public boolean beforeTransfer(int mode,String name){choices.clear();selected=-1;return true;}
    public boolean command(String op,int[] args,byte[] bytes)throws IOException{
        String value=new String(bytes,StandardCharsets.UTF_8);
        switch(op){
        case "demo.scene":scene=Binary.safePath(value);choices.clear();return true;
        case "demo.text":text=value;backlog.add(value);if(backlog.size()>200)backlog.remove(0);return true;
        case "demo.audio":audio=Binary.safePath(value);audioGeneration++;return true;
        case "demo.wait_choice":return !choices.isEmpty();
        case "host.command_with_string":if(args.length<4)throw new IOException("Choice rectangle missing");choices.add(new Choice(args,bytes));return true;
        default:return false;
        }
    }
    public void save(DataOutput out)throws IOException{
        out.writeUTF(scene);out.writeUTF(text);out.writeUTF(audio);out.writeInt(audioGeneration);out.writeInt(selected);
        out.writeInt(backlog.size());for(String s:backlog)out.writeUTF(s);out.writeInt(choices.size());
        for(Choice c:choices){for(int v:c.rect)out.writeInt(v);out.writeInt(c.action.length);out.write(c.action);}
    }
    public void load(DataInput in)throws IOException{
        scene=in.readUTF();text=in.readUTF();audio=in.readUTF();if(!scene.isEmpty())Binary.safePath(scene);if(!audio.isEmpty())Binary.safePath(audio);
        audioGeneration=in.readInt();selected=in.readInt();int n=in.readInt();if(n<0||n>200)throw new IOException("Backlog limit");backlog.clear();for(int i=0;i<n;i++)backlog.add(in.readUTF());
        n=in.readInt();if(n<0||n>100)throw new IOException("Choice limit");choices.clear();for(int i=0;i<n;i++){int[] r=new int[4];for(int j=0;j<4;j++)r[j]=in.readInt();int length=in.readInt();if(length<0||length>4096)throw new IOException("Choice label limit");byte[] b=new byte[length];in.readFully(b);choices.add(new Choice(r,b));}
    }
}
