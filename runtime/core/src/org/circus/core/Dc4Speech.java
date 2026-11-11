package org.circus.core;

import java.io.*;
import java.util.*;

/** PH native AE:FF/47 name-position registry and optional COMU caption queue.
 * Registration is stateful even when eye/mouth animation is not drawn. */
public final class Dc4Speech {
    private static final int MAGIC=0x50485332;
    private int group=-1,x=-1,y=-1,commentType;
    private final int[] pan=new int[3];
    private final List<LinkedHashMap<String,int[]>> regions=new ArrayList<>();
    private final List<byte[]> comments=new ArrayList<>();
    private String pausedVoice="";
    private boolean voicePaused,pausedLoop,pausedActive;
    public Dc4Speech(){for(int i=0;i<20;i++)regions.add(new LinkedHashMap<>());}
    public int[] region(int group,String name){int[] r=regions.get(group).get(name.toUpperCase(Locale.ROOT));return r==null?null:r.clone();}
    public int regionCount(int group){return regions.get(group).size();}
    public int commentType(){return commentType;}
    public int commentCount(){return comments.size();}
    public byte[] commentRaw(int index){return comments.get(index).clone();}
    public void clearComments(){comments.clear();}
    public int audioPan(int channel){return pan[channel==0?0:channel==5?2:1];}
    public boolean voicePaused(){return voicePaused;}
    /** 457D90 closes prior COMU objects before selecting implementation1/2. */
    public void configureComment(int type)throws IOException{require(type>=0&&type<=2,"COMU implementation");commentType=type;comments.clear();}
    public boolean command(Dc4Host h,String op,int[] a,byte[] bytes)throws IOException{
        if(op.equals("ph.audio.control")){
            require(a.length==4,"audio control arguments");
            switch(a[3]){
            case 0:{
                // 43B820 is SetCurrentPosition followed by Play. Native A9
                // first writes the one-percent increment back to99:187.
                int value=h.variable(99,187);if(value!=99){value++;h.variable(99,187,value);}
                h.musicRoom.seek=Math.max(0,Math.min(100,value));h.musicRoom.seekGeneration++;return true;
            }
            case 11:
                if(!voicePaused){pausedVoice=h.audio[5];pausedLoop=h.audioLoop[5];pausedActive=h.audioIsActive(5);voicePaused=true;h.replaceVoice("",false,false);}
                return true;
            case 10:
                // Native43AF10/43AED0 preserve the DirectSound byte cursor.
                // The portable bridge currently restarts the retained voice
                // resource; resource/loop/active ownership still round trips.
                if(voicePaused){h.replaceVoice(pausedVoice,pausedLoop,pausedActive);voicePaused=false;pausedVoice="";pausedLoop=pausedActive=false;}
                return true;
            default:throw new IOException("Unimplemented PH audio control mode "+a[3]);
            }
        }
        if(op.equals("ph.audio.spatial_register")){
            // Native46 clears actor-following flag41E18, and40E3B0 clears
            // the51-entry name/pan table416E8 and count41E14. All494 real
            // callers use exactly(0,"-"). No positional override can be
            // installed in this portable subset, so that table remains empty;
            // independent AF buffer panning is deliberately left untouched.
            require(a.length==1&&a[0]==0&&Arrays.equals(bytes,new byte[]{'-'}),"spatial voice registration outside audited clear command");
            return true;
        }
        if(op.equals("ph.audio.pan")){
            if(a[3]>=3)return true;
            require(a[3]>=0,"audio pan group");
            // AF groups0/1/2 fan out to native buffers0..1/2..7/8..11.
            // 43AC20 is IDirectSoundBuffer::SetPan (vtable40).
            require(a[2]==0,"timed audio pan (not used by this corpus)");
            pan[a[3]]=a[0]*100;return true;
        }
        if(op.equals("ph.actor.lip_position")){
            if(a[3]!=255)return false;
            group=a[0];x=a[1];y=a[2];return true;
        }
        if(op.equals("ph.actor.lip_name")){
            // 430A42 silently skips47 until all three AE:FF registers are initialized.
            if(group==-1||x==-1||y==-1)return true;
            require(group>=0&&group<20,"face-position group");
            String name=TextCodec.cp932(bytes).toUpperCase(Locale.ROOT);
            require(bytes.length<32,"face-position name");
            LinkedHashMap<String,int[]> table=regions.get(group);
            require(table.containsKey(name)||table.size()<100,"face-position capacity");
            table.put(name,new int[]{x,y});return true;
        }
        if(op.equals("ph.comment.text")){
            // 457F70 returns immediately without a live type1/type2 object.
            int limit=commentType==1?20:commentType==2?200:0;
            if(comments.size()<limit){require(bytes.length<8192,"COMU caption size");comments.add(bytes.clone());}
            return true;
        }
        return false;
    }
    public void save(DataOutput out)throws IOException{
        out.writeInt(MAGIC);out.writeInt(group);out.writeInt(x);out.writeInt(y);out.writeInt(commentType);for(int p:pan)out.writeInt(p);
        for(Map<String,int[]> table:regions){out.writeInt(table.size());for(Map.Entry<String,int[]> e:table.entrySet()){out.writeUTF(e.getKey());out.writeInt(e.getValue()[0]);out.writeInt(e.getValue()[1]);}}
        out.writeInt(comments.size());for(byte[] b:comments){out.writeInt(b.length);out.write(b);}
        out.writeBoolean(voicePaused);out.writeUTF(pausedVoice);out.writeBoolean(pausedLoop);out.writeBoolean(pausedActive);
    }
    public void load(DataInput in)throws IOException{
        require(in.readInt()==MAGIC,"speech snapshot version");group=in.readInt();x=in.readInt();y=in.readInt();commentType=in.readInt();
        require(group>=-1&&group<20&&commentType>=0&&commentType<=2,"speech registers");for(int i=0;i<3;i++)pan[i]=in.readInt();
        for(Map<String,int[]> table:regions){table.clear();int n=in.readInt();require(n>=0&&n<=100,"speech registry count");for(int i=0;i<n;i++){String name=in.readUTF();require(name.length()<32&&!table.containsKey(name),"speech registry name");table.put(name,new int[]{in.readInt(),in.readInt()});}}
        comments.clear();int n=in.readInt();require(n>=0&&n<=(commentType==1?20:commentType==2?200:0),"COMU queue size");
        for(int i=0;i<n;i++){int size=in.readInt();require(size>=0&&size<8192,"COMU caption size");byte[] b=new byte[size];in.readFully(b);comments.add(b);}
        voicePaused=in.readBoolean();pausedVoice=in.readUTF();pausedLoop=in.readBoolean();pausedActive=in.readBoolean();
        require(pausedVoice.length()<=8192&&(voicePaused||pausedVoice.isEmpty()&&!pausedLoop&&!pausedActive),"paused voice snapshot");
    }
    private static void require(boolean ok,String what)throws IOException{if(!ok)throw new IOException("Invalid PH "+what);}
}
