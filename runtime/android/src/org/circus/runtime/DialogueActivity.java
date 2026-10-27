package org.circus.runtime;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.util.AtomicFile;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.circus.core.*;

/** Android presentation adapter exercising actual DD bindings with original test data. */
public final class DialogueActivity extends Activity {
    private final Handler handler=new Handler();
    private DialogueHost host;private MesVm vm;private TextView title,speaker,text;
    private boolean active;private MesVm displayedVm;private long displayedTicket=-1;
    private final Runnable pump=()->pump();
    private MesVm.Library library(){return name->{try(InputStream in=getAssets().open("demo/native-dialogue.mes")){return new MesScript(name,Binary.read(in,1024*1024),Profiles.get("dc3dd-a5a8"));}};}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(14,23,37));root.setPadding(24,24,24,16);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(24+i.getSystemWindowInsetLeft(),24+i.getSystemWindowInsetTop(),24+i.getSystemWindowInsetRight(),16+i.getSystemWindowInsetBottom());return i;});
        title=label(16,0xffa9c5dd);root.addView(title);
        speaker=label(23,0xff9bc6ba);root.addView(speaker);
        text=label(26,Color.WHITE);text.setPadding(8,24,8,24);ScrollView scroll=new ScrollView(this);scroll.addView(text);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(this);
        for(String caption:new String[]{"继续","保存正文","读取正文","重置正文","返回"}){
            Button b=new Button(this);b.setText(caption);buttons.addView(b,new LinearLayout.LayoutParams(0,-2,1));
            b.setOnClickListener(v->{switch(caption){case "继续":advance();break;case "保存正文":save();break;case "读取正文":load();break;case "重置正文":reset();break;default:finish();}});
        }
        root.addView(buttons);setContentView(root);root.requestApplyInsets();reset();
    }
    private TextView label(int size,int color){TextView v=new TextView(this);v.setTextColor(color);v.setTextSize(size);return v;}
    private void reset(){try{host=new DialogueHost(TextCodec.identity());vm=new MesVm(library(),host,"native-dialogue");displayedVm=null;pump();}catch(Exception e){failure(e);}}
    private void pump(){
        handler.removeCallbacks(pump);if(vm==null)return;vm.run(1000);
        String state=vm.status()==MesVm.Status.WAIT_HOST?(host.waitReason()==8?"等待显示完成":"等待点击"):vm.status()==MesVm.Status.HALTED?"测试结束":vm.status().name();
        title.setText("DC3DD 正文指令 · 自制测试数据 · "+state);speaker.setText(host.shownSpeaker);
        text.setText(vm.status()==MesVm.Status.FAULT?vm.error():host.shownText);
        if(active&&vm.status()==MesVm.Status.WAIT_HOST&&(displayedVm!=vm||displayedTicket!=host.waitTicket())){
            displayedVm=vm;displayedTicket=host.waitTicket();final MesVm current=vm;final long ticket=displayedTicket;
            // TextView renders the whole string. Acknowledge on the next animation frame,
            // using this VM identity and ticket so a load/restart invalidates old callbacks.
            text.postOnAnimation(()->{if(!active||vm!=current)return;try{if(current.signalHost(ticket,MesVm.HostSignal.TEXT_COMPLETE))pump();}catch(IOException e){failure(e);}});
        }
        if(active&&vm.status()==MesVm.Status.RUNNING)handler.postDelayed(pump,16);
    }
    private void advance(){try{if(vm!=null)vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE);pump();}catch(IOException e){failure(e);}}
    private void save(){try{AtomicFile file=new AtomicFile(new File(getFilesDir(),"dialogue.cvs"));FileOutputStream out=null;try{byte[] b=vm.snapshot();out=file.startWrite();out.write(b);file.finishWrite(out);}catch(IOException e){file.failWrite(out);throw e;}Toast.makeText(this,"正文状态已保存",Toast.LENGTH_SHORT).show();}catch(Exception e){failure(e);}}
    private void load(){try{byte[] bytes;try(InputStream in=new AtomicFile(new File(getFilesDir(),"dialogue.cvs")).openRead()){bytes=Binary.read(in,4*1024*1024);}DialogueHost candidate=new DialogueHost(TextCodec.identity());MesVm restored=new MesVm(library(),candidate,"native-dialogue");restored.restore(bytes);host=candidate;vm=restored;displayedVm=null;pump();}catch(Exception e){failure(e);}}
    private void failure(Exception e){new AlertDialog.Builder(this).setTitle("正文执行停止").setMessage(e.getMessage()).setPositiveButton("关闭",null).show();}
    @Override protected void onResume(){super.onResume();active=true;displayedVm=null;pump();}
    @Override protected void onPause(){active=false;handler.removeCallbacks(pump);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
