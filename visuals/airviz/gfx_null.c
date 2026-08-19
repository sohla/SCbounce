/*----------------------------------------------------------------------
 * gfx_null.c - the headless build.
 *
 *     make airviz-headless && ./airviz-headless -n -v
 *
 * Links in place of gfx.c so the OSC path, the wire format and the whole
 * event/envelope pipeline can be exercised on a machine with no display -
 * a laptop, or the Pi over ssh. `-n -v` prints every event as it is
 * parsed, which is the fastest way to tell a sender bug from a renderer
 * bug: if the fields are right here, the problem is in GL, and if they are
 * not, the problem is in airviz.scd.
 *
 * It also builds and runs on macOS, so the SC side can be developed
 * against it without touching the Pi.
 *--------------------------------------------------------------------*/
#include "airviz.h"
#include <time.h>

#ifdef __APPLE__
#include <mach/mach_time.h>
#endif

static int null_w = 1920, null_h = 1080;

int av_gfx_open(int w, int h, int fullscreen, const char *title) {
    (void)fullscreen; (void)title;
    if (w > 0) null_w = w;
    if (h > 0) null_h = h;
    return 1;
}

void av_gfx_size(int *w, int *h) { *w = null_w; *h = null_h; }
void av_gfx_begin_frame(void) {}
void av_gfx_end_frame(void) {}
void av_gfx_close(void) {}

void av_gfx_stroke(const vec2 *pts, int n, int closed, float half_w, const float rgba[4]) {
    (void)pts; (void)n; (void)closed; (void)half_w; (void)rgba;
}

void av_gfx_fill(const vec2 *pts, int n, const float rgba[4]) {
    (void)pts; (void)n; (void)rgba;
}

double av_now(void) {
#ifdef CLOCK_MONOTONIC
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (double)t.tv_sec + (double)t.tv_nsec * 1e-9;
#else
    return (double)clock() / (double)CLOCKS_PER_SEC;
#endif
}
