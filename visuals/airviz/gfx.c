/*----------------------------------------------------------------------
 * gfx.c - EGL + GLES2 on X11, and the two primitives everything is built
 * from: a stroked polyline and a filled polygon.
 *
 * WHY X11 AND NOT DRM/KMS. Direct GBM/KMS would be lower latency and is
 * the right answer for a dedicated visuals box, but it needs DRM master -
 * which the desktop compositor already holds, and the Pi still has to run
 * the SC panel UI on the DSI. Under labwc/XWayland an X11 window costs one
 * extra composite pass and about 250 fewer lines than the Wayland
 * boilerplate. See README for the KMS route.
 *
 * WHY NO MSAA. A 4K multisample buffer is the one thing on this GPU that
 * really does cost memory bandwidth. Strokes are expanded to a triangle
 * ribbon on the CPU and antialiased analytically in the fragment shader
 * against a 1px feather, so AA is free and independent of resolution.
 * That is the whole reason 4K becomes affordable here and is not under Qt.
 *--------------------------------------------------------------------*/
#include "airviz.h"

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <X11/Xlib.h>
#include <X11/Xatom.h>

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <time.h>

#define AV_MAX_VERTS (AV_MAX_POINTS * 2 + 4)

static Display   *dpy;
static Window     win;
static EGLDisplay egl_dpy = EGL_NO_DISPLAY;
static EGLSurface egl_surf = EGL_NO_SURFACE;
static EGLContext egl_ctx = EGL_NO_CONTEXT;
static int        fb_w, fb_h;

static GLuint prog, vbo;
static GLint  a_pos, a_side, u_res, u_color, u_halfw, u_solid;

/* vSide runs -1..+1 across the ribbon. The fragment shader turns the
 * distance from the ribbon edge into coverage, so a 0.5px stroke fades
 * rather than dropping out - which is what Qt's antialiased hairline does
 * and what a lot of these marks rely on at low amplitude. */
static const char *VS =
    "attribute vec2 aPos;\n"
    "attribute float aSide;\n"
    "uniform vec2 uRes;\n"
    "varying float vSide;\n"
    "void main(){\n"
    "  vSide = aSide;\n"
    "  vec2 n = aPos / uRes * 2.0 - 1.0;\n"
    "  gl_Position = vec4(n.x, -n.y, 0.0, 1.0);\n"   /* y down, as Qt */
    "}\n";

static const char *FS =
    "precision mediump float;\n"
    "uniform vec4 uColor;\n"
    "uniform float uHalfW;\n"
    "uniform float uSolid;\n"
    "varying float vSide;\n"
    "void main(){\n"
    "  float a = uColor.a;\n"
    "  if (uSolid < 0.5) {\n"
    "    float d = (1.0 - abs(vSide)) * uHalfW;\n"   /* px in from the edge */
    "    a *= clamp(d, 0.0, 1.0);\n"
    "  }\n"
    "  gl_FragColor = vec4(uColor.rgb * a, a);\n"    /* premultiplied */
    "}\n";

double av_now(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (double)t.tv_sec + (double)t.tv_nsec * 1e-9;
}

static GLuint compile(GLenum type, const char *src) {
    GLuint s = glCreateShader(type);
    GLint ok = 0;
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(s, sizeof log, NULL, log);
        fprintf(stderr, "[airviz] shader: %s\n", log);
        return 0;
    }
    return s;
}

