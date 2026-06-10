// Minimal stub for libnoise — OrcaSlicer Mobile Android build
// Provides just enough API surface for FuzzySkin.cpp to compile
#ifndef LIBNOISE_NOISE_H_
#define LIBNOISE_NOISE_H_

#include <cmath>
#include <memory>

namespace noise {
namespace module {

class Module {
public:
    Module() {}
    Module(int src_count) : m_source_count(src_count) {}
    virtual ~Module() {}

    virtual int GetSourceModuleCount() const { return m_source_count; }
    virtual double GetValue(double x, double y, double z) const = 0;

    int m_source_count = 0;
};

class Perlin : public Module {
public:
    Perlin() : Module(0) {}
    virtual double GetValue(double x, double y, double z) const override {
        // Simple approximation: standard perlin-like pseudo-random
        double n = std::sin(x * 12.9898 + y * 78.233 + z * 45.164) * 43758.5453;
        return n - std::floor(n) * 2.0 - 1.0;  // [-1, 1]
    }
    void SetFrequency(double f) { m_frequency = f; }
    void SetOctaveCount(int c) { m_octave_count = c; }
    void SetPersistence(double p) { m_persistence = p; }
    double m_frequency = 1.0;
    int    m_octave_count = 4;
    double m_persistence = 0.5;
};

class Billow : public Module {
public:
    Billow() : Module(0) {}
    virtual double GetValue(double x, double y, double z) const override {
        double n = std::sin(x * 9.371 + y * 61.789 + z * 33.427) * 29183.291;
        return std::abs(n - std::floor(n)) * 2.0 - 1.0;
    }
    void SetFrequency(double f) { m_frequency = f; }
    void SetOctaveCount(int c) { m_octave_count = c; }
    void SetPersistence(double p) { m_persistence = p; }
    double m_frequency = 1.0;
    int    m_octave_count = 4;
    double m_persistence = 0.5;
};

class RidgedMulti : public Module {
public:
    RidgedMulti() : Module(0) {}
    virtual double GetValue(double x, double y, double z) const override {
        double n = std::sin(x * 7.191 + y * 53.447 + z * 29.371) * 37291.229;
        return 1.0 - std::abs(n - std::floor(n)) * 2.0;
    }
    void SetFrequency(double f) { m_frequency = f; }
    void SetOctaveCount(int c) { m_octave_count = c; }
    double m_frequency = 1.0;
    int    m_octave_count = 4;
};

class Voronoi : public Module {
public:
    Voronoi() : Module(0) {}
    virtual double GetValue(double x, double y, double z) const override {
        // Cell-based noise approximation
        double n = std::sin(x * 5.817 + y * 41.233 + z * 19.997) * 46371.337;
        return n - std::floor(n);
    }
    void SetFrequency(double f) { m_frequency = f; }
    void SetDisplacement(double d) { m_displacement = d; }
    double m_frequency = 1.0;
    double m_displacement = 1.0;
};

} // namespace module
} // namespace noise

#endif // LIBNOISE_NOISE_H_