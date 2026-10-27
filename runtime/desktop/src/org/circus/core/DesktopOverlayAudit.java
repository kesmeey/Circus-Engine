package org.circus.core;
import java.nio.file.*;
public final class DesktopOverlayAudit {
    public static void main(String[] args)throws Exception{
        try(DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),true)){
            System.out.println(new DmOverlayAudit((DesktopLegacy)app.engine).run(Paths.get(args[1])));
        }
    }
}
