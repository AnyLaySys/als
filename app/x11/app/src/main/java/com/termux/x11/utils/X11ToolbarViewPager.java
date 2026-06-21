package com.termux.x11.utils;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.termux.x11.LorieView;
import com.termux.x11.MainActivity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class X11ToolbarViewPager {
    public static void applyToolbarLayout(MainActivity activity) {
        KeyboardView.applyToolbarLayout(activity);
    }

    public static boolean handleBack(MainActivity activity) {
        return KeyboardView.handleBack(activity);
    }

    public static void releaseKeyboardModifiers(LorieView view) {
        KeyboardView.releaseModifiers(view);
    }

    public static class PageAdapter extends PagerAdapter {
        final MainActivity mActivity;

        public PageAdapter(MainActivity activity) {
            this.mActivity = activity;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @SuppressLint("ClickableViewAccessibility")
        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup collection, int position) {
            View layout = new KeyboardView(mActivity);
            collection.addView(layout);
            return layout;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
            collection.removeView((View) view);
        }
    }

    public static class OnPageChangeListener extends ViewPager.SimpleOnPageChangeListener {
        final MainActivity act;

        public OnPageChangeListener(MainActivity activity) {
            this.act = activity;
        }

        @Override
        public void onPageSelected(int position) {
            act.getLorieView().requestFocus();
        }
    }

    private static class KeyboardView extends View {
        private static final String UP = "\u2191";
        private static final String DOWN = "\u2193";
        private static final String LEFT = "\u2190";
        private static final String RIGHT = "\u2192";
        private static final String[][] COMPACT_ROWS = {
                {"Esc", "F1", "F2", "F3", "\u00b7", "F4", "F5", "F6", "Del"},
                {"Shift", "F7", "F8", "F9", UP, "F10", "F11", "F12", "Back"},
                {"Tab", "Ctrl", "Alt", LEFT, DOWN, RIGHT, "Home", "End", "Enter"}
        };
        private static final String[][] FULL_ROWS = {
                {"Esc", "F1", "F2", "F3", "F4", "F5", "F6", "", "F7", "F8", "F9", "F10", "F11", "F12", "Del"},
                {"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "Back"},
                {"Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]", "\\"},
                {"Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "Enter"},
                {"Shift", "Z", "X", "C", "V", "B", "N", "M", ",", ".", UP, "/"},
                {"Ctrl", "Alt", "Home", " ", "End", LEFT, DOWN, RIGHT}
        };
        private static final Map<String, Integer> KEY_CODES = new HashMap<>();
        private static final Map<String, Integer> PRINTABLE_KEY_CODES = new HashMap<>();
        private static final Map<String, String> SHIFT_SYMBOLS = new HashMap<>();
        private static boolean ctrlActive;
        private static boolean shiftActive;
        private static boolean altActive;
        private static boolean capsActive;
        private static boolean fullKeyboardVisible;
        private static boolean floating;
        private static int keyboardOffsetX;
        private static int keyboardOffsetY;

        static {
            putKey("Esc", KeyEvent.KEYCODE_ESCAPE);
            putKey("Tab", KeyEvent.KEYCODE_TAB);
            putKey("Enter", KeyEvent.KEYCODE_ENTER);
            putKey("Back", KeyEvent.KEYCODE_DEL);
            putKey("Del", KeyEvent.KEYCODE_FORWARD_DEL);
            putKey("Home", KeyEvent.KEYCODE_MOVE_HOME);
            putKey("End", KeyEvent.KEYCODE_MOVE_END);
            putKey(UP, KeyEvent.KEYCODE_DPAD_UP);
            putKey(DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
            putKey(LEFT, KeyEvent.KEYCODE_DPAD_LEFT);
            putKey(RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT);
            for (int i = 1; i <= 12; i++)
                putKey("F" + i, KeyEvent.KEYCODE_F1 + i - 1);
            for (char c = 'A'; c <= 'Z'; c++)
                putPrintable(String.valueOf(c), KeyEvent.KEYCODE_A + c - 'A');
            for (char c = '0'; c <= '9'; c++)
                putPrintable(String.valueOf(c), KeyEvent.KEYCODE_0 + c - '0');
            putPrintable("`", KeyEvent.KEYCODE_GRAVE);
            putPrintable("-", KeyEvent.KEYCODE_MINUS);
            putPrintable("=", KeyEvent.KEYCODE_EQUALS);
            putPrintable("[", KeyEvent.KEYCODE_LEFT_BRACKET);
            putPrintable("]", KeyEvent.KEYCODE_RIGHT_BRACKET);
            putPrintable("\\", KeyEvent.KEYCODE_BACKSLASH);
            putPrintable(";", KeyEvent.KEYCODE_SEMICOLON);
            putPrintable("'", KeyEvent.KEYCODE_APOSTROPHE);
            putPrintable(",", KeyEvent.KEYCODE_COMMA);
            putPrintable(".", KeyEvent.KEYCODE_PERIOD);
            putPrintable("/", KeyEvent.KEYCODE_SLASH);
            putPrintable(" ", KeyEvent.KEYCODE_SPACE);
            putSymbol("`", "~");
            putSymbol("1", "!");
            putSymbol("2", "@");
            putSymbol("3", "#");
            putSymbol("4", "$");
            putSymbol("5", "%");
            putSymbol("6", "^");
            putSymbol("7", "&");
            putSymbol("8", "*");
            putSymbol("9", "(");
            putSymbol("0", ")");
            putSymbol("-", "_");
            putSymbol("=", "+");
            putSymbol("[", "{");
            putSymbol("]", "}");
            putSymbol("\\", "|");
            putSymbol(";", ":");
            putSymbol("'", "\"");
            putSymbol(",", "<");
            putSymbol(".", ">");
            putSymbol("/", "?");
        }

        private final MainActivity activity;
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final SparseArray<KeyTouch> touches = new SparseArray<>();
        private final int touchSlop;

        KeyboardView(MainActivity activity) {
            super(activity);
            this.activity = activity;
            touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
            setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setFocusable(false);
            setClickable(true);
            setBackgroundColor(Color.TRANSPARENT);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(9f * activity.getResources().getDisplayMetrics().scaledDensity);
            setOnHoverListener((v, e) -> true);
            setOnGenericMotionListener((v, e) -> true);
        }

        static int getToolbarHeight(MainActivity activity) {
            if (!fullKeyboardVisible)
                return Math.round(18f * activity.getResources().getDisplayMetrics().density * rows().length);
            View content = activity.findViewById(android.R.id.content);
            return content != null && content.getHeight() > 0 ? content.getHeight() : activity.getResources().getDisplayMetrics().heightPixels;
        }

        static void releaseModifiers(LorieView view) {
            if (view == null)
                return;
            if (ctrlActive)
                view.sendKeyEvent(0, KeyEvent.KEYCODE_CTRL_LEFT, false);
            if (shiftActive)
                view.sendKeyEvent(0, KeyEvent.KEYCODE_SHIFT_LEFT, false);
            if (altActive)
                view.sendKeyEvent(0, KeyEvent.KEYCODE_ALT_LEFT, false);
            ctrlActive = false;
            shiftActive = false;
            altActive = false;
            capsActive = false;
        }

        static void applyToolbarLayout(MainActivity activity) {
            ViewPager pager = activity.getTerminalToolbarViewPager();
            if (pager == null)
                return;
            ViewGroup.LayoutParams layoutParams = pager.getLayoutParams();
            layoutParams.width = floating ? getFloatingWidth(activity) : ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = getToolbarHeight(activity);
            pager.setLayoutParams(layoutParams);
            pager.setBackgroundColor(Color.TRANSPARENT);
            pager.setTranslationX(floating ? keyboardOffsetX : 0);
            pager.setTranslationY(floating ? keyboardOffsetY : 0);
            activity.getLorieView().setContentInsets(0, 0, 0, 0);
        }

        static int getFloatingWidth(MainActivity activity) {
            if (!fullKeyboardVisible)
                return Math.round(360f * activity.getResources().getDisplayMetrics().density);
            View content = activity.findViewById(android.R.id.content);
            int width = content != null && content.getWidth() > 0 ? content.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
            return width / 2;
        }

        static boolean handleBack(MainActivity activity) {
            if (!fullKeyboardVisible && !floating)
                return false;
            fullKeyboardVisible = false;
            floating = false;
            applyToolbarLayout(activity);
            ViewPager pager = activity.getTerminalToolbarViewPager();
            if (pager != null)
                pager.invalidate();
            return true;
        }

        private static void putKey(String label, int keyCode) {
            KEY_CODES.put(label, keyCode);
        }

        private static void putPrintable(String label, int keyCode) {
            PRINTABLE_KEY_CODES.put(label.toLowerCase(Locale.US), keyCode);
        }

        private static void putSymbol(String label, String shifted) {
            SHIFT_SYMBOLS.put(label, shifted);
        }

        private static String[][] rows() {
            return fullKeyboardVisible ? FULL_ROWS : COMPACT_ROWS;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            String[][] rows = rows();
            float rowHeight = getHeight() / (float) rows.length;
            for (int r = 0; r < rows.length; r++)
                drawRow(canvas, rows[r], r, r * rowHeight, rowHeight);
        }

        private void drawRow(Canvas canvas, String[] row, int rowIndex, float top, float rowHeight) {
            float total = rowWeight(row);
            float x = 0;
            for (int c = 0; c < row.length; c++) {
                String label = row[c];
                float width = getWidth() * keyWeight(label) / total;
                rect.set(x, top, x + width, top + rowHeight);
                boolean active = isPressed(rowIndex, c) || modifierActive(label);
                String text = displayLabel(label);
                if (!text.isEmpty()) {
                    textPaint.setColor(Color.GRAY);
                    Paint.FontMetrics fm = textPaint.getFontMetrics();
                    canvas.drawText(text, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, textPaint);
                }
                x += width;
            }
        }

        private boolean isPressed(int row, int col) {
            for (int i = 0; i < touches.size(); i++) {
                KeyTouch touch = touches.valueAt(i);
                if (touch.row == row && touch.col == col)
                    return true;
            }
            return false;
        }

        private float rowWeight(String[] row) {
            float total = 0;
            for (String label : row)
                total += keyWeight(label);
            return total;
        }

        private float keyWeight(String label) {
            if (!fullKeyboardVisible)
                return 1f;
            if (" ".equals(label))
                return 4.2f;
            if ("Ctrl".equals(label) || "Alt".equals(label) || "Home".equals(label) || "End".equals(label))
                return 1.2f;
            return 1f;
        }

        private String displayLabel(String label) {
            if (isControlKey(label))
                return "";
            if (isModifier(label) || label.length() > 1)
                return label;
            String base = label.toLowerCase(Locale.US);
            if (shiftActive) {
                String shifted = SHIFT_SYMBOLS.get(base);
                return shifted != null ? shifted : label.toUpperCase(Locale.US);
            }
            if (capsActive && Character.isLetter(label.charAt(0)))
                return label.toUpperCase(Locale.US);
            return label;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    startTouch(event, event.getActionIndex());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    moveTouches(event);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    finishTouch(event.getPointerId(event.getActionIndex()), true, event);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    finishAll();
                    return true;
                default:
                    return true;
            }
        }

        private void startTouch(MotionEvent event, int index) {
            KeyRef ref = findKey(event.getX(index), event.getY(index));
            if (ref == null)
                return;
            KeyTouch touch = new KeyTouch();
            touch.pointerId = event.getPointerId(index);
            touch.row = ref.row;
            touch.col = ref.col;
            touch.label = ref.label;
            touch.downRawX = rawX(event, index);
            touch.downRawY = rawY(event, index);
            touch.lastRawX = touch.downRawX;
            touch.lastRawY = touch.downRawY;
            touch.startOffsetX = floating ? keyboardOffsetX : 0;
            touch.startOffsetY = floating ? keyboardOffsetY : 0;
            touches.put(touch.pointerId, touch);
            if (getParent() != null)
                getParent().requestDisallowInterceptTouchEvent(true);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (isModifier(touch.label)) {
                setModifier(touch.label, true);
            } else if (!isControlKey(touch.label)) {
                sendKey(touch.label);
                startRepeat(touch);
            }
            invalidate();
        }

        private void moveTouches(MotionEvent event) {
            for (int i = 0; i < touches.size(); i++) {
                KeyTouch touch = touches.valueAt(i);
                if (!isControlKey(touch.label))
                    continue;
                int index = event.findPointerIndex(touch.pointerId);
                if (index < 0)
                    continue;
                float rawX = rawX(event, index);
                float rawY = rawY(event, index);
                float dx = rawX - touch.downRawX;
                float dy = rawY - touch.downRawY;
                if (!touch.dragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                    touch.dragging = true;
                    floating = true;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    keyboardOffsetX = touch.startOffsetX + Math.round(dx);
                    keyboardOffsetY = touch.startOffsetY + Math.round(dy);
                    touch.lastRawX = rawX;
                    touch.lastRawY = rawY;
                    applyToolbarLayout(activity);
                    invalidate();
                }
                if (touch.dragging) {
                    keyboardOffsetX += Math.round(rawX - touch.lastRawX);
                    keyboardOffsetY += Math.round(rawY - touch.lastRawY);
                    touch.lastRawX = rawX;
                    touch.lastRawY = rawY;
                    ViewPager pager = activity.getTerminalToolbarViewPager();
                    if (pager != null) {
                        pager.setTranslationX(keyboardOffsetX);
                        pager.setTranslationY(keyboardOffsetY);
                    }
                    invalidate();
                }
            }
        }

        private void finishTouch(int pointerId, boolean tap, MotionEvent event) {
            KeyTouch touch = touches.get(pointerId);
            if (touch == null)
                return;
            handler.removeCallbacks(touch.repeat);
            if (isModifier(touch.label))
                setModifier(touch.label, false);
            if (tap && isControlKey(touch.label)) {
                int index = event.findPointerIndex(pointerId);
                float dx = index >= 0 ? rawX(event, index) - touch.downRawX : 0;
                float dy = index >= 0 ? rawY(event, index) - touch.downRawY : 0;
                if (!touch.dragging && dx * dx + dy * dy <= touchSlop * touchSlop)
                    tapControlKey();
            }
            touches.remove(pointerId);
            if (touches.size() == 0 && getParent() != null)
                getParent().requestDisallowInterceptTouchEvent(false);
            invalidate();
        }

        private void finishAll() {
            for (int i = touches.size() - 1; i >= 0; i--) {
                KeyTouch touch = touches.valueAt(i);
                handler.removeCallbacks(touch.repeat);
                if (isModifier(touch.label))
                    setModifier(touch.label, false);
            }
            touches.clear();
            if (getParent() != null)
                getParent().requestDisallowInterceptTouchEvent(false);
            invalidate();
        }

        private void startRepeat(KeyTouch touch) {
            touch.repeat = new Runnable() {
                @Override
                public void run() {
                    sendKey(touch.label);
                    handler.postDelayed(this, 30);
                }
            };
            handler.postDelayed(touch.repeat, 270);
        }

        private void tapControlKey() {
            if (floating) {
                floating = false;
                applyToolbarLayout(activity);
            } else {
                toggleFullKeyboard();
            }
        }

        private void toggleFullKeyboard() {
            fullKeyboardVisible = !fullKeyboardVisible;
            applyToolbarLayout(activity);
            invalidate();
        }

        private float rawX(MotionEvent event, int index) {
            return event.getRawX() + event.getX(index) - event.getX();
        }

        private float rawY(MotionEvent event, int index) {
            return event.getRawY() + event.getY(index) - event.getY();
        }

        private KeyRef findKey(float x, float y) {
            String[][] rows = rows();
            if (getWidth() <= 0 || getHeight() <= 0 || y < 0 || y > getHeight())
                return null;
            int rowIndex = Math.min(rows.length - 1, Math.max(0, (int) (y / (getHeight() / (float) rows.length))));
            String[] row = rows[rowIndex];
            float total = rowWeight(row);
            float left = 0;
            for (int c = 0; c < row.length; c++) {
                float width = getWidth() * keyWeight(row[c]) / total;
                if (x >= left && x <= left + width)
                    return new KeyRef(rowIndex, c, row[c]);
                left += width;
            }
            return null;
        }

        private static boolean isControlKey(String label) {
            return label == null || label.isEmpty() || "\u00b7".equals(label);
        }

        private static boolean isModifier(String label) {
            return "Ctrl".equals(label) || "Shift".equals(label) || "Alt".equals(label) || "Caps".equals(label);
        }

        private boolean modifierActive(String label) {
            switch (label) {
                case "Ctrl":
                    return ctrlActive;
                case "Shift":
                    return shiftActive;
                case "Alt":
                    return altActive;
                case "Caps":
                    return capsActive;
                default:
                    return false;
            }
        }

        private void setModifier(String label, boolean active) {
            LorieView view = activity.getLorieView();
            switch (label) {
                case "Ctrl":
                    if (ctrlActive != active)
                        view.sendKeyEvent(0, KeyEvent.KEYCODE_CTRL_LEFT, active);
                    ctrlActive = active;
                    break;
                case "Shift":
                    if (shiftActive != active)
                        view.sendKeyEvent(0, KeyEvent.KEYCODE_SHIFT_LEFT, active);
                    shiftActive = active;
                    break;
                case "Alt":
                    if (altActive != active)
                        view.sendKeyEvent(0, KeyEvent.KEYCODE_ALT_LEFT, active);
                    altActive = active;
                    break;
                case "Caps":
                    capsActive = active;
                    break;
            }
            invalidate();
        }

        private void sendKey(String label) {
            LorieView view = activity.getLorieView();
            Integer keyCode = KEY_CODES.get(label);
            if (keyCode != null) {
                view.sendKeyEvent(0, keyCode, true);
                view.sendKeyEvent(0, keyCode, false);
                return;
            }
            String base = label.toLowerCase(Locale.US);
            Integer printableKeyCode = PRINTABLE_KEY_CODES.get(base);
            if ((ctrlActive || shiftActive || altActive) && printableKeyCode != null) {
                view.sendKeyEvent(0, printableKeyCode, true);
                view.sendKeyEvent(0, printableKeyCode, false);
                return;
            }
            String text = textFor(label);
            if (!text.isEmpty())
                view.sendTextEvent(text.getBytes(UTF_8));
        }

        private String textFor(String label) {
            if (" ".equals(label))
                return " ";
            String base = label.toLowerCase(Locale.US);
            if (shiftActive) {
                String shifted = SHIFT_SYMBOLS.get(base);
                return shifted != null ? shifted : label.toUpperCase(Locale.US);
            }
            if (capsActive && label.length() == 1 && Character.isLetter(label.charAt(0)))
                return label.toUpperCase(Locale.US);
            return label.toLowerCase(Locale.US);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        protected void onDetachedFromWindow() {
            finishAll();
            super.onDetachedFromWindow();
        }
    }

    private static class KeyRef {
        final int row;
        final int col;
        final String label;

        KeyRef(int row, int col, String label) {
            this.row = row;
            this.col = col;
            this.label = label;
        }
    }

    private static class KeyTouch {
        int pointerId;
        int row;
        int col;
        float downRawX;
        float downRawY;
        float lastRawX;
        float lastRawY;
        int startOffsetX;
        int startOffsetY;
        boolean dragging;
        String label;
        Runnable repeat = () -> {};
    }
}
