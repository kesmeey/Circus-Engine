package org.circus.core;
import java.nio.file.*;
public final class DesktopGalleryAudit {
    public static void main(String[] args)throws Exception{try(DesktopRuntime runtime=new DesktopRuntime(Paths.get(args[0]),true)){System.out.print(new DdGalleryAudit((DesktopDd)runtime.engine).run(Paths.get(args[1])));}}
}
