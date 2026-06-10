// Minimal stub for OrcaSlicer mobile build
#ifndef slic3r_Utils_ColorSpaceConvert_hpp_
#define slic3r_Utils_ColorSpaceConvert_hpp_

#include <cmath>
#include <algorithm>

namespace Slic3r { namespace Utils {

// RGB to HSV conversion
inline void RGB2HSV(float r, float g, float b, float *h, float *s, float *v) {
    float max = std::max({r, g, b});
    float min = std::min({r, g, b});
    *v = max;
    float delta = max - min;
    if (max != 0.0f)
        *s = delta / max;
    else
        *s = 0.0f;

    if (delta == 0.0f)
        *h = 0.0f;
    else if (r == max)
        *h = (g - b) / delta;
    else if (g == max)
        *h = 2.0f + (b - r) / delta;
    else
        *h = 4.0f + (r - g) / delta;

    *h *= 60.0f;
    if (*h < 0.0f) *h += 360.0f;
    *h /= 360.0f;  // normalize to [0,1]
}

}} // namespace Slic3r::Utils

#endif // slic3r_Utils_ColorSpaceConvert_hpp_