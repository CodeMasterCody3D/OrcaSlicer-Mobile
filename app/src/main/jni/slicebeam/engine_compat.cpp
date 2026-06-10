// Engine compatibility shims for the OrcaSlicer engine swap.
//
// This translation unit provides:
//   1. The single nanosvg implementation (the rest of the engine only includes the header).
//   2. Stub definitions for engine features whose third-party backends are not vendored in
//      this Android build, so the libslic3r library links. The corresponding source files
//      (Format/DRC.cpp, MeshBoolean.cpp) are intentionally excluded from the build because
//      their dependencies (draco, mcut) are not available here.

// --- nanosvg single-TU implementation -------------------------------------------------
#define NANOSVG_IMPLEMENTATION
#include "nanosvg/nanosvg.h"

// --- stubs for unvendored backends ----------------------------------------------------
#include <string>
#include <vector>

#include <boost/log/trivial.hpp>

#include "libslic3r/TriangleMesh.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/MeshBoolean.hpp"
#include "libslic3r/Format/DRC.hpp"

namespace Slic3r {

// Draco-compressed mesh loading (Format/DRC.cpp). Draco is not vendored.
bool load_drc(const char * /*path*/, TriangleMesh * /*meshptr*/) {
    BOOST_LOG_TRIVIAL(warning) << "load_drc: Draco support is not available in this build";
    return false;
}

bool load_drc(const char * /*path*/, Model * /*model*/, const char * /*object_name*/) {
    BOOST_LOG_TRIVIAL(warning) << "load_drc: Draco support is not available in this build";
    return false;
}



} // namespace Slic3r
