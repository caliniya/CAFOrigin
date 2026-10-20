package caliniya.caf;

import arc.*;
import caliniya.caf.lagrange.*;
import caliniya.caf.platform.Platform;

import java.util.*;

public class CAF extends ApplicationCore{
    private final Platform platform;
    /** Ready-to-call mapping supplied by the launcher; core never loads the library. */
    public final LagrangeApi lagrange;

    public CAF(Platform platform){
        this.platform = Objects.requireNonNull(platform);
        lagrange = Objects.requireNonNull(platform.lagrange(), "The platform must supply a ready Lagrange mapping.");
    }

    @Override
    public void setup(){
        Core.settings.setAppName("CAF");
        Core.settings.setDataDirectory(Objects.requireNonNull(platform.dataDirectory()));
    }

    @Override
    public void update(){
        Core.graphics.clear(0.08f, 0.08f, 0.1f, 1f);
        super.update();
    }
}
