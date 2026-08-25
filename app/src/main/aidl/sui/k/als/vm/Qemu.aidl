package sui.k.als.vm;

import android.view.Surface;
import sui.k.als.vm.QemuCallback;

interface Qemu {
    void start(long token, String backend, String workDir, String configuration, int consolePid, in Surface surface, float refreshRate, QemuCallback callback);
    void setSurface(long token, in Surface surface, float refreshRate);
    void pointer(long token, float x, float y, int buttons);
    void scroll(long token, float x, float y);
    void key(long token, int scanCode, boolean down);
    void stop(long token);
}
