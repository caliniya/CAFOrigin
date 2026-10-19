package caliniya.caf;

import arc.backend.sdl.*;
import arc.files.*;

import java.io.*;

public class DesktopLauncher{
    public static void main(String[] args) throws Exception{
        File location = new File(DesktopLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File home = location.isFile() ? location.getParentFile() : new File(".");
        Fi data = new Fi(System.getProperty("caf.datadir", new File(home, "data").getPath()));

        SdlConfig config = new SdlConfig();
        config.title = "CAF";
        config.appName = "CAF";
        config.appIdentifier = "caliniya.caf";
        config.width = 1000;
        config.height = 700;
        new SdlApplication(new CAF(data), config);
    }
}
