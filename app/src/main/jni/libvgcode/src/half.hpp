#ifndef VGCODE_HALF_HPP
#define VGCODE_HALF_HPP

#include <cstdint>
#include <cstring>

namespace libvgcode {

// Simple float32 to float16 converter
// Based on: https://stackoverflow.com/questions/1659440/32-bit-to-16-bit-floating-point-conversion
inline uint16_t float_to_half(float f) {
    uint32_t i;
    std::memcpy(&i, &f, 4);
    uint32_t s = (i >> 16) & 0x00008000;
    uint32_t e = ((i >> 23) & 0x000000ff) - (127 - 15);
    uint32_t m = i & 0x007fffff;

    if (e <= 0) {
        if (e < -10) {
            return (uint16_t)s;
        }
        m = (m | 0x00800000) >> (1 - e);
        return (uint16_t)(s | (m >> 13));
    } else if (e == 0xff - (127 - 15)) {
        if (m == 0) {
            return (uint16_t)(s | 0x7c00);
        } else {
            m >>= 13;
            return (uint16_t)(s | 0x7c00 | m | (m == 0));
        }
    } else {
        if (e > 30) {
            return (uint16_t)(s | 0x7c00);
        }
        return (uint16_t)(s | (e << 10) | (m >> 13));
    }
}

} // namespace libvgcode

#endif // VGCODE_HALF_HPP
