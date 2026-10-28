package org.circus.runtime;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.circus.core.*;

/** Library only; the game's title and menus still come from its original scripts. */
public final class LauncherActivity extends Activity {
    static final String[] ROUTES={"森園立夏","芳乃シャルル","葛木姫乃","瑠川さら","陽ノ下葵","美琴","雪村すもも"};
    static final String[] ENTRIES={"DD_1_0611_A1","DD_2_1128_E1","DD_3_0211_A1","DD_4_0908_E1","DD_5_0924_B1","DD_6_1211_C1","DD_7_0121_A1"};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private GameLibrary library;private LinearLayout content;private String relink;private boolean loading;private ProgressDialog progress;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setFlags(1024,1024);if(state!=null)relink=state.getString("relink");try{library=new GameLibrary(this);}catch(Exception e){error(e);}}
    @Override protected void onResume(){super.onResume();show();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("relink",relink);}
    @Override protected void onDestroy(){if(progress!=null)progress.dismiss();worker.shutdown();super.onDestroy();}
    private void show(){if(library==null)return;ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(1);content.setPadding(32,18,32,18);content.setBackgroundColor(0xff101b2b);scroll.addView(content);scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(scroll);scroll.requestApplyInsets();
        label(content,"CIRCUS Runtime",27);label(content,"最近打开的游戏会保存在这里。游戏中双指点按或按返回键打开菜单，双指滑动可滚动列表。",15);
        LinearLayout actions=row(content);button(actions,"添加游戏目录",()->pick(null));button(actions,"关于",()->new AlertDialog.Builder(this).setTitle("0.13.1 Android 开发版").setMessage("支持 DC3DD、DC2DM、D.C.P.C DVD（2063）和 D.C.II P.C（5769）。新增 DC3R Unity 版的初步兼容，完整路线仍在验证。直接解释原版脚本、标题和菜单；不执行 Windows EXE。双指点按或返回键对应鼠标右键，列表支持双指滑动。APK 不含游戏资源。\n\n核心 GPL-3.0-only；PL_MPEG、GARbro 相关解码器为 MIT。其他引擎版本、Windows 注入式汉化仍需单独适配。").setPositiveButton("关闭",null).show());button(actions,"开发测试",()->startActivity(new Intent(this,MainActivity.class)));
        label(content,"最近打开",21);
        try{List<GameLibrary.Entry> entries=library.entries();if(entries.isEmpty())label(content,"尚未添加游戏。请选择包含 AdvData 或 DC3_Data 的游戏目录。",16);
            for(GameLibrary.Entry e:entries){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(16,10,16,10);card.setBackgroundColor(0xff203148);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,6,0,6);content.addView(card,p);label(card,e.title,20);label(card,Uri.decode(e.tree)+"\nEXE："+(e.executable.isEmpty()?"待选择":e.executable.equals("@none")?"仅资源数据":e.executable)+" · 文字："+e.encoding,13);LinearLayout buttons=row(card);button(buttons,"启动 · "+e.title,()->launch(e));button(buttons,"配置 · "+e.title,()->configure(e,false));button(buttons,"更多 · "+e.title,()->more(e));}
        }catch(Exception e){error(e);}
    }
    private void label(LinearLayout parent,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(0xffd6e7f6);v.setPadding(0,4,0,4);parent.addView(v);}
    private LinearLayout row(LinearLayout parent){LinearLayout row=new LinearLayout(this);parent.addView(row);return row;}
    private void button(LinearLayout row,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(3,3,3,3);row.addView(b,p);b.setOnClickListener(v->{if(!loading)action.run();});}
    private void launch(GameLibrary.Entry e){if(!e.configured){configure(e,true);return;}startActivity(new Intent(this,RuntimeActivity.class).putExtra("tree",e.tree).putExtra("library_id",e.id).putExtra("executable",e.executable).putExtra("encoding",e.encoding).putExtra("configuration",e.configuration));}
    private void more(GameLibrary.Entry e){new AlertDialog.Builder(this).setTitle(e.title).setItems(new String[]{"重新定位目录 / 恢复访问权限","从列表移除（保留游戏和存档）"},(d,n)->{if(n==0)pick(e.id);else try{library.remove(e.id);show();}catch(Exception ex){error(ex);}}).setNegativeButton("取消",null).show();}
    private void pick(String id){relink=id;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,10);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=10)return;String id=relink;relink=null;if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);GameLibrary.Entry e;if(id==null)e=library.add(uri.toString());else{library.relink(id,uri.toString());e=library.get(id);}show();configure(e,false);}catch(Exception e){error(e);}}
    interface Job<T>{T run()throws Exception;}interface Done<T>{void accept(T value)throws Exception;}
    private <T>void background(Job<T> job,Done<T> done){loading=true;progress=ProgressDialog.show(this,"读取游戏配置","正在检查目录和文件…",true,false);worker.execute(()->{try{T value=job.run();runOnUiThread(()->{complete();if(isDestroyed())return;try{done.accept(value);}catch(Exception e){error(e);}});}catch(Exception e){runOnUiThread(()->{complete();if(!isDestroyed())error(e);});}});}
    private void complete(){loading=false;if(progress!=null){progress.dismiss();progress=null;}}
    private void configure(GameLibrary.Entry e,boolean launchAfter){background(()->{GameResources files=new GameResources(new DocumentTree(getContentResolver(),Uri.parse(e.tree)));Profile profile=UnityCircusResources.candidate(files)?null:GameVersion.detect(files).profile();String id=profile==null?UnityCircusResources.ID:profile.id;if(!e.profile.isEmpty()&&!e.profile.equals(id))throw new IOException("所选目录的游戏版本与原记录不同，请作为新游戏添加");if(profile==null)new CircusScrScript("start",new UnityCircusResources(files).script("start"));return new Object[]{files,profile};},r->settings(e,(GameResources)r[0],(Profile)r[1],launchAfter));}
    private Spinner spinner(LinearLayout box,String title,List<String> labels,int selected){label(box,title,16);Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection(Math.max(0,selected));box.addView(s);return s;}
    private void settings(GameLibrary.Entry e,GameResources files,Profile profile,boolean launchAfter)throws IOException{
        if(profile==null){new AlertDialog.Builder(this).setTitle(e.title+" · DC3R Unity").setMessage("已识别 DC3R Unity 版资源和启动脚本。该版本直接读取 DC3_Data 中的资源包，文字为 UTF-8，无需选择 EXE 数据表。兼容功能仍在验证中。").setPositiveButton(launchAfter?"保存并启动":"保存",(d,w)->{try{e.profile=UnityCircusResources.ID;e.executable="@none";e.encoding="UTF-8";e.configuration="@none";library.configure(e);e.configured=true;show();if(launchAfter)launch(e);}catch(Exception error){error(error);}}).setNegativeButton("取消",null).show();return;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(24,8,24,8);ScrollView scroll=new ScrollView(this);scroll.addView(box);
        label(box,"所选 EXE 仅用于读取角色、姓名等数据表，不执行 Windows 程序。注入式汉化需要另外适配。",14);
        List<String> exes=new ArrayList<>(Arrays.asList("","@none")),exeLabels=new ArrayList<>(Arrays.asList("请选择 EXE 数据文件","仅使用资源数据（不读取 EXE）"));for(String path:files.executableCandidates()){exes.add(path);exeLabels.add(files.displayPath(path));}
        if(!exes.contains(e.executable)){exes.add(e.executable);exeLabels.add(e.executable+"（文件已丢失，请重新选择）");}
        Spinner exe=spinner(box,"EXE 数据文件",exeLabels,exes.indexOf(e.executable));if(exes.size()==2&&e.executable.isEmpty())exe.setSelection(1);
        List<String> encodings=Arrays.asList("auto","windows-31j","GBK");Spinner encoding=spinner(box,"脚本文字编码",Arrays.asList("自动识别已验证版本","日文 CP932","简体中文 GBK"),encodings.indexOf(e.encoding));
        List<String> configs=new ArrayList<>(Arrays.asList("","@none")),configLabels=new ArrayList<>(Arrays.asList("自动读取 uif_config.json","不使用字符替换表"));List<String> paths=new ArrayList<>(files.paths());Collections.sort(paths);for(String path:paths)if(path.endsWith(".json")){configs.add(path);configLabels.add(files.displayPath(path));}if(!configs.contains(e.configuration)){configs.add(e.configuration);configLabels.add(e.configuration+"（文件已丢失）");}
        Spinner config=spinner(box,"字符替换配置",configLabels,configs.indexOf(e.configuration));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(e.title+" · "+profile.id).setView(scroll).setPositiveButton(launchAfter?"保存并启动":"保存",null).setNegativeButton("取消",null).create();dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{if(loading)return;if(exe.getSelectedItemPosition()==0){Toast.makeText(this,"请选择 EXE，或明确选择仅使用资源数据",Toast.LENGTH_LONG).show();return;}String chosenExe=exes.get(exe.getSelectedItemPosition()),chosenEncoding=encodings.get(encoding.getSelectedItemPosition()),chosenConfig=configs.get(config.getSelectedItemPosition());
            background(()->{GameResources selected=new GameResources(new DocumentTree(getContentResolver(),Uri.parse(e.tree)),chosenExe);TextCodec codec=GameText.select(selected,profile,chosenEncoding,chosenConfig);ExecutableData.validate(new CircusResources(selected,profile),codec);return true;},ok->{e.profile=profile.id;e.executable=chosenExe;e.encoding=chosenEncoding;e.configuration=chosenConfig;library.configure(e);e.configured=true;dialog.dismiss();show();if(launchAfter)launch(e);});
        });
    }
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("无法完成操作").setMessage(e.getMessage()+"\n\n目录无法访问时，可在游戏的“更多”中重新定位。记录和存档不会因此删除。").setPositiveButton("关闭",null).show();}
}
