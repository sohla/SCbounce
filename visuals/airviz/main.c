/*----------------------------------------------------------------------
 * main.c - the event pool, the envelope/modulation pipeline, and the
 * frame loop. This is the port of visualCore.scd's drawCanvas.
 *
 * ONE THREAD, NO LOCKS. OSC is drained non-blocking at the top of each
 * frame. It could be a reader thread with a ring buffer, but there is no
 * point: every event already carries a `delay` and sclang stamps it
 * s.latency ahead (main.sc:144), so up to a frame of scheduling jitter
 * lands well inside a window the system was already designed around.
 * Threads here would buy nothing and cost a lock in the draw path.
 *
 * NO ALLOCATION AFTER STARTUP. Fixed event pool, fixed point buffers. A
 * malloc in a 60Hz loop is a stutter waiting for a busy performance.
 *--------------------------------------------------------------------*/
#include "airviz.h"
#include "osc.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif
#define TAU (2.0f * (float)M_PI)

#define AV_WIRE_ARGS 41          /* fields before the optional points blob */

static av_event pool[AV_MAX_EVENTS];
static int      pool_next;
static int      running = 1;
static int      sock = -1;
static int      verbose;
static int      headless;   /* -n : no draw, dump parsed events instead */

static vec2 bufA[AV_MAX_POINTS], bufB[AV_MAX_POINTS];

/* warn-once on an unknown shape, mirroring visualCore.scd:194 */
static char warned[16][AV_NAME_LEN];
static int  nwarned;

static const char *KNOWN[] = {
    "circle","arc","blobby","square","triangle","hexagon","star","cross",
    "line","wave","spiral","leaf", NULL
};

static void on_sig(int s) { (void)s; running = 0; }

/*--------------------------------------------------------------------*/
static float env_at(const av_env *e, float t) {
    if (t <= 0.f) return 0.f;
    if (t >= 1.f) return 1.f;
    switch (e->kind) {
        case AV_ENV_SIN:   return 0.5f - 0.5f * cosf((float)M_PI * t);
        case AV_ENV_WELCH: return sinf((float)M_PI * 0.5f * t);
        case AV_ENV_SQR:   return t * t;
        case AV_ENV_CUB:   return t * t * t;
        case AV_ENV_CURVE:
            if (fabsf(e->curve) < 0.001f) return t;
            return (1.f - expf(t * e->curve)) / (1.f - expf(e->curve));
        default:           return t;
    }
}

static float lerp(float a, float b, float t) { return a + (b - a) * t; }

/*----------------------------------------------------------------------
 * modulation - a transliteration of visualCore.scd:105-154.
 *
 * Including the consequence documented there: every displacement is
 * windowed by |sin(harmonics*t*2pi)|, which is 0 at both ends of a path,
 * so a 2-POINT PATH NEVER MOVES however large amp is. That is deliberate
 * (a warped shape must not tear at its seam) and is preserved, so marks
 * built from 2-point spans behave here exactly as they do under Qt.
 *--------------------------------------------------------------------*/
static void modulate(const av_event *ev, vec2 *p, int n, vec2 pos,
                     float size_norm, double now) {
    float amp, lfo;
    int i;

    if (ev->modType == AV_MOD_NONE || n < 2) return;

    amp = ev->modAmp * (1.f - size_norm);
    lfo = sinf(TAU * ev->modFreq * (float)now + ev->modPhase) * amp;

    memcpy(bufB, p, (size_t)n * sizeof(vec2));

    for (i = 0; i < n; i++) {
        float t = (float)i / (float)(n - 1);
        float dx = bufB[i].x - pos.x, dy = bufB[i].y - pos.y;

        switch (ev->modType) {
            case AV_MOD_RADIAL: {
                float f = lfo * fabsf(sinf(ev->modHarm * t * TAU));
                float r = sqrtf(dx * dx + dy * dy);
                float a = atan2f(dy, dx);
                p[i].x = pos.x + (r + f) * cosf(a);
                p[i].y = pos.y + (r + f) * sinf(a);
                break;
            }
            case AV_MOD_NORMAL: {
                float d = lfo * sinf(ev->modHarm * t * TAU);
                float a = atan2f(dy, dx) + (float)M_PI * 0.5f;
                p[i].x = bufB[i].x + d * cosf(a);
                p[i].y = bufB[i].y + d * sinf(a);
                break;
            }
            case AV_MOD_NOISE: {
                float seed = (float)i * 1000.f + floorf((float)now) * 50.f;
                float jf   = sinf(t * ev->modHarm * TAU);
                float rx   = fabsf(sinf(seed * 12345.6789f));
                float ry   = fabsf(sinf((seed + 500.f) * 12345.6789f));
                p[i].x = bufB[i].x + lfo * jf * (rx * 2.f - 1.f);
                p[i].y = bufB[i].y + lfo * jf * (ry * 2.f - 1.f);
                break;
            }
            case AV_MOD_PHASE: {
                float idx = (float)i + lfo * ev->modHarm;
                float span = (float)(n - 1);
                int lo, hi; float b;
                idx = fmodf(idx, span); if (idx < 0.f) idx += span;
                lo = (int)floorf(idx);
                hi = (lo + 1) % n;
                b  = idx - (float)lo;
                p[i].x = lerp(bufB[lo].x, bufB[hi].x, b);
                p[i].y = lerp(bufB[lo].y, bufB[hi].y, b);
                break;
            }
        }
    }
}

