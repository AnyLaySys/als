package com.termux.x11;
import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.os.Build.VERSION.SDK_INT;
import static android.system.Os.getuid;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;
import com.google.android.material.color.DynamicColors;
import com.termux.x11.utils.SamsungDexUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
@SuppressWarnings("deprecation")
public class LoriePreferences extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    static final String ACTION_PREFERENCES_CHANGED = "com.termux.x11.ACTION_PREFERENCES_CHANGED";
    static Handler handler = Looper.getMainLooper() != null ? new Handler(Looper.getMainLooper()) : null;
    private static Prefs prefs = null;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PREFERENCES_CHANGED.equals(intent.getAction()) && intent.getBooleanExtra("fromBroadcast", false))
                updatePreferencesLayout();
        }
    };
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) updatePreferencesLayout();
    }
    private void updatePreferencesLayout() {
        getSupportFragmentManager().getFragments().forEach(fragment -> {
            if (fragment instanceof LoriePreferenceFragment)
                ((LoriePreferenceFragment) fragment).updatePreferencesLayout();
        });
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setHomeButtonEnabled(false);
        }
        prefs = new Prefs(this);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.settings_container, new LoriePreferenceFragment(null)).commit();
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.app_bar), (v, insets) -> {
            v.setPadding(v.getPaddingLeft(), insets.getInsets(WindowInsetsCompat.Type.systemBars()).top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_container), (v, insets) -> {
            v.setPadding(insets.getInsets(WindowInsetsCompat.Type.systemBars()).left, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).right, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
            return insets;
        });
    }
    @SuppressLint("WrongConstant")
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_PREFERENCES_CHANGED);
        registerReceiver(receiver, filter, SDK_INT >= Build.VERSION_CODES.TIRAMISU ? RECEIVER_NOT_EXPORTED : 0);
    }
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
    private void showFragment(PreferenceFragmentCompat fragment) {
        getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right).replace(R.id.settings_container, fragment).addToBackStack(null).commit();
    }
    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        final LoriePreferenceFragment fragment = new LoriePreferenceFragment(pref.getFragment());
        fragment.setTargetFragment(caller, 0);
        showFragment(fragment);
        return true;
    }
    public static class LoriePreferenceFragment extends PreferenceFragmentCompat implements OnPreferenceChangeListener {
        final String root;
        private final Runnable updateLayout = this::updatePreferencesLayout;
        public LoriePreferenceFragment() {
            this(null);
        }
        public LoriePreferenceFragment(String root) {
            this.root = root;
        }
        private void setupPreference(Preference p) {
            p.setIconSpaceReserved(false);
            p.setOnPreferenceChangeListener(this);
            p.setPreferenceDataStore(prefs);
            int id;
            String key = p.getKey();
            if (key != null && (id = findId(key)) != 0) p.setTitle(getResources().getString(id));
            if (key != null && (id = findId(key + "_summary")) != 0)
                p.setSummary(getResources().getString(id));
            if (p instanceof ListPreference) {
                ListPreference list = (ListPreference) p;
                if (key != null && prefs.keys.containsKey(key)) {
                    list.setEntries(Objects.requireNonNull(prefs.keys.get(key)).asList().getEntries());
                    list.setEntryValues(prefs.keys.get(key).asList().getValues());
                }
                list.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            }
            if (p instanceof PreferenceGroup) {
                PreferenceGroup group = (PreferenceGroup) p;
                for (int i = 0; i < group.getPreferenceCount(); i++)
                    setupPreference(group.getPreference(i));
            }
        }
        @Override
        public void onResume() {
            super.onResume();
            LoriePreferences activity = (LoriePreferences) getActivity();
            if (activity != null) {
                ActionBar actionBar = activity.getSupportActionBar();
                if (actionBar != null) actionBar.setTitle(getPreferenceScreen().getTitle());
            }
        }
        @SuppressLint("DiscouragedApi")
        int findId(String name) {
            return getResources().getIdentifier("lorie_pref_" + name, "string", getContext().getPackageName());
        }
        @Override
        @SuppressLint("ApplySharedPref")
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            getPreferenceManager().setPreferenceDataStore(prefs);
            if ((Integer.parseInt(prefs.touchMode.get()) - 1) > 2) prefs.touchMode.put("1");
            setPreferencesFromResource(R.xml.preferences, root == null ? "main" : root);
            int id;
            PreferenceScreen screen = getPreferenceScreen();
            if ((id = findId(screen.getKey())) != 0) screen.setTitle(getResources().getString(id));
            setupPreference(screen);
            setSummary("displayStretch", R.string.lorie_pref_summary_requiresExactOrCustom);
            setSummary("adjustResolution", R.string.lorie_pref_summary_requiresExactOrCustom);
            setSummary("scaleTouchpad", R.string.lorie_pref_summary_requiresTrackpadAndNative);
            if (!SamsungDexUtils.available()) setVisible("dexMetaKeyCapture", false);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setVisible("hideCutout", false);
        }
        private void setSummary(CharSequence key, int disabled) {
            Preference pref = findPreference(key);
            if (pref != null)
                pref.setSummaryProvider(p -> p.isEnabled() ? null : getResources().getString(disabled));
        }
        private void setVisible(CharSequence key, boolean value) {
            Preference p = findPreference(key);
            if (p != null) p.setVisible(value);
        }
        private void setEnabled(CharSequence key, boolean value) {
            Preference p = findPreference(key);
            if (p != null) p.setEnabled(value);
        }
        @SuppressWarnings("ConstantConditions")
        void updatePreferencesLayout() {
            if (getContext() == null) return;
            for (PrefsProto.Preference prefInfo : prefs.keys.values()) {
                Preference p = findPreference(prefInfo.key);
                if (p == null) continue;
                if (p instanceof ListPreference) {
                    ((ListPreference) p).setValue(prefs.getString(prefInfo.key, (String) prefInfo.defValue));
                } else if (p instanceof SwitchPreferenceCompat) {
                    ((SwitchPreferenceCompat) p).setChecked(prefs.getBoolean(prefInfo.key, (Boolean) prefInfo.defValue));
                } else if (p instanceof EditTextPreference) {
                    ((EditTextPreference) p).setText(prefs.getString(prefInfo.key, (String) prefInfo.defValue));
                } else if (p instanceof SeekBarPreference) {
                    ((SeekBarPreference) p).setValue(prefs.getInt(prefInfo.key, (Integer) prefInfo.defValue));
                }
            }
            String displayResMode = prefs.displayResolutionMode.get();
            setVisible("displayScale", displayResMode.contentEquals("scaled"));
            setVisible("displayResolutionExact", displayResMode.contentEquals("exact"));
            setVisible("displayResolutionCustom", displayResMode.contentEquals("custom"));
            boolean displayStretchEnabled = "exact".contentEquals(prefs.displayResolutionMode.get()) || "custom".contentEquals(prefs.displayResolutionMode.get());
            setEnabled("displayStretch", displayStretchEnabled);
            setEnabled("adjustResolution", displayStretchEnabled);
            setEnabled("scaleTouchpad", "1".equals(prefs.touchMode.get()) && !"native".equals(prefs.displayResolutionMode.get()));
            boolean requestNotificationPermissionVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(requireContext(), POST_NOTIFICATIONS) == PERMISSION_DENIED;
            setVisible("requestNotificationPermission", requestNotificationPermissionVisible);
        }
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            updatePreferencesLayout();
        }
        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
        }
        @Override
        public boolean onPreferenceTreeClick(@NonNull Preference p) {
            if (p.getKey() == null) return super.onPreferenceTreeClick(p);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && "requestNotificationPermission".contentEquals(p.getKey())) {
                ActivityCompat.requestPermissions(requireActivity(), new String[]{POST_NOTIFICATIONS}, 101);
                return true;
            }
            updatePreferencesLayout();
            return super.onPreferenceTreeClick(p);
        }
        @SuppressLint("ApplySharedPref")
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String key = preference.getKey();
            handler.removeCallbacks(updateLayout);
            handler.postDelayed(updateLayout, 50);
            if ("displayScale".contentEquals(key)) {
                int scale = (Integer) newValue;
                if (scale % 10 != 0) {
                    scale = Math.round(((float) scale) / 10) * 10;
                    ((SeekBarPreference) preference).setValue(scale);
                    return false;
                }
            }
            if ("displayResolutionCustom".contentEquals(key)) {
                String value = (String) newValue;
                try {
                    String[] resolution = value.split("x");
                    int width = Integer.parseInt(resolution[0]);
                    int height = Integer.parseInt(resolution[1]);
                    if (width <= 0 || height <= 0) throw new NumberFormatException();
                } catch (NumberFormatException | PatternSyntaxException ignored) {
                    Toast.makeText(getActivity(), R.string.lorie_toast_wrong_resolution_format, Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            if ("showAdditionalKbd".contentEquals(key) && (Boolean) newValue)
                prefs.additionalKbdVisible.put(true);
            requireContext().sendBroadcast(new Intent(ACTION_PREFERENCES_CHANGED) {{
                putExtra("key", key);
                putExtra("fromBroadcast", true);
                setPackage(requireContext().getPackageName());
            }});
            return true;
        }
        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            super.onDisplayPreferenceDialog(preference);
        }
    }
    public static class Receiver extends BroadcastReceiver {
        private static final IBinder iface = new IRemoteCmdImterface.Stub() {
            @Override
            public void exit(int code, String output) {
                System.out.println(output);
                CmdEntryPoint.handler.post(() -> System.exit(code));
            }
        };
        public Receiver() {
            super();
        }
        private static void help() {
            System.err.print("termux-x11-preference [list] {key:value} [{key2:value2}]...");
            System.exit(0);
        }
        @Keep
        @SuppressLint("WrongConstant")
        public static void main(String[] args) {
            ParcelFileDescriptor in = ParcelFileDescriptor.adoptFd(0);
            Intent i = new Intent("com.termux.x11.CHANGE_PREFERENCE");
            Bundle bundle = new Bundle();
            boolean inputIsFile = !android.system.Os.isatty(in.getFileDescriptor());
            in.detachFd();
            bundle.putBinder(null, iface);
            i.setPackage("com.termux.x11");
            i.putExtra(null, bundle);
            if (getuid() == 0 || getuid() == 2000) i.setFlags(0x00400000);
            if (inputIsFile && System.in != null) {
                Scanner scanner = new Scanner(System.in);
                String line;
                String[] v;
                while (scanner.hasNextLine()) {
                    line = scanner.nextLine();
                    if (!line.contains("=")) help();
                    v = line.split("=");
                    if (v[0].startsWith("\"") && v[0].endsWith("\""))
                        v[0] = v[0].substring(1, v[0].length() - 1);
                    if (v[1].startsWith("\"") && v[1].endsWith("\""))
                        v[1] = v[1].substring(1, v[1].length() - 1);
                    i.putExtra(v[0], v[1]);
                }
            }
            for (String a : args) {
                if ("list".equals(a)) {
                    i.putExtra("list", "");
                } else if (a != null && a.contains(":")) {
                    String[] v = a.split(":");
                    i.putExtra(v[0], v[1]);
                } else help();
            }
            CmdEntryPoint.handler.post(() -> CmdEntryPoint.sendBroadcast(i));
            CmdEntryPoint.handler.postDelayed(() -> {
                System.err.println("Failed to obtain response from app.");
                System.exit(1);
            }, 5000);
            Looper.loop();
        }
        @Override
        public IBinder peekService(Context myContext, Intent service) {
            return super.peekService(myContext, service);
        }
        @SuppressLint("ApplySharedPref")
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent != null ? intent.getBundleExtra(null) : null;
            IBinder ibinder = bundle != null ? bundle.getBinder(null) : null;
            IRemoteCmdImterface remote = ibinder != null ? IRemoteCmdImterface.Stub.asInterface(ibinder) : null;
            try {
                if (intent != null && intent.getExtras() != null) {
                    Prefs p = (MainActivity.getInstance() != null) ? new Prefs(MainActivity.getInstance()) : (prefs != null ? prefs : new Prefs(context));
                    if (intent.getStringExtra("list") != null) {
                        StringBuilder result = new StringBuilder();
                        for (PrefsProto.Preference pref : p.keys.values()) {
                            if (pref.type == String.class)
                                result.append("\"").append(pref.key).append("\"=\"").append(pref.asString().get()).append("\"\n");
                            else if (pref.type == int.class)
                                result.append("\"").append(pref.key).append("\"=\"").append(pref.asInt().get()).append("\"\n");
                            else if (pref.type == boolean.class)
                                result.append("\"").append(pref.key).append("\"=\"").append(pref.asBoolean().get()).append("\"\n");
                            else if (pref.type == String[].class) {
                                String[] entries = context.getResources().getStringArray(pref.asList().entries);
                                String[] values = context.getResources().getStringArray(pref.asList().values);
                                String value = pref.asList().get();
                                int index = Arrays.asList(values).indexOf(value);
                                if (index != -1) value = entries[index];
                                result.append("\"").append(pref.key).append("\"=\"").append(value).append("\"\n");
                            }
                        }
                        sendResponse(remote, 0, 2, result.substring(0, result.length() - 1));
                        return;
                    }
                    SharedPreferences.Editor edit = p.get().edit();
                    for (String key : intent.getExtras().keySet()) {
                        if (key == null) continue;
                        String newValue = intent.getStringExtra(key);
                        if (newValue == null) continue;
                        switch (key) {
                            case "displayResolutionCustom": {
                                try {
                                    String[] resolution = newValue.split("x");
                                    int width = Integer.parseInt(resolution[0]);
                                    int height = Integer.parseInt(resolution[1]);
                                    if (width <= 0 || height <= 0)
                                        throw new NumberFormatException();
                                } catch (NumberFormatException | PatternSyntaxException ignored) {
                                    sendResponse(remote, 1, 1, "displayResolutionCustom: Wrong resolution format.");
                                    return;
                                }
                                edit.putString("displayResolutionCustom", newValue);
                                break;
                            }
                            case "tc_displayScale": {
                                edit.putInt("displayScale", Math.round(Float.parseFloat(newValue) * (float) p.displayScale.get()));
                                break;
                            }
                            default: {
                                PrefsProto.Preference pref = p.keys.get(key);
                                if (pref != null && pref.type == boolean.class) {
                                    edit.putBoolean(key, "true".contentEquals(newValue));
                                    if ("showAdditionalKbd".contentEquals(key) && "true".contentEquals(newValue))
                                        edit.putBoolean("additionalKbdVisible", true);
                                } else if (pref != null && pref.type == int.class) {
                                    try {
                                        edit.putInt(key, Integer.parseInt(newValue));
                                    } catch (NumberFormatException |
                                             PatternSyntaxException exception) {
                                        sendResponse(remote, 1, 4, key + ": failed to parse integer: " + exception);
                                        return;
                                    }
                                } else if (pref != null && pref.type == String[].class) {
                                    PrefsProto.ListPreference _p = (PrefsProto.ListPreference) pref;
                                    String[] entries = _p.getEntries();
                                    String[] values = _p.getValues();
                                    int index = Arrays.asList(entries).indexOf(newValue);
                                    if (index == -1 && _p.entries != _p.values)
                                        index = Arrays.asList(values).indexOf(newValue);
                                    if (index != -1) {
                                        edit.putString(key, values[index]);
                                        break;
                                    }
                                    sendResponse(remote, 1, 1, key + ": can not be set to \"" + newValue + "\", possible options are " + Arrays.toString(entries) + (_p.entries != _p.values ? " or " + Arrays.toString(values) : ""));
                                    return;
                                } else {
                                    sendResponse(remote, 1, 4, key + ": unrecognised option");
                                    return;
                                }
                            }
                        }
                        Intent intent0 = new Intent(ACTION_PREFERENCES_CHANGED);
                        intent0.putExtra("key", key);
                        intent0.putExtra("fromBroadcast", true);
                        intent0.setPackage(context.getPackageName());
                        context.sendBroadcast(intent0);
                    }
                    edit.commit();
                }
                sendResponse(remote, 0, 2, "Done");
            } catch (Exception e) {
                sendResponse(remote, 1, 4, e.toString());
            }
        }
        void sendResponse(IRemoteCmdImterface remote, int status, int oldStatus, String text) {
            if (remote != null) {
                try {
                    remote.exit(status, text);
                } catch (RemoteException ex) {
                }
            } else if (isOrderedBroadcast()) {
                setResultCode(oldStatus);
                setResultData(text);
            }
        }
    }
    @SuppressLint("ApplySharedPref")
    public static class PrefsProto extends PreferenceDataStore {
        protected Context ctx;
        protected SharedPreferences preferences;
        protected PrefsProto(Context ctx) {
            this.ctx = ctx;
            preferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        }
        @Override
        public void putBoolean(String k, boolean v) {
            preferences.edit().putBoolean(k, v).commit();
        }
        @Override
        public boolean getBoolean(String k, boolean d) {
            return preferences.getBoolean(k, d);
        }
        @Override
        public void putString(String k, @Nullable String v) {
            preferences.edit().putString(k, v).commit();
        }
        @Override
        public void putStringSet(String k, @Nullable Set<String> v) {
            preferences.edit().putStringSet(k, v).commit();
        }
        @Override
        public void putInt(String k, int v) {
            preferences.edit().putInt(k, v).commit();
        }
        @Override
        public void putLong(String k, long v) {
            preferences.edit().putLong(k, v).commit();
        }
        @Override
        public void putFloat(String k, float v) {
            preferences.edit().putFloat(k, v).commit();
        }
        @Nullable
        @Override
        public String getString(String k, @Nullable String d) {
            return preferences.getString(k, d);
        }
        @Nullable
        @Override
        public Set<String> getStringSet(String k, @Nullable Set<String> ds) {
            return preferences.getStringSet(k, ds);
        }
        @Override
        public int getInt(String k, int d) {
            return preferences.getInt(k, d);
        }
        @Override
        public long getLong(String k, long d) {
            return preferences.getLong(k, d);
        }
        @Override
        public float getFloat(String k, float d) {
            return preferences.getFloat(k, d);
        }
        public SharedPreferences get() {
            return preferences;
        }
        public static class Preference {
            protected final String key;
            protected final Class<?> type;
            protected final Object defValue;
            protected Preference(String key, Class<?> class_, Object default_) {
                this.key = key;
                this.type = class_;
                this.defValue = default_;
            }
            public ListPreference asList() {
                return (ListPreference) this;
            }
            public StringPreference asString() {
                return (StringPreference) this;
            }
            public IntPreference asInt() {
                return (IntPreference) this;
            }
            public BooleanPreference asBoolean() {
                return (BooleanPreference) this;
            }
        }
        public class BooleanPreference extends Preference {
            public BooleanPreference(String key, boolean defValue) {
                super(key, boolean.class, defValue);
            }
            public boolean get() {
                return preferences.getBoolean(key, (boolean) defValue);
            }
            public void put(boolean v) {
                preferences.edit().putBoolean(key, v).commit();
            }
        }
        public class IntPreference extends Preference {
            public IntPreference(String key, int defValue) {
                super(key, int.class, defValue);
            }
            public int get() {
                return preferences.getInt(key, (int) defValue);
            }
        }
        public class StringPreference extends Preference {
            public StringPreference(String key, String defValue) {
                super(key, String.class, defValue);
            }
            public String get() {
                return preferences.getString(key, (String) defValue);
            }
        }
        public class ListPreference extends Preference {
            private final int entries, values;
            public ListPreference(String key, String defValue, int entries, int values) {
                super(key, String[].class, defValue);
                this.entries = entries;
                this.values = values;
            }
            public String get() {
                return preferences.getString(key, (String) defValue);
            }
            public void put(String v) {
                preferences.edit().putString(key, v).commit();
            }
            public String[] getEntries() {
                return getArrayItems(entries, ctx.getResources());
            }
            public String[] getValues() {
                return getArrayItems(values, ctx.getResources());
            }
            private String[] getArrayItems(int resourceId, Resources resources) {
                ArrayList<String> itemList = new ArrayList<>();
                try (TypedArray typedArray = resources.obtainTypedArray(resourceId)) {
                    for (int i = 0; i < typedArray.length(); i++) {
                        int type = typedArray.getType(i);
                        if (type == TypedValue.TYPE_STRING) {
                            itemList.add(typedArray.getString(i));
                        } else if (type == TypedValue.TYPE_REFERENCE) {
                            int resIdOfArray = typedArray.getResourceId(i, 0);
                            itemList.addAll(Arrays.asList(resources.getStringArray(resIdOfArray)));
                        }
                    }
                }
                return itemList.toArray(new String[0]);
            }
        }
    }
}
