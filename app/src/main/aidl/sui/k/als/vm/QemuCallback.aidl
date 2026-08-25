package sui.k.als.vm;

oneway interface QemuCallback {
    void running(long token);
    void exited(long token, int status);
    void failed(long token, String error);
}
