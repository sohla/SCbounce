/*----------------------------------------------------------------------
 * osc.h - a minimal OSC 1.0 reader. Header-only, no allocation.
 *
 * Enough for what sclang actually sends: messages, and #bundle wrappers
 * (SC uses those whenever a latency is in play). Timetags are IGNORED on
 * purpose - the wire format carries its own `delay` field, so the renderer
 * schedules against its own monotonic clock and never has to agree with
 * sclang about what time it is.
 *
 * Supported tags: i f s b, and T/F/N/I (zero-width, read as 1/0/0/0).
 * d h are skipped correctly so an unexpected one cannot desync the reader.
 *--------------------------------------------------------------------*/
#ifndef AIRVIZ_OSC_H
#define AIRVIZ_OSC_H

#include <string.h>
#include <stdint.h>

typedef struct {
    const char *addr;
    const char *tags;      /* without the leading ',' */
    const char *data;      /* start of the argument block */
    const char *end;
} osc_msg;

typedef struct {
    const char *tags;
    const char *p;
    const char *end;
    int         i;         /* index into tags */
} osc_iter;

static inline uint32_t osc_be32(const char *p) {
    return ((uint32_t)(unsigned char)p[0] << 24) | ((uint32_t)(unsigned char)p[1] << 16)
         | ((uint32_t)(unsigned char)p[2] << 8)  |  (uint32_t)(unsigned char)p[3];
}

static inline size_t osc_pad4(size_t n) { return (n + 3u) & ~(size_t)3u; }

/* Returns 1 and fills m, or 0 if the buffer is not a well-formed message. */
static inline int osc_parse(const char *buf, size_t len, osc_msg *m) {
    size_t a, t;
    if (len < 8) return 0;
    a = strnlen(buf, len);
    if (a == len) return 0;
    m->addr = buf;
    a = osc_pad4(a + 1);
    if (a >= len || buf[a] != ',') return 0;
    t = strnlen(buf + a, len - a);
    if (a + t >= len) return 0;
    m->tags = buf + a + 1;
    m->data = buf + a + osc_pad4(t + 1);
    m->end  = buf + len;
    return m->data <= m->end;
}

static inline void osc_begin(const osc_msg *m, osc_iter *it) {
    it->tags = m->tags; it->p = m->data; it->end = m->end; it->i = 0;
}

/* Advance past one argument without decoding it. Returns 0 at the end or on
 * a tag whose width is unknown, which stops the reader rather than letting
 * it walk off into the next argument. */
static inline int osc_skip(osc_iter *it) {
    char c = it->tags[it->i];
    size_t w;
    if (!c) return 0;
    switch (c) {
        case 'T': case 'F': case 'N': case 'I': w = 0; break;
        case 'i': case 'f': case 'r': case 'c': case 'm': w = 4; break;
        case 'h': case 'd': case 't': w = 8; break;
        case 's': case 'S':
            if (it->p >= it->end) return 0;
            w = osc_pad4(strnlen(it->p, (size_t)(it->end - it->p)) + 1);
            break;
        case 'b':
            if (it->end - it->p < 4) return 0;
            w = 4 + osc_pad4(osc_be32(it->p));
            break;
        default: return 0;
    }
    if ((size_t)(it->end - it->p) < w) return 0;
    it->p += w; it->i++;
    return 1;
}

static inline char osc_tag(const osc_iter *it) { return it->tags[it->i]; }

static inline int osc_int(osc_iter *it, int32_t *out) {
    char c = osc_tag(it);
    if (c == 'T') { *out = 1; return osc_skip(it); }
    if (c == 'F' || c == 'N' || c == 'I') { *out = 0; return osc_skip(it); }
    if (c != 'i' && c != 'f') return 0;
    if (it->end - it->p < 4) return 0;
    if (c == 'i') {
        *out = (int32_t)osc_be32(it->p);
    } else {
        uint32_t u = osc_be32(it->p); float f; memcpy(&f, &u, 4); *out = (int32_t)f;
    }
    return osc_skip(it);
}

static inline int osc_float(osc_iter *it, float *out) {
    char c = osc_tag(it);
    uint32_t u;
    if (c == 'T') { *out = 1.f; return osc_skip(it); }
    if (c == 'F' || c == 'N') { *out = 0.f; return osc_skip(it); }
    if (c != 'f' && c != 'i') return 0;
    if (it->end - it->p < 4) return 0;
    u = osc_be32(it->p);
    if (c == 'f') memcpy(out, &u, 4); else *out = (float)(int32_t)u;
    return osc_skip(it);
}

/* Borrows the string in place - valid until the next recv into the buffer. */
static inline int osc_str(osc_iter *it, const char **out) {
    if (osc_tag(it) != 's' && osc_tag(it) != 'S') return 0;
    *out = it->p;
    return osc_skip(it);
}

static inline int osc_blob(osc_iter *it, const char **out, uint32_t *n) {
    if (osc_tag(it) != 'b') return 0;
    if (it->end - it->p < 4) return 0;
    *n = osc_be32(it->p);
    *out = it->p + 4;
    return osc_skip(it);
}

static inline float osc_blob_f32(const char *b, int i) {
    uint32_t u = osc_be32(b + i * 4); float f; memcpy(&f, &u, 4); return f;
}

static inline int osc_is_bundle(const char *buf, size_t len) {
    return len >= 16 && memcmp(buf, "#bundle", 8) == 0;
}

#endif