/*--------------------------------------------------------------------*/
static void draw_event(const av_event *ev, double now, int w, int h) {
    float mx = (float)w * 0.5f, my = (float)h * 0.5f;
    float elapsed = (float)(now - ev->start);
    float nt = 0.f;
    float size, width, col[4], sn, cr, sr;
    vec2  pos;
    int   n, i, closed;

    if (elapsed < 0.f) return;                    /* still latency-delayed */
    if (ev->dur >= 0.0) {
        if (ev->dur != 0.0) nt = elapsed / (float)ev->dur;
        if (nt >= 1.f) return;
    }
    /* held events keep nt == 0, so every start->end blend resolves to its
     * start value - the same static behaviour visualCore has for inf. */

    pos.x = lerp(ev->sx, ev->ex, env_at(&ev->xenv, nt)) * mx + mx;
    pos.y = lerp(ev->sy, ev->ey, env_at(&ev->yenv, nt)) * my + my;

    sn    = env_at(&ev->senv, nt);
    size  = lerp(ev->size0, ev->size1, sn);
    width = lerp(ev->w0, ev->w1, env_at(&ev->wenv, nt));
    {
        float ct = env_at(&ev->cenv, nt);
        for (i = 0; i < 4; i++) col[i] = lerp(ev->col0[i], ev->col1[i], ct);
    }

    if (ev->nuser > 0) {
        /* explicit points arrive in unit space so they still track size */
        n = ev->nuser; if (n > AV_MAX_POINTS) n = AV_MAX_POINTS;
        for (i = 0; i < n; i++) {
            bufA[i].x = pos.x + ev->user[i].x * size;
            bufA[i].y = pos.y + ev->user[i].y * size;
        }
        closed = (ev->closed >= 0) ? ev->closed : 1;
    } else {
        n = av_shape_points(ev->shape, ev, pos, size, bufA, AV_MAX_POINTS);
        closed = (ev->closed >= 0) ? ev->closed : av_shape_is_closed(ev->shape);
    }
    if (n < 2) return;

    modulate(ev, bufA, n, pos, sn, now);

    if (ev->rot != 0.f) {
        cr = cosf(ev->rot); sr = sinf(ev->rot);
        for (i = 0; i < n; i++) {
            float dx = bufA[i].x - pos.x, dy = bufA[i].y - pos.y;
            bufA[i].x = pos.x + dx * cr - dy * sr;
            bufA[i].y = pos.y + dx * sr + dy * cr;
        }
    }

    /* \fill on a \line is a stroke, as visualCore.scd:166 has it */
    if (ev->fill && strcmp(ev->shape, "line") != 0) av_gfx_fill(bufA, n, col);
    else                                            av_gfx_stroke(bufA, n, closed, width * 0.5f, col);
}

/*--------------------------------------------------------------------*/
static av_event *alloc_event(void) {
    int i;
    for (i = 0; i < AV_MAX_EVENTS; i++) {
        av_event *e = &pool[(pool_next + i) % AV_MAX_EVENTS];
        if (!e->live) { pool_next = (pool_next + i + 1) % AV_MAX_EVENTS; return e; }
    }
    return NULL;   /* pool full: drop. Never stall the loop to make room. */
}

static void warn_shape(const char *name) {
    int i;
    for (i = 0; KNOWN[i]; i++) if (!strcmp(KNOWN[i], name)) return;
    for (i = 0; i < nwarned; i++) if (!strcmp(warned[i], name)) return;
    if (nwarned < 16) {
        snprintf(warned[nwarned++], AV_NAME_LEN, "%s", name);
        fprintf(stderr, "[airviz] unknown shape %s - drawing a circle\n", name);
    }
}

