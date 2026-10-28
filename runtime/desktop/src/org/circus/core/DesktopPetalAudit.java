package org.circus.core;
import java.nio.file.*;
public final class DesktopPetalAudit {
    public static void main(String[] args)throws Exception{
        try(DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),true)){
            System.out.println(new DdPetalAudit((DesktopDd)app.engine).run(Paths.get(args[1]),args.length>2&&args[2].equals("replay")));
        }
    }
}
