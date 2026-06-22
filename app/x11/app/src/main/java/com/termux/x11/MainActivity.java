package com.termux.x11;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.KeyEvent.*;
import static android.view.WindowManager.LayoutParams.*;
import static com.termux.x11.CmdEntryPoint.ACTION_START;
import static com.termux.x11.LoriePreferences.ACTION_PREFERENCES_CHANGED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.window.OnBackInvokedDispatcher;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.math.MathUtils;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.color.DynamicColors;
import com.termux.x11.input.InputEventSender;
import com.termux.x11.input.InputStub;
import com.termux.x11.input.TouchInputHandler;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.x11.utils.X11ToolbarViewPager;

import java.util.Map;

import me.weishu.reflection.Reflection;

@SuppressLint("ApplySharedPref")
@SuppressWarnings({"deprecation", "unused"})
public class MainActivity extends AppCompatActivity {
    public static final String ACTION_STOP = "com.termux.x11.ACTION_STOP";
    public static final String ACTION_CUSTOM = "com.termux.x11.ACTION_CUSTOM";

    public static Handler handler = new Handler();
    FrameLayout frm;
    private TouchInputHandler mInputHandler;
    protected ICmdEntryInterface service = null;
    private Notification mNotification;
    private final int mNotificationId = 7897;
    NotificationManager mNotificationManager;
    static InputMethodManager inputMethodManager;
    private static boolean showIMEWhileExternalConnected = true;
    private static boolean externalKeyboardConnected = false;
    private View.OnKeyListener mLorieKeyListener;
    private long lastBackPressedAt = 0;
    private long mainScreenVisibleUntil = 0;
    private final Runnable hideMainScreen = () -> {
        mainScreenVisibleUntil = 0;
        clientConnectedStateChanged();
    };

    public static Prefs prefs = null;

