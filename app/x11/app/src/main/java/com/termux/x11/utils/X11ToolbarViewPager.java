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
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.termux.x11.LorieView;
import com.termux.x11.MainActivity;
import com.termux.x11.input.InputStub;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class X11ToolbarViewPager {
    public static void applyToolbarLayout(MainActivity activity) {
        KeyboardView.applyToolbarLayout(activity);
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
        private static final String UP = "↑", DOWN = "↓", LEFT = "←", RIGHT = "→";
        private static final String[][] COMPACT_ROWS = {{"Esc", "F1", "F2", "F3", "·", "F4", "F5", "F6", "Del"}, {"Shift", "F7", "F8", "F9", UP, "F10", "F11", "F12", "Back"}, {"Tab", "Ctrl", "Alt", LEFT, DOWN, RIGHT, "Home", "End", "Enter"}};
        private static final String[][] FULL_ROWS = {{"Esc", "F1", "F2", "F3", "F4", "F5", "F6", "", "F7", "F8", "F9", "F10", "F11", "F12", "Del"}, {"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "Back"}, {"Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]", "\\"}, {"Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "Enter"}, {"Shift", "Z", "X", "C", "V", "B", "N", "M", ",", ".", UP, "/"}, {"Ctrl", "Alt", "Home", " ", "End", LEFT, DOWN, RIGHT}};
        private static final Map<String, Integer> KEY_CODES = new HashMap<>(), PRINTABLE_KEY_CODES = new HashMap<>();
        private static final Map<String, String> SHIFT_SYMBOLS = new HashMap<>();
        private static final int KEYBOARD = 0, TOUCHPAD = 1;
        private static boolean ctrlActive, shiftActive, altActive, capsActive, fullKeyboardVisible, floating;
        private static int keyboardOffsetX, keyboardOffsetY;

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
            for (int i = 1; i <= 12; i++) putKey("F" + i, KeyEvent.KEYCODE_F1 + i - 1);
            for (char c = 'A'; c <= 'Z'; c++)
                putPrintable(String.valueOf(c), KeyEvent.KEYCODE_A + c - 'A');
            for (char c = '0'; c <= '9'; c++)
                putPrintable(String.valueOf(c), KeyEvent.KEYCODE_0 + c - '0');
            String[] pKeys = {"`", "-", "=", "[", "]", "\\", ";", "'", ",", ".", "/", " "};
            int[] pCodes = {KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_SPACE};
            for (int i = 0; i < pKeys.length; i++) putPrintable(pKeys[i], pCodes[i]);
            String[] syms = {"`", "~", "1", "!", "2", "@", "3", "#", "4", "$", "5", "%", "6", "^", "7", "&", "8", "*", "9", "(", "0", ")", "-", "_", "=", "+", "[", "{", "]", "}", "\\", "|", ";", ":", "'", "\"", ",", "<", ".", ">", "/", "?"};
            for (int i = 0; i < syms.length; i += 2) putSymbol(syms[i], syms[i + 1]);
        }

        private final MainActivity activity;
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final SparseArray<KeyTouch> touches = new SparseArray<>();
        private final int touchSlop, tapTimeout;
        private int touchpadPointerId = -1, touchpadMaxPointers;
        private float touchpadDownX, touchpadDownY, touchpadLastX, touchpadLastY;
        private long touchpadDownTime;
        private boolean touchpadMoved;
        private Runnable touchpadLongPressRunnable;
        private boolean isTouchpadDragging;

        KeyboardView(@NonNull MainActivity activity) {
            super(activity);
            this.activity = activity;
            ViewConfiguration config = ViewConfiguration.get(activity);
            touchSlop = config.getScaledTouchSlop();
            tapTimeout = ViewConfiguration.getTapTimeout();
            setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

        static int getToolbarWidth(MainActivity activity) {
            View content = activity.findViewById(android.R.id.content);
            int fullWidth = content != null && content.getWidth() > 0 ? content.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
            if (!fullKeyboardVisible) return fullWidth;
            int fullHeight = content != null && content.getHeight() > 0 ? content.getHeight() : activity.getResources().getDisplayMetrics().heightPixels;
            return fullWidth > fullHeight ? (fullWidth * 2) / 3 : fullWidth;
        }

        static void releaseModifiers(LorieView view) {
            if (view == null) return;
            if (ctrlActive) view.sendKeyEvent(0, KeyEvent.KEYCODE_CTRL_LEFT, false);
            if (shiftActive) view.sendKeyEvent(0, KeyEvent.KEYCODE_SHIFT_LEFT, false);
            if (altActive) view.sendKeyEvent(0, KeyEvent.KEYCODE_ALT_LEFT, false);
            ctrlActive = shiftActive = altActive = capsActive = false;
        }

        static void applyToolbarLayout(MainActivity activity) {
            ViewPager pager = activity.getTerminalToolbarViewPager();
            if (pager == null) return;
            ViewGroup.LayoutParams lp = pager.getLayoutParams();
            boolean isPortrait = portrait(activity);
            if (fullKeyboardVisible && !isPortrait) {
                lp.width = getToolbarWidth(activity);
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            } else if (fullKeyboardVisible) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = (getToolbarHeight(activity) * 2) / 3;
            } else {
                lp.width = floating ? getFloatingWidth(activity) : ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = getToolbarHeight(activity);
            }
            if (lp instanceof FrameLayout.LayoutParams)
                ((FrameLayout.LayoutParams) lp).gravity = Gravity.BOTTOM | ((fullKeyboardVisible && !isPortrait && !floating) ? Gravity.END : Gravity.START);
            pager.setLayoutParams(lp);
            pager.setTranslationX(floating ? keyboardOffsetX : 0);
            pager.setTranslationY(floating ? keyboardOffsetY : 0);
            activity.getLorieView().setContentInsets(0, 0, 0, 0);
        }

        static int getFloatingWidth(MainActivity activity) {
            if (!fullKeyboardVisible)
                return Math.round(360f * activity.getResources().getDisplayMetrics().density);
            View content = activity.findViewById(android.R.id.content);
            return (content != null && content.getWidth() > 0 ? content.getWidth() : activity.getResources().getDisplayMetrics().widthPixels) / 2;
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

        private static boolean isControlKey(String label) {
            return label == null || label.isEmpty() || "·".equals(label);
        }

        private static boolean isModifier(String label) {
            return "Ctrl".equals(label) || "Shift".equals(label) || "Alt".equals(label) || "Caps".equals(label);
        }

        private static boolean portrait(MainActivity activity) {
            return activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            drawRows(canvas);
        }

        private void drawRows(Canvas canvas) {
            String[][] rows = rows();
            float left = areaLeft(), top = areaTop(), width = areaWidth(), rowHeight = areaHeight() / rows.length;
            for (String[] row : rows) {
                drawRow(canvas, row, left, top, width, rowHeight);
                top += rowHeight;
            }
        }

        private void drawRow(Canvas canvas, String[] row, float left, float top, float areaWidth, float rowHeight) {
            float total = rowWeight(row), x = left;
            for (String label : row) {
                float width = areaWidth * keyWeight(label) / total;
                rect.set(x, top, x + width, top + rowHeight);
                String text = displayLabel(label);
                if (!text.isEmpty()) {
                    textPaint.setColor(Color.GRAY);
                    Paint.FontMetrics fm = textPaint.getFontMetrics();
                    canvas.drawText(text, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, textPaint);
                }
                x += width;
            }
        }

        private float rowWeight(String[] row) {
            float total = 0;
            for (String label : row) total += keyWeight(label);
            return total;
        }

        private float keyWeight(String label) {
            if (!fullKeyboardVisible) return 1f;
            if (" ".equals(label)) return 4.2f;
            if ("Ctrl".equals(label) || "Alt".equals(label) || "Home".equals(label) || "End".equals(label))
                return 1.2f;
            return 1f;
        }

        private String displayLabel(String label) {
            if (isControlKey(label)) return "";
            if (isModifier(label) || label.length() > 1) return label;
            String base = label.toLowerCase(Locale.US);
            if (shiftActive) {
                String shifted = SHIFT_SYMBOLS.get(base);
                return shifted != null ? shifted : label.toUpperCase(Locale.US);
            }
            return capsActive && Character.isLetter(label.charAt(0)) ? label.toUpperCase(Locale.US) : label;
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if ((fullKeyboardVisible && areaAt(event.getX(event.getActionIndex()), event.getY(event.getActionIndex())) == TOUCHPAD) || touchpadPointerId != -1) {
                        touchpad(event);
                        return true;
                    }
                    startTouch(event, event.getActionIndex());
                    performClick();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (touchpadPointerId != -1) touchpad(event);
                    else moveTouches(event);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if (touchpadPointerId != -1) touchpad(event);
                    else finishTouch(event.getPointerId(event.getActionIndex()), event);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    finishTouchpad();
                    finishAll();
                    return true;
                default:
                    return true;
            }
        }

        private void startTouch(MotionEvent event, int index) {
            KeyRef ref = findKey(event.getX(index), event.getY(index));
            if (ref == null) return;
            KeyTouch touch = new KeyTouch();
            touch.pointerId = event.getPointerId(index);
            touch.label = ref.label;
            touch.downRawX = touch.lastRawX = rawX(event, index);
            touch.downRawY = touch.lastRawY = rawY(event, index);
            touch.startOffsetX = floating ? keyboardOffsetX : 0;
            touch.startOffsetY = floating ? keyboardOffsetY : 0;
            touches.put(touch.pointerId, touch);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (isModifier(touch.label)) setModifier(touch.label, true);
            else if (!isControlKey(touch.label)) {
                sendKey(touch.label);
                startRepeat(touch);
            }
            invalidate();
        }

        private void moveTouches(MotionEvent event) {
            for (int i = 0; i < touches.size(); i++) {
                KeyTouch touch = touches.valueAt(i);
                if (!isControlKey(touch.label)) continue;
                int index = event.findPointerIndex(touch.pointerId);
                if (index < 0) continue;
                float rawX = rawX(event, index), rawY = rawY(event, index), dx = rawX - touch.downRawX, dy = rawY - touch.downRawY;
                if (!touch.dragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                    touch.dragging = floating = true;
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

        private void finishTouch(int pointerId, MotionEvent event) {
            KeyTouch touch = touches.get(pointerId);
            if (touch == null) return;
            handler.removeCallbacks(touch.repeat);
            if (isModifier(touch.label)) setModifier(touch.label, false);
            if (isControlKey(touch.label)) {
                int index = event.findPointerIndex(pointerId);
                float dx = index >= 0 ? rawX(event, index) - touch.downRawX : 0, dy = index >= 0 ? rawY(event, index) - touch.downRawY : 0;
                if (!touch.dragging && dx * dx + dy * dy <= touchSlop * touchSlop) tapControlKey();
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
                if (isModifier(touch.label)) setModifier(touch.label, false);
            }
            touches.clear();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
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
            } else toggleFullKeyboard();
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
            if (areaAt(x, y) != KEYBOARD) return null;
            String[][] rows = rows();
            float left = areaLeft(), top = areaTop(), width = areaWidth(), height = areaHeight();
            if (width <= 0 || height <= 0 || x < left || x > left + width || y < top || y > top + height)
                return null;
            float localX = x - left, localY = y - top;
            int rowIndex = Math.min(rows.length - 1, Math.max(0, (int) (localY / (height / rows.length))));
            String[] row = rows[rowIndex];
            float total = rowWeight(row), keyLeft = 0;
            for (String key : row) {
                float keyWidth = width * keyWeight(key) / total;
                if (localX >= keyLeft && localX <= keyLeft + keyWidth) return new KeyRef(key);
                keyLeft += keyWidth;
            }
            return null;
        }

        private boolean portrait() {
            return getHeight() >= getWidth();
        }

        private int areaAt(float x, float y) {
            if (!fullKeyboardVisible) return KEYBOARD;
            return portrait() ? (y < getHeight() / 2f ? TOUCHPAD : KEYBOARD) : (x >= getWidth() / 2f ? KEYBOARD : TOUCHPAD);
        }

        private float areaLeft() {
            return (!fullKeyboardVisible || portrait()) ? 0 : getWidth() / 2f;
        }

        private float areaTop() {
            return (!fullKeyboardVisible || !portrait()) ? 0 : getHeight() / 2f;
        }

        private float areaWidth() {
            return (!fullKeyboardVisible || portrait()) ? getWidth() : getWidth() / 2f;
        }

        private float areaHeight() {
            return (!fullKeyboardVisible || !portrait()) ? getHeight() : getHeight() / 2f;
        }

        private void touchpad(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchpadPointerId = event.getPointerId(event.getActionIndex());
                    touchpadMaxPointers = 1;
                    touchpadDownX = touchpadLastX = event.getX(event.getActionIndex());
                    touchpadDownY = touchpadLastY = event.getY(event.getActionIndex());
                    touchpadDownTime = event.getEventTime();
                    touchpadMoved = false;
                    isTouchpadDragging = false;
                    if (touchpadLongPressRunnable != null)
                        handler.removeCallbacks(touchpadLongPressRunnable);
                    touchpadLongPressRunnable = () -> {
                        if (touchpadPointerId != -1 && !touchpadMoved && touchpadMaxPointers == 1) {
                            isTouchpadDragging = true;
                            activity.getLorieView().sendMouseEvent(0, 0, InputStub.BUTTON_LEFT, true, true);
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        }
                    };
                    handler.postDelayed(touchpadLongPressRunnable, ViewConfiguration.getLongPressTimeout());
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (touchpadLongPressRunnable != null) {
                        handler.removeCallbacks(touchpadLongPressRunnable);
                        touchpadLongPressRunnable = null;
                    }
                    touchpadMaxPointers = Math.max(touchpadMaxPointers, event.getPointerCount());
                    touchpadDownX = touchpadLastX = focusX(event);
                    touchpadDownY = touchpadLastY = focusY(event);
                    touchpadMoved = false;
                    return;
                case MotionEvent.ACTION_POINTER_UP:
                    int index = event.getActionIndex() == 0 ? 1 : 0;
                    touchpadPointerId = event.getPointerId(index);
                    touchpadLastX = event.getX(index);
                    touchpadLastY = event.getY(index);
                    return;
                case MotionEvent.ACTION_MOVE:
                    int idx = event.findPointerIndex(touchpadPointerId);
                    if (idx < 0) return;
                    float x = event.getPointerCount() > 1 ? focusX(event) : event.getX(idx);
                    float y = event.getPointerCount() > 1 ? focusY(event) : event.getY(idx);
                    float dx = x - touchpadLastX, dy = y - touchpadLastY;
                    if (dx * dx + dy * dy > 0) {
                        if (!touchpadMoved && (x - touchpadDownX) * (x - touchpadDownX) + (y - touchpadDownY) * (y - touchpadDownY) > touchSlop * touchSlop) {
                            touchpadMoved = true;
                            if (touchpadLongPressRunnable != null && !isTouchpadDragging) {
                                handler.removeCallbacks(touchpadLongPressRunnable);
                                touchpadLongPressRunnable = null;
                            }
                        }
                        if (event.getPointerCount() > 1)
                            activity.getLorieView().sendMouseWheelEvent(-dx, -dy);
                        else
                            activity.getLorieView().sendMouseEvent(dx, dy, InputStub.BUTTON_UNDEFINED, false, true);
                        touchpadLastX = x;
                        touchpadLastY = y;
                    }
                    return;
                case MotionEvent.ACTION_UP:
                    if (touchpadLongPressRunnable != null) {
                        handler.removeCallbacks(touchpadLongPressRunnable);
                        touchpadLongPressRunnable = null;
                    }
                    if (!isTouchpadDragging && !touchpadMoved && event.getEventTime() - touchpadDownTime <= tapTimeout)
                        clickTouchpad(touchpadMaxPointers);
                    finishTouchpad();
                    return;
                case MotionEvent.ACTION_CANCEL:
                    finishTouchpad();
                    return;
                default:
            }
        }

        private float focusX(MotionEvent event) {
            float x = 0;
            for (int i = 0; i < event.getPointerCount(); i++) x += event.getX(i);
            return x / event.getPointerCount();
        }

        private float focusY(MotionEvent event) {
            float y = 0;
            for (int i = 0; i < event.getPointerCount(); i++) y += event.getY(i);
            return y / event.getPointerCount();
        }

        private void clickTouchpad(int pointers) {
            int button = pointers == 1 ? InputStub.BUTTON_LEFT : pointers == 2 ? InputStub.BUTTON_RIGHT : pointers == 3 ? InputStub.BUTTON_MIDDLE : InputStub.BUTTON_UNDEFINED;
            if (button == InputStub.BUTTON_UNDEFINED) return;
            LorieView view = activity.getLorieView();
            view.sendMouseEvent(0, 0, button, true, true);
            view.sendMouseEvent(0, 0, button, false, true);
        }

        private void finishTouchpad() {
            touchpadPointerId = -1;
            touchpadMaxPointers = 0;
            if (touchpadLongPressRunnable != null) {
                handler.removeCallbacks(touchpadLongPressRunnable);
                touchpadLongPressRunnable = null;
            }
            if (isTouchpadDragging) {
                activity.getLorieView().sendMouseEvent(0, 0, InputStub.BUTTON_LEFT, false, true);
                isTouchpadDragging = false;
            }
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
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
            if (!text.isEmpty()) view.sendTextEvent(text.getBytes(UTF_8));
        }

        private String textFor(String label) {
            if (" ".equals(label)) return " ";
            String base = label.toLowerCase(Locale.US);
            if (shiftActive) {
                String shifted = SHIFT_SYMBOLS.get(base);
                return shifted != null ? shifted : label.toUpperCase(Locale.US);
            }
            return capsActive && label.length() == 1 && Character.isLetter(label.charAt(0)) ? label.toUpperCase(Locale.US) : label.toLowerCase(Locale.US);
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

        @Override
        protected void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            fullKeyboardVisible = floating = false;
            keyboardOffsetX = keyboardOffsetY = 0;
            releaseModifiers(activity.getLorieView());
            finishAll();
            finishTouchpad();
            ViewPager pager = activity.getTerminalToolbarViewPager();
            if (pager != null) {
                pager.setTranslationX(0);
                pager.setTranslationY(0);
            }
            applyToolbarLayout(activity);
            invalidate();
        }
    }

    private static class KeyRef {
        final String label;

        KeyRef(String label) {
            this.label = label;
        }
    }

    private static class KeyTouch {
        int pointerId, startOffsetX, startOffsetY;
        float downRawX, downRawY, lastRawX, lastRawY;
        boolean dragging;
        String label;
        Runnable repeat = () -> {
        };
    }
}