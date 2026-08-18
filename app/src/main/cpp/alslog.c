#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/uio.h>
#include <time.h>
#include <unistd.h>

#define ALSLOG_PATH "/data/local/tmp/als/als.log"

static pthread_mutex_t stdio_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t alslog_mutex = PTHREAD_MUTEX_INITIALIZER;
static int saved_stdio[3] = {-2, -2, -2};
static int stdio_redirected;
static int alslog_fd = -1;

static int duplicate_fd(int fd)
{
    int result;

    do {
        result = fcntl(fd, F_DUPFD_CLOEXEC, 3);
    } while (result < 0 && errno == EINTR);
    if (result < 0 && errno == EBADF) {
        return -1;
    }
    if (result < 0) {
        return -2;
    }
    return result;
}

static int open_terminal(jint pid)
{
    char path[64];
    int terminal_fd;

    snprintf(path, sizeof(path), "/proc/%d/fd/0", pid);
    do {
        terminal_fd = open(path, O_RDWR | O_NOCTTY | O_CLOEXEC);
    } while (terminal_fd < 0 && errno == EINTR);
    if (terminal_fd < 0) {
        return -1;
    }
    if (terminal_fd < 3) {
        int duplicate = fcntl(terminal_fd, F_DUPFD_CLOEXEC, 3);

        if (duplicate < 0) {
            close(terminal_fd);
            return -1;
        }
        close(terminal_fd);
        terminal_fd = duplicate;
    }
    return terminal_fd;
}

static void restore_stdio_locked(void)
{
    int index;

    if (!stdio_redirected) {
        return;
    }
    fflush(NULL);
    for (index = 0; index < 3; index++) {
        if (saved_stdio[index] >= 0) {
            dup2(saved_stdio[index], index);
            close(saved_stdio[index]);
        } else if (saved_stdio[index] == -1) {
            close(index);
        }
        saved_stdio[index] = -2;
    }
    stdio_redirected = 0;
}

static int alslog_open_locked(void)
{
    if (alslog_fd >= 0) {
        return 0;
    }
    mkdir("/data/local/tmp/als", 0755);
    do {
        alslog_fd = open(ALSLOG_PATH, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
    } while (alslog_fd < 0 && errno == EINTR);
    return alslog_fd < 0 ? -1 : 0;
}

static int alslog_writev_all(int fd, struct iovec *buffers, int count)
{
    while (count > 0) {
        ssize_t written;

        do {
            written = writev(fd, buffers, count);
        } while (written < 0 && errno == EINTR);
        if (written <= 0) {
            return -1;
        }
        while (count > 0 && (size_t) written >= buffers[0].iov_len) {
            written -= (ssize_t) buffers[0].iov_len;
            buffers++;
            count--;
        }
        if (count > 0 && written > 0) {
            buffers[0].iov_base = (char *) buffers[0].iov_base + written;
            buffers[0].iov_len -= written;
        }
    }
    return 0;
}

static char alslog_priority(int priority)
{
    switch (priority) {
    case ANDROID_LOG_VERBOSE:
        return 'V';
    case ANDROID_LOG_DEBUG:
        return 'D';
    case ANDROID_LOG_INFO:
        return 'I';
    case ANDROID_LOG_WARN:
        return 'W';
    case ANDROID_LOG_ERROR:
        return 'E';
    case ANDROID_LOG_FATAL:
        return 'F';
    default:
        return '?';
    }
}

static void alslog_file_logger(const struct __android_log_message *log_message)
{
    const char *tag = log_message->tag ? log_message->tag : "ALS";
    const char *message = log_message->message ? log_message->message : "";
    struct timespec now;
    struct iovec buffers[3];
    char prefix[160];
    int length;
    size_t prefix_length;

    clock_gettime(CLOCK_REALTIME, &now);
    length = snprintf(prefix, sizeof(prefix), "%lld.%03ld %c/%s: ",
                      (long long) now.tv_sec, now.tv_nsec / 1000000,
                      alslog_priority(log_message->priority), tag);
    prefix_length = length > 0
        ? ((size_t) length < sizeof(prefix) ? (size_t) length : sizeof(prefix) - 1)
        : 0;
    buffers[0].iov_base = prefix;
    buffers[0].iov_len = prefix_length;
    buffers[1].iov_base = (void *) message;
    buffers[1].iov_len = strlen(message);
    buffers[2].iov_base = "\n";
    buffers[2].iov_len = 1;
    pthread_mutex_lock(&alslog_mutex);
    if (alslog_open_locked() == 0) {
        if (alslog_writev_all(alslog_fd, buffers, 3) < 0) {
            close(alslog_fd);
            alslog_fd = -1;
        }
    }
    pthread_mutex_unlock(&alslog_mutex);
    __android_log_logd_logger(log_message);
}

JNIEXPORT jint JNICALL
Java_sui_k_als_agl_AglNative_redirectStdio(JNIEnv *env, jobject object, jint pid)
{
    int terminal_fd;
    int error = 0;
    int index;

    pthread_mutex_lock(&stdio_mutex);
    if (stdio_redirected) {
        error = EBUSY;
        goto done;
    }
    if (pid <= 0) {
        error = EINVAL;
        goto done;
    }
    terminal_fd = open_terminal(pid);
    if (terminal_fd < 0) {
        error = errno;
        goto done;
    }
    for (index = 0; index < 3; index++) {
        saved_stdio[index] = duplicate_fd(index);
        if (saved_stdio[index] == -1) {
            continue;
        }
        if (saved_stdio[index] == -2) {
            error = errno;
            while (--index >= 0) {
                if (saved_stdio[index] >= 0) {
                    close(saved_stdio[index]);
                }
                saved_stdio[index] = -2;
            }
            close(terminal_fd);
            goto done;
        }
    }
    stdio_redirected = 1;
    fflush(NULL);
    for (index = 0; index < 3; index++) {
        if (dup2(terminal_fd, index) < 0) {
            error = errno;
            restore_stdio_locked();
            break;
        }
    }
    close(terminal_fd);

done:
    pthread_mutex_unlock(&stdio_mutex);
    return error;
}

JNIEXPORT jint JNICALL
Java_sui_k_als_agl_AglNative_rebindOutput(JNIEnv *env, jobject object, jint pid)
{
    int terminal_fd;
    int error = 0;
    int target;

    pthread_mutex_lock(&stdio_mutex);
    if (!stdio_redirected) {
        error = EINVAL;
        goto done;
    }
    terminal_fd = open_terminal(pid);
    if (terminal_fd < 0) {
        error = errno;
        goto done;
    }
    fflush(NULL);
    for (target = 1; target <= 2; target++) {
        if (dup2(terminal_fd, target) < 0) {
            error = errno;
            break;
        }
    }
    close(terminal_fd);

done:
    pthread_mutex_unlock(&stdio_mutex);
    return error;
}

JNIEXPORT void JNICALL
Java_sui_k_als_agl_AglNative_restoreStdio(JNIEnv *env, jobject object)
{
    pthread_mutex_lock(&stdio_mutex);
    restore_stdio_locked();
    pthread_mutex_unlock(&stdio_mutex);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    __android_log_set_minimum_priority(ANDROID_LOG_VERBOSE);
    __android_log_set_logger(alslog_file_logger);
    return JNI_VERSION_1_6;
}
