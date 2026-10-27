import org.circus.core.*;
import java.nio.file.*;

public final class LegacyInputAuditMain {
    public static void main(String[] args)throws Exception {
        Path output=Paths.get(args[1]);System.setProperty("dc.saveRoot",output.resolve("saves").toString());
        GameResources files=new GameResources(new LocalGameStorage(Paths.get(args[0])));
        CircusResources data=new CircusResources(files,GameVersion.detect(files).profile());
        DesktopLegacy engine=new DesktopLegacy(data,GameText.detect(files,data.profile),true);
        System.out.print(new LegacyInputAudit(engine).run(output));
    }
}
