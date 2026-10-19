package caliniya.caf;

import arc.*;
import arc.files.*;


public class CAF extends ApplicationCore{
    private final Fi dataDirectory;

    public CAF(Fi dataDirectory){
        this.dataDirectory = dataDirectory;
    }

    @Override
    public void setup(){
        Core.settings.setAppName("CAF");
        Core.settings.setDataDirectory(dataDirectory);
    }

    @Override
    public void update(){
        Core.graphics.clear(0.08f, 0.08f, 0.1f, 1f);
        super.update();
    }
}
