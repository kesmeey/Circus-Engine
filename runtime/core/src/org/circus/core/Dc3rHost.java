package org.circus.core;

import java.io.*;
import java.util.*;

/** State and original-script presentation commands for the Unity CircusScr frontend.
 * Coordinates are original 1024x576 screen coordinates. No title/route layout is invented. */
public final class Dc3rHost implements CircusScrVm.Host {
    public interface TextAssets {String read(String name)throws IOException;}
    public interface Capture {String capture()throws IOException;}
    public TextAssets textAssets;public Capture capture;
    public static final class Layer {
        public String resource="",second="";
        public int x,y,pivotX,pivotY,scale=100,tint,priority,moveX0,moveY0,moveX1,moveY1,shakeAxis,shakeX,shakeY;public long moveTime,moveElapsed,shakeElapsed;public boolean ease;
        public Layer copy(){Layer l=new Layer();l.resource=resource;l.second=second;l.x=x;l.y=y;l.pivotX=pivotX;l.pivotY=pivotY;l.scale=scale;l.tint=tint;l.priority=priority;l.moveX0=moveX0;l.moveY0=moveY0;l.moveX1=moveX1;l.moveY1=moveY1;l.moveTime=moveTime;l.moveElapsed=moveElapsed;l.ease=ease;l.shakeAxis=shakeAxis;l.shakeX=shakeX;l.shakeY=shakeY;l.shakeElapsed=shakeElapsed;return l;}
    }
    public static final class Quad {
        public String work="";public int x,y,sx,sy,width,height;public boolean opaque;
        public int animation=-1,animA,animB,animC,actionSpeed=1;public long animationStart;public float scrollU,scaleX=1,scaleY=1,alpha=1;public String action="",resource="";public final List<String> packFrames=new ArrayList<>();
        public Quad copy(){Quad q=new Quad();q.work=work;q.x=x;q.y=y;q.sx=sx;q.sy=sy;q.width=width;q.height=height;q.opaque=opaque;q.animation=animation;q.animA=animA;q.animB=animB;q.animC=animC;q.animationStart=animationStart;q.action=action;q.resource=resource;q.actionSpeed=actionSpeed;q.packFrames.addAll(packFrames);return q;}
    }
    public static final class Text {
        public int x,y,leading,style,width=570,fontSize=23,color;public String text="";public boolean loopTyping;public long typingStart;
        public Text copy(){Text t=new Text();t.x=x;t.y=y;t.leading=leading;t.style=style;t.width=width;t.text=text;t.fontSize=fontSize;t.color=color;t.loopTyping=loopTyping;t.typingStart=typingStart;return t;}
    }
    public static final class Button {
        public int left,top,right,bottom,hoverSound=-1,clickSound=-1;
        public String action="";
        public final List<Quad> normal=new ArrayList<>(),hover=new ArrayList<>(),pressed=new ArrayList<>();
        public final List<Text> normalText=new ArrayList<>(),hoverText=new ArrayList<>(),pressedText=new ArrayList<>();
        public int sliderStart,sliderEnd,sliderSteps,sliderFlag,targetFlag,flagValue;public boolean sliderVertical;
        public boolean contains(int x,int y){return x>=left&&x<right&&y>=top&&y<bottom;}
        public Button copy(){Button b=new Button();b.left=left;b.top=top;b.right=right;b.bottom=bottom;b.hoverSound=hoverSound;b.clickSound=clickSound;b.action=action;b.sliderVertical=sliderVertical;b.sliderStart=sliderStart;b.sliderEnd=sliderEnd;b.sliderSteps=sliderSteps;b.sliderFlag=sliderFlag;b.targetFlag=targetFlag;b.flagValue=flagValue;copyQuads(normal,b.normal);copyQuads(hover,b.hover);copyQuads(pressed,b.pressed);copyTexts(normalText,b.normalText);copyTexts(hoverText,b.hoverText);copyTexts(pressedText,b.pressedText);return b;}
    }
    public static final class Panel {
        public final List<Quad> quads=new ArrayList<>();public final List<Text> texts=new ArrayList<>();public final List<Button> buttons=new ArrayList<>();
        public Panel copy(){Panel p=new Panel();copyQuads(quads,p.quads);copyTexts(texts,p.texts);for(Button b:buttons)p.buttons.add(b.copy());return p;}
    }
    public static final class Choice {public final String label,caption;Choice(String l,String c){label=l;caption=c;}}
    public static final class History {public String name,text,voice,script;public byte[] rollback=new byte[0];public final Set<String> captures=new TreeSet<>();public History(String n,String t,String v,String s){name=n;text=t;voice=v;script=s;}}
    public static final class SaveInfo {public final String date,caption;public SaveInfo(String date,String caption){this.date=date;this.caption=caption;}}
    public final Map<Integer,SaveInfo> availableSaves=new HashMap<>();
    public final Layer[] layers=new Layer[13];
    public final Map<String,String> works=new HashMap<>();
    public final Map<Integer,Integer> flags=new HashMap<>(),colors=new HashMap<>();
    public final Map<Integer,Panel> putDefs=new HashMap<>(),menuDefs=new HashMap<>();
    public final Set<String> viewedCg=new TreeSet<>(),viewedScripts=new TreeSet<>();
    private final Map<String,Integer> cgIds=new HashMap<>(),sceneIds=new HashMap<>();private final Map<Integer,String> cgNames=new HashMap<>();private CircusScrVm currentVm;
    public final List<History> history=new ArrayList<>();private boolean omitHistory;private static final int HISTORY_LIMIT=8*1024*1024;
    public final List<Choice> choices=new ArrayList<>();
    public Panel back=new Panel(),fore=new Panel(),menu=new Panel(),fogBack=new Panel(),fogFore=new Panel();
    public String speaker="",body="",movie="",yesJump="",noJump="",cancelJump="",hitJump="",musicJump="",platformAction="";
    public final String[] audio={"","","","","",""};public final int[] audioGeneration=new int[6];public final boolean[] audioLoop=new boolean[6],audioActive=new boolean[6];private final long[] audioFade=new long[6];
    public boolean textVisible,menuWaiting,choiceWaiting,exitRequested,persistRequested,fullscreen,altText;
    public int hovered=-1,pressed=-1,movieGeneration,saveRequest=-1,loadRequest=-1,autosaveGeneration,sceneRevision;
    public int faceX,faceY,putTarget=1,putX,putY,fromX,fromY;
    public long ticket,elapsedMillis;
    public boolean transitionRequested;public long transitionDuration;public int transitionRevision;
    public String transitionMask="";public float transitionFuzziness=.8f;
    public int sakura,sakuraX,sakuraY,fog;public boolean rain;
    public int screenBreakType=-1;public boolean expandedMessageMenu;
    public int cameraX,cameraY;private int cameraShake;private long cameraShakeElapsed;private long flashElapsed,flashDuration,endingRemaining;private int flashPhase=-1;
    private Panel selectSlots=new Panel();private final Map<Integer,Panel> selectEntries=new TreeMap<>();private int lastSelectEntry;private boolean musicGallery,musicFinished;private final List<String> musicTracks=new ArrayList<>();private final Map<Integer,String> musicTitles=new TreeMap<>();
    private int selectClick=-1,selectHover=-1;private Button dragButton;
    public boolean backlogActive;public int backlogPosition,historyJumpRequest=-1;
    private Quad actionEntity;private final List<String> loadedPack=new ArrayList<>();private final Map<String,Dc3rAnimation> actions=new HashMap<>();
    private int nextSound=2;private boolean waitVoice,returnTextWait;private Layer[] qLayers;private Panel qBack,qFore,qFogBack,qFogFore;private int qSakura,qSakuraX,qSakuraY,qFog;private boolean qRain;private String qSpeaker="",qBody="",qLoopSound="";private boolean qText;private final Map<String,String> qWorks=new HashMap<>();private Quad lastPutQuad,lastMenuQuad;private Text lastPutText;
    private static void copyQuads(List<Quad> a,List<Quad> b){for(Quad q:a)b.add(q.copy());}
    private static void copyTexts(List<Text> a,List<Text> b){for(Text t:a)b.add(t.copy());}
    public Dc3rHost(){for(int i=0;i<layers.length;i++){layers[i]=new Layer();layers[i].priority=i;}defaults();flags.put((101<<16)+101,-1);flags.put((101<<16)+103,1);flags.put((101<<16)+104,0);flags.put((101<<16)+600,1);flags.put((101<<16)+601,0);}
    public void defaults(){int[][] d={{5,1,1},{99,3,1},{99,10,1},{99,11,7},{99,14,1},{99,15,1},{99,5,1},{99,6,1},{99,7,20},{99,8,26},{99,9,26},{99,26,8},{99,27,8},{99,28,0},{99,29,8},{99,30,1},{99,12,1},{99,17,0},{99,56,0},{99,31,0},{99,237,1},{101,999,1}};for(int[] a:d)flags.put((a[0]<<16)|a[1],a[2]);}
    @Override public int readFlag(int flag){if(flag==(99<<16)+251)return currentVm!=null&&viewedScripts.contains(currentVm.scriptName().toLowerCase(Locale.ROOT))?1:0;if(flag==(99<<16)+250)return audioActive[0]?0:1;if(flag==(99<<16)+249)return audioActive[5]?0:1;if(flag==(99<<16)+248){for(int i=1;i<5;i++)if(audioActive[i])return 0;return 1;}return flags.getOrDefault(flag,0);}
    @Override public void writeFlag(int flag,int value){flags.put(flag,value);}
    public void configureCatalogues(String cgList,String sceneList)throws IOException{parseCatalogue(cgList,cgIds,cgNames);parseCatalogue(sceneList,sceneIds,null);}
    private static void parseCatalogue(String input,Map<String,Integer> byName,Map<Integer,String> byId)throws IOException{byName.clear();if(byId!=null)byId.clear();for(String line:input.replace("\ufeff","").split("\\r?\\n")){if(line.trim().isEmpty())continue;String[] parts=line.split("\\t");if(parts.length<2)throw new IOException("Invalid Unity catalogue line");try{int id=Integer.parseInt(parts[0]);String name=parts[1].toLowerCase(Locale.ROOT);byName.put(name,id);if(byId!=null)byId.put(id,name);}catch(NumberFormatException e){throw new IOException("Invalid Unity catalogue id",e);}}}
    private void markCg(String name){name=name.toLowerCase(Locale.ROOT);viewedCg.add(name);Integer id=cgIds.get(name);if(id!=null)writeFlag((97<<16)+id,1);}
    private void markScript(String name){name=name.toLowerCase(Locale.ROOT);viewedScripts.add(name);Integer id=sceneIds.get(name);if(id!=null)writeFlag((98<<16)+id,1);}
    // CsData.Val remains the default zero when CsData was constructed from a string.
    // Real Face/Bup calls pass an unused third texture string in numeric fields.
    private static int n(CircusScrVm.Value[] a,int i)throws IOException{if(i>=a.length)throw new IOException("Unity command numeric argument "+i);return a[i].number;}
    private static String s(CircusScrVm.Value[] a,int i)throws IOException{if(i>=a.length||!a[i].isString)throw new IOException("Unity command string argument "+i);return a[i].string;}
    private Panel put(){return putTarget==0?back:fore;}
    private Button lastButton()throws IOException{if(menu.buttons.isEmpty())throw new IOException("Menu property without menu");return menu.buttons.get(menu.buttons.size()-1);}
    private Quad quad(CircusScrVm.Value[] a,boolean offset,boolean opaque)throws IOException{Quad q=new Quad();q.x=n(a,0)+(offset?putX:0);q.y=n(a,1)+(offset?putY:0);q.work=s(a,2);q.sx=n(a,3)+(offset?fromX:0);q.sy=n(a,4)+(offset?fromY:0);q.width=n(a,5)-q.sx;q.height=n(a,6)-q.sy;q.opaque=opaque;if(q.width<0||q.height<0)throw new IOException("Negative Unity sprite extent");return q;}
    private Text text(CircusScrVm.Value[] a,boolean caption)throws IOException{Text t=new Text();t.x=n(a,0);t.y=n(a,1);if(caption){t.style=n(a,2);t.text=s(a,3);}else{t.leading=n(a,2);t.style=n(a,3);if(a.length>4)t.width=n(a,4);}return t;}
    private void publish(){sceneRevision++;}
    private void hideText(){textVisible=false;layers[12].resource="";layers[12].second="";}
    private void clearPut(){lastPutQuad=null;lastPutText=null;back=new Panel();fore=new Panel();putTarget=1;altText=false;}
    private void clearMenu(){menu=new Panel();hovered=pressed=-1;menuWaiting=choiceWaiting=false;}
    private void clearFog(){fogBack=new Panel();fogFore=new Panel();fog=0;writeFlag((101<<16)+108,0);}
    private Quad fogQuad(int sx,int sy,int width,int height){Quad q=new Quad();q.work="fog";q.sx=sx;q.sy=sy;q.width=width;q.height=height;return q;}
    private void fogMode(int type)throws IOException{if(type==0){clearFog();works.remove("fog");return;}if(type==1||type==2){clearFog();works.put("fog","system/"+(type==1?"fog":"fogw"));fogFore.quads.add(fogQuad(0,0,1024,576));}else if(type==3){if(fog==3)return;clearFog();Quad q=fogQuad(0,600,3072,600);q.animation=258;q.animA=-2048;q.animB=34;q.animationStart=elapsedMillis;fogFore.quads.add(q);q=fogQuad(0,0,3072,600);q.animation=258;q.animA=-2048;q.animB=60;q.animationStart=elapsedMillis;fogBack.quads.add(q);}else if(type==4){fogFore.quads.add(fogQuad(1024,576,1024,576));fogBack=new Panel();}else throw new IOException("Unsupported Unity fog mode "+type);fog=type;writeFlag((101<<16)+108,type);}
    private void clearLayers(){for(int i=0;i<11;i++){layers[i].resource="";layers[i].second="";layers[i].scale=100;layers[i].pivotX=layers[i].pivotY=0;layers[i].moveTime=layers[i].moveElapsed=0;layers[i].shakeAxis=0;layers[i].shakeElapsed=0;}}
    private void setLayer(Layer l,String resource,String second,int x,int y,int pivotX,int pivotY,int tint){l.resource=resource;l.second=second;l.x=x;l.y=y;l.pivotX=pivotX;l.pivotY=pivotY;l.tint=tint;l.moveTime=l.moveElapsed=0;l.shakeAxis=0;l.shakeElapsed=0;transitionRequested=true;}
    private void bust(int slot,CircusScrVm.Value[] a)throws IOException{Layer l=layers[slot];if(!a[0].isString){l.resource=l.second="";return;}setLayer(l,"stand/"+s(a,0),s(a,1).isEmpty()?"":"stand/"+s(a,1),l.x,l.y,n(a,3),n(a,4),n(a,2));}
    private void move(Layer l,int x,int y,long duration,boolean ease){l.moveX0=l.x;l.moveY0=l.y;l.moveX1=x;l.moveY1=y;l.moveTime=Math.max(1,duration);l.moveElapsed=0;l.ease=ease;}
    private void play(int channel,String resource,boolean loop){if(resource.equals("se/se255"))return;if(resource.equals("voice/000silent24"))resource="";audio[channel]=resource;audioLoop[channel]=loop;audioFade[channel]=0;audioActive[channel]=!resource.isEmpty();audioGeneration[channel]++;}
    public void audioCompleted(int channel,int generation){if(channel>=0&&channel<6&&audioGeneration[channel]==generation){audioActive[channel]=false;if(channel==0&&musicGallery&&!audioLoop[channel])musicFinished=true;}}
    public void mediaCompleted(CircusScrVm vm){movie="";vm.resume();}
    public void elapse(long ms,CircusScrVm vm)throws IOException{ms=Math.max(0,ms);elapsedMillis+=ms;if(musicFinished){musicFinished=false;if(musicGallery&&!musicJump.isEmpty()){menuWaiting=false;vm.jump(musicJump);}}if(flashDuration>0){flashElapsed=Math.min(flashDuration,flashElapsed+ms);int phase=(int)(flashElapsed/75);if(phase!=flashPhase){flashPhase=phase;clearPut();if((phase&1)==0&&flashElapsed<flashDuration)addFlash();publish();}if(flashElapsed==flashDuration){flashDuration=0;clearPut();}}if(endingRemaining>0){endingRemaining=Math.max(0,endingRemaining-ms);if(endingRemaining==0){writeFlag((5<<16)+1,1);back.quads.removeIf(q->q.work.startsWith("GED"));fore.quads.removeIf(q->q.work.startsWith("GED"));works.keySet().removeIf(k->k.startsWith("GED"));}}for(Layer l:layers){if(l.moveTime>0){l.moveElapsed=Math.min(l.moveTime,l.moveElapsed+ms);double t=l.moveElapsed/(double)l.moveTime;if(l.ease)t=Math.sin(t*Math.PI/2);l.x=l.moveX0+(int)Math.round((l.moveX1-l.moveX0)*t);l.y=l.moveY0+(int)Math.round((l.moveY1-l.moveY0)*t);if(l.moveElapsed==l.moveTime)l.moveTime=0;}if(l.shakeAxis!=0){l.shakeElapsed=Math.min(300,l.shakeElapsed+ms);int offset=shakeValue(l.shakeElapsed,300,l.shakeAxis==1?new int[]{0,0,30,0,30,0,30,0,0}:new int[]{0,0,-30,30,-30,30,0,0});l.x=l.shakeX+(l.shakeAxis==2?offset:0);l.y=l.shakeY+(l.shakeAxis==1?offset:0);if(l.shakeElapsed==300)l.shakeAxis=0;}}if(cameraShake!=0){cameraShakeElapsed=Math.min(500,cameraShakeElapsed+ms);int offset=shakeValue(cameraShakeElapsed,500,new int[]{0,0,-50,50,-50,50,-50,50,0,0});cameraX=cameraShake==1?offset:0;cameraY=cameraShake==2?offset:0;if(cameraShakeElapsed==500)cameraShake=0;}for(int i=0;i<6;i++)if(audioFade[i]>0){audioFade[i]=Math.max(0,audioFade[i]-ms);if(audioFade[i]==0)play(i,"",false);}if(waitVoice&&!audioActive[5]){waitVoice=false;vm.resume();}}
    private static int shakeValue(long time,long duration,int[] points){double x=time*(points.length-1)/(double)duration;int i=Math.min(points.length-2,(int)x);return (int)Math.round(points[i]+(points[i+1]-points[i])*(x-i));}
    public Quad animated(Quad original)throws IOException{if(original.animation<0)return original;Quad q=original.copy();long time=Math.max(0,elapsedMillis-q.animationStart);int type=q.animation&15;if(type==0||type==1){int step=q.animA<=0?0:(int)(time*34/(1000L*q.animA))%Math.max(1,q.animC+1);if(type==0)q.sx+=step*q.animB;else q.sy+=step*q.animB;}else if(type==2||type==3){long duration=Math.max(1,q.animB*1000L);double t=((q.animation&256)!=0?time%duration:Math.min(time,duration))/(double)duration;if(type==2)q.x+=(int)((q.animA-q.x)*t);else q.y+=(int)((-q.animA-q.y)*t);}else if(type==4){long duration=q.animC==0?1:Math.max(1,q.animB*1000L/q.animC/9);q.scrollU=.6f*((q.animation&256)!=0?time%duration:Math.min(time,duration))/duration;}else if(type==6){Dc3rAnimation action=action(q.action);double t=time*q.actionSpeed/1000.0;q.scaleX=(float)action.value('W',t,100)/100;q.scaleY=(float)action.value('H',t,100)/100;q.alpha=Math.max(0,Math.min(1,(float)action.value('T',t,16)/16));q.x=(int)Math.round(action.value('X',t,q.x)-(q.scaleX-1)*512);q.y=(int)Math.round(action.value('Y',t,q.y)-(q.scaleY-1)*300);}else if(type==7){if(q.packFrames.isEmpty())throw new IOException("Empty Unity animation pack");long frame=time*12/1000;int index=(q.animation&256)!=0?(int)(frame%q.packFrames.size()):(int)Math.min(frame,q.packFrames.size()-1);q.resource="anim/"+q.packFrames.get(index);}return q;}
    private Dc3rAnimation action(String text)throws IOException{Dc3rAnimation a=actions.get(text);if(a==null){a=new Dc3rAnimation(text);actions.put(text,a);}return a;}
    private static Quad galleryQuad(int x,int y,String work,int sx,int sy,int width,int height){Quad q=new Quad();q.x=x;q.y=y;q.work=work;q.sx=sx;q.sy=sy;q.width=width;q.height=height;return q;}
    private void musicButtonQuads(List<Quad> out,int x,int y,int state,int id){out.add(galleryQuad(x+40,y+4,"work1",600,172+40*state,295,36));out.add(galleryQuad(x,y,"work2",48*state+144*(id/50),40*(id%50),48,38));}
    private void buildMusicGallery()throws IOException{if(!musicGallery){if(textAssets==null)throw new IOException("Unity music catalogue unavailable");musicTitles.clear();for(Map.Entry<Integer,Panel> e:selectEntries.entrySet()){if(e.getValue().texts.isEmpty())throw new IOException("Music track title missing: "+e.getKey());musicTitles.put(e.getKey(),e.getValue().texts.get(0).text);}musicTracks.clear();for(String line:textAssets.read("bgmlist").replace("\ufeff","").split("\r?\n"))if(!line.trim().isEmpty())musicTracks.add(line.trim());for(int key:new int[]{(99<<16)+254,13303888,13303892,(99<<16)+255,(7<<16)+98})writeFlag(key,0);musicGallery=true;}int start=readFlag(13303888);for(int i=0;i<selectSlots.buttons.size();i++){int id=start+i;String title=musicTitles.get(id);if(title==null)throw new IOException("Music gallery index "+id);Button slot=selectSlots.buttons.get(i),b=new Button();int x=slot.left,y=slot.top;b.left=x;b.top=y;b.right=x+295;b.bottom=y+36;b.action="mplay";b.targetFlag=(99<<16)+254;b.flagValue=id;b.clickSound=selectClick;b.hoverSound=selectHover;musicButtonQuads(b.normal,x,y,0,id);musicButtonQuads(b.hover,x,y,1,id);musicButtonQuads(b.pressed,x,y,2,id);Text t=new Text();t.x=x+50;t.y=y+14;t.text=title;t.fontSize=14;t.width=290;t.color=0xff000000;b.normalText.add(t);b.hoverText.add(t.copy());b.pressedText.add(t.copy());menu.buttons.add(b);}int selected=readFlag((99<<16)+255);if(selected!=255){String title=musicTitles.get(selected);if(title==null)throw new IOException("Music playing track index "+selected);Text t=new Text();t.x=694;t.y=165;t.text=String.format(Locale.ROOT,"%02d: %s",selected+1,title);t.fontSize=18;t.color=0xffffffff;t.width=290;put().texts.add(t);}}
    private void playGalleryTrack()throws IOException{int index=readFlag((99<<16)+255);if(!musicGallery||index<0||index>=musicTracks.size())throw new IOException("Invalid music gallery track "+index);String name=musicTracks.get(index);play(0,name.startsWith("bgm/")?name:"bgm/"+name,readFlag((9<<16)+30)==1);musicFinished=false;}
    private void addFlash(){Quad q=new Quad();q.work="work4";q.width=1024;q.height=576;put().quads.add(q);}
    private void startGrandEnding(CircusScrVm vm){for(int i=1;i<26;i++){int id=i>=13?i+1:i;String work="GED"+i;works.put(work,String.format(Locale.ROOT,"system/ged%02d",id));Quad q=new Quad();q.work=work;q.y=600*i;q.width=1024;q.height=600;q.animation=3;q.animA=15600-q.y;q.animB=238;q.animationStart=elapsedMillis;put().quads.add(q);}writeFlag((5<<16)+1,0);endingRemaining=239000;publish();ticket++;vm.waitTime(endingRemaining);}
    private void copyBack()throws IOException{if(capture==null)throw new IOException("Unity frame capture is unavailable");hideText();String resource=capture.capture();clearLayers();clearPut();clearMenu();clearFog();sakura=0;rain=false;setLayer(layers[0],resource,"",0,0,0,0,0);publish();}
    private void startAction(CircusScrVm vm,int speed,boolean loop,boolean freeze)throws IOException{if(actionEntity==null)throw new IOException("Action start without Action1");Quad q=actionEntity;actionEntity=null;if(freeze)copyBack();q.actionSpeed=Math.max(1,speed);q.animationStart=elapsedMillis;if(loop)q.animation|=256;put().quads.add(q);publish();if(!loop){long duration=(q.animation&15)==7?q.packFrames.size()*1000L/12:(long)(action(q.action).durationSeconds*1000/q.actionSpeed);ticket++;vm.waitTime(duration);}}
    public String displayText(Text t,String value){if(!t.loopTyping||value.isEmpty())return value;int speed=Math.max(0,Math.min(11,readFlag((99<<16)+26)));if(speed==11)return value;int count=value.codePointCount(0,value.length());long advance=Math.max(1,3L*(11-speed)),typing=count*advance,hold=(long)((4+count/60.0)*Math.max(0,1-readFlag((99<<16)+29)/20.0)*1000),phase=Math.floorMod(elapsedMillis-t.typingStart,Math.max(1,typing+hold));int visible=(int)Math.min(count,phase/advance);return value.substring(0,value.offsetByCodePoints(0,visible));}
    private void animation(Quad q,CircusScrVm.Value[] a)throws IOException{if(q==null)throw new IOException("Animation without a quad");int type=n(a,0)&15;if(type>4)throw new IOException("Unimplemented Unity quad animation "+n(a,0));q.animation=n(a,0);q.animA=n(a,1);q.animB=n(a,2);q.animC=n(a,3);q.animationStart=elapsedMillis;}
    public float volume(int ch){int flag=(99<<16)+(ch==0?7:ch==5?9:8),value=Math.max(0,Math.min(30,readFlag(flag)));if(ch==0&&readFlag((99<<16)+14)==0||ch>0&&ch<5&&readFlag((99<<16)+15)==0)return 0;return value/30f*(audioFade[ch]>0?audioFade[ch]/1000f:1);}
    private void waitMenu(CircusScrVm vm,boolean hide){if(hide)hideText();publish();menuWaiting=true;hovered=pressed=-1;ticket++;vm.waitHost();}
    private void transition(CircusScrVm vm,long millis,boolean wait){hideText();transitionMask="";transitionFuzziness=.8f;screenBreakType=-1;transitionDuration=Math.max(0,millis);transitionRevision++;publish();transitionRequested=false;if(wait&&millis>0){ticket++;vm.waitTime(millis);}}
    public Panel messageMenu(){return menuDefs.get(expandedMessageMenu?6:1);}
    public List<Button> buttons(){Panel p=menuWaiting?menu:textVisible?messageMenu():null;return p==null?Collections.emptyList():Collections.unmodifiableList(p.buttons);}
    public int hit(int x,int y){List<Button> b=buttons();for(int i=b.size()-1;i>=0;i--)if(!b.get(i).action.isEmpty()&&b.get(i).contains(x,y))return i;return -1;}
    public void hover(int x,int y){int found=hit(x,y);if(found!=hovered){hovered=found;if(found>=0&&buttons().get(found).hoverSound>=0)play(2,String.format(Locale.ROOT,"se/se%03d",buttons().get(found).hoverSound),false);}}
    public List<Quad> buttonQuads(Button b,int state){List<Quad> source=state==2&&!b.pressed.isEmpty()?b.pressed:state>0&&!b.hover.isEmpty()?b.hover:b.normal;if(b.sliderSteps<=1||b.sliderVertical||b.pressed.size()<2)return source;List<Quad> result=new ArrayList<>();for(int i=0;i<source.size();i++)if(source!=b.pressed||i!=1)result.add(source.get(i));Quad fill=b.pressed.get(1).copy();fill.width=(int)Math.round(fill.width*Math.max(0,Math.min(b.sliderSteps-1,readFlag(b.sliderFlag)))/(double)(b.sliderSteps-1));result.add(fill);return result;}
    public boolean drag(int x,int y,boolean begin,CircusScrVm vm)throws IOException{if(begin){dragButton=null;for(Button b:buttons())if(b.sliderSteps>1&&b.contains(x,y)){dragButton=b;break;}if(dragButton==null)return false;}if(dragButton==null)return false;Button b=dragButton;int pos=b.sliderVertical?y:x;int value=(int)Math.round((pos-b.sliderStart)*(b.sliderSteps-1)/(double)Math.max(1,b.sliderEnd-b.sliderStart));value=Math.max(0,Math.min(b.sliderSteps-1,value));writeFlag(b.sliderFlag,value);if(b.sliderVertical&&backlogActive){backlogPosition=value;Dc3rBacklog.build(this);}publish();return true;}
    public void endDrag(CircusScrVm vm)throws IOException{Button b=dragButton;dragButton=null;if(b!=null&&!b.sliderVertical){int index=buttons().indexOf(b);if(index>=0)choose(index,vm);}}
    public void cancelDrag(){dragButton=null;}
    private void endBacklog(CircusScrVm vm){backlogActive=false;clearMenu();yesJump=noJump=cancelJump=hitJump="";vm.resume();ticket++;}
    private boolean backlogAction(String action,CircusScrVm vm)throws IOException{if(action.equals(":endlog")){endBacklog(vm);return true;}if(action.equals(":logjump")){rewindHistory(backlogPosition,vm);return true;}if(action.startsWith(":history")){int index=Integer.parseInt(action.substring(8))+backlogPosition;if(index>=0&&index<history.size())play(5,history.get(index).voice,false);return true;}int delta=action.equals(":logpageup")?-4:action.equals(":logpagedown")?4:action.equals(":loglineup")?-1:action.equals(":loglinedown")?1:0;if(delta!=0){backlogPosition=Math.max(0,Math.min(Math.max(0,history.size()-4),backlogPosition+delta));Dc3rBacklog.build(this);publish();return true;}return action.equals("-")||action.equals(":backlogslider");}
    public void choose(int index,CircusScrVm vm)throws IOException{List<Button> buttons=buttons();if(index<0||index>=buttons.size())throw new IOException("Stale Unity menu selection");Button b=buttons.get(index);String action=b.action;if(backlogActive&&backlogAction(action,vm))return;if(action.isEmpty()||action.equals("-"))return;if(b.clickSound>=0)play(2,String.format(Locale.ROOT,"se/se%03d",b.clickSound),false);if(b.targetFlag!=0)writeFlag(b.targetFlag,b.flagValue);hovered=pressed=-1;
        if(action.equals(":n")&&noJump.isEmpty()){expandedMessageMenu=!expandedMessageMenu;return;}
        if(action.equals(":auto")||action.equals(":rapid")||action.equals(":clear")||action.equals(":qs")||action.equals(":ql")){platformAction=action.substring(1);return;}
        if(action.equals(":option")||action.equals(":backlog")||action.equals(":torisetu")){if(action.equals(":backlog")&&history.isEmpty())return;writeFlag((9<<16)+19,0);storeQuickScene();hideText();vm.changeScript(action.equals(":option")?"option":action.equals(":backlog")?"log":"manual","",true,true);return;}
        writeFlag((99<<16)+252,index);menuWaiting=false;vm.resume();ticket++;if(choiceWaiting){int choice=index-1;if(action.length()==2&&action.charAt(0)==':'&&action.charAt(1)>='1'&&action.charAt(1)<='4')choice=action.charAt(1)-'1';if(choice<0||choice>=choices.size())throw new IOException("Invalid original choice slot "+index);writeFlag((99<<16)+254,choice);choiceWaiting=false;vm.jump(choices.get(choice).label);return;}
        if(action.equals(":y")){vm.jump(yesJump);return;}if(action.equals(":n")){vm.jump(noJump);return;}if(action.startsWith(":"))throw new IOException("Unimplemented original menu action "+action);vm.jump(action);}
    public void cancel(CircusScrVm vm)throws IOException{if(backlogActive){endBacklog(vm);return;}if(!cancelJump.isEmpty()){menuWaiting=false;vm.resume();vm.jump(cancelJump);}else if(textVisible){storeQuickScene();vm.changeScript("advmenu","",true,true);}}
    public void advance(CircusScrVm vm){textVisible=false;vm.resume();ticket++;}
    private void storeQuickScene(){if(qLayers!=null)return;qLayers=new Layer[layers.length];for(int i=0;i<layers.length;i++)qLayers[i]=layers[i].copy();qBack=back.copy();qFore=fore.copy();qFogBack=fogBack.copy();qFogFore=fogFore.copy();qSakura=sakura;qSakuraX=sakuraX;qSakuraY=sakuraY;qFog=fog;qRain=rain;qSpeaker=speaker;qBody=body;qText=textVisible;qWorks.clear();qWorks.putAll(works);qLoopSound=audioActive[1]?audio[1]:"";}
    private void restoreQuickScene(){if(qLayers==null)return;for(int i=0;i<layers.length;i++)layers[i]=qLayers[i];back=qBack;fore=qFore;fogBack=qFogBack;fogFore=qFogFore;sakura=qSakura;sakuraX=qSakuraX;sakuraY=qSakuraY;fog=qFog;rain=qRain;speaker=qSpeaker;body=qBody;textVisible=qText;works.clear();works.putAll(qWorks);if(!qLoopSound.isEmpty())play(1,qLoopSound,true);qLayers=null;clearMenu();publish();}
    private void captureHistory(CircusScrVm vm)throws IOException{if(vm.callDepth()!=0||history.isEmpty())return;History h=history.get(history.size()-1);ByteArrayOutputStream raw=new ByteArrayOutputStream();omitHistory=true;try(DataOutputStream out=new DataOutputStream(new java.util.zip.DeflaterOutputStream(raw))){save(out);vm.save(out);}finally{omitHistory=false;}h.rollback=raw.toByteArray();for(String name:works.values())if(name.startsWith("@capture/"))h.captures.add(name);for(Layer l:layers)if(l.resource.startsWith("@capture/"))h.captures.add(l.resource);long bytes=0;for(History entry:history)bytes+=entry.rollback.length;for(History entry:history){if(bytes<=HISTORY_LIMIT)break;bytes-=entry.rollback.length;entry.rollback=new byte[0];entry.captures.clear();}}
    public boolean canRewindHistory(int index){return index>=0&&index<history.size()&&history.get(index).rollback.length>0;}
    public void forgetHistoryCapture(String name){for(History h:history)if(h.captures.contains(name)){h.rollback=new byte[0];h.captures.clear();}}
    public void rewindHistory(int index,CircusScrVm vm)throws IOException{if(!canRewindHistory(index))return;History h=history.get(index);List<History> retained=new ArrayList<>(history.subList(0,index+1));long previousTicket=ticket;int[] previousAudio=audioGeneration.clone();int previousMovie=movieGeneration;byte[] packed=h.rollback;ByteArrayOutputStream raw=new ByteArrayOutputStream();try(InputStream input=new java.util.zip.InflaterInputStream(new ByteArrayInputStream(packed))){byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))>=0;){if(raw.size()+n>16*1024*1024)throw new IOException("Unity history expansion limit");raw.write(buffer,0,n);}}try(DataInputStream input=new DataInputStream(new ByteArrayInputStream(raw.toByteArray()))){load(input);vm.load(input);if(input.available()!=0)throw new IOException("Trailing Unity history state");}history.clear();history.addAll(retained);ticket=previousTicket+1;backlogActive=false;currentVm=vm;for(int i=0;i<6;i++)audioGeneration[i]=Math.max(audioGeneration[i],previousAudio[i])+1;movieGeneration=Math.max(movieGeneration,previousMovie)+1;sceneRevision++;}
    private int saveSlot(int slot){return slot==-2?101:slot==-1?readFlag((101<<16)+101):slot;}
    private static Quad saveQuad(int x,int y,int type,int state,boolean load){Quad q=new Quad();q.work="work1";q.x=x;q.y=y;q.sx=state*336;q.sy=(type+(load&&state>0?3:0))*84;q.width=335;q.height=84;return q;}
    private static Text saveText(int x,int y,String text,boolean number){Text t=new Text();t.x=x;t.y=y;t.text=text;t.color=0xff101020;t.width=190;t.fontSize=number?25:18;return t;}
    private void buildSaveMenu(boolean save)throws IOException{int page=readFlag((99<<16));if(page<0||page>10)throw new IOException("Save page outside original range");for(int i=0;i<10;i++){int slot=-2+page*10+i,x=248+(i>=5?336:0),y=96+(i%5)*83,type=slot==-2?0:slot==-1?1:2;SaveInfo info=availableSaves.get(saveSlot(slot));boolean enabled=save?slot>=0:info!=null;List<Text> labels=new ArrayList<>();labels.add(saveText(x+116,y+15,info==null?"---- --/-- --:--":info.date,false));if(info!=null)labels.add(saveText(x+116,y+41,info.caption,false));if(slot>=0)labels.add(saveText(x+55,y+25,String.format(Locale.ROOT,"%03d",slot),true));if(enabled){Button b=new Button();b.left=x;b.top=y;b.right=x+335;b.bottom=y+84;b.action="selsave";b.targetFlag=(101<<16)+102;b.flagValue=slot;b.clickSound=selectClick;b.hoverSound=selectHover;b.normal.add(saveQuad(x,y,type,0,!save));b.hover.add(saveQuad(x,y,type,1,!save));b.pressed.add(saveQuad(x,y,type,2,!save));copyTexts(labels,b.normalText);copyTexts(labels,b.hoverText);menu.buttons.add(b);}else{put().quads.add(saveQuad(x,y,type,0,!save));put().texts.addAll(labels);}}}
    @Override public void afterTransfer(CircusScrVm vm){currentVm=vm;if(returnTextWait){returnTextWait=false;vm.waitClick();}}
    public void savePersistent(DataOutput out)throws IOException{out.writeInt(0x55335031);Map<Integer,Integer> global=new TreeMap<>();for(Map.Entry<Integer,Integer> e:flags.entrySet())if(e.getKey()>=655360)global.put(e.getKey(),e.getValue());writeInts(out,global);writeStrings(out,viewedCg);writeStrings(out,viewedScripts);}
    public void loadPersistent(DataInput in)throws IOException{if(in.readInt()!=0x55335031)throw new IOException("Invalid Unity persistent state");Map<Integer,Integer> restored=readInts(in);for(int key:restored.keySet())if(key<655360)throw new IOException("Local flag in persistent state");flags.putAll(restored);readStrings(in,viewedCg);readStrings(in,viewedScripts);}
    public void save(DataOutput out)throws IOException{
        out.writeInt(0x55334831);writeInts(out,flags);writeInts(out,colors);out.writeInt(works.size());for(Map.Entry<String,String> e:new TreeMap<>(works).entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}writeStrings(out,viewedCg);writeStrings(out,viewedScripts);
        for(Layer l:layers)writeLayer(out,l);writePanel(out,back);writePanel(out,fore);writePanel(out,menu);writePanel(out,fogBack);writePanel(out,fogFore);writePanels(out,putDefs);writePanels(out,menuDefs);
        for(String s:new String[]{speaker,body,movie,yesJump,noJump,cancelJump,hitJump,musicJump,transitionMask})out.writeUTF(s);
        for(boolean b:new boolean[]{textVisible,menuWaiting,choiceWaiting,exitRequested,persistRequested,fullscreen,altText,transitionRequested,rain,waitVoice})out.writeBoolean(b);
        for(int n:new int[]{hovered,pressed,movieGeneration,saveRequest,loadRequest,autosaveGeneration,sceneRevision,faceX,faceY,putTarget,putX,putY,fromX,fromY,transitionRevision,sakura,sakuraX,sakuraY,fog,nextSound})out.writeInt(n);
        out.writeLong(ticket);out.writeLong(elapsedMillis);out.writeLong(transitionDuration);out.writeFloat(transitionFuzziness);out.writeInt(screenBreakType);out.writeBoolean(expandedMessageMenu);out.writeInt(selectClick);out.writeInt(selectHover);out.writeInt(cameraX);out.writeInt(cameraY);out.writeInt(cameraShake);out.writeLong(cameraShakeElapsed);out.writeBoolean(backlogActive);out.writeInt(backlogPosition);writePanel(out,selectSlots);writePanels(out,selectEntries);out.writeInt(lastSelectEntry);out.writeLong(flashElapsed);out.writeLong(flashDuration);out.writeInt(flashPhase);out.writeLong(endingRemaining);out.writeBoolean(musicGallery);out.writeBoolean(musicFinished);writeStrings(out,musicTracks);out.writeInt(musicTitles.size());for(Map.Entry<Integer,String> e:musicTitles.entrySet()){out.writeInt(e.getKey());out.writeUTF(e.getValue());}
        for(int i=0;i<6;i++){out.writeUTF(audio[i]);out.writeInt(audioGeneration[i]);out.writeBoolean(audioLoop[i]);out.writeBoolean(audioActive[i]);out.writeLong(audioFade[i]);}writeStrings(out,loadedPack);writeQuads(out,actionEntity==null?Collections.emptyList():Collections.singletonList(actionEntity));
        out.writeInt(choices.size());for(Choice c:choices){out.writeUTF(c.label);out.writeUTF(c.caption);}out.writeInt(omitHistory?0:history.size());if(!omitHistory)for(History h:history){out.writeUTF(h.name);out.writeUTF(h.text);out.writeUTF(h.voice);out.writeUTF(h.script);out.writeInt(h.rollback.length);out.write(h.rollback);writeStrings(out,h.captures);}
        out.writeBoolean(qLayers!=null);if(qLayers!=null){for(Layer l:qLayers)writeLayer(out,l);writePanel(out,qBack);writePanel(out,qFore);writePanel(out,qFogBack);writePanel(out,qFogFore);out.writeInt(qSakura);out.writeInt(qSakuraX);out.writeInt(qSakuraY);out.writeInt(qFog);out.writeBoolean(qRain);out.writeUTF(qSpeaker);out.writeUTF(qBody);out.writeBoolean(qText);out.writeUTF(qLoopSound);out.writeInt(qWorks.size());for(Map.Entry<String,String> e:new TreeMap<>(qWorks).entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}}
    }
    public void load(DataInput in)throws IOException{
        if(in.readInt()!=0x55334831)throw new IOException("Invalid Unity host state");Map<Integer,Integer> saved=readInts(in),global=new HashMap<>();for(Map.Entry<Integer,Integer> e:flags.entrySet())if(e.getKey()>=655360)global.put(e.getKey(),e.getValue());for(Map.Entry<Integer,Integer> e:saved.entrySet()){int bank=e.getKey()>>>16;if((bank==97||bank==98)&&e.getValue()!=0)global.put(e.getKey(),1);}flags.clear();flags.putAll(saved);flags.putAll(global);colors.clear();colors.putAll(readInts(in));works.clear();for(int n=count(in,4096);n>0;n--)works.put(in.readUTF(),in.readUTF());readStrings(in,viewedCg);readStrings(in,viewedScripts);
        for(int i=0;i<layers.length;i++)layers[i]=readLayer(in);back=readPanel(in);fore=readPanel(in);menu=readPanel(in);fogBack=readPanel(in);fogFore=readPanel(in);readPanels(in,putDefs);readPanels(in,menuDefs);
        speaker=in.readUTF();body=in.readUTF();movie=in.readUTF();yesJump=in.readUTF();noJump=in.readUTF();cancelJump=in.readUTF();hitJump=in.readUTF();musicJump=in.readUTF();transitionMask=in.readUTF();
        textVisible=in.readBoolean();menuWaiting=in.readBoolean();choiceWaiting=in.readBoolean();exitRequested=in.readBoolean();persistRequested=in.readBoolean();fullscreen=in.readBoolean();altText=in.readBoolean();transitionRequested=in.readBoolean();rain=in.readBoolean();waitVoice=in.readBoolean();
        hovered=in.readInt();pressed=in.readInt();movieGeneration=in.readInt();saveRequest=in.readInt();loadRequest=in.readInt();autosaveGeneration=in.readInt();sceneRevision=in.readInt();faceX=in.readInt();faceY=in.readInt();putTarget=in.readInt();putX=in.readInt();putY=in.readInt();fromX=in.readInt();fromY=in.readInt();transitionRevision=in.readInt();sakura=in.readInt();sakuraX=in.readInt();sakuraY=in.readInt();fog=in.readInt();nextSound=in.readInt();ticket=in.readLong();elapsedMillis=in.readLong();transitionDuration=in.readLong();if(ticket<0||elapsedMillis<0||transitionDuration<0||putTarget<0||putTarget>1||nextSound<2||nextSound>4)throw new IOException("Invalid Unity host counters");
        transitionFuzziness=in.readFloat();if(!Float.isFinite(transitionFuzziness)||transitionFuzziness<0||transitionFuzziness>1)throw new IOException("Invalid Unity wipe fuzziness");screenBreakType=in.readInt();expandedMessageMenu=in.readBoolean();selectClick=in.readInt();selectHover=in.readInt();cameraX=in.readInt();cameraY=in.readInt();cameraShake=in.readInt();cameraShakeElapsed=in.readLong();backlogActive=in.readBoolean();backlogPosition=in.readInt();selectSlots=readPanel(in);readPanels(in,selectEntries);lastSelectEntry=in.readInt();flashElapsed=in.readLong();flashDuration=in.readLong();flashPhase=in.readInt();endingRemaining=in.readLong();musicGallery=in.readBoolean();musicFinished=in.readBoolean();musicTracks.clear();readStrings(in,musicTracks);musicTitles.clear();for(int n=count(in,1024);n>0;n--)musicTitles.put(in.readInt(),in.readUTF());historyJumpRequest=-1;dragButton=null;
        for(int i=0;i<6;i++){audio[i]=in.readUTF();audioGeneration[i]=in.readInt();audioLoop[i]=in.readBoolean();audioActive[i]=in.readBoolean();audioFade[i]=in.readLong();}loadedPack.clear();readStrings(in,loadedPack);List<Quad> pending=new ArrayList<>();readQuads(in,pending);if(pending.size()>1)throw new IOException("Invalid saved action entity");actionEntity=pending.isEmpty()?null:pending.get(0);
        choices.clear();for(int n=count(in,50);n>0;n--)choices.add(new Choice(in.readUTF(),in.readUTF()));history.clear();int historyBytes=0;for(int n=count(in,1000);n>0;n--){History h=new History(in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF());int size=count(in,HISTORY_LIMIT);historyBytes+=size;if(historyBytes>HISTORY_LIMIT)throw new IOException("Unity history memory limit");h.rollback=new byte[size];in.readFully(h.rollback);readStrings(in,h.captures);history.add(h);}
        qLayers=null;if(in.readBoolean()){qLayers=new Layer[layers.length];for(int i=0;i<layers.length;i++)qLayers[i]=readLayer(in);qBack=readPanel(in);qFore=readPanel(in);qFogBack=readPanel(in);qFogFore=readPanel(in);qSakura=in.readInt();qSakuraX=in.readInt();qSakuraY=in.readInt();qFog=in.readInt();qRain=in.readBoolean();qSpeaker=in.readUTF();qBody=in.readUTF();qText=in.readBoolean();qLoopSound=in.readUTF();qWorks.clear();for(int n=count(in,4096);n>0;n--)qWorks.put(in.readUTF(),in.readUTF());}lastPutQuad=put().quads.isEmpty()?null:put().quads.get(put().quads.size()-1);lastMenuQuad=null;lastPutText=null;
    }
    private static int count(DataInput in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IOException("Unity saved collection limit");return n;}
    private static void writeInts(DataOutput out,Map<Integer,Integer> map)throws IOException{out.writeInt(map.size());for(Map.Entry<Integer,Integer> e:new TreeMap<>(map).entrySet()){out.writeInt(e.getKey());out.writeInt(e.getValue());}}
    private static Map<Integer,Integer> readInts(DataInput in)throws IOException{Map<Integer,Integer> m=new HashMap<>();for(int n=count(in,100000);n>0;n--)m.put(in.readInt(),in.readInt());return m;}
    private static void writeStrings(DataOutput out,Collection<String> values)throws IOException{out.writeInt(values.size());for(String s:values)out.writeUTF(s);}
    private static void readStrings(DataInput in,Collection<String> values)throws IOException{for(int n=count(in,100000);n>0;n--)values.add(in.readUTF());}
    private static void writeLayer(DataOutput out,Layer l)throws IOException{out.writeUTF(l.resource);out.writeUTF(l.second);for(int n:new int[]{l.x,l.y,l.pivotX,l.pivotY,l.scale,l.tint,l.priority,l.moveX0,l.moveY0,l.moveX1,l.moveY1,l.shakeAxis,l.shakeX,l.shakeY})out.writeInt(n);out.writeLong(l.moveTime);out.writeLong(l.moveElapsed);out.writeBoolean(l.ease);out.writeLong(l.shakeElapsed);}
    private static Layer readLayer(DataInput in)throws IOException{Layer l=new Layer();l.resource=in.readUTF();l.second=in.readUTF();l.x=in.readInt();l.y=in.readInt();l.pivotX=in.readInt();l.pivotY=in.readInt();l.scale=in.readInt();l.tint=in.readInt();l.priority=in.readInt();l.moveX0=in.readInt();l.moveY0=in.readInt();l.moveX1=in.readInt();l.moveY1=in.readInt();l.shakeAxis=in.readInt();l.shakeX=in.readInt();l.shakeY=in.readInt();l.moveTime=in.readLong();l.moveElapsed=in.readLong();l.ease=in.readBoolean();l.shakeElapsed=in.readLong();if(Math.abs((long)l.scale)>10000||l.moveTime<0||l.moveTime>86400000||l.moveElapsed<0)throw new IOException("Unity layer transform limit");return l;}
    private static void writeQuads(DataOutput out,List<Quad> qs)throws IOException{out.writeInt(qs.size());for(Quad q:qs){out.writeUTF(q.work);for(int n:new int[]{q.x,q.y,q.sx,q.sy,q.width,q.height,q.animation,q.animA,q.animB,q.animC})out.writeInt(n);out.writeBoolean(q.opaque);out.writeLong(q.animationStart);out.writeUTF(q.action);out.writeUTF(q.resource);out.writeInt(q.actionSpeed);writeStrings(out,q.packFrames);}}
    private static void readQuads(DataInput in,List<Quad> qs)throws IOException{for(int n=count(in,4096);n>0;n--){Quad q=new Quad();q.work=in.readUTF();q.x=in.readInt();q.y=in.readInt();q.sx=in.readInt();q.sy=in.readInt();q.width=in.readInt();q.height=in.readInt();q.animation=in.readInt();q.animA=in.readInt();q.animB=in.readInt();q.animC=in.readInt();q.opaque=in.readBoolean();q.animationStart=in.readLong();q.action=in.readUTF();q.resource=in.readUTF();q.actionSpeed=in.readInt();readStrings(in,q.packFrames);if(q.width<0||q.height<0||q.width>32768||q.height>32768||q.animationStart<0||q.actionSpeed<1)throw new IOException("Invalid Unity saved quad");qs.add(q);}}
    private static void writeTexts(DataOutput out,List<Text> ts)throws IOException{out.writeInt(ts.size());for(Text t:ts){for(int n:new int[]{t.x,t.y,t.leading,t.style,t.width,t.fontSize,t.color})out.writeInt(n);out.writeUTF(t.text);out.writeBoolean(t.loopTyping);out.writeLong(t.typingStart);}}
    private static void readTexts(DataInput in,List<Text> ts)throws IOException{for(int n=count(in,4096);n>0;n--){Text t=new Text();t.x=in.readInt();t.y=in.readInt();t.leading=in.readInt();t.style=in.readInt();t.width=in.readInt();t.fontSize=in.readInt();t.color=in.readInt();t.text=in.readUTF();t.loopTyping=in.readBoolean();t.typingStart=in.readLong();ts.add(t);}}
    private static void writePanel(DataOutput out,Panel p)throws IOException{writeQuads(out,p.quads);writeTexts(out,p.texts);out.writeInt(p.buttons.size());for(Button b:p.buttons){for(int n:new int[]{b.left,b.top,b.right,b.bottom,b.hoverSound,b.clickSound,b.sliderStart,b.sliderEnd,b.sliderSteps,b.sliderFlag,b.targetFlag,b.flagValue})out.writeInt(n);out.writeBoolean(b.sliderVertical);out.writeUTF(b.action);writeQuads(out,b.normal);writeQuads(out,b.hover);writeQuads(out,b.pressed);writeTexts(out,b.normalText);writeTexts(out,b.hoverText);writeTexts(out,b.pressedText);}}
    private static Panel readPanel(DataInput in)throws IOException{Panel p=new Panel();readQuads(in,p.quads);readTexts(in,p.texts);for(int n=count(in,4096);n>0;n--){Button b=new Button();b.left=in.readInt();b.top=in.readInt();b.right=in.readInt();b.bottom=in.readInt();b.hoverSound=in.readInt();b.clickSound=in.readInt();b.sliderStart=in.readInt();b.sliderEnd=in.readInt();b.sliderSteps=in.readInt();b.sliderFlag=in.readInt();b.targetFlag=in.readInt();b.flagValue=in.readInt();b.sliderVertical=in.readBoolean();b.action=in.readUTF();readQuads(in,b.normal);readQuads(in,b.hover);readQuads(in,b.pressed);readTexts(in,b.normalText);readTexts(in,b.hoverText);readTexts(in,b.pressedText);p.buttons.add(b);}return p;}
    private static void writePanels(DataOutput out,Map<Integer,Panel> panels)throws IOException{out.writeInt(panels.size());for(Map.Entry<Integer,Panel> e:new TreeMap<>(panels).entrySet()){out.writeInt(e.getKey());writePanel(out,e.getValue());}}
    private static void readPanels(DataInput in,Map<Integer,Panel> panels)throws IOException{panels.clear();for(int n=count(in,256);n>0;n--)panels.put(in.readInt(),readPanel(in));}
    @Override public boolean command(int command,CircusScrVm.Value[] a,CircusScrVm vm)throws IOException{
        currentVm=vm;
        switch(command){
        case 0:case 120:startAction(vm,n(a,0),false,true);return true;
        case 1:{Dc3rAnimation parsed=action(s(a,1));Quad q=new Quad();q.work=s(a,0);q.width=1024;q.height=576;q.action=s(a,1);q.animation=parsed.pack?7:6;if(parsed.pack){if(loadedPack.isEmpty())throw new IOException("Action requires loaded animation pack");q.packFrames.addAll(loadedPack);loadedPack.clear();}actionEntity=q;return true;}
        case 2:startAction(vm,1,true,false);return true;
        case 7:case 8:case 29:case 40:case 42:case 43:case 45:case 46:case 51:case 57:case 70:case 71:case 73:case 75:case 76:case 78:case 84:case 87:case 90:case 128:case 136:case 137:case 139:case 145:case 163:case 176:case 177:return true; // These IDs have no entry in the original ScriptActionData dispatch table.
        case 3:autosaveGeneration++;return true;
        case 4:writeFlag((5<<16)+1,n(a,0));return true;
        case 5:{String ref=s(a,0)+"/"+s(a,1);clearLayers();clearPut();clearMenu();setLayer(layers[0],ref,"",0,0,0,0,0);markCg(s(a,1));return true;}
        case 6:{String name=cgNames.get(n(a,0));if(name==null)throw new IOException("Unity CG id "+n(a,0));clearLayers();clearPut();clearMenu();setLayer(layers[0],"bg/"+name,"",0,0,0,0,0);markCg(name);return true;}
        case 9:layers[0].x=-n(a,0);layers[0].y=-n(a,1);return true;
        case 10:move(layers[0],-n(a,0),-n(a,1),n(a,2)*1000L/3,false);return true;
        case 11:if(n(a,0)!=-1)throw new IOException("Unsupported bup mode");for(int i=1;i<=10;i++)layers[i].resource=layers[i].second="";return true;
        case 12:return true; // Original CmdBup_b is empty.
        case 13:case 14:case 15:case 16:{int i=(command-13)*2+1;layers[i].x=layers[i+1].x=n(a,0);layers[i].y=layers[i+1].y=n(a,1);return true;}
        case 17:case 18:case 19:case 20:{int i=(command-17)*2+1;bust(i,a);if(!a[0].isString)bust(i+1,a);return true;}
        case 21:case 22:case 23:case 24:bust((command-21)*2+2,a);return true;
        case 25:case 26:case 27:case 28:{Layer l=layers[(command-25)*2+1];int end=l.x;l.x+=n(a,0)==1?-30:30;move(l,end,l.y,300,false);return true;}
        case 30:case 31:case 32:case 33:{int i=(command-30)*2+1;layers[i].scale=layers[i+1].scale=n(a,0);return true;}
        case 34:case 35:for(int i=0;i<4;i++)if((n(a,0)&(1<<i))!=0)for(int j=i*2+1;j<=i*2+2;j++){Layer l=layers[j];l.shakeAxis=command==34?1:2;l.shakeElapsed=0;l.shakeX=l.x;l.shakeY=l.y;}ticket++;vm.waitTime(300);return true;
        case 36:case 166:cameraShake=command==36?1:2;cameraShakeElapsed=0;ticket++;vm.waitTime(500);return true;
        case 37:storeQuickScene();hideText();return true;
        case 38:case 39:restoreQuickScene();returnTextWait=textVisible&&readFlag((99<<16)+19)!=4;yesJump=noJump=cancelJump="";return true;
        case 41:setLayer(layers[0],s(a,0)+"/"+s(a,1),"",0,0,0,0,0);return true;
        case 44:clearMenu();return true;
        case 47:copyBack();return true;
        case 48:ticket++;vm.waitTime(n(a,0)*200L);return true;
        case 49:hideText();return true;
        case 50:case 52:publish();return true;
        case 53:defaults();return true;
        case 54:loadRequest=saveSlot(readFlag((101<<16)+102));if(loadRequest<0)throw new IOException("No recent save slot");vm.waitHost();return true;
        case 55:saveRequest=saveSlot(readFlag((101<<16)+102));writeFlag((101<<16)+101,saveRequest);vm.waitHost();return true;
        case 56:publish();return true;
        case 58:return true; // Original command sends optional analytics only.
        case 59:exitRequested=true;vm.waitHost();return true;
        case 60:if(!a[0].isString){layers[12].resource=layers[12].second="";}else{setLayer(layers[12],"face/"+s(a,0),s(a,1).isEmpty()?"":"face/"+s(a,1),faceX,faceY,0,0,n(a,2));textVisible=true;}return true;
        case 61:faceX=n(a,0);faceY=n(a,1);return true;
        case 62:case 65:transition(vm,500,true);return true;
        case 63:case 66:transition(vm,n(a,0)*1000L/3,true);return true;
        case 64:transition(vm,n(a,0)*2000L/3,false);return true;
        case 67:case 68:clearLayers();clearPut();clearMenu();setLayer(layers[0],"bg/black","",0,0,0,0,0);transition(vm,command==67?500:n(a,0)*1000L/3,true);return true;
        case 69:if(transitionRequested)transition(vm,500,true);else publish();return true;
        case 72:flags.keySet().removeIf(k->k<655360);writeFlag((5<<16)+1,1);return true;
        case 74:case 89:return true; // VM owns flag assignment and branching.
        case 77:fogMode(n(a,0));return true;
        case 79:fromX=n(a,0);fromY=n(a,1);return true;
        case 80:fullscreen=true;return true;
        case 81:{int mode=n(a,0),value=n(a,1);if(mode==6){buildSaveMenu(value!=1);return true;}if(mode==7){if(value==1){backlogActive=true;backlogPosition=Math.max(0,history.size()-4);Dc3rBacklog.build(this);waitMenu(vm,true);}return true;}if(mode==11){if(value==1)playGalleryTrack();else if(value==2){musicGallery=false;musicFinished=false;}return true;}if(mode==12){if(value==0)buildMusicGallery();return true;}if(mode==210){startGrandEnding(vm);return true;}if(mode==18){if(value==1)persistRequested=true;return true;}if(mode==19){writeFlag((9<<16)+87,availableSaves.isEmpty()?1:0);return true;}if(mode!=7&&mode!=11&&mode!=12&&mode!=210)return true; // Original CmdGame logs and returns for every unhandled mode.
            throw new IOException("Unimplemented Unity game mode "+mode+","+value);}
        case 82:cancelJump=!a[0].isString||s(a,0).equals("-")?"":s(a,0);return true;
        case 83:hitJump=!a[0].isString||s(a,0).equals("-")?"":s(a,0);return true;
        case 85:musicJump=!a[0].isString||s(a,0).equals("-")?"":s(a,0);return true;
        case 86:noJump=s(a,0);return true;
        case 88:yesJump=s(a,0);return true;
        case 91:publish();ticket++;if(n(a,0)==0)vm.waitClick();else vm.waitTime(n(a,0)*1000L/3);return true;
        case 92:history.clear();return true;
        case 93:works.put("fog","system/"+s(a,0));return true;
        case 94:if(textAssets==null)throw new IOException("Unity animation pack reader unavailable");loadedPack.clear();for(String line:textAssets.read(s(a,0)).replace("\ufeff","").split("\\r?\\n"))if(!line.trim().isEmpty())loadedPack.add(line.trim());if(loadedPack.isEmpty())throw new IOException("Empty Unity animation pack "+s(a,0));return true;
        case 95:case 170:case 174:return true; // Empty original preload/waitbup bodies.
        case 96:transition(vm,n(a,0)*1000L/3,true);transitionMask=works.getOrDefault("work3","");return true;
        case 97:{Button b=new Button();b.left=n(a,0);b.top=n(a,1);b.right=n(a,2);b.bottom=n(a,3);b.action=a[4].isString?s(a,4):"";menu.buttons.add(b);return true;}
        case 99:{Button b=lastButton();b.sliderStart=n(a,0);b.sliderEnd=n(a,1);b.sliderSteps=n(a,2);b.sliderFlag=n(a,3);return true;}
        case 100:return true; // Original CmdMenubar1 is empty.
        case 98:animation(lastMenuQuad,a);return true;
        case 101:case 102:lastMenuQuad=quad(a,false,command==101);lastButton().hover.add(lastMenuQuad);return true;
        case 103:lastButton().hoverText.add(text(a,true));return true;
        case 104:menuDefs.put(n(a,0),menu.copy());clearMenu();return true;
        case 105:case 106:lastMenuQuad=quad(a,false,command==105);lastButton().pressed.add(lastMenuQuad);return true;
        case 107:lastButton().pressedText.add(text(a,true));return true;
        case 108:case 109:lastMenuQuad=quad(a,false,command==108);lastButton().normal.add(lastMenuQuad);return true;
        case 110:lastButton().normalText.add(text(a,true));return true;
        case 111:return true; // Keyboard shortcut assignment does not alter touch hit areas.
        case 112:lastButton().clickSound=n(a,0);lastButton().hoverSound=n(a,1);return true;
        case 113:if(audioActive[0])audioFade[0]=1000;return true;
        case 114:play(0,"",false);return true;
        case 115:movie=s(a,0);movieGeneration++;for(int i=0;i<6;i++)play(i,"",false);ticket++;vm.waitHost();return true;
        case 116:play(0,"bgm/"+s(a,0),true);return true;
        case 117:if(!audio[0].equals("bgm/"+s(a,0)))play(0,"bgm/"+s(a,0),true);return true;
        case 118:audioLoop[0]=n(a,0)!=0;return true;
        case 119:play(0,"bgm/"+(a[0].isString?s(a,0):String.format(Locale.ROOT,"bgm%03d",n(a,0))),false);return true;
        case 121:if(altText){if(put().texts.isEmpty())throw new IOException("Alternate text object missing");put().texts.get(0).text=s(a,1);}else{speaker=s(a,0);body=s(a,1);textVisible=true;publish();history.add(new History(speaker,body,audio[5],vm.scriptName()));if(history.size()>1000)history.remove(0);ticket++;vm.waitClick();captureHistory(vm);}return true;
        case 122:if(qLayers!=null){layers[0]=qLayers[0].copy();layers[0].moveTime=layers[0].moveElapsed=0;layers[0].shakeAxis=0;}return true;
        case 123:if(qLayers!=null)for(int i=1;i<11;i++)layers[i]=qLayers[i].copy();return true;
        case 124:if(qLayers!=null){fogBack=qFogBack.copy();fogFore=qFogFore.copy();fog=qFog;if(qWorks.containsKey("fog"))works.put("fog",qWorks.get("fog"));}return true;
        case 125:case 126:lastPutText=null;lastPutQuad=quad(a,true,command==125);put().quads.add(lastPutQuad);return true;
        case 127:if(lastPutText!=null){lastPutText.loopTyping=true;lastPutText.typingStart=elapsedMillis;}else animation(lastPutQuad,a);return true;
        case 129:clearPut();return true;
        case 130:putDefs.put(n(a,0),put().copy());clearPut();return true;
        case 131:putX=n(a,0);putY=n(a,1);return true;
        case 132:putTarget=0;return true;
        case 133:putTarget=1;return true;
        case 134:case 135:storeQuickScene();markScript(vm.scriptName());clearMenu();hideText();yesJump=noJump=cancelJump="";return true;
        case 138:rain=n(a,0)==1;return true;
        case 140:publish();return true;
        case 141:sakura=n(a,0);sakuraX=n(a,1);sakuraY=n(a,2);return true;
        case 142:autosaveGeneration++;return true;
        case 143:persistRequested=true;return true;
        case 144:{int count=n(a,0);if(count<0||count>1000)throw new IOException("Invalid Unity flash count");if(count>0){flashElapsed=0;flashPhase=0;flashDuration=count*150L-75;works.put("work4","system/white");addFlash();publish();ticket++;vm.waitTime(flashDuration);}return true;}
        case 146:{int type=n(a,0);clearLayers();clearPut();clearMenu();clearFog();setLayer(layers[0],"bg/black","",0,0,0,0,0);sakura=0;rain=false;transition(vm,type==4?2000:3000,true);screenBreakType=type;return true;}
        case 147:if(n(a,0)==0){sakura=0;rain=false;clearFog();}else if(n(a,0)==7)fogMode(n(a,1)==1?1:0);else throw new IOException("Unsupported Unity screen effect "+n(a,0));return true;
        case 148:case 149:markScript(vm.scriptName());clearMenu();hideText();yesJump=noJump=cancelJump="";altText=false;persistRequested=true;return true;
        case 150:{int speed=n(a,2);if(speed<=0)throw new IOException("Invalid background scroll speed");long time=Math.max(Math.abs((long)n(a,0)),Math.abs((long)n(a,1)))/speed*1000/12;move(layers[0],-n(a,0),-n(a,1),time,true);ticket++;vm.waitTime(time);return true;}
        case 151:clearMenu();choices.clear();return true;
        case 152:choices.add(new Choice(s(a,0),s(a,1)));return true;
        case 153:selectClick=n(a,0);selectHover=n(a,1);return true;
        case 155:selectClick=selectHover=-1;selectSlots=new Panel();selectEntries.clear();return true;
        case 156:{Button b=new Button();b.left=n(a,0);b.top=n(a,1);b.right=b.left+n(a,2);b.bottom=b.top+n(a,3);selectSlots.buttons.add(b);return true;}
        case 157:{int id=n(a,0);Panel p=new Panel();Quad q=new Quad();q.work=s(a,1);q.sx=n(a,2);q.sy=n(a,3);q.width=n(a,4);q.height=n(a,5);p.quads.add(q);Button b=new Button();b.action=s(a,6);p.buttons.add(b);selectEntries.put(id,p);lastSelectEntry=id;return true;}
        case 158:{Panel p=selectEntries.get(lastSelectEntry);if(p==null)throw new IOException("Selectstr without select entry");p.texts.clear();p.texts.add(text(a,true));return true;}
        case 154:colors.put(n(a,0),0xff000000|(n(a,1)&255)<<16|(n(a,2)&255)<<8|(n(a,3)&255));return true;
        case 159:for(int i=1;i<5;i++)if(audioActive[i])audioFade[i]=1000;return true;
        case 160:play(1,"se/"+s(a,0),true);return true;
        case 161:play(nextSound,"se/"+s(a,0),false);nextSound=nextSound==4?2:nextSound+1;return true;
        case 162:for(int i=1;i<5;i++)play(i,"",false);return true;
        case 164:lastPutText=text(a,false);lastPutQuad=null;put().texts.add(lastPutText);altText=true;return true;
        case 165:if(!s(a,1).equals("-")){int slot=n(a,0);if(slot<0||slot>2)throw new IOException("Unity sysgrp slot "+slot);works.put(slot==0?"mes":"sys"+slot,"system/"+s(a,1));}return true;
        case 167:{long time=Math.max(0,n(a,1)*1000L/120);move(layers[0],n(a,0),-n(a,1),time,false);ticket++;vm.waitTime(time);return true;}
        case 168:return true; // Original version command has no visual output.
        case 169:{String name=s(a,0).replace('\\','/'),folder=vm.sourceFile();if(folder==null||folder.isEmpty())folder=vm.scriptName();folder=folder.replace('\\','/');while(folder.startsWith("./"))folder=folder.substring(2);if(folder.toLowerCase(Locale.ROOT).endsWith(".inc"))folder="";if(folder.toLowerCase(Locale.ROOT).endsWith(".txt"))folder=folder.substring(0,folder.length()-4);if(name.equalsIgnoreCase("000silent24")||name.equalsIgnoreCase("000silent24.pcm"))play(5,"",false);else play(5,"voice/"+(name.indexOf('/')>=0||folder.isEmpty()?name:folder+"/"+name),false);return true;}
        case 171:play(5,"",false);return true;
        case 172:publish();ticket++;vm.waitTime(n(a,0)*1000L/3);return true;
        case 173:ticket++;vm.waitTime(n(a,0));return true;
        case 175:waitMenu(vm,true);return true;
        case 178:waitMenu(vm,false);return true;
        case 179:{Panel template=menuDefs.get(3);if(template==null||template.buttons.size()<choices.size()+1)throw new IOException("Original selection template missing");menu=template.copy();while(menu.buttons.size()>choices.size()+1)menu.buttons.remove(menu.buttons.size()-1);for(int i=0;i<choices.size();i++){Button b=menu.buttons.get(i+1);for(List<Text> texts:Arrays.asList(b.normalText,b.hoverText,b.pressedText))if(!texts.isEmpty())texts.get(0).text=choices.get(i).caption;}waitMenu(vm,false);choiceWaiting=true;return true;}
        case 180:if(audioActive[5]){waitVoice=true;ticket++;vm.waitHost();}return true;
        case 181:fullscreen=false;return true;
        case 182:{int type=n(a,0);if(type>=0&&type<=2){transition(vm,1000,true);if(type>0){works.put("work3","system/"+(type==1?"maskltr":"masktlbr"));transitionMask=works.get("work3");transitionFuzziness=.1f;}}return true;}
        case 183:case 184:case 185:works.put("work"+(command-182),"system/"+s(a,0));return true;
        case 186:{Panel t=menuDefs.get(2);if(t==null)throw new IOException("Original confirmation template missing");menu=t.copy();int dx=n(a,0),dy=n(a,1);for(Button b:menu.buttons){b.left+=dx;b.right+=dx;b.top+=dy;b.bottom+=dy;for(List<Quad> qs:Arrays.asList(b.normal,b.hover,b.pressed))for(Quad q:qs){q.x+=dx;q.y+=dy;}for(List<Text> ts:Arrays.asList(b.normalText,b.hoverText,b.pressedText))for(Text text:ts){text.x+=dx;text.y+=dy;}}if(!menu.buttons.isEmpty()){Button b=menu.buttons.get(0);int caption=n(a,2);if(caption<0||caption>=b.pressed.size())throw new IOException("Confirmation caption "+caption);b.normal.add(b.pressed.get(caption));b.pressed.clear();}waitMenu(vm,true);return true;}
        case 187:return true; // Steam achievement callback; no game-state dependency.
        default:return false;
        }
    }
}