static void handle_ev(osc_iter *it, double now) {
    av_event *e = alloc_event();
    av_event tmp;
    const char *shape = "circle";
    const char *blob = NULL;
    uint32_t blob_n = 0;
    float delay = 0.f, durf = 0.f;
    int32_t iv; float fv; int i, ok = 1;

    memset(&tmp, 0, sizeof tmp);

#define GI(dst) do { if (!ok || !osc_int(it, &iv)) { ok = 0; } else (dst) = iv; } while (0)
#define GF(dst) do { if (!ok || !osc_float(it, &fv)) { ok = 0; } else (dst) = fv; } while (0)

    GI(tmp.src);
    if (ok && !osc_str(it, &shape)) ok = 0;
    GF(delay);
    GF(durf);
    GF(tmp.sx); GF(tmp.sy); GF(tmp.ex); GF(tmp.ey);
    GI(tmp.xenv.kind); GF(tmp.xenv.curve);
    GI(tmp.yenv.kind); GF(tmp.yenv.curve);
    GF(tmp.size0); GF(tmp.size1); GI(tmp.senv.kind); GF(tmp.senv.curve);
    GF(tmp.w0); GF(tmp.w1); GI(tmp.wenv.kind); GF(tmp.wenv.curve);
    for (i = 0; i < 4; i++) GF(tmp.col0[i]);
    for (i = 0; i < 4; i++) GF(tmp.col1[i]);
    GI(tmp.cenv.kind); GF(tmp.cenv.curve);
    GF(tmp.rot);
    GI(tmp.fill); GI(tmp.closed); GI(tmp.npts);
    GI(tmp.modType);
    GF(tmp.modFreq); GF(tmp.modAmp); GF(tmp.modPhase); GF(tmp.modHarm);
    GF(tmp.p0); GF(tmp.p1);
#undef GI
#undef GF

    if (!ok) { if (verbose) fprintf(stderr, "[airviz] malformed /av/ev\n"); return; }

    if (osc_tag(it) == 'b' && osc_blob(it, &blob, &blob_n)) {
        int np = (int)(blob_n / 8);
        if (np > AV_MAX_POINTS) np = AV_MAX_POINTS;
        for (i = 0; i < np; i++) {
            tmp.user[i].x = osc_blob_f32(blob, i * 2);
            tmp.user[i].y = osc_blob_f32(blob, i * 2 + 1);
        }
        tmp.nuser = np;
    }

    snprintf(tmp.shape, AV_NAME_LEN, "%s", shape);
    if (!tmp.nuser) warn_shape(tmp.shape);
    tmp.dur   = (double)durf;      /* < 0 == held, mirrors duration: inf */
    tmp.start = now + (double)delay;
    tmp.live  = 1;

    if (!e) { if (verbose) fprintf(stderr, "[airviz] pool full, dropped\n"); return; }
    *e = tmp;

    if (headless && verbose) {
        printf("ev src=%d shape=%-10s delay=%.3f dur=%6.2f  s=(%.2f,%.2f)->(%.2f,%.2f)\n"
               "   size %.1f->%.1f  width %.1f->%.1f  rot %.3f  fill=%d closed=%d npts=%d\n"
               "   col (%.2f %.2f %.2f %.2f)->(%.2f %.2f %.2f %.2f)\n"
               "   env x=%d/%.2f y=%d/%.2f size=%d/%.2f width=%d/%.2f col=%d/%.2f\n"
               "   mod type=%d freq=%.2f amp=%.2f harm=%.2f   p0=%.3f p1=%.3f  userpts=%d\n",
               tmp.src, tmp.shape, delay, tmp.dur, tmp.sx, tmp.sy, tmp.ex, tmp.ey,
               tmp.size0, tmp.size1, tmp.w0, tmp.w1, tmp.rot,
               tmp.fill, tmp.closed, tmp.npts,
               tmp.col0[0], tmp.col0[1], tmp.col0[2], tmp.col0[3],
               tmp.col1[0], tmp.col1[1], tmp.col1[2], tmp.col1[3],
               tmp.xenv.kind, tmp.xenv.curve, tmp.yenv.kind, tmp.yenv.curve,
               tmp.senv.kind, tmp.senv.curve, tmp.wenv.kind, tmp.wenv.curve,
               tmp.cenv.kind, tmp.cenv.curve,
               tmp.modType, tmp.modFreq, tmp.modAmp, tmp.modHarm,
               tmp.p0, tmp.p1, tmp.nuser);
        fflush(stdout);
    }
}

