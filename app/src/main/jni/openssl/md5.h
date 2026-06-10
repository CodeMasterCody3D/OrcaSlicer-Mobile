// Minimal drop-in replacement for OpenSSL's <openssl/md5.h>.
//
// OpenSSL is not vendored in this Android build, but libslic3r/Format/bbs_3mf.cpp only needs
// the classic MD5_CTX / MD5_Init / MD5_Update / MD5_Final API to compute the gcode md5 metadata
// when writing a BBS 3MF. This header provides a small, self-contained, public-domain MD5
// implementation (based on the RFC 1321 reference / Alexander Peslyak's public-domain version)
// exposing exactly that API.

#ifndef SLICEBEAM_OPENSSL_MD5_COMPAT_H
#define SLICEBEAM_OPENSSL_MD5_COMPAT_H

#include <cstdint>
#include <cstring>

#define MD5_DIGEST_LENGTH 16

typedef struct MD5state_st {
    uint32_t lo, hi;
    uint32_t a, b, c, d;
    unsigned char buffer[64];
    uint32_t block[16];
} MD5_CTX;

namespace slicebeam_md5_detail {

#define SLICEBEAM_MD5_F(x, y, z) ((z) ^ ((x) & ((y) ^ (z))))
#define SLICEBEAM_MD5_G(x, y, z) ((y) ^ ((z) & ((x) ^ (y))))
#define SLICEBEAM_MD5_H(x, y, z) (((x) ^ (y)) ^ (z))
#define SLICEBEAM_MD5_H2(x, y, z) ((x) ^ ((y) ^ (z)))
#define SLICEBEAM_MD5_I(x, y, z) ((y) ^ ((x) | ~(z)))

#define SLICEBEAM_MD5_STEP(f, a, b, c, d, x, t, s) \
    (a) += f((b), (c), (d)) + (x) + (t); \
    (a) = (((a) << (s)) | (((a) & 0xffffffff) >> (32 - (s)))); \
    (a) += (b);

inline const void *body(MD5_CTX *ctx, const void *data, unsigned long size) {
    const unsigned char *ptr = (const unsigned char *) data;
    uint32_t a, b, c, d;
    uint32_t saved_a, saved_b, saved_c, saved_d;

    a = ctx->a; b = ctx->b; c = ctx->c; d = ctx->d;

    do {
        saved_a = a; saved_b = b; saved_c = c; saved_d = d;

        auto set = [&](int n) -> uint32_t {
            return ctx->block[n] = (uint32_t) ptr[n * 4] | ((uint32_t) ptr[n * 4 + 1] << 8) |
                                   ((uint32_t) ptr[n * 4 + 2] << 16) | ((uint32_t) ptr[n * 4 + 3] << 24);
        };
        auto get = [&](int n) -> uint32_t { return ctx->block[n]; };

        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, a, b, c, d, set(0), 0xd76aa478, 7)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, d, a, b, c, set(1), 0xe8c7b756, 12)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, c, d, a, b, set(2), 0x242070db, 17)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, b, c, d, a, set(3), 0xc1bdceee, 22)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, a, b, c, d, set(4), 0xf57c0faf, 7)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, d, a, b, c, set(5), 0x4787c62a, 12)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, c, d, a, b, set(6), 0xa8304613, 17)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, b, c, d, a, set(7), 0xfd469501, 22)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, a, b, c, d, set(8), 0x698098d8, 7)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, d, a, b, c, set(9), 0x8b44f7af, 12)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, c, d, a, b, set(10), 0xffff5bb1, 17)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, b, c, d, a, set(11), 0x895cd7be, 22)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, a, b, c, d, set(12), 0x6b901122, 7)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, d, a, b, c, set(13), 0xfd987193, 12)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, c, d, a, b, set(14), 0xa679438e, 17)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_F, b, c, d, a, set(15), 0x49b40821, 22)

        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, a, b, c, d, get(1), 0xf61e2562, 5)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, d, a, b, c, get(6), 0xc040b340, 9)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, c, d, a, b, get(11), 0x265e5a51, 14)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, b, c, d, a, get(0), 0xe9b6c7aa, 20)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, a, b, c, d, get(5), 0xd62f105d, 5)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, d, a, b, c, get(10), 0x02441453, 9)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, c, d, a, b, get(15), 0xd8a1e681, 14)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, b, c, d, a, get(4), 0xe7d3fbc8, 20)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, a, b, c, d, get(9), 0x21e1cde6, 5)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, d, a, b, c, get(14), 0xc33707d6, 9)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, c, d, a, b, get(3), 0xf4d50d87, 14)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, b, c, d, a, get(8), 0x455a14ed, 20)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, a, b, c, d, get(13), 0xa9e3e905, 5)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, d, a, b, c, get(2), 0xfcefa3f8, 9)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, c, d, a, b, get(7), 0x676f02d9, 14)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_G, b, c, d, a, get(12), 0x8d2a4c8a, 20)

        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H, a, b, c, d, get(5), 0xfffa3942, 4)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H2, d, a, b, c, get(8), 0x8771f681, 11)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H, c, d, a, b, get(11), 0x6d9d6122, 16)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H2, b, c, d, a, get(14), 0xfde5380c, 23)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H, a, b, c, d, get(1), 0xa4beea44, 4)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H2, d, a, b, c, get(4), 0x4bdecfa9, 11)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H, c, d, a, b, get(7), 0xf6bb4b60, 16)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H2, b, c, d, a, get(10), 0xbebfbc70, 23)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H, a, b, c, d, get(13), 0x289b7ec6, 4)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H2, d, a, b, c, get(0), 0xeaa127fa, 11)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H, c, d, a, b, get(3), 0xd4ef3085, 16)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H2, b, c, d, a, get(6), 0x04881d05, 23)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H, a, b, c, d, get(9), 0xd9d4d039, 4)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H2, d, a, b, c, get(12), 0xe6db99e5, 11)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H, c, d, a, b, get(15), 0x1fa27cf8, 16)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_H2, b, c, d, a, get(2), 0xc4ac5665, 23)

        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, a, b, c, d, get(0), 0xf4292244, 6)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, d, a, b, c, get(7), 0x432aff97, 10)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, c, d, a, b, get(14), 0xab9423a7, 15)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, b, c, d, a, get(5), 0xfc93a039, 21)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, a, b, c, d, get(12), 0x655b59c3, 6)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, d, a, b, c, get(3), 0x8f0ccc92, 10)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, c, d, a, b, get(10), 0xffeff47d, 15)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, b, c, d, a, get(1), 0x85845dd1, 21)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, a, b, c, d, get(8), 0x6fa87e4f, 6)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, d, a, b, c, get(15), 0xfe2ce6e0, 10)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, c, d, a, b, get(6), 0xa3014314, 15)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, b, c, d, a, get(13), 0x4e0811a1, 21)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, a, b, c, d, get(4), 0xf7537e82, 6)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, d, a, b, c, get(11), 0xbd3af235, 10)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, c, d, a, b, get(2), 0x2ad7d2bb, 15)
        SLICEBEAM_MD5_STEP(SLICEBEAM_MD5_I, b, c, d, a, get(9), 0xeb86d391, 21)

        a += saved_a; b += saved_b; c += saved_c; d += saved_d;
        ptr += 64;
    } while (size -= 64);

    ctx->a = a; ctx->b = b; ctx->c = c; ctx->d = d;
    return ptr;
}

