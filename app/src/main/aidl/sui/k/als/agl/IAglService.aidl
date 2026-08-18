package sui.k.als.agl;

import android.view.Surface;
import sui.k.als.agl.IAglCallback;

oneway interface IAglService {
    void start(int backend, String configuration, String workDir, int consolePid, in Surface surface, float refreshRate, IAglCallback callback);
    void setSurface(in Surface surface, float refreshRate);
    void clearSurface(float refreshRate);
    void pointer(float x, float y, int buttons);
    void scroll(float x, float y);
    void key(int scanCode, boolean down);
    void stop();
}
