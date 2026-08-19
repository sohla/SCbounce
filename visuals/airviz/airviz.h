/*----------------------------------------------------------------------
 * airviz.h - shared types for the GLES2 visual renderer.
 *
 * The event model is a direct port of code3.0/visualCore.scd: an event is
 * fired once with a start time and a duration, and every start*->end* pair
 * blends across it. Nothing is streamed per frame except explicit points.
 *
 * Units match visualCore exactly:
 *   sx/sy/ex/ey  normalised, 0 = canvas centre, +-1 = edge (y DOWN, as Qt)
 *   size, width  PIXELS
 *   rot          radians, clockwise on screen, about the mark's own origin
 *   dur          seconds; < 0 means held (visualCore's `duration: inf`)
 *--------------------------------------------------------------------*/
#ifndef AIRVIZ_H
#define AIRVIZ_H

#define AV_MAX_EVENTS 512
#define AV_MAX_POINTS 256
#define AV_NAME_LEN   24

typedef struct { float x, y; } vec2;

/* Env shapes. SC's \exp is unrepresentable on a 0->1 segment (it needs a
 * non-zero start), so the sender maps it to CURVE with curve = -4. */
enum {
    AV_ENV_LIN = 0,
    AV_ENV_SIN,
    AV_ENV_WELCH,
    AV_ENV_SQR,
    AV_ENV_CUB,
    AV_ENV_CURVE
};

/* mirrors visualCore.scd:113 \modulation[\type] */
enum {
    AV_MOD_NONE = 0,
    AV_MOD_RADIAL,
    AV_MOD_NORMAL,
    AV_MOD_NOISE,
    AV_MOD_PHASE
};

typedef struct {
    int   kind;
    float curve;   /* only read when kind == AV_ENV_CURVE */
} av_env;

typedef struct {
    int    live;
    int    src;                    /* device port - the routing key */
    char   shape[AV_NAME_LEN];

    double start;                  /* monotonic seconds, already delayed */
    double dur;                    /* < 0 == held, never culled */

    float  sx, sy, ex, ey;
    av_env xenv, yenv, senv, wenv, cenv;

    float  size0, size1;
    float  w0, w1;
    float  col0[4], col1[4];

    float  rot;
    int    fill, closed, npts;

    int    modType;
    float  modFreq, modAmp, modPhase, modHarm;

    float  p0, p1;                 /* per-shape params: arc span/start, etc */

    int    nuser;                  /* explicit points, unit space; 0 = use shape */
    vec2   user[AV_MAX_POINTS];
} av_event;

/* shapes.c - the vdefLib.scd port. Writes into out, returns the count.
 * pos and size are the already-resolved pixel values for this frame. */
int  av_shape_points(const char *name, const av_event *ev,
                     vec2 pos, float size, vec2 *out, int cap);
int  av_shape_is_closed(const char *name);   /* default \closed for a shape */

/* gfx.c - EGL + GLES2 */
int  av_gfx_open(int want_w, int want_h, int fullscreen, const char *title);
void av_gfx_size(int *w, int *h);
void av_gfx_begin_frame(void);
void av_gfx_end_frame(void);
void av_gfx_close(void);

/* One stroked polyline. half_w is in pixels; colour is straight (not
 * premultiplied) RGBA and is premultiplied inside the shader. */
void av_gfx_stroke(const vec2 *pts, int n, int closed,
                   float half_w, const float rgba[4]);
/* One filled polygon, triangle-fanned from its centroid. */
void av_gfx_fill(const vec2 *pts, int n, const float rgba[4]);

double av_now(void);   /* CLOCK_MONOTONIC seconds */

#endif
