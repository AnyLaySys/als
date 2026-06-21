#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <unistd.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/mman.h>
#include <errno.h>
#include <jni.h>
#include <android/looper.h>
#include <wchar.h>
#include <linux/in.h>
#include <arpa/inet.h>
#include <poll.h>
#include "lorie.h"

#pragma clang diagnostic ignored "-Wunknown-pragmas"
#pragma ide diagnostic ignored "cppcoreguidelines-narrowing-conversions"
#pragma ide diagnostic ignored "ConstantFunctionResult"
extern volatile int conn_fd; // The only variable from shared with X server code.

static struct {
    jclass self;
    jmethodID getInstance, clientConnectedStateChanged, resetIme;
} MainActivity = {0};

static struct {
    jclass self;
    jmethodID forName;
    jmethodID decode;
} Charset = {0};

static struct {
    jclass self;
    jmethodID toString;
} CharBuffer = {0};

static JNIEnv *guienv = NULL; // Must be used only in GUI thread.
static jobject globalThiz = NULL;

static jclass FindClassOrDie(JNIEnv *env, const char* name) {
    jclass clazz = (*env)->FindClass(env, name);
    if (!clazz) {
        char buffer[1024] = {0};
        sprintf(buffer, "class %s not found", name);
        (*env)->FatalError(env, buffer);
        return NULL;
    }

    return (*env)->NewGlobalRef(env, clazz);
}

static jclass FindMethodOrDie(JNIEnv *env, jclass clazz, const char* name, const char* signature, jboolean isStatic) {
    __typeof__((*env)->GetMethodID) getMethodID = isStatic ? (*env)->GetStaticMethodID : (*env)->GetMethodID;
    jmethodID method = getMethodID(env, clazz, name, signature);
    if (!method) {
        char buffer[1024] = {0};
        sprintf(buffer, "method %s %s not found", name, signature);
        (*env)->FatalError(env, buffer);
        return NULL;
    }

    return method;
}

static jboolean requestConnection(__unused JNIEnv *env, __unused jclass clazz) {
#define check(cond, fmt, ...) if ((cond)) goto end
    bool sent = JNI_FALSE;
    // We do not want to block GUI thread for a long time so we will set timeout to 20 msec.
    struct sockaddr_in server = { .sin_family = AF_INET, .sin_port = htons(PORT), .sin_addr.s_addr = inet_addr("127.0.0.1") };
    int so_error, sock = socket(AF_INET, SOCK_STREAM, 0);
    check(sock < 0, "Could not create socket: %s", strerror(errno));
    check(fcntl(sock, F_SETFL, O_NONBLOCK) < 0, "failed to set socket non-block: %s", strerror(errno));
    int r = connect(sock, (struct sockaddr *)&server, sizeof(server));
    check(r < 0 && errno != EINPROGRESS, "failed to connect socket: %s", strerror(errno));
    if (r < 0 && errno == EINPROGRESS) {
        // Connection is in progress; use poll to wait for it
        struct pollfd pfd = { .fd = sock, .events = POLLOUT };
        r = poll(&pfd, 1, 20);  // timeout set to 50ms
        if (!r) goto end;
        check(r < 0, "poll failed: %s", strerror(errno));
        socklen_t len = sizeof(so_error);
        check(getsockopt(sock, SOL_SOCKET, SO_ERROR, &so_error, &len) < 0, "getsockopt failed: %s", strerror(errno));
        if (so_error == ECONNREFUSED) goto end;
        check(so_error != 0, "Connection failed: %s", strerror(so_error));

        check(write(sock, MAGIC, sizeof(MAGIC)) < 0, "failed to send message: %s", strerror(errno));
        sent = JNI_TRUE;
        goto end;
    }

    check(1, "something went wrong: %s, %s", strerror(errno), strerror(r));

    end: if (sock >= 0) close(sock);
    return sent;
#undef errorReturn
}

static void connect_(__unused JNIEnv* env, __unused jobject cls, jint fd);
static void nativeInit(JNIEnv *env, jobject thiz) {
    JavaVM* vm;
    if (!Charset.self) {
        // Init clipboard-related JNI stuff
        Charset.self = FindClassOrDie(env, "java/nio/charset/Charset");
        Charset.forName = FindMethodOrDie(env, Charset.self, "forName", "(Ljava/lang/String;)Ljava/nio/charset/Charset;", JNI_TRUE);
        Charset.decode = FindMethodOrDie(env, Charset.self, "decode", "(Ljava/nio/ByteBuffer;)Ljava/nio/CharBuffer;", JNI_FALSE);

        CharBuffer.self = FindClassOrDie(env,  "java/nio/CharBuffer");
        CharBuffer.toString = FindMethodOrDie(env, CharBuffer.self, "toString", "()Ljava/lang/String;", JNI_FALSE);

        MainActivity.self = FindClassOrDie(env,  "com/termux/x11/MainActivity");
        MainActivity.getInstance = FindMethodOrDie(env, MainActivity.self, "getInstance", "()Lcom/termux/x11/MainActivity;", JNI_TRUE);
        MainActivity.clientConnectedStateChanged = FindMethodOrDie(env, MainActivity.self, "clientConnectedStateChanged", "()V", JNI_FALSE);
        MainActivity.resetIme = FindMethodOrDie(env, (*env)->GetObjectClass(env, thiz), "resetIme", "()V", JNI_FALSE);
    }

    rendererInit(env);

    (*env)->GetJavaVM(env, &vm);
    (*vm)->AttachCurrentThread(vm, &guienv, NULL);
    globalThiz = (*guienv)->NewGlobalRef(env, thiz);
    connect_(NULL, NULL, -1);
}

static int xcallback(int fd, int events, __unused void* data) {
    JNIEnv *env = guienv;
    jobject thiz = globalThiz;

    if (events & (ALOOPER_EVENT_ERROR | ALOOPER_EVENT_HANGUP)) {
        jobject instance = (*env)->CallStaticObjectMethod(env, MainActivity.self, MainActivity.getInstance);
        if (instance)
            (*env)->CallVoidMethod(env, instance, MainActivity.clientConnectedStateChanged);

        ALooper_removeFd(ALooper_forThread(), fd);
        close(conn_fd);
        conn_fd = -1;
        rendererSetSharedState(NULL);
        rendererRemoveAllBuffers();
        return 1;
    }

    if (conn_fd != -1) {
        lorieEvent e = {0};

        again:
        if (read(conn_fd, &e, sizeof(e)) == sizeof(e)) {
            switch(e.type) {
                case EVENT_CLIPBOARD_SEND: {
                    if (!e.clipboardSend.count)
                        break;
                    char clipboard[e.clipboardSend.count + 1];
                    memset(clipboard, 0, e.clipboardSend.count + 1);
                    read(conn_fd, clipboard, sizeof(clipboard));
                    clipboard[e.clipboardSend.count] = 0;
                    jmethodID id = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, thiz), "setClipboardText","(Ljava/lang/String;)V");
                    jobject bb = (*env)->NewDirectByteBuffer(env, clipboard, strlen(clipboard));
                    jobject charset = (*env)->CallStaticObjectMethod(env, Charset.self, Charset.forName, (*env)->NewStringUTF(env, "UTF-8"));
                    jobject cb = (*env)->CallObjectMethod(env, charset, Charset.decode, bb);
                    (*env)->DeleteLocalRef(env, bb);

                    jstring str = (*env)->CallObjectMethod(env, cb, CharBuffer.toString);
                    (*env)->CallVoidMethod(env, thiz, id, str);
                    break;
                }
                case EVENT_CLIPBOARD_REQUEST: {
                    (*env)->CallVoidMethod(env, thiz, (*env)->GetMethodID(env, (*env)->GetObjectClass(env, thiz), "requestClipboard", "()V"));
                    break;
                }
                case EVENT_SHARED_SERVER_STATE: {
                    struct lorie_shared_server_state* state = NULL;
                    int stateFd = ancil_recv_fd(conn_fd);

                    if (stateFd < 0)
                        break;

                    state = mmap(NULL, sizeof(*state), PROT_READ|PROT_WRITE, MAP_SHARED, stateFd, 0);
                    if (!state || state == MAP_FAILED)
                        state = NULL;

                    rendererSetSharedState(state);

                    close(stateFd); // Closing file descriptor does not unmmap shared memory fragment.
                    break;
                }
                case EVENT_ADD_BUFFER: {
                    static LorieBuffer* buffer = NULL;
                    LorieBuffer_recvHandleFromUnixSocket(conn_fd, &buffer);
                    rendererAddBuffer(buffer);
                    break;
                }
                case EVENT_REMOVE_BUFFER: {
                    rendererRemoveBuffer(e.removeBuffer.id);
                    break;
                }
                case EVENT_WINDOW_FOCUS_CHANGED: {
                    (*env)->CallVoidMethod(env, thiz, MainActivity.resetIme);
                }
            }
        }

        int n;
        if (ioctl(conn_fd, FIONREAD, &n) >= 0 && n > sizeof(e))
            goto again;
    }

    return 1;
}

static void connect_(__unused JNIEnv* env, __unused jobject cls, jint fd) {
    if (conn_fd != -1) {
        ALooper_removeFd(ALooper_forThread(), conn_fd);
        close(conn_fd);
        rendererSetSharedState(NULL);
        rendererRemoveAllBuffers();
    }

    if ((conn_fd = fd) != -1)
        ALooper_addFd(ALooper_forThread(), fd, 0, ALOOPER_EVENT_INPUT | ALOOPER_EVENT_ERROR | ALOOPER_EVENT_HANGUP, xcallback, NULL);
}

static jboolean connected(__unused JNIEnv* env,__unused jclass clazz) {
    return conn_fd != -1;
}

static void setClipboardSyncEnabled(__unused JNIEnv* env, __unused jobject cls, jboolean enable, __unused jboolean ignored) {
    if (conn_fd != -1) {
        lorieEvent e = { .clipboardEnable = { .t = EVENT_CLIPBOARD_ENABLE, .enable = enable } };
        write(conn_fd, &e, sizeof(e));
    }
}

static void sendClipboardAnnounce(__unused JNIEnv *env, __unused jobject thiz) {
    if (conn_fd != -1) {
        lorieEvent e = { .type = EVENT_CLIPBOARD_ANNOUNCE };
        write(conn_fd, &e, sizeof(e));
    }
}

static void sendClipboardEvent(JNIEnv *env, __unused jobject thiz, jbyteArray text) {
    if (conn_fd != -1 && text) {
        jsize length = (*env)->GetArrayLength(env, text);
        jbyte* str = (*env)->GetByteArrayElements(env, text, NULL);
        lorieEvent e = { .clipboardSend = { .t = EVENT_CLIPBOARD_SEND, .count = length } };
        write(conn_fd, &e, sizeof(e));
        write(conn_fd, str, length);
        (*env)->ReleaseByteArrayElements(env, text, str, JNI_ABORT);
    }
}

static void sendWindowChange(__unused JNIEnv* env, __unused jobject cls, jint width, jint height, jint framerate, jstring jname) {
    if (conn_fd != -1) {
        const char *name = (!jname || width <= 0 || height <= 0) ? NULL : (*env)->GetStringUTFChars(env, jname, JNI_FALSE);
        lorieEvent e = { .screenSize = { .t = EVENT_SCREEN_SIZE, .width = width, .height = height, .framerate = framerate, .name_size = (name ? strlen(name) : 0) } };
        write(conn_fd, &e, sizeof(e));
        if (name) {
            write(conn_fd, name, strlen(name));
            (*env)->ReleaseStringUTFChars(env, jname, name);
        }
    }
}

static void sendMouseEvent(__unused JNIEnv* env, __unused jobject cls, jfloat x, jfloat y, jint which_button, jboolean button_down, jboolean relative) {
    if (conn_fd != -1) {
        if (which_button > 0)
            (*env)->CallVoidMethod(env, globalThiz, MainActivity.resetIme);
        lorieEvent e = { .mouse = { .t = EVENT_MOUSE, .x = x, .y = y, .detail = which_button, .down = button_down, .relative = relative } };
        write(conn_fd, &e, sizeof(e));
    }
}

static void sendTouchEvent(__unused JNIEnv* env, __unused jobject cls, jint action, jint id, jint x, jint y) {
    if (conn_fd != -1 && action != -1) {
        lorieEvent e = { .touch = { .t = EVENT_TOUCH, .type = action, .id = id, .x = x, .y = y } };
        write(conn_fd, &e, sizeof(e));
    }
}

static void sendStylusEvent(__unused JNIEnv *env, __unused jobject thiz, jfloat x, jfloat y,
                            jint pressure, jint tilt_x, jint tilt_y,
                            jint orientation, jint buttons, jboolean eraser, jboolean mouse) {
    if (conn_fd != -1) {
        (*env)->CallVoidMethod(env, globalThiz, MainActivity.resetIme);
        lorieEvent e = { .stylus = { .t = EVENT_STYLUS, .x = x, .y = y, .pressure = pressure, .tilt_x = tilt_x, .tilt_y = tilt_y, .orientation = orientation, .buttons = buttons, .eraser = eraser, .mouse = mouse } };
        write(conn_fd, &e, sizeof(e));
    }
}

static void requestStylusEnabled(__unused JNIEnv *env, __unused jclass clazz, jboolean enabled) {
    if (conn_fd != -1) {
        lorieEvent e = { .stylusEnable = { .t = EVENT_STYLUS_ENABLE, .enable = enabled } };
        write(conn_fd, &e, sizeof(e));
    }
}

static jboolean sendKeyEvent(__unused JNIEnv* env, __unused jobject cls, jint scan_code, jint key_code, jboolean key_down) {
    if (conn_fd != -1) {
        int code = (scan_code) ?: android_to_linux_keycode[key_code];
        lorieEvent e = { .key = { .t = EVENT_KEY, .key = code + 8, .state = key_down } };
        write(conn_fd, &e, sizeof(e));
    }

    return true;
}

static void sendTextEvent(JNIEnv *env, __unused jobject thiz, jbyteArray text) {
    if (conn_fd != -1 && text) {
        jsize length = (*env)->GetArrayLength(env, text);
        jbyte *str = (*env)->GetByteArrayElements(env, text, NULL);
        char *p = (char*) str;
        mbstate_t mbstate = { 0 };
        if (!length)
            return;

        while (*p) {
            wchar_t wc;
            size_t len = mbrtowc(&wc, p, MB_CUR_MAX, &mbstate);

            if (len == (size_t)-1 || len == (size_t)-2)
                break;

            if (len == 0)
                break;

            lorieEvent e = { .unicode = { .t = EVENT_UNICODE, .code = wc } };
            write(conn_fd, &e, sizeof(e));
            p += len;
            if (p - (char*) str >= length)
                break;
            usleep(2500);
        }

        (*env)->ReleaseByteArrayElements(env, text, str, JNI_ABORT);
    }
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, __unused void *reserved) {
    JNIEnv* env;
    static JNINativeMethod methods[] = {
            {"nativeInit", "()V", (void *)&nativeInit},
            {"surfaceChanged", "(Landroid/view/Surface;)V", (void *)&rendererSetWindow},
            {"setViewport", "(IIIIII)V", (void *)&rendererSetViewport},
            {"setRendererZoom", "(I)V", (void *)&rendererSetZoom},
            {"setFiltering", "(I)V", (void *)&rendererSetFiltering},
            {"connect", "(I)V", (void *)&connect_},
            {"connected", "()Z", (void *)&connected},
            {"setClipboardSyncEnabled", "(ZZ)V", (void *)&setClipboardSyncEnabled},
            {"sendClipboardAnnounce", "()V", (void *)&sendClipboardAnnounce},
            {"sendClipboardEvent", "([B)V", (void *)&sendClipboardEvent},
            {"sendWindowChange", "(IIILjava/lang/String;)V", (void *)&sendWindowChange},
            {"sendMouseEvent", "(FFIZZ)V", (void *)&sendMouseEvent},
            {"sendTouchEvent", "(IIII)V", (void *)&sendTouchEvent},
            {"sendStylusEvent", "(FFIIIIIZZ)V", (void *)&sendStylusEvent},
            {"requestStylusEnabled", "(Z)V", (void *)&requestStylusEnabled},
            {"sendKeyEvent", "(IIZI)Z", (void *)&sendKeyEvent},
            {"sendTextEvent", "([B)V", (void *)&sendTextEvent},
            {"requestConnection", "()Z", (void *)&requestConnection},
    };
    (*vm)->AttachCurrentThread(vm, &env, NULL);
    jclass cls = (*env)->FindClass(env, "com/termux/x11/LorieView");
    (*env)->RegisterNatives(env, cls, methods, sizeof(methods)/sizeof(methods[0]));

    return JNI_VERSION_1_6;
}
