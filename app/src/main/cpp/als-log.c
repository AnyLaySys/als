#include <android/log.h>
#include <fcntl.h>
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define ALS_LOG_PATH "/data/local/tmp/als/als.log"

static void als_log_write_all(int fd, const char *buffer, size_t length)
{
    while (length) {
        ssize_t written = write(fd, buffer, length);

        if (written <= 0) {
            return;
        }
        buffer += written;
        length -= written;
    }
}

static char als_log_priority(int priority)
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

static void als_log_file_logger(const struct __android_log_message *log_message)
{
    const char *tag = log_message->tag ? log_message->tag : "ALS";
    const char *message = log_message->message ? log_message->message : "";
    struct timespec now;
    char prefix[160];
    int fd;
    int length;

    mkdir("/data/local/tmp/als", 0755);
    fd = open(ALS_LOG_PATH, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
    if (fd >= 0) {
        clock_gettime(CLOCK_REALTIME, &now);
        length = snprintf(prefix, sizeof(prefix), "%lld.%03ld %c/%s: ",
                          (long long) now.tv_sec, now.tv_nsec / 1000000,
                          als_log_priority(log_message->priority), tag);
        if (length > 0) {
            als_log_write_all(fd, prefix, length < sizeof(prefix) ? length : sizeof(prefix) - 1);
        }
        als_log_write_all(fd, message, strlen(message));
        als_log_write_all(fd, "\n", 1);
        close(fd);
    }
    __android_log_logd_logger(log_message);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    __android_log_set_logger(als_log_file_logger);
    return JNI_VERSION_1_6;
}
