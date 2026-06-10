#ifndef BBL_UTILS_HPP
#define BBL_UTILS_HPP

#include "libslic3r/Point.hpp"

// EnumFaceTypes / FaceProperty are now defined in admesh/stl.h alongside indexed_triangle_set,
// which carries the per-face properties vector. The duplicate definitions were removed from here.


namespace Slic3r {
    // rotation_from_two_vectors() and extract_euler_angles() are now provided by
    // libslic3r/Geometry.{hpp,cpp} in the OrcaSlicer engine; the local shims were removed
    // to avoid ambiguous-overload errors.

    double area_of_boundingbox(BoundingBoxf3 bb) {
        return double(bb.max(0) - bb.min(0)) * (bb.max(1) - bb.min(1));
    }

    stl_vertex get_its_vertex(indexed_triangle_set& its, int facet_idx, int vertex_idx) {
        return its.vertices[its.indices[facet_idx][vertex_idx]];
    }

    float get_its_facet_area(indexed_triangle_set& its, int facet_idx) {
        return std::abs((get_its_vertex(its, facet_idx, 0) - get_its_vertex(its, facet_idx, 1))
                                .cross(get_its_vertex(its, facet_idx, 0) - get_its_vertex(its, facet_idx, 2)).norm()) / 2;
    }

    void rotate_model_instance(ModelInstance* obj, Matrix3d& rotation_matrix) {
        auto m_transformation = obj->get_transformation();
        auto rotation = m_transformation.get_rotation_matrix();
        rotation      = rotation_matrix * rotation;
        obj->set_rotation(Geometry::Transformation(rotation).get_rotation());
    }
}

#endif //BBL_UTILS_HPP