static void handle_packet(const char *buf, size_t len, double now) {
    osc_msg m;
    osc_iter it;

    if (osc_is_bundle(buf, len)) {
        size_t o = 16;                         /* timetag ignored on purpose */
        while (o + 4 <= len) {
            uint32_t sz = osc_be32(buf + o);
            if (o + 4 + sz > len) break;
            handle_packet(buf + o + 4, sz, now);
            o += 4 + sz;
        }
        return;
    }
    if (!osc_parse(buf, len, &m)) return;
    osc_begin(&m, &it);

    if (!strcmp(m.addr, "/av/ev")) {
        handle_ev(&it, now);
    } else if (!strcmp(m.addr, "/av/clear")) {
        int32_t src; int i;
        if (osc_int(&it, &src))
            for (i = 0; i < AV_MAX_EVENTS; i++)
                if (pool[i].live && pool[i].src == src) pool[i].live = 0;
    } else if (!strcmp(m.addr, "/av/quit")) {
        running = 0;
    }
}

static void drain_osc(double now) {
    static char buf[65536];
    for (;;) {
        ssize_t n = recv(sock, buf, sizeof buf, 0);
        if (n <= 0) {
            if (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK)
                fprintf(stderr, "[airviz] recv: %s\n", strerror(errno));
            return;
        }
        handle_packet(buf, (size_t)n, now);
    }
}

/*--------------------------------------------------------------------*/
int main(int argc, char **argv) {
    struct sockaddr_in sa;
    int port = 57130, w = 0, h = 0, fullscreen = 1, i;
    double t_stats;
    long frames = 0;
    double busy = 0.0, busy_max = 0.0;

    for (i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "-p") && i + 1 < argc)      port = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-w") && i + 1 < argc) w = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-h") && i + 1 < argc) h = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-W"))                 fullscreen = 0;
        else if (!strcmp(argv[i], "-v"))                 verbose = 1;
        else if (!strcmp(argv[i], "-n"))                 headless = 1;
        else {
            printf("usage: airviz [-p port] [-w W] [-h H] [-W windowed] [-n headless] [-v]\n");
            return 0;
        }
    }

    signal(SIGINT, on_sig);
    signal(SIGTERM, on_sig);

    sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) { perror("socket"); return 1; }
    memset(&sa, 0, sizeof sa);
    sa.sin_family = AF_INET;
    sa.sin_addr.s_addr = htonl(INADDR_ANY);
    sa.sin_port = htons((uint16_t)port);
    if (bind(sock, (struct sockaddr *)&sa, sizeof sa) < 0) { perror("bind"); return 1; }
    fcntl(sock, F_SETFL, O_NONBLOCK);
    printf("[airviz] listening on udp/%d\n", port);

    if (!av_gfx_open(w, h, fullscreen, "AirKit Visuals")) return 1;

    t_stats = av_now();
    while (running) {
        double now = av_now(), t0;
        int cw, ch;

        drain_osc(now);
        av_gfx_size(&cw, &ch);
        if (!headless) av_gfx_begin_frame();

        t0 = av_now();
        for (i = 0; i < AV_MAX_EVENTS; i++) {
            av_event *e = &pool[i];
            if (!e->live) continue;
            if (e->dur >= 0.0 && (now - e->start) >= e->dur) { e->live = 0; continue; }
            if (!headless) draw_event(e, now, cw, ch);
        }
        {
            double d = av_now() - t0;
            busy += d;
            if (d > busy_max) busy_max = d;
        }

        /* eglSwapBuffers is the frame pacer; with no GL there is none, so
         * headless has to sleep or it spins a core at 100%. */
        if (headless) usleep(4000); else av_gfx_end_frame();
        frames++;

        if (verbose && now - t_stats >= 2.0) {
            int live = 0;
            for (i = 0; i < AV_MAX_EVENTS; i++) live += pool[i].live;
            printf("[airviz] %5.1f fps  cpu %.2f ms (max %.2f)  live %d\n",
                   frames / (now - t_stats),
                   busy / frames * 1000.0, busy_max * 1000.0, live);
            frames = 0; busy = 0.0; busy_max = 0.0; t_stats = now;
        }
    }

    av_gfx_close();
    close(sock);
    printf("[airviz] bye\n");
    return 0;
}
