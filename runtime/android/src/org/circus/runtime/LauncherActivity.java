package org.circus.runtime;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Data-library shell. The title screen is executed from the original MES files. */
public final class LauncherActivity extends Activity {
    static final String[] ROUTES={"森園立夏","芳乃シャルル","葛木姫乃","瑠川さら","陽ノ下葵","美琴","雪村すもも"};
    static final String[] ENTRIES={"DD_1_0611_A1","DD_2_1128_E1","DD_3_0211_A1","DD_4_0908_E1","DD_5_0924_B1","DD_6_1211_C1","DD_7_0121_A1"};
    private String tree;private LinearLayout content;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setFlags(1024,1024);}
    @Override protected void onResume(){super.onResume();tree=getSharedPreferences("library",0).getString("game_tree",null);if(tree==null){tree=getSharedPreferences("MainActivity",0).getString("game_tree",null);if(tree!=null)getSharedPreferences("library",0).edit().putString("game_tree",tree).apply();}show();}
    private void show(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(1);content.setPadding(32,18,32,18);content.setBackgroundColor(0xff101b2b);scroll.addView(content);scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(scroll);scroll.requestApplyInsets();
        TextView title=new TextView(this);title.setText("CIRCUS Runtime");title.setTextSize(27);title.setTextColor(0xffe5f1ff);content.addView(title);
        TextView hint=new TextView(this);hint.setText(tree==null?"请选择含 AdvData 的游戏根目录（也能识别只包含一部游戏的外层文件夹）。游戏数据由你自行提供。":"已选择游戏目录。启动时会识别版本，已接入的版本由原版脚本显示标题和菜单。");hint.setTextColor(0xffa8bfd5);hint.setPadding(0,6,0,12);content.addView(hint);
        LinearLayout actions=row();button(actions,"选择数据目录",this::pick);button(actions,"启动游戏",()->launch("START",-1));
        LinearLayout extra=row();button(extra,"关于",()->new AlertDialog.Builder(this).setTitle("原版系统兼容开发版").setMessage("0.11 Android 开发版：四版本共用与 PC 0.11 相同的脚本、原版菜单、回看、鉴赏、姓名编辑及存档逻辑，Android Canvas 负责显示，触摸负责输入。支持 DC3DD、DC2DM、D.C.P.C DVD（2063）和 D.C.II P.C（5769）。返回键对应右键；原版滚动条可拖动，也可用双指上下滑动列表。字体和部分演出仍有平台差异，其他游戏版本需单独验证。APK 不含游戏资源。\n\n核心 GPL-3.0-only；MPEG 解码 PL_MPEG、图像和 XPCM 解码参考 GARbro（MIT）。").setPositiveButton("关闭",null).show());button(extra,"开发测试",()->startActivity(new Intent(this,MainActivity.class)));

    }
    private LinearLayout row(){LinearLayout row=new LinearLayout(this);content.addView(row);return row;}
    private void button(LinearLayout row,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(3,3,3,3);row.addView(b,p);b.setOnClickListener(v->action.run());}
    private void launch(String entry,int slot){if(tree==null){pick();return;}try{if(slot>=0&&!new GameSaves(this,tree).exists(slot)){Toast.makeText(this,"还没有此存档，请先开始游戏",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(this,RuntimeActivity.class).putExtra("tree",tree).putExtra("slot",slot).putExtra("original_system",true);if(entry!=null)i.putExtra("entry",entry);startActivity(i);}catch(Exception e){error(e);}}
    private void saves(){if(tree==null){pick();return;}try{GameSaves saves=new GameSaves(this,tree);String[] labels=new String[7];for(int i=0;i<7;i++)labels[i]=saves.label(i);new AlertDialog.Builder(this).setTitle("读取存档").setItems(labels,(d,i)->launch(null,i)).setNegativeButton("取消",null).show();}catch(Exception e){error(e);}}
    private void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,10);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=10||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);tree=uri.toString();getSharedPreferences("library",0).edit().putString("game_tree",tree).apply();show();}catch(Exception e){error(e);}}
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("无法完成操作").setMessage(e.getMessage()).setPositiveButton("关闭",null).show();}
}