#undef SLICEBEAM_MD5_F
#undef SLICEBEAM_MD5_G
#undef SLICEBEAM_MD5_H
#undef SLICEBEAM_MD5_H2
#undef SLICEBEAM_MD5_I
#undef SLICEBEAM_MD5_STEP

} // namespace slicebeam_md5_detail

inline int MD5_Init(MD5_CTX *ctx) {
    ctx->a = 0x67452301; ctx->b = 0xefcdab89; ctx->c = 0x98badcfe; ctx->d = 0x10325476;
    ctx->lo = 0; ctx->hi = 0;
    return 1;
}

inline int MD5_Update(MD5_CTX *ctx, const void *data, unsigned long size) {
    uint32_t saved_lo = ctx->lo;
    if ((ctx->lo = (saved_lo + size) & 0x1fffffff) < saved_lo)
        ctx->hi++;
    ctx->hi += (uint32_t) (size >> 29);

    uint32_t used = saved_lo & 0x3f;
    if (used) {
        uint32_t available = 64 - used;
        if (size < available) {
            std::memcpy(&ctx->buffer[used], data, size);
            return 1;
        }
        std::memcpy(&ctx->buffer[used], data, available);
        data = (const unsigned char *) data + available;
        size -= available;
        slicebeam_md5_detail::body(ctx, ctx->buffer, 64);
    }

    if (size >= 64) {
        data = slicebeam_md5_detail::body(ctx, data, size & ~(unsigned long) 0x3f);
        size &= 0x3f;
    }

    std::memcpy(ctx->buffer, data, size);
    return 1;
}

inline int MD5_Final(unsigned char *result, MD5_CTX *ctx) {
    uint32_t used = ctx->lo & 0x3f;
    ctx->buffer[used++] = 0x80;
    uint32_t available = 64 - used;

    if (available < 8) {
        std::memset(&ctx->buffer[used], 0, available);
        slicebeam_md5_detail::body(ctx, ctx->buffer, 64);
        used = 0;
        available = 64;
    }

    std::memset(&ctx->buffer[used], 0, available - 8);

    ctx->lo <<= 3;
    ctx->buffer[56] = (unsigned char) (ctx->lo);
    ctx->buffer[57] = (unsigned char) (ctx->lo >> 8);
    ctx->buffer[58] = (unsigned char) (ctx->lo >> 16);
    ctx->buffer[59] = (unsigned char) (ctx->lo >> 24);
    ctx->buffer[60] = (unsigned char) (ctx->hi);
    ctx->buffer[61] = (unsigned char) (ctx->hi >> 8);
    ctx->buffer[62] = (unsigned char) (ctx->hi >> 16);
    ctx->buffer[63] = (unsigned char) (ctx->hi >> 24);

    slicebeam_md5_detail::body(ctx, ctx->buffer, 64);

    result[0] = (unsigned char) (ctx->a);
    result[1] = (unsigned char) (ctx->a >> 8);
    result[2] = (unsigned char) (ctx->a >> 16);
    result[3] = (unsigned char) (ctx->a >> 24);
    result[4] = (unsigned char) (ctx->b);
    result[5] = (unsigned char) (ctx->b >> 8);
    result[6] = (unsigned char) (ctx->b >> 16);
    result[7] = (unsigned char) (ctx->b >> 24);
    result[8] = (unsigned char) (ctx->c);
    result[9] = (unsigned char) (ctx->c >> 8);
    result[10] = (unsigned char) (ctx->c >> 16);
    result[11] = (unsigned char) (ctx->c >> 24);
    result[12] = (unsigned char) (ctx->d);
    result[13] = (unsigned char) (ctx->d >> 8);
    result[14] = (unsigned char) (ctx->d >> 16);
    result[15] = (unsigned char) (ctx->d >> 24);

    std::memset(ctx, 0, sizeof(*ctx));
    return 1;
}

#endif // SLICEBEAM_OPENSSL_MD5_COMPAT_H
