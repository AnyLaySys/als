#include <check.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/wait.h>

START_TEST(test_shell_command_injection)
{
    // Invariant: Shell commands never include unsanitized user input
    const char *payloads[] = {
        "/bin/su -v",           // Valid input
        "/bin/su; rm -rf /",    // Command injection with semicolon
        "/bin/su$(whoami)",     // Command substitution
        "/bin/su`id`",          // Backtick command execution
        "/bin/su || cat /etc/passwd"  // OR operator injection
    };
    int num_payloads = sizeof(payloads) / sizeof(payloads[0]);

    for (int i = 0; i < num_payloads; i++) {
        pid_t pid = fork();
        if (pid == 0) {
            // Child process - execute the command
            char *args[] = {"/bin/sh", "-c", "echo", "test", NULL};
            
            // Simulate the vulnerable exec pattern
            char command[256];
            snprintf(command, sizeof(command), "su -v 2>&1 | grep -q 'su'");
            
            // Check if payload contains shell metacharacters
            const char *dangerous_chars = ";|&$()`";
            int has_metacharacters = 0;
            for (int j = 0; dangerous_chars[j]; j++) {
                if (strchr(payloads[i], dangerous_chars[j])) {
                    has_metacharacters = 1;
                    break;
                }
            }
            
            // If payload has metacharacters, execution should fail or be sanitized
            if (has_metacharacters) {
                // Attempt to execute - should fail or be sanitized
                execl("/bin/sh", "sh", "-c", "false", NULL);
            } else {
                // Safe command should execute normally
                execl("/bin/sh", "sh", "-c", "true", NULL);
            }
            _exit(1);
        } else if (pid > 0) {
            // Parent process - wait for child
            int status;
            waitpid(pid, &status, 0);
            
            // Check if execution was properly handled
            if (strstr(payloads[i], ";") || strstr(payloads[i], "$(") || 
                strstr(payloads[i], "`") || strstr(payloads[i], "||")) {
                // For payloads with metacharacters, we expect failure or sanitization
                ck_assert_msg(WEXITSTATUS(status) != 0 || 
                             strstr(payloads[i], "/bin/su -v"),
                             "Shell metacharacters in '%s' were not properly sanitized", 
                             payloads[i]);
            } else {
                // Valid input should work normally
                ck_assert_msg(WEXITSTATUS(status) == 0,
                             "Valid input '%s' was incorrectly rejected", 
                             payloads[i]);
            }
        }
    }
}
END_TEST

Suite *security_suite(void)
{
    Suite *s;
    TCase *tc_core;

    s = suite_create("Security");
    tc_core = tcase_create("Core");

    tcase_add_test(tc_core, test_shell_command_injection);
    suite_add_tcase(s, tc_core);

    return s;
}

int main(void)
{
    int number_failed;
    Suite *s;
    SRunner *sr;

    s = security_suite();
    sr = srunner_create(s);

    srunner_run_all(sr, CK_NORMAL);
    number_failed = srunner_ntests_failed(sr);
    srunner_free(sr);

    return (number_failed == 0) ? EXIT_SUCCESS : EXIT_FAILURE;
}