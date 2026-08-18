package sui.k.als.agl;

oneway interface IAglCallback {
    void onRunning();
    void onFinished(int status, String message);
}
