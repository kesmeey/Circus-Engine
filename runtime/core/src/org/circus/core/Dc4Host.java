package org.circus.core;

import java.io.*;

/** Plus Harmony ABB6 binding. Native system services remain edition-owned. */
public final class Dc4Host extends Dc3Host {
    private Variables ordinary;
    public final Dc4UiCommands phUi=new Dc4UiCommands();
    public final Dc4SystemCommands phSystem=new Dc4SystemCommands();
    public final Dc4Speech phSpeech=new Dc4Speech();
    public final Dc4Macros phMacros=new Dc4Macros();
    public final Dc4SceneCommands phScene=new Dc4SceneCommands();
    public final Dc4Credits phCredits=new Dc4Credits();
    public final Dc4Blur phBlur=new Dc4Blur();
    public final Dc4Animation phAnimation=new Dc4Animation();
    public final Dc4Ruby phRuby=new Dc4Ruby();
    public Dc4Host(CircusResources resources,TextCodec codec,boolean originalSystem){
        super(resources,codec,originalSystem);
        // Original engine string constants (178ee9... PE data), not D.D. names.
        dialogue.familyName="常坂";dialogue.givenName="一登";
        scene.camera.enablePh();scene.published=scene.capture();
    }
    @Override public void attach(Variables variables){super.attach(variables);ordinary=variables;}
    /** Compatibility query inferred from the eight real ED BGM-completion
     * loops. The extracted40CC50 getter has no matching250 branch, so this
     * is deliberately PH-owned portable behavior, not claimed native proof.
     * Only actual playback/completion generations determine the result. */
    @Override public int read(int bank,int index)throws IOException{if(bank==99&&index==250)return audioIsActive(0)?0:1;return super.read(bank,index);}
    public int variable(int bank,int index)throws IOException{return ordinary.read(bank,index);}
    public void variable(int bank,int index,int value)throws IOException{ordinary.write(bank,index,value);}
    public void musicTrack(int catalogue,int mode)throws IOException{super.command("audio.music",new int[]{catalogue,mode},new byte[0]);}
    public void replaceVoice(String resource,boolean loop,boolean active){replaceAudioNow(5,resource,loop,active);}
    public boolean baseCommand(String op,int[] args,byte[] bytes)throws IOException{return super.command(op,args,bytes);}
    @Override public int audioPan(int channel){return phSpeech.audioPan(channel);}
    public void startCredits(int mode)throws IOException{phCredits.start(resources,variable(3,0),mode);delay(phCredits.completionMillis(),false);}
    @Override public int interceptInstruction(MesScript script,MesScript.Instruction instruction)throws IOException{return phMacros.intercept(script,instruction);}
    @Override protected boolean dispatchDuringDialogue(){return phMacros.replaying();}
    @Override protected void beforeScenePublish()throws IOException{phMacros.beforePublish(this);phScene.prepare(this);}
    @Override public void elapse(long milliseconds){super.elapse(milliseconds);phUi.elapse(this,milliseconds);phScene.elapse(this,milliseconds);phBlur.elapse(milliseconds);phAnimation.elapse(milliseconds);try{phMacros.elapse(this,milliseconds);if(phCredits.elapse(milliseconds)==Dc4Credits.Step.COMPLETE)state.write(100,phCredits.completionFlag(),1);}catch(IOException failure){ui.nativeFailure(failure);}}
    @Override public boolean beforeTransfer(int mode,String name)throws IOException{boolean result=super.beforeTransfer(mode,name);if(mode==0){phScene.reset(this);phBlur.reset();phAnimation.reset();}return result;}
    @Override public boolean signal(long expected,MesVm.HostSignal signal)throws IOException{
        boolean advancingText=dialogue.waiting();boolean accepted=super.signal(expected,signal);
        if(accepted&&advancingText&&signal==MesVm.HostSignal.ADVANCE)phMacros.finish(this);
        return accepted;
    }
    @Override public boolean command(String op,int[] args,byte[] bytes)throws IOException{
        if(op.equals("text.body")){bytes=phMacros.parseBody(bytes,state.read(99,26));bytes=phRuby.parse(bytes);}
        if(op.equals("ph.scene.background")&&(bytes.length==0||bytes[0]!=':')){phBlur.reset();phAnimation.reset();}
        if(op.equals("wait.duration")&&args[0]==255){delay(phUi.remainingMillis(0),false);return true;}
        if(op.equals("wait.duration")&&args[0]==254){delay(Math.max(Math.max(scene.animationRemaining(),phAnimation.remainingMillis()),phScene.animationRemaining()),false);return true;}
        if(op.equals("scene.transition")&&(args[0]==31||args[0]==32))phScene.stopCastMotion(this);
        if(Dc4Transition.command(this,op,args,bytes))return true;
        if(phAnimation.command(this,op,args))return true;
        if(phBlur.command(this,op,args))return true;
        if(phScene.command(this,op,args,bytes))return true;
        if(phUi.command(this,op,args,bytes))return true;
        if(phSpeech.command(this,op,args,bytes))return true;
        if(op.equals("system.command"))return phSystem.command(this,args);
        if(op.equals("system.persist")&&args[0]==29){state.resetPhPreferences(args[1]);return true;}
        if(op.equals("media.movie")){
            String name=TextCodec.cp932(bytes);if(name.lastIndexOf('.')<Math.max(name.lastIndexOf('/'),name.lastIndexOf('\\')))name+=".mpg";
            if(name.indexOf('.')<0)name+=".mpg";
            return super.command(op,args,name.getBytes(Binary.SJIS));
        }
        return super.command(op,args,bytes);
    }
    @Override public void save(DataOutput out)throws IOException{super.save(out);out.writeInt(0x50484831);phUi.save(out);phSystem.save(out);phSpeech.save(out);phMacros.save(out);phScene.save(out);phCredits.save(out);phBlur.save(out);phAnimation.save(out);phRuby.save(out);}
    @Override public void load(DataInput in)throws IOException{super.load(in);if(in.readInt()!=0x50484831)throw new IOException("PH host snapshot version");phUi.load(in);phSystem.load(in,state);phSpeech.load(in);phMacros.load(in);phScene.load(in);phCredits.load(in);phBlur.load(in);phAnimation.load(in);phRuby.load(in);}
}
