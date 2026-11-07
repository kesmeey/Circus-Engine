package org.circus.core;
import java.io.IOException;
/**Current target entrypoints for common voice/BGM/SE and the native voice
 *list. Bytes/globals stay borrowed; unrelated/movie/callback targets require
 *the next complete consumer in the explicit handler chain. */
public final class Pc1RawAudioHandlers implements Pc1RawDispatch.Leaves {
 private final Pc1Runtime runtime;private final Pc1NativeMemory bus;private final Pc1GraphicsPipeline graphics;private final Pc1Audio audio;private final Pc1TextServices.Settings settings;private final Pc1Transition transition;private final Pc1VoiceDispatch voice;private final Pc1RawDispatch.Leaves other;private int depth;
 public Pc1RawAudioHandlers(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1Audio audio,Pc1TextServices.Settings settings,Pc1Transition transition,Pc1RawDispatch.Leaves other)throws IOException{
  if(runtime==null||graphics==null||graphics.runtime()!=runtime||audio==null||settings==null||transition==null)throw new IOException("PC1 raw audio handlers require canonical runtime/graphics/audio/settings/transition");audio.requireBus(runtime.nativeMemory());transition.requireGraph(graphics);this.runtime=runtime;bus=runtime.nativeMemory();this.graphics=graphics;this.audio=audio;this.settings=settings;this.transition=transition;this.other=other==null?new Pc1RawDispatch.Leaves(){}:other;
  voice=Pc1VoiceDispatch.typed(bus,audio,source->audio.voiceAdmitted(source,settings.current()));runtime.bindNativeBoundary(this::requireBoundary);
 }
 public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 raw audio handler snapshot during native consumer");voice.requireBoundary();transition.requireBoundary();}
 private interface Work{void run()throws IOException;}private void call(Work work)throws IOException{depth++;try{graphics.render().nativeCall(work::run);}finally{depth--;}}
 public void releaseGraphics411080(int selector,int mode)throws IOException{other.releaseGraphics411080(selector,mode);}public void error411050(int raw)throws IOException{other.error411050(raw);}public int transition41A0B0(int target,int argument)throws IOException{return other.transition41A0B0(target,argument);}
 private int g(int at)throws IOException{return bus.readInt32(at);}private void g(int at,int value)throws IOException{bus.writeInt32(at,value);}
 private boolean stopName(Pc1ByteSource source)throws IOException{for(int n=0;;n++){int a=source.unsignedByte(n)&255,b=bus.readAddress(0x43d99c+n)&255;if(a!=b)return false;if(a==0)return true;}}
 private int remainder(int dividend,int divisor)throws IOException{if(divisor==0||dividend==Integer.MIN_VALUE&&divisor==-1)throw new IOException("PC1 native IDIV fault before remainder publication");return dividend%divisor;}
 private void music(Pc1RawDispatch.Registers r)throws IOException{
  int track=r.arg0,mode=r.arg1;if(track==255){g(0x9d8a0c,-1);audio.immediateStopMusic(0,settings.current());return;}
  if(track==254){track=g(0x968a00);g(Pc1Fetch.ARGUMENT0,track);}
  if(mode==0){g(0xbb8438,0);audio.requestMusic(track,0,settings.current());}else if(mode==1){if(g(0x9d8a0c)!=track){g(0xbb8438,0);audio.requestMusic(track,0,settings.current());}}else if(mode==2){g(0xbb8438,1);audio.requestMusic(track,1,settings.current());}
  audio.armTimer();g(0x9d8a0c,g(Pc1Fetch.ARGUMENT0));
 }
 private void sound(Pc1RawDispatch.Registers r)throws IOException{if(r.arg0==255){if(r.arg1==1){audio.startSoundFade(settings.current());audio.armTimer();}else audio.stopSound();g(0x9d8a10,0);}else{audio.sound(r.arg0,r.arg1,settings.current());int second=g(Pc1Fetch.ARGUMENT1),first=g(Pc1Fetch.ARGUMENT0);g(0x9d8a10,(second<<8)|first);}}
 private void voiceList(int mode)throws IOException{
  if(mode==0){if(g(0xbb84bc)>0){int random=runtime.nativeCrtRandom(),index=remainder(random,g(0xbb84bc));g(Pc1Fetch.ARGUMENT1,index);voice.voice(Pc1ByteSource.addressed(bus,0x9b96e4+(index<<7)));}}
  else if(mode==1){int count=g(0xbb84bc),index=0;g(Pc1Fetch.ARGUMENT1,0);if(count>0)do{audio.mixVoiceSource(Pc1ByteSource.addressed(bus,0x9b96e4+(index<<7)),index,settings.current());index=g(Pc1Fetch.ARGUMENT1);count=g(0xbb84bc);index++;g(Pc1Fetch.ARGUMENT1,index);}while(index<count);g(Pc1Fetch.ARGUMENT1,0);index=0;if(count>0)do{audio.startMix(index);index=g(Pc1Fetch.ARGUMENT1);count=g(0xbb84bc);index++;g(Pc1Fetch.ARGUMENT1,index);}while(index<count);}
  else if(mode==2)g(0xbb84bc,0);
 }
 public Pc1RawDispatch.Flow handler(int target,Pc1RawDispatch.Registers r,Pc1Fetch.Frame frame)throws IOException{final Pc1RawDispatch.Flow[] flow={Pc1RawDispatch.Flow.COMMON_TAIL};call(()->{
  switch(target){
   case 0x414b64:music(r);break;
   case 0x412826:sound(r);break;
   case 0x414bf1:{Pc1ByteSource source=Pc1ByteSource.local(r.source());if(stopName(source))audio.stopVoices();else{if(g(0x9b9f18)!=0){graphics.render().resources().releaseNative(1,0);graphics.render().resources().releaseNative(2,0);graphics.render().bakePending();if(transition.begin(g(0x43d418),1)!=Pc1TextPresentation.Transition.COMPLETE){flow[0]=Pc1RawDispatch.Flow.RETURN_ONE_WITHOUT_TAIL;return;}g(0xbb844c,0);}voice.voice(source);}break;}
   case 0x414c84:{int count=g(0xbb84bc);if(count<10){Pc1NativePaths.copy(Pc1ByteSource.addressed(bus,0x9b96e4+(count<<7)),Pc1ByteSource.local(r.source()));g(0xbb84bc,count+1);}break;}
   case 0x414ccc:voiceList(r.arg0);break;
   case 0x414dc0:if(r.arg0==0)g(0xbb8428,0);else{int random=runtime.nativeCrtRandom();g(0xbb8428,remainder(random,g(Pc1Fetch.ARGUMENT0)));}break;
   default:flow[0]=other.handler(target,r,frame);break;
  }
 });return flow[0];}
}
