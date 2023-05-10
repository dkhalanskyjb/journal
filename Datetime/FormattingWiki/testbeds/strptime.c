#define _XOPEN_SOURCE
#include <time.h>
#include <stdlib.h>
#include <stdio.h>

int main(int argc, char **argv) {
    if (argc != 3) {
        printf("Usage: %s FORMAT STRING\n", argv[0]);
        return 1;
    }
    const char *format = argv[1];
    const char *datestr = argv[2];
    struct tm tm;
    tm.tm_sec = 99;
    tm.tm_min = 99;
    tm.tm_hour = 99;
    tm.tm_mday = 99;
    tm.tm_mon = 99 - 1;
    tm.tm_year = 9999 - 1900;
    tm.tm_wday = -1;
    tm.tm_yday = 999 - 1;
    tm.tm_isdst = -1;
    const char *rest = strptime(datestr, format, &tm);
    if (rest == 0) {
        printf("Could not parse the string\n");
    } else if (*rest != 0) {
        printf("Not the whole string was consumed: '%s'\n", rest);
    }
    const char *wday;
    switch (tm.tm_wday) {
        case 0: wday = "Sunday"; break;
        case 1: wday = "Monday"; break;
        case 2: wday = "Tuesday"; break;
        case 3: wday = "Wednesday"; break;
        case 4: wday = "Thursday"; break;
        case 5: wday = "Friday"; break;
        case 6: wday = "Saturday"; break;
        case -1: wday = "Unknown day of the week"; break;
        default: exit(-2);
    }
    printf("%04d-%02d-%02dT%02d:%02d:%02d (day of year %d, %s), %s\n",
        tm.tm_year + 1900,
        tm.tm_mon + 1,
        tm.tm_mday,
        tm.tm_hour,
        tm.tm_min,
        tm.tm_sec,
        tm.tm_yday + 1,
        wday,
        tm.tm_isdst > 0 ? "DST" : tm.tm_isdst == 0 ? "no DST" : "maybe DST"
    );
}
