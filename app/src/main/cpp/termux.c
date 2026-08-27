#include <dirent.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define UNUSED(x) x __attribute__((unused))

static int throw_runtime_exception(JNIEnv *env, const char *message) {
    jclass exception = (*env)->FindClass(env, "java/lang/RuntimeException");
    (*env)->ThrowNew(env, exception, message);
    return -1;
}

static int create_subprocess(JNIEnv *env, const char *command, const char *working_directory,
                             char *const arguments[], char **environment, int *process_id,
                             jint rows, jint columns, jint cell_width, jint cell_height) {
    int master = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (master < 0) {
        return throw_runtime_exception(env, "Cannot open /dev/ptmx");
    }

    char device_name[64];
    if (grantpt(master) || unlockpt(master) ||
        ptsname_r(master, device_name, sizeof(device_name))) {
        close(master);
        return throw_runtime_exception(env, "Cannot grantpt()/unlockpt()/ptsname_r() on /dev/ptmx");
    }

    struct termios attributes;
    tcgetattr(master, &attributes);
    attributes.c_iflag |= IUTF8;
    attributes.c_iflag &= ~(IXON | IXOFF);
    tcsetattr(master, TCSANOW, &attributes);

    struct winsize size = {.ws_row = (unsigned short) rows, .ws_col = (unsigned short) columns, .ws_xpixel = (unsigned short) (
            columns * cell_width), .ws_ypixel = (unsigned short) (rows * cell_height)};
    ioctl(master, TIOCSWINSZ, &size);

    pid_t pid = fork();
    if (pid < 0) {
        close(master);
        return throw_runtime_exception(env, "Fork failed");
    }
    if (pid > 0) {
        *process_id = (int) pid;
        return master;
    }

    sigset_t signals;
    sigfillset(&signals);
    sigprocmask(SIG_UNBLOCK, &signals, 0);
    close(master);
    setsid();

    int slave = open(device_name, O_RDWR);
    if (slave < 0) {
        _exit(1);
    }

    dup2(slave, STDIN_FILENO);
    dup2(slave, STDOUT_FILENO);
    dup2(slave, STDERR_FILENO);

    DIR *descriptor_directory = opendir("/proc/self/fd");
    if (descriptor_directory) {
        int directory_descriptor = dirfd(descriptor_directory);
        struct dirent *entry;
        while ((entry = readdir(descriptor_directory))) {
            int descriptor = atoi(entry->d_name);
            if (descriptor > STDERR_FILENO && descriptor != directory_descriptor) {
                close(descriptor);
            }
        }
        closedir(descriptor_directory);
    }

    clearenv();
    if (environment) {
        while (*environment) {
            putenv(*environment++);
        }
    }

    if (chdir(working_directory)) {
        char *message;
        if (asprintf(&message, "chdir(\"%s\")", working_directory) < 0) {
            message = "chdir()";
        }
        perror(message);
        fflush(stderr);
    }

    execvp(command, arguments);
    char *message;
    if (asprintf(&message, "exec(\"%s\")", command) < 0) {
        message = "exec()";
    }
    perror(message);
    _exit(1);
}

static char **copy_string_array(JNIEnv *env, jobjectArray source) {
    jsize length = source ? (*env)->GetArrayLength(env, source) : 0;
    if (!length) {
        return NULL;
    }

    char **result = malloc((length + 1) * sizeof(char *));
    if (!result) {
        throw_runtime_exception(env, "Cannot allocate string array");
        return NULL;
    }

    for (jsize index = 0; index < length; index++) {
        jstring value = (jstring) (*env)->GetObjectArrayElement(env, source, index);
        const char *characters = (*env)->GetStringUTFChars(env, value, NULL);
        if (!characters) {
            result[index] = NULL;
            break;
        }
        result[index] = strdup(characters);
        (*env)->ReleaseStringUTFChars(env, value, characters);
    }
    result[length] = NULL;
    return result;
}

static void free_string_array(char **values) {
    if (!values) {
        return;
    }
    for (char **value = values; *value; value++) {
        free(*value);
    }
    free(values);
}

JNIEXPORT jint JNICALL
Java_com_termux_terminal_JNI_createSubprocess(JNIEnv *env, jclass UNUSED(type), jstring command,
                                              jstring working_directory, jobjectArray arguments,
                                              jobjectArray environment, jintArray process_id_array,
                                              jint rows, jint columns, jint cell_width,
                                              jint cell_height) {
    char **native_arguments = copy_string_array(env, arguments);
    if ((*env)->ExceptionCheck(env)) {
        free_string_array(native_arguments);
        return -1;
    }

    char **native_environment = copy_string_array(env, environment);
    if ((*env)->ExceptionCheck(env)) {
        free_string_array(native_arguments);
        free_string_array(native_environment);
        return -1;
    }

    const char *native_command = (*env)->GetStringUTFChars(env, command, NULL);
    const char *native_working_directory = (*env)->GetStringUTFChars(env, working_directory, NULL);
    int process_id = 0;
    int master = create_subprocess(env, native_command, native_working_directory, native_arguments,
                                   native_environment, &process_id, rows, columns, cell_width,
                                   cell_height);
    (*env)->ReleaseStringUTFChars(env, command, native_command);
    (*env)->ReleaseStringUTFChars(env, working_directory, native_working_directory);
    free_string_array(native_arguments);
    free_string_array(native_environment);

    jint *process_id_value = (*env)->GetPrimitiveArrayCritical(env, process_id_array, NULL);
    if (!process_id_value) {
        if (master >= 0) {
            close(master);
        }
        return throw_runtime_exception(env, "Cannot access process id array");
    }
    *process_id_value = process_id;
    (*env)->ReleasePrimitiveArrayCritical(env, process_id_array, process_id_value, 0);
    return master;
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_setPtyWindowSize(JNIEnv *UNUSED(env), jclass UNUSED(type),
                                              jint descriptor, jint rows, jint columns,
                                              jint cell_width, jint cell_height) {
    struct winsize size = {.ws_row = (unsigned short) rows, .ws_col = (unsigned short) columns, .ws_xpixel = (unsigned short) (
            columns * cell_width), .ws_ypixel = (unsigned short) (rows * cell_height)};
    ioctl(descriptor, TIOCSWINSZ, &size);
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_setPtyUTF8Mode(JNIEnv *UNUSED(env), jclass UNUSED(type),
                                            jint descriptor) {
    struct termios attributes;
    tcgetattr(descriptor, &attributes);
    if (!(attributes.c_iflag & IUTF8)) {
        attributes.c_iflag |= IUTF8;
        tcsetattr(descriptor, TCSANOW, &attributes);
    }
}

JNIEXPORT jint JNICALL
Java_com_termux_terminal_JNI_waitFor(JNIEnv *UNUSED(env), jclass UNUSED(type), jint process_id) {
    int status;
    waitpid(process_id, &status, 0);
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return -WTERMSIG(status);
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_close(JNIEnv *UNUSED(env), jclass UNUSED(type), jint descriptor) {
    close(descriptor);
}