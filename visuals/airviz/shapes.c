/*----------------------------------------------------------------------
 * shapes.c - a port of code3.0/vdefLib.scd.
 *
 * These are deliberately transliterations, not improvements. Every default
 * point count, every magic constant and every quirk is carried across so a
 * personality drawn here looks like the same personality drawn under Qt -
 * including the ones that are arguably wrong:
 *
 *   \star  steps by i*pi/5 for 32 points, so the angle wraps several times.
 *          vdefLib.scd:118 says that IS the look, so it is preserved.
 *   \square clamps points-per-side to a minimum of 2, because the divide by
 *          (count-1) put a nan through every corner below that.
 *
 * ev->p0 / ev->p1 carry the per-shape parameters that vdefLib reads out of
 * \modulation (arcSpan/arcStart). They are separate wire fields here
 * because \modulation is the warp, and overloading it was only ever a
 * consequence of SC events having one free-form key.
 *--------------------------------------------------------------------*/
#include "airviz.h"
#include <math.h>
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif
#define TAU (2.0f * (float)M_PI)

static vec2 pt(float x, float y) { vec2 p; p.x = x; p.y = y; return p; }

static vec2 polar(vec2 o, float r, float a) {
    return pt(o.x + r * cosf(a), o.y + r * sinf(a));
}

static vec2 blend(vec2 a, vec2 b, float t) {
    return pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
}

static int deflt(int n, int fallback) { return n > 0 ? n : fallback; }

int av_shape_is_closed(const char *name) {
    if (!strcmp(name, "arc")    || !strcmp(name, "line") ||
        !strcmp(name, "wave")   || !strcmp(name, "spiral")) return 0;
    return 1;
}