int av_gfx_open(int want_w, int want_h, int fullscreen, const char *title) {
    static const EGLint cfg_attr[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 0,
        EGL_NONE
    };
    static const EGLint ctx_attr[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLConfig cfg;
    EGLint n = 0, maj, min;
    XSetWindowAttributes swa;
    GLuint vs, fs;

    dpy = XOpenDisplay(NULL);
    if (!dpy) { fprintf(stderr, "[airviz] no X display\n"); return 0; }

    if (want_w <= 0) want_w = DisplayWidth(dpy, DefaultScreen(dpy));
    if (want_h <= 0) want_h = DisplayHeight(dpy, DefaultScreen(dpy));

    swa.event_mask = StructureNotifyMask | KeyPressMask;
    swa.background_pixel = 0;
    swa.border_pixel = 0;
    swa.override_redirect = False;
    win = XCreateWindow(dpy, DefaultRootWindow(dpy), 0, 0, want_w, want_h, 0,
                        CopyFromParent, InputOutput, CopyFromParent,
                        CWEventMask | CWBackPixel | CWBorderPixel, &swa);
    XStoreName(dpy, win, title ? title : "airviz");

    if (fullscreen) {
        Atom st = XInternAtom(dpy, "_NET_WM_STATE", False);
        Atom fsa = XInternAtom(dpy, "_NET_WM_STATE_FULLSCREEN", False);
        XChangeProperty(dpy, win, st, XA_ATOM, 32, PropModeReplace,
                        (unsigned char *)&fsa, 1);
    }
    XMapWindow(dpy, win);
    XFlush(dpy);

    egl_dpy = eglGetDisplay((EGLNativeDisplayType)dpy);
    if (!eglInitialize(egl_dpy, &maj, &min)) {
        fprintf(stderr, "[airviz] eglInitialize failed\n"); return 0;
    }
    eglBindAPI(EGL_OPENGL_ES_API);
    if (!eglChooseConfig(egl_dpy, cfg_attr, &cfg, 1, &n) || n < 1) {
        fprintf(stderr, "[airviz] no EGL config\n"); return 0;
    }
    egl_surf = eglCreateWindowSurface(egl_dpy, cfg, (EGLNativeWindowType)win, NULL);
    egl_ctx  = eglCreateContext(egl_dpy, cfg, EGL_NO_CONTEXT, ctx_attr);
    if (egl_surf == EGL_NO_SURFACE || egl_ctx == EGL_NO_CONTEXT) {
        fprintf(stderr, "[airviz] EGL surface/context failed\n"); return 0;
    }
    eglMakeCurrent(egl_dpy, egl_surf, egl_surf, egl_ctx);
    eglSwapInterval(egl_dpy, 1);

    vs = compile(GL_VERTEX_SHADER, VS);
    fs = compile(GL_FRAGMENT_SHADER, FS);
    if (!vs || !fs) return 0;
    prog = glCreateProgram();
    glAttachShader(prog, vs); glAttachShader(prog, fs);
    glBindAttribLocation(prog, 0, "aPos");
    glBindAttribLocation(prog, 1, "aSide");
    glLinkProgram(prog);
    glUseProgram(prog);
    a_pos   = 0;
    a_side  = 1;
    u_res   = glGetUniformLocation(prog, "uRes");
    u_color = glGetUniformLocation(prog, "uColor");
    u_halfw = glGetUniformLocation(prog, "uHalfW");
    u_solid = glGetUniformLocation(prog, "uSolid");

    glGenBuffers(1, &vbo);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, AV_MAX_VERTS * 3 * sizeof(float), NULL, GL_STREAM_DRAW);
    glEnableVertexAttribArray(a_pos);
    glEnableVertexAttribArray(a_side);
    glVertexAttribPointer(a_pos,  2, GL_FLOAT, GL_FALSE, 3 * sizeof(float), (void *)0);
    glVertexAttribPointer(a_side, 1, GL_FLOAT, GL_FALSE, 3 * sizeof(float), (void *)(2 * sizeof(float)));

    /* premultiplied source-over - matches Qt's default composite over the
     * fixed black ground. For additive (often better over black) this is
     * the single line to change: GL_ONE, GL_ONE. */
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);

    eglQuerySurface(egl_dpy, egl_surf, EGL_WIDTH,  &fb_w);
    eglQuerySurface(egl_dpy, egl_surf, EGL_HEIGHT, &fb_h);
    printf("[airviz] %dx%d  GL_RENDERER=%s\n", fb_w, fb_h, glGetString(GL_RENDERER));
    return 1;
}

void av_gfx_size(int *w, int *h) { *w = fb_w; *h = fb_h; }

void av_gfx_begin_frame(void) {
    XEvent e;
    while (XPending(dpy)) XNextEvent(dpy, &e);   /* drain; resize is picked up below */

    eglQuerySurface(egl_dpy, egl_surf, EGL_WIDTH,  &fb_w);
    eglQuerySurface(egl_dpy, egl_surf, EGL_HEIGHT, &fb_h);

    glViewport(0, 0, fb_w, fb_h);
    glClearColor(0.f, 0.f, 0.f, 1.f);            /* grounds are fixed black */
    glClear(GL_COLOR_BUFFER_BIT);
    glUniform2f(u_res, (float)fb_w, (float)fb_h);
}

void av_gfx_end_frame(void) { eglSwapBuffers(egl_dpy, egl_surf); }

