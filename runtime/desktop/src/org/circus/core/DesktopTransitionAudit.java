package org.circus.core;
import java.nio.file.*;
public final class DesktopTransitionAudit {
    public static void main(String[] args)throws Exception{
        try(DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),true)){
            System.out.println(new DdTransitionAudit((DesktopDd)app.engine).run(Paths.get(args[1])));
        }
    }
}
