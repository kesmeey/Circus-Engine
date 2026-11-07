package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**Actual hidden HWND/GDI/InvalidateRect/WM_PAINT through the assembled graph.
 *Synthetic script/images only.405800 startup and audio are not simulated.*/
public final class Pc1WindowsServicesAudit {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void applyDirty(byte[] destination,byte[] source,Pc1Raster.Rect raw){int l=raw.left,t=raw.top,r=raw.right,b=raw.bottom;if(l>=r||t>=b){l=t=0;r=b=1;}for(int y=t;y<b;y++)System.arraycopy(source,(599-y)*2400+l*3,destination,(599-y)*2400+l*3,(r-l)*3);}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require explicit audited window.dll and pc1_gdi.dll");
        Pc1WindowsServices[] services={null};Pc1GraphicsPipeline[] graph={null};byte[][] expected={null};int[] paints={0};boolean[] callbackFence={false};
        Pc1WindowsWindow.NativeDispatcher dispatcher=new Pc1WindowsWindow.NativeDispatcher(){
            public Pc1WindowsWindow.Dispatch dispatch(long hwnd,int message,long w,long l)throws IOException{
                if(message==0xf&&services[0]!=null){
                    applyDirty(expected[0],graph[0].render().compositor().front().bgr24(),graph[0].render().compositor().rawDirty());
                    try{services[0].requireSnapshotBoundary();}catch(IOException required){callbackFence[0]=true;}
                    paints[0]++;return services[0].paint();
                }
                if(message>=0x400&&message<0x8000)throw new IOException("Audit requires unimplemented engine message"+message);
                if(message==0x113)throw new IOException("Audit requires unimplemented native timer dispatch");
                return Pc1WindowsWindow.Dispatch.unhandled();
            }
            public int quitFlag()throws IOException{return graph[0]==null?0:graph[0].runtime().graphicsMemory().readInt(0x94c688);}
        };
        try(Pc1WindowsWindow window=new Pc1WindowsWindow(Paths.get(args[0]),dispatcher);Pc1GdiGlyph font=new Pc1GdiGlyph(Paths.get(args[1]),window.hwnd())){
            Pc1RenderContextTests.Fixture fixture=new Pc1RenderContextTests.Fixture(font,null);Pc1WindowsServices.Consumers required=new Pc1WindowsServices.Consumers(){
                private IOException missing(){return new IOException("Audit has no physical audio/special/accelerated/direct-paint owner");}
                public void sound(int id,int mode)throws IOException{throw missing();}public void voice(int ptr)throws IOException{throw missing();}public void specialListItem(int visible,int data,boolean selected)throws IOException{throw missing();}public void accelerated(long dib,int[] rect)throws IOException{throw missing();}
            };
            Pc1WindowsServices platform=new Pc1WindowsServices(fixture.host.pc1,window,required);Pc1GraphicsPipeline pipeline=new Pc1GraphicsPipeline(fixture.host.pc1,new Pc1Compositor(window.dibRgb()),Pc1GlyphCache.fromMemory(font.fontMemory()),font,fixture.backend,platform);platform.bind(pipeline);services[0]=platform;graph[0]=pipeline;fixture.context=pipeline.render();fixture.initializeKnownScene();fixture.memory.writeInt(0x94c694,1);expected[0]=window.compatibleRgb().bgr24();
            check(window.windowInfo()[1]==0,"Self-authored native window remains hidden");platform.ensureEffectTimer();int token=fixture.memory.readInt(0x94c67c);platform.ensureEffectTimer();check(token!=0&&fixture.memory.readInt(0x94c67c)==token,"407080 uses actual SetTimer5 and retains canonical token on repeated call");check(window.killTimer(token)!=0,"Actual effect timer can be cancelled before audit pump");fixture.memory.writeInt(0x94c67c,0);
            byte[] before=window.compatibleRgb().bgr24();pipeline.render().drawInline(24,20,32,new byte[]{'A','B',0,0},7);check(!Arrays.equals(pipeline.render().compositor().front().bgr24(),window.dibRgb().bgr24()),"Actual GDI/native pixel kernel changes canonical front before explicit platform transfer");
            pipeline.render().compositor().present();check(Arrays.equals(before,window.compatibleRgb().bgr24())&&pipeline.render().compositor().invalidations().isEmpty(),"Required InvalidateRect leaf schedules painting without prematurely changing DDB or using a debug journal");
            check(window.postMessage(0xf,0,0)!=0,"Queue actual hidden WM_PAINT");check(platform.pumpMessages()==0&&paints[0]>0,"Actual405710 drain dispatches complete mapped native paint and reads canonical quit flag");
            check(Arrays.equals(expected[0],window.compatibleRgb().bgr24()),"All1.44MB actual DDB bytes match native dirty-only copies through production adapter");
            check(pipeline.render().compositor().rawDirty().left==800&&fixture.memory.readInt(0x4304e8)==800&&callbackFence[0],"Paint resets shared canonical dirty after EndPaint and forbids callback snapshots");
            check(Arrays.equals(window.dibRgb().bgr24(),pipeline.render().compositor().front().bgr24()),"Paint uploads actual canonical BGR24 front into independent DIB before copy");
            fixture.backend.images.put("bg",Pc1GraphicsResources.Source.rgb(Pc1RenderContextTests.solid(800,600,0x204060)));pipeline.render().path(0,new byte[]{'b','g'});pipeline.render().compositor().installRgb(21,Pc1RenderContextTests.solid(4,3,0xe0c040));
            for(int n=0;n<50;n++)fixture.memory.writeInt(Pc1Effects.PARTICLES+n*100+8,0);
            for(int[] row:new int[][]{{0,0},{8,1},{12,2},{16,3},{20,2},{36,4},{40,3},{56,0},{60,100},{64,0},{72,0},{76,0},{84,0},{88,0},{96,0}})fixture.memory.writeInt(Pc1Effects.PARTICLES+row[0],row[1]);
            for(int[] row:new int[][]{{Pc1Effects.KIND,1},{Pc1Effects.SPRITE_HANDLE,21},{Pc1Effects.ENABLED,1},{Pc1Effects.BUSY,0},{Pc1Effects.RECT_COUNT,0},{Pc1Effects.PREVIOUS_COUNT,0},{Pc1Effects.RECT_BANK,0},{Pc1Effects.TOGGLE,0},{Pc1Effects.WIND_MODE,0},{Pc1Effects.WIND,0}})fixture.memory.writeInt(row[0],row[1]);
            pipeline.timerEffects();byte[] timerFront=pipeline.render().compositor().front().bgr24();for(int y=3;y<6;y++)System.arraycopy(timerFront,(599-y)*2400+2*3,expected[0],(599-y)*2400+2*3,4*3);
            check(pipeline.render().compositor().front().rgb(2,3)==0x808050&&Arrays.equals(expected[0],window.compatibleRgb().bgr24()),"Full effect update/partial repair/opacity/GetDC/twoBitBlt/ReleaseDC chain changes exact actual DDB pixels");
            check(fixture.memory.readInt(Pc1Effects.RECT_COUNT)==1&&fixture.memory.readInt(Pc1Effects.RECT_BANK)==1&&fixture.memory.readInt(0x4304e8)==800,"Native effect rectangle bank advances and resets canonical dirty only after direct physical paint");
            platform.requireSnapshotBoundary();System.out.println("paintCount="+paints[0]);System.out.println("frontSha256="+Binary.sha256(pipeline.render().compositor().front().bgr24()));System.out.println("ddbSha256="+Binary.sha256(window.compatibleRgb().bgr24()));System.out.println("PASS "+checks+" actual Windows graphics services checks");
        }
    }
}