void av_gfx_close(void) {
    if (egl_dpy != EGL_NO_DISPLAY) {
        eglMakeCurrent(egl_dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (egl_ctx  != EGL_NO_CONTEXT) eglDestroyContext(egl_dpy, egl_ctx);
        if (egl_surf != EGL_NO_SURFACE) eglDestroySurface(egl_dpy, egl_surf);
        eglTerminate(egl_dpy);
    }
    if (dpy) { XDestroyWindow(dpy, win); XCloseDisplay(dpy); }
}

/*----------------------------------------------------------------------
 * stroke : expand the polyline to a triangle ribbon with mitred joins.
 *
 * bongo1 fires \startWidth 23, so joins are visible and a naive
 * quad-per-segment would show gaps on every corner of the ring.
 *--------------------------------------------------------------------*/
static float verts[AV_MAX_VERTS * 3];

static vec2 norm_of(vec2 a, vec2 b) {
    float dx = b.x - a.x, dy = b.y - a.y;
    float l = sqrtf(dx * dx + dy * dy);
    vec2 n;
    if (l < 1e-6f) { n.x = 0.f; n.y = 0.f; return n; }
    n.x = -dy / l; n.y = dx / l;
    return n;
}

void av_gfx_stroke(const vec2 *pts, int n, int closed,
                   float half_w, const float rgba[4]) {
    int i, nv = 0;
    float fade = 1.f;
    float hw = half_w;

    if (n < 2 || rgba[3] <= 0.f) return;
    if (n > AV_MAX_POINTS) n = AV_MAX_POINTS;

    /* below half a pixel, stop thinning and start fading - otherwise thin
     * strokes alias in and out as the width envelope crosses the grid */
    if (hw < 0.5f) { fade = hw / 0.5f; hw = 0.5f; }

    for (i = 0; i < n; i++) {
        vec2 na, nb, m;
        float len, k;
        int prev = (i == 0) ? (closed ? n - 1 : 0) : i - 1;
        int next = (i == n - 1) ? (closed ? 0 : n - 1) : i + 1;

        na = (i == 0 && !closed) ? norm_of(pts[i], pts[next])
                                 : norm_of(pts[prev], pts[i]);
        nb = (i == n - 1 && !closed) ? norm_of(pts[prev], pts[i])
                                     : norm_of(pts[i], pts[next]);
        m.x = na.x + nb.x; m.y = na.y + nb.y;
        len = sqrtf(m.x * m.x + m.y * m.y);
        if (len < 1e-6f) { m = na; k = 1.f; }
        else {
            m.x /= len; m.y /= len;
            k = m.x * na.x + m.y * na.y;         /* cos(half the turn) */
            k = (k < 0.25f) ? 4.f : 1.f / k;     /* miter limit */
        }
        verts[nv*3+0] = pts[i].x + m.x * hw * k;
        verts[nv*3+1] = pts[i].y + m.y * hw * k;
        verts[nv*3+2] =  1.f; nv++;
        verts[nv*3+0] = pts[i].x - m.x * hw * k;
        verts[nv*3+1] = pts[i].y - m.y * hw * k;
        verts[nv*3+2] = -1.f; nv++;
    }
    if (closed && nv + 2 <= AV_MAX_VERTS) {
        memcpy(&verts[nv*3], &verts[0], 3 * sizeof(float)); nv++;
        memcpy(&verts[nv*3], &verts[3], 3 * sizeof(float)); nv++;
    }

    glBufferSubData(GL_ARRAY_BUFFER, 0, nv * 3 * sizeof(float), verts);
    glUniform4f(u_color, rgba[0], rgba[1], rgba[2], rgba[3] * fade);
    glUniform1f(u_halfw, hw);
    glUniform1f(u_solid, 0.f);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, nv);
}

/*----------------------------------------------------------------------
 * fill : triangle fan from the centroid.
 *
 * LIMITATION, and it is the same one Qt does not have: this is correct
 * only for polygons that are star-shaped about their centroid. Every form
 * in vdefLib is (\star and \cross included), but a self-intersecting
 * points func will fill wrong rather than erroring. Stroke is unaffected.
 *--------------------------------------------------------------------*/
void av_gfx_fill(const vec2 *pts, int n, const float rgba[4]) {
    int i, nv = 0;
    vec2 c = { 0.f, 0.f };

    if (n < 3 || rgba[3] <= 0.f) return;
    if (n > AV_MAX_POINTS) n = AV_MAX_POINTS;
    for (i = 0; i < n; i++) { c.x += pts[i].x; c.y += pts[i].y; }
    c.x /= (float)n; c.y /= (float)n;

    verts[nv*3+0] = c.x; verts[nv*3+1] = c.y; verts[nv*3+2] = 0.f; nv++;
    for (i = 0; i < n; i++) {
        verts[nv*3+0] = pts[i].x; verts[nv*3+1] = pts[i].y; verts[nv*3+2] = 0.f; nv++;
    }
    verts[nv*3+0] = pts[0].x; verts[nv*3+1] = pts[0].y; verts[nv*3+2] = 0.f; nv++;

    glBufferSubData(GL_ARRAY_BUFFER, 0, nv * 3 * sizeof(float), verts);
    glUniform4f(u_color, rgba[0], rgba[1], rgba[2], rgba[3]);
    glUniform1f(u_halfw, 1.f);
    glUniform1f(u_solid, 1.f);
    glDrawArrays(GL_TRIANGLE_FAN, 0, nv);
}
