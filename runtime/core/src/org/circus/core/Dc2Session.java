package org.circus.core;

import java.io.*;

/** A native system script temporarily borrows the story VM; its return restores that caller. */
public final class Dc2Session {
    public MesVm vm;public Dc2Host host;
    private final CircusResources data;private final TextCodec codec;
    public interface HostInitializer {void initialize(Dc2Host host)throws IOException;}
    private final HostInitializer initializer;
    private byte[] caller;private String callerScript;
    public Dc2Session(CircusResources data,TextCodec codec)throws IOException{this(data,codec,null);}
    /** Platform-owned settings are installed before the initial native loader
     *captures its read byte. The same initializer applies to replacement hosts. */
    public Dc2Session(CircusResources data,TextCodec codec,HostInitializer initializer)throws IOException{this.data=data;this.codec=codec;this.initializer=initializer;host=Dc2Host.create(data,codec);if(initializer!=null)initializer.initialize(host);vm=new MesVm(data.scripts(),host,"START");}
    private boolean typedSystem(){return host instanceof PcHost&&((PcHost)host).pc1!=null&&((PcHost)host).pc1.inSystem();}
    public boolean inSystem(){return caller!=null||typedSystem();}
    public void openNativeSystem()throws IOException{
        if(inSystem()||!host.textWaiting)throw new IOException("No stable story context for native menu");
        if(host instanceof PcHost&&((PcHost)host).pc1!=null)throw new IOException("PC1 backlog uses the live native history cursor; its redraw lifecycle is not yet mapped");
        caller=vm.snapshot();callerScript=vm.scriptName();host.enterSystem();
    }
    public void openSystem(String script)throws IOException{
        openSystem(script,0);
    }
    public void openSystem(String script,int eventKind)throws IOException{
        if(inSystem()||(!host.textWaiting&&!host.ui.waiting))throw new IOException("No stable story context for system menu");
        if(host instanceof PcHost&&((PcHost)host).pc1!=null){vm.enterNativeSystem(script,eventKind);return;}
        byte[] saved=vm.snapshot();String name=vm.scriptName();host.enterSystem();vm.enterSystem(script);caller=saved;callerScript=name;
    }
    public void returnToStory()throws IOException{
        if(typedSystem()){vm.returnSystemContext();return;}
        if(caller==null)throw new IOException("System return without caller");byte[] saved=caller;restore(saved);caller=null;callerScript=null;
    }
    public byte[] storySnapshot()throws IOException{return caller==null?vm.snapshot():caller.clone();}
    public String storyScript(){return caller!=null?callerScript:typedSystem()?((PcHost)host).pc1.system().returnPoint().script:vm.scriptName();}
    public void restore(byte[] saved)throws IOException{
        Dc2Host h=Dc2Host.create(data,codec);if(initializer!=null)initializer.initialize(h);MesVm m=new MesVm(data.scripts(),h,"START");m.restore(saved);h.copyPersistentFrom(host);h.captureRenderer=host.captureRenderer;if(h instanceof PcHost&&host instanceof PcHost)((PcHost)h).nativeFrameCapture=((PcHost)host).nativeFrameCapture;host=h;vm=m;caller=null;callerScript=null;
    }
    /** Returning to MAIN abandons the suspended story. */
    public void captureHistory()throws IOException{
        if(host instanceof PcHost&&((PcHost)host).pc1!=null)return;
        if(inSystem()||(!host.textWaiting&&!host.presentationWaiting)||host.history.entries.isEmpty())return;
        Dc2History.Entry e=host.history.entries.get(host.history.entries.size()-1);if(e.resume.length!=0)return;
        host.history.omitFromSnapshot=true;try{e.resume=vm.snapshot();}finally{host.history.omitFromSnapshot=false;}
        int total=0;for(int n=host.history.entries.size()-1;n>=0;n--){Dc2History.Entry old=host.history.entries.get(n);total+=old.resume.length;if(total>2*1024*1024)old.resume=new byte[0];}
    }
    public void rewindHistory(int index)throws IOException{
        if(index<0||index>=host.history.entries.size())throw new IOException("History entry missing");
        java.util.List<Dc2History.Entry> prefix=new java.util.ArrayList<>(host.history.entries.subList(0,index+1));byte[] snapshot=prefix.get(index).resume;
        if(snapshot.length==0)throw new IOException("History checkpoint expired");restore(snapshot);host.history.entries.addAll(prefix);host.history.active=false;host.history.selected=index;host.history.cursor=Math.max(0,index-3);
    }
    public void observeTransfer(){if(caller!=null&&vm.scriptName().equalsIgnoreCase("MAIN")){caller=null;callerScript=null;host.systemScene=null;}}
}