    private static boolean oldFullscreen = false, oldHideCutout = false;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChangedListener = (__, key) -> onPreferencesChanged(key);

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_START.equals(intent.getAction())) {
                try {
                    onReceiveConnection(intent);
                } catch (Exception e) {
                }
            } else if (ACTION_STOP.equals(intent.getAction())) {
                finishAffinity();
            } else if (ACTION_PREFERENCES_CHANGED.equals(intent.getAction())) {
                if (!"additionalKbdVisible".equals(intent.getStringExtra("key")))
                    onPreferencesChanged("");
            } else if (ACTION_CUSTOM.equals(intent.getAction())) {
                mInputHandler.extractUserActionFromPreferences(prefs, intent.getStringExtra("what")).accept(0, true);
            }
        }
    };

    ViewTreeObserver.OnPreDrawListener mOnPredrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            if (!LorieView.connected())
                return false;

            finishStartupDraw();
            return true;
        }
    };

    private void finishStartupDraw() {
        View content = findViewById(android.R.id.content);
        content.getViewTreeObserver().removeOnPreDrawListener(mOnPredrawListener);
        content.invalidate();
    }

    @SuppressLint("StaticFieldLeak")
    private static MainActivity instance;

    public MainActivity() {
        instance = this;
    }

    public static Prefs getPrefs() {
        return prefs;
    }

    public static MainActivity getInstance() {
        return instance;
    }

    private void applyAlsDefaults() {
        SharedPreferences.Editor editor = prefs.get().edit();
        boolean changed = false;
        if (!"native".equals(prefs.displayResolutionMode.get())) {
            editor.putString("displayResolutionMode", "native");
            changed = true;
        }
        if (prefs.showAdditionalKbd.get() && !prefs.additionalKbdVisible.get()) {
            editor.putBoolean("additionalKbdVisible", true);
            changed = true;
        }
        if (changed)
            editor.commit();
    }

    private void showMainScreenBriefly() {
        mainScreenVisibleUntil = SystemClock.uptimeMillis() + 3000;
        handler.removeCallbacks(hideMainScreen);
        handler.postDelayed(hideMainScreen, 3000);
    }

    @Override
    @SuppressLint({"AppCompatMethod", "ObsoleteSdkInt", "ClickableViewAccessibility", "WrongConstant", "UnspecifiedRegisterReceiverFlag"})
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);

        prefs = new Prefs(this.getApplicationContext());
        applyAlsDefaults();
        int modeValue = Integer.parseInt(prefs.touchMode.get()) - 1;
        if (modeValue > 2)
            prefs.touchMode.put("1");

        oldFullscreen = prefs.fullscreen.get();
        oldHideCutout = prefs.hideCutout.get();

        prefs.get().registerOnSharedPreferenceChangeListener(preferencesChangedListener);

        getWindow().setFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | FLAG_KEEP_SCREEN_ON | FLAG_TRANSLUCENT_STATUS, 0);
        setContentView(R.layout.main_activity);

        frm = findViewById(R.id.frame);
        findViewById(R.id.preferences_button).setOnClickListener((l) -> startActivity(new Intent(this, LoriePreferences.class) {{ setAction(Intent.ACTION_MAIN); }}));
        findViewById(R.id.exit_button).setOnClickListener((l) -> finish());

        LorieView lorieView = findViewById(R.id.lorieView);
        View lorieParent = (View) lorieView.getParent();

        mInputHandler = new TouchInputHandler(this, new InputEventSender(lorieView));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                this::handleBackNavigation
            );
        }

        mLorieKeyListener = (v, k, e) -> {
            InputDevice dev = e.getDevice();
            boolean result = mInputHandler.sendKeyEvent(e);

            // Do not steal dedicated buttons from a full external keyboard.
            return result;
        };

        lorieParent.setOnTouchListener((v, e) -> {
            // Avoid batched MotionEvent objects and reduce potential latency.
            // For reference: https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features#rendering.
            if (e.getAction() == MotionEvent.ACTION_DOWN)
                lorieParent.requestUnbufferedDispatch(e);

            return mInputHandler.handleTouchEvent(lorieParent, lorieView, e);
        });
        lorieParent.setOnHoverListener((v, e) -> mInputHandler.handleTouchEvent(lorieParent, lorieView, e));
        lorieParent.setOnGenericMotionListener((v, e) -> mInputHandler.handleTouchEvent(lorieParent, lorieView, e));
        lorieView.setOnCapturedPointerListener((v, e) -> mInputHandler.handleTouchEvent(lorieView, lorieView, e));
        lorieParent.setOnCapturedPointerListener((v, e) -> mInputHandler.handleTouchEvent(lorieView, lorieView, e));
        lorieView.setOnKeyListener(mLorieKeyListener);

        lorieView.setCallback((screenWidth, screenHeight, inputTransform) ->
                mInputHandler.handleInputTransformChanged(screenWidth, screenHeight, inputTransform));

        registerReceiver(receiver, new IntentFilter(ACTION_START) {{
            addAction(ACTION_PREFERENCES_CHANGED);
            addAction(ACTION_STOP);
            addAction(ACTION_CUSTOM);
        }}, SDK_INT >= VERSION_CODES.TIRAMISU ? RECEIVER_EXPORTED : 0);

        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        // Listening for keyboard insets to manually resize content (replaces buggy ADJUST_RESIZE)
        ViewCompat.setOnApplyWindowInsetsListener(frm, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            boolean fs = prefs.fullscreen.get();
            v.setPadding(
                bars.left,
                fs ? 0 : bars.top,
                bars.right,
                ime.bottom + (fs ? 0 : bars.bottom)
            );
            return windowInsets;
        });

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotification = buildNotification();
        mNotificationManager.notify(mNotificationId, mNotification);

        if (tryConnect()) {
            final View content = findViewById(android.R.id.content);
            content.getViewTreeObserver().addOnPreDrawListener(mOnPredrawListener);
            handler.postDelayed(this::finishStartupDraw, 500);
        } else if (LorieView.connected()) {
            showMainScreenBriefly();
            clientConnectedStateChanged();
        }
        onPreferencesChanged("");

        toggleExtraKeys(false, false);

        if (SDK_INT >= VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PERMISSION_GRANTED
                && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 0);
        }

        onReceiveConnection(getIntent());
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideMainScreen);
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    void onReceiveConnection(Intent intent) {
        Bundle bundle = intent == null ? null : intent.getBundleExtra(null);
        IBinder ibinder = bundle == null ? null : bundle.getBinder(null);
        if (ibinder == null)
            return;

        service = ICmdEntryInterface.Stub.asInterface(ibinder);
        try {
            service.asBinder().linkToDeath(() -> {
                service = null;

                runOnUiThread(() -> { LorieView.connect(-1); clientConnectedStateChanged();} );
            }, 0);
        } catch (RemoteException ignored) {}

        try {
            if (service != null && service.asBinder().isBinderAlive()) {
                tryConnect();

                if (intent != getIntent())
                    getIntent().putExtra(null, bundle);
            }
        } catch (Exception e) {
        }
    }

    boolean tryConnect() {
        if (LorieView.connected())
            return false;

        if (service == null) {
            boolean sent = LorieView.requestConnection();
            handler.postDelayed(this::tryConnect, 250);
            return true;
        }

        try {
            ParcelFileDescriptor fd = service.getXConnection();
            if (fd != null) {
                LorieView.connect(fd.detachFd());
                showMainScreenBriefly();
                finishStartupDraw();
                getLorieView().triggerCallback();
                clientConnectedStateChanged();
                getLorieView().reloadPreferences(prefs);
            } else
                handler.postDelayed(this::tryConnect, 250);
        } catch (Exception e) {
            service = null;

            handler.postDelayed(this::tryConnect, 250);
        }
        return false;
    }

    void onPreferencesChanged(String key) {
        if ("additionalKbdVisible".equals(key))
            return;

        handler.removeCallbacks(this::onPreferencesChangedCallback);
        handler.postDelayed(this::onPreferencesChangedCallback, 100);
    }

    @SuppressLint("UnsafeIntentLaunch")
    void onPreferencesChangedCallback() {
        onWindowFocusChanged(hasWindowFocus());
        LorieView lorieView = getLorieView();

        mInputHandler.reloadPreferences(prefs);
        lorieView.reloadPreferences(prefs);

        setTerminalToolbarView();

        lorieView.triggerCallback();

        showIMEWhileExternalConnected = prefs.showIMEWhileExternalConnected.get();

        lorieView.requestLayout();
        lorieView.invalidate();

        for (StatusBarNotification notification: mNotificationManager.getActiveNotifications())
            if (notification.getId() == mNotificationId) {
                mNotification = buildNotification();
                mNotificationManager.notify(mNotificationId, mNotification);
            }
    }

    @Override
    public void onResume() {
        super.onResume();

        mNotification = buildNotification();
        mNotificationManager.notify(mNotificationId, mNotification);

        setTerminalToolbarView();
        getLorieView().requestFocus();
    }

    @Override
    public void onPause() {
        inputMethodManager.hideSoftInputFromWindow(getWindow().getDecorView().getRootView().getWindowToken(), 0);

        for (StatusBarNotification notification: mNotificationManager.getActiveNotifications())
            if (notification.getId() == mNotificationId)
                mNotificationManager.cancel(mNotificationId);

        super.onPause();
    }

    public LorieView getLorieView() {
        return findViewById(R.id.lorieView);
    }

    public ViewPager getTerminalToolbarViewPager() {
        return findViewById(R.id.terminal_toolbar_view_pager);
    }

    private void setTerminalToolbarView() {
        final ViewPager pager = getTerminalToolbarViewPager();
        ViewGroup parent = (ViewGroup) pager.getParent();

        boolean showNow = LorieView.connected() && prefs.showAdditionalKbd.get() && prefs.additionalKbdVisible.get();

        pager.setVisibility(showNow ? View.VISIBLE : View.INVISIBLE);

        if (showNow) {
            pager.setAdapter(new X11ToolbarViewPager.PageAdapter(this));
            pager.clearOnPageChangeListeners();
            pager.addOnPageChangeListener(new X11ToolbarViewPager.OnPageChangeListener(this));
            pager.bringToFront();
        } else {
            parent.removeView(pager);
            parent.addView(pager, 0);
            X11ToolbarViewPager.releaseKeyboardModifiers(getLorieView());
        }

        X11ToolbarViewPager.applyToolbarLayout(this);
        getLorieView().setContentInsets(0, 0, 0, 0);
        getLorieView().requestFocus();
    }

    public void toggleExtraKeys(boolean visible, boolean saveState) {
        boolean enabled = prefs.showAdditionalKbd.get();

        if (enabled && LorieView.connected() && saveState)
            prefs.additionalKbdVisible.put(visible);

        setTerminalToolbarView();
    }

    public void toggleExtraKeys() {
        toggleExtraKeys(getTerminalToolbarViewPager().getVisibility() != View.VISIBLE, true);
    }

    public boolean handleKey(KeyEvent e) {
        return mLorieKeyListener.onKey(getLorieView(), e.getKeyCode(), e);
    }

    @SuppressLint("ObsoleteSdkInt")
    Notification buildNotification() {
        NotificationCompat.Builder builder =  new NotificationCompat.Builder(this, getNotificationChannel(mNotificationManager))
                .setContentTitle("Termux:X11")
                .setSmallIcon(R.drawable.ic_x11_icon)
                .setContentText(getResources().getText(R.string.lorie_notification_content_text))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setSilent(true)
                .setShowWhen(false)
                .setColor(0xFF607D8B);
        return mInputHandler.setupNotification(prefs, builder).build();
    }

    private String getNotificationChannel(NotificationManager notificationManager){
        String channelId = getResources().getString(R.string.x11_app_name);
        String channelName = getResources().getString(R.string.x11_app_name);
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        if (SDK_INT >= VERSION_CODES.Q)
            channel.setAllowBubbles(false);
        notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    int orientation;

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation != orientation)
            inputMethodManager.hideSoftInputFromWindow(getWindow().getDecorView().getRootView().getWindowToken(), 0);

        orientation = newConfig.orientation;
        setTerminalToolbarView();
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Window window = getWindow();
        View decorView = window.getDecorView();
        boolean fullscreen = prefs.fullscreen.get();
        boolean hideCutout = prefs.hideCutout.get();

        if (oldHideCutout != hideCutout || oldFullscreen != fullscreen) {
            oldHideCutout = hideCutout;
            oldFullscreen = fullscreen;
            // For some reason cutout or fullscreen change makes layout calculations wrong and invalid.
            // I did not find simple and reliable way to fix it so it is better to start from the beginning.
            recreate();
            return;
        }

        int requestedOrientation;
        switch (prefs.forceOrientation.get()) {
            case "portrait": requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; break;
            case "landscape": requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; break;
            case "reverse portrait": requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT; break;
            case "reverse landscape": requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE; break;
            default: requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }

        if (getRequestedOrientation() != requestedOrientation)
            setRequestedOrientation(requestedOrientation);

        if (hasFocus) {
            if (SDK_INT >= VERSION_CODES.P) {
                if (hideCutout)
                    getWindow().getAttributes().layoutInDisplayCutoutMode = (SDK_INT >= VERSION_CODES.R) ?
                            LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS :
                            LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                else
                    getWindow().getAttributes().layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            }

            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
        }

        window.setFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | FLAG_KEEP_SCREEN_ON | FLAG_TRANSLUCENT_STATUS, 0);
        if (hasFocus) {
            if (fullscreen) {
                window.addFlags(FLAG_FULLSCREEN);
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            } else {
                window.clearFlags(FLAG_FULLSCREEN);
                decorView.setSystemUiVisibility(0);
            }
        }

        if (prefs.keepScreenOn.get())
            window.addFlags(FLAG_KEEP_SCREEN_ON);
        else
            window.clearFlags(FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP)
                handleBackNavigation();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        long now = SystemClock.uptimeMillis();
        if (now - lastBackPressedAt <= 1500) {
            lastBackPressedAt = 0;
            finish();
            return;
        }
        lastBackPressedAt = now;
        showKeyboard();
    }

    private void showKeyboard() {
        if (inputMethodManager == null)
            return;
        getLorieView().requestFocus();
        inputMethodManager.showSoftInput(getLorieView(), InputMethodManager.SHOW_FORCED);
    }

    public static boolean hasPipPermission(@NonNull Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOpsManager == null)
            return false;
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        else
            return appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    public void onUserLeaveHint() {
        if (prefs.PIP.get() && hasPipPermission(this)) {
            enterPictureInPictureMode();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
    }

    /**
     * Manually toggle soft keyboard visibility
     * @param context calling context
     */
    public static void toggleKeyboardVisibility(Context context) {
        if(inputMethodManager != null) {
            if (!externalKeyboardConnected || showIMEWhileExternalConnected)
                inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            else
                inputMethodManager.hideSoftInputFromWindow(getInstance().getWindow().getDecorView().getRootView().getWindowToken(), 0);

            getInstance().getLorieView().requestFocus();
        }
    }

    @SuppressWarnings("SameParameterValue")
    void clientConnectedStateChanged() {
        runOnUiThread(()-> {
            boolean connected = LorieView.connected();
            boolean showMainScreen = !connected || SystemClock.uptimeMillis() < mainScreenVisibleUntil;
            setTerminalToolbarView();
            findViewById(R.id.stub).setVisibility(showMainScreen?View.VISIBLE:View.INVISIBLE);
            getLorieView().setVisibility(connected?View.VISIBLE:View.INVISIBLE);

            // We should recover connection in the case if file descriptor for some reason was broken...
            if (!connected)
                tryConnect();
            else
                getLorieView().setPointerIcon(PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL));

            onWindowFocusChanged(hasWindowFocus());
        });
    }

    public static boolean isConnected() {
        if (getInstance() == null)
            return false;

        return LorieView.connected();
    }

    public static void getRealMetrics(DisplayMetrics m) {
        if (getInstance() != null &&
                getInstance().getLorieView() != null &&
                getInstance().getLorieView().getDisplay() != null)
            getInstance().getLorieView().getDisplay().getRealMetrics(m);
    }

    public static void setCapturingEnabled(boolean enabled) {
        if (getInstance() == null || getInstance().mInputHandler == null)
            return;

        getInstance().mInputHandler.setCapturingEnabled(enabled);
    }

    public boolean shouldInterceptKeys() {
        if (mInputHandler == null || !hasWindowFocus())
            return false;

        return mInputHandler.shouldInterceptKeys();
    }

    public void setExternalKeyboardConnected(boolean connected) {
        externalKeyboardConnected = connected;
        if (connected && !showIMEWhileExternalConnected)
            inputMethodManager.hideSoftInputFromWindow(getWindow().getDecorView().getRootView().getWindowToken(), 0);
        getLorieView().requestFocus();
    }
}