int av_shape_points(const char *name, const av_event *ev,
                    vec2 pos, float size, vec2 *out, int cap) {
    int n, i;

    if (cap > AV_MAX_POINTS) cap = AV_MAX_POINTS;

    /*--- closed round forms ---*/
    if (!strcmp(name, "circle")) {
        n = deflt(ev->npts, 32); if (n > cap) n = cap;
        for (i = 0; i < n; i++) out[i] = polar(pos, size, (float)i / n * TAU);
        return n;
    }
    if (!strcmp(name, "arc")) {
        float span  = (ev->p0 != 0.f) ? ev->p0 : TAU;
        float start = (ev->p1 != 0.f) ? ev->p1 : (-span * 0.5f);
        n = deflt(ev->npts, 32); if (n > cap) n = cap; if (n < 2) n = 2;
        for (i = 0; i < n; i++)
            out[i] = polar(pos, size, start + ((float)i / (n - 1) * span));
        return n;
    }
    if (!strcmp(name, "blobby")) {
        n = deflt(ev->npts, 64); if (n > cap) n = cap;
        for (i = 0; i < n; i++) {
            float a = (float)i / n * TAU;
            float r = size * (0.8f + 0.3f * sinf(a * 3.f) + 0.15f * cosf(a * 5.f));
            out[i] = pt(pos.x + r * cosf(a), pos.y + r * sinf(a));
        }
        return n;
    }

    /*--- polygons ---*/
    if (!strcmp(name, "square")) {
        int per, s, j, k = 0;
        vec2 v[4];
        n = deflt(ev->npts, 32);
        per = n / 4; if (per < 2) per = 2;
        if (per * 4 > cap) per = cap / 4;
        v[0] = pt(pos.x - size, pos.y - size);
        v[1] = pt(pos.x + size, pos.y - size);
        v[2] = pt(pos.x + size, pos.y + size);
        v[3] = pt(pos.x - size, pos.y + size);
        for (s = 0; s < 4; s++)
            for (j = 0; j < per; j++)
                out[k++] = blend(v[s], v[(s + 1) & 3], (float)j / (per - 1));
        return k;
    }
    if (!strcmp(name, "triangle")) {
        float h = size * sqrtf(3.f) / 2.f;
        vec2 v[3];
        n = deflt(ev->npts, 32); if (n < 3) n = 3; if (n > cap) n = cap;
        v[0] = pt(pos.x,        pos.y - h);
        v[1] = pt(pos.x + size, pos.y + h / 2.f);
        v[2] = pt(pos.x - size, pos.y + h / 2.f);
        for (i = 0; i < n; i++) {
            float t = (float)i / n * 3.f;
            int   e = (int)floorf(t);
            out[i] = blend(v[e % 3], v[(e + 1) % 3], t - (float)e);
        }
        return n;
    }
    if (!strcmp(name, "hexagon")) {
        n = deflt(ev->npts, 6); if (n > cap) n = cap;
        for (i = 0; i < n; i++) {
            float a = (float)i * (TAU / n);
            out[i] = pt(pos.x + size * cosf(a), pos.y + size * sinf(a));
        }
        return n;
    }
    if (!strcmp(name, "star")) {
        float inner = (ev->p0 != 0.f) ? ev->p0 : 0.5f;
        n = deflt(ev->npts, 32); if (n > cap) n = cap;
        for (i = 0; i < n; i++) {
            float a = (float)i * (float)M_PI / 5.f;
            float r = (i % 2 == 0) ? size : size * inner;
            out[i] = pt(pos.x + r * cosf(a), pos.y + r * sinf(a));
        }
        return n;
    }
    if (!strcmp(name, "cross")) {
        float t = size / 6.f;
        if (cap < 12) return 0;
        out[0]  = pt(pos.x - t,    pos.y - size);
        out[1]  = pt(pos.x + t,    pos.y - size);
        out[2]  = pt(pos.x + t,    pos.y - t);
        out[3]  = pt(pos.x + size, pos.y - t);
        out[4]  = pt(pos.x + size, pos.y + t);
        out[5]  = pt(pos.x + t,    pos.y + t);
        out[6]  = pt(pos.x + t,    pos.y + size);
        out[7]  = pt(pos.x - t,    pos.y + size);
        out[8]  = pt(pos.x - t,    pos.y + t);
        out[9]  = pt(pos.x - size, pos.y + t);
        out[10] = pt(pos.x - size, pos.y - t);
        out[11] = pt(pos.x - t,    pos.y - t);
        return 12;
    }

    /*--- open forms ---*/
    if (!strcmp(name, "line")) {
        vec2 a = pt(pos.x - size, pos.y), b = pt(pos.x + size, pos.y);
        n = deflt(ev->npts, 32); if (n < 2) n = 2; if (n > cap) n = cap;
        for (i = 0; i < n; i++) out[i] = blend(a, b, (float)i / (n - 1));
        return n;
    }
    if (!strcmp(name, "wave")) {
        n = deflt(ev->npts, 64); if (n < 2) n = 2; if (n > cap) n = cap;
        for (i = 0; i < n; i++) {
            float t = (float)i / (n - 1);
            out[i] = pt(pos.x + (t * size * 2.f) - size,
                        pos.y + sinf(t * TAU) * (size / 2.f));
        }
        return n;
    }
    if (!strcmp(name, "spiral")) {
        float turns = (ev->p0 != 0.f) ? ev->p0 : 3.f;
        n = deflt(ev->npts, 100); if (n < 2) n = 2; if (n > cap) n = cap;
        for (i = 0; i < n; i++) {
            float p = (float)i / (n - 1);
            out[i] = polar(pos, size * p, p * turns * TAU);
        }
        return n;
    }
    if (!strcmp(name, "leaf")) {
        float w = size / 3.f;
        int half = deflt(ev->npts, 20) / 2, k = 0;
        if (half < 2) half = 2;
        if (half * 2 + 1 > cap) half = (cap - 1) / 2;
        for (i = 0; i <= half; i++) {
            float t = (float)i / half;
            out[k++] = pt(pos.x + size * t,
                          pos.y + w * sinf(t * (float)M_PI)
                                * (0.5f + sinf(t * (float)M_PI * 0.2f) * 0.5f));
        }
        for (i = 0; i < half; i++) {
            float t = (float)(half - i - 1) / half;
            out[k++] = pt(pos.x + size * t,
                          pos.y - w * sinf(t * (float)M_PI)
                                * (0.4f + sinf(t * (float)M_PI * 0.2f) * 0.5f));
        }
        return k;
    }

    /* unknown name -> circle, exactly as visualCore.scd:192 does. main.c
     * owns the warn-once so this stays a pure generator. */
    n = deflt(ev->npts, 32); if (n > cap) n = cap;
    for (i = 0; i < n; i++) out[i] = polar(pos, size, (float)i / n * TAU);
    return n;
}
