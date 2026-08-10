#include <android/log.h>
#include <fcntl.h>
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define ALSLOG_PATH "/data/local/tmp/als/als.log"

static void alslog_write_all(int fd, const char *buffer, size_t length)
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
    char prefix[160];
    int fd;
    int length;

    mkdir("/data/local/tmp/als", 0755);
    fd = open(ALSLOG_PATH, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
    if (fd >= 0) {
        clock_gettime(CLOCK_REALTIME, &now);
        length = snprintf(prefix, sizeof(prefix), "%lld.%03ld %c/%s: ",
                          (long long) now.tv_sec, now.tv_nsec / 1000000,
                          alslog_priority(log_message->priority), tag);
        if (length > 0) {
            alslog_write_all(fd, prefix, length < sizeof(prefix) ? length : sizeof(prefix) - 1);
        }
        alslog_write_all(fd, message, strlen(message));
        alslog_write_all(fd, "\n", 1);
        close(fd);
    }
    __android_log_logd_logger(log_message);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    __android_log_set_minimum_priority(ANDROID_LOG_VERBOSE);
    __android_log_set_logger(alslog_file_logger);
    return JNI_VERSION_1_6;
}
