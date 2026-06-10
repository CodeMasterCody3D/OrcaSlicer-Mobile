#include "Viewer.hpp"
#include "PathVertex.hpp"
#include "Types.hpp"
#include "GCodeInputData.hpp"
#include "GCode/GCodeProcessor.hpp"

#ifndef SLICE_BEAM_LIBVGCODE_UTILS_HPP
#define SLICE_BEAM_LIBVGCODE_UTILS_HPP

libvgcode::Vec3 libvgcode_convert_vec3f(const Slic3r::Vec3f& v) {
    return { v.x(), v.y(), v.z() };
}

// The OrcaSlicer engine uses Slic3r::ExtrusionRole (er* enum from ExtrusionEntity.hpp) instead of
// PrusaSlicer's GCodeExtrusionRole. Roles with no libvgcode equivalent are mapped to the closest match.
libvgcode::EGCodeExtrusionRole libvgcode_convert_extrusion_role(Slic3r::ExtrusionRole role) {
    switch (role) {
        case Slic3r::erNone:                      { return libvgcode::EGCodeExtrusionRole::None; }
        case Slic3r::erPerimeter:                 { return libvgcode::EGCodeExtrusionRole::Perimeter; }
        case Slic3r::erExternalPerimeter:         { return libvgcode::EGCodeExtrusionRole::ExternalPerimeter; }
        case Slic3r::erOverhangPerimeter:         { return libvgcode::EGCodeExtrusionRole::OverhangPerimeter; }
        case Slic3r::erInternalInfill:            { return libvgcode::EGCodeExtrusionRole::InternalInfill; }
        case Slic3r::erSolidInfill:               { return libvgcode::EGCodeExtrusionRole::SolidInfill; }
        case Slic3r::erTopSolidInfill:            { return libvgcode::EGCodeExtrusionRole::TopSolidInfill; }
        case Slic3r::erBottomSurface:             { return libvgcode::EGCodeExtrusionRole::SolidInfill; }
        case Slic3r::erIroning:                   { return libvgcode::EGCodeExtrusionRole::Ironing; }
        case Slic3r::erBridgeInfill:              { return libvgcode::EGCodeExtrusionRole::BridgeInfill; }
        case Slic3r::erInternalBridgeInfill:      { return libvgcode::EGCodeExtrusionRole::BridgeInfill; }
        case Slic3r::erGapFill:                   { return libvgcode::EGCodeExtrusionRole::GapFill; }
        case Slic3r::erSkirt:                     { return libvgcode::EGCodeExtrusionRole::Skirt; }
        case Slic3r::erBrim:                      { return libvgcode::EGCodeExtrusionRole::Skirt; }
        case Slic3r::erSupportMaterial:           { return libvgcode::EGCodeExtrusionRole::SupportMaterial; }
        case Slic3r::erSupportMaterialInterface:  { return libvgcode::EGCodeExtrusionRole::SupportMaterialInterface; }
        case Slic3r::erSupportTransition:         { return libvgcode::EGCodeExtrusionRole::SupportMaterialInterface; }
        case Slic3r::erWipeTower:                 { return libvgcode::EGCodeExtrusionRole::WipeTower; }
        case Slic3r::erCustom:                    { return libvgcode::EGCodeExtrusionRole::Custom; }
        default:                                  { return libvgcode::EGCodeExtrusionRole::None; }
    }
}

libvgcode::EMoveType libvgcode_convert_move_type(Slic3r::EMoveType type) {
    switch (type) {
        case Slic3r::EMoveType::Noop:         { return libvgcode::EMoveType::Noop; }
        case Slic3r::EMoveType::Retract:      { return libvgcode::EMoveType::Retract; }
        case Slic3r::EMoveType::Unretract:    { return libvgcode::EMoveType::Unretract; }
        case Slic3r::EMoveType::Seam:         { return libvgcode::EMoveType::Seam; }
        case Slic3r::EMoveType::Tool_change:  { return libvgcode::EMoveType::ToolChange; }
        case Slic3r::EMoveType::Color_change: { return libvgcode::EMoveType::ColorChange; }
        case Slic3r::EMoveType::Pause_Print:  { return libvgcode::EMoveType::PausePrint; }
        case Slic3r::EMoveType::Custom_GCode: { return libvgcode::EMoveType::CustomGCode; }
        case Slic3r::EMoveType::Travel:       { return libvgcode::EMoveType::Travel; }
        case Slic3r::EMoveType::Wipe:         { return libvgcode::EMoveType::Wipe; }
        case Slic3r::EMoveType::Extrude:      { return libvgcode::EMoveType::Extrude; }
        default:                              { return libvgcode::EMoveType::COUNT; }
    }
}

libvgcode::Color libvgcode_convert_color_rgba(const Slic3r::ColorRGBA& c) {
    return { static_cast<uint8_t>(c.r() * 255.0f), static_cast<uint8_t>(c.g() * 255.0f), static_cast<uint8_t>(c.b() * 255.0f) };
}

libvgcode::Color libvgcode_convert_color(const std::string& color_str) {
    Slic3r::ColorRGBA color_rgba;
    return decode_color(color_str, color_rgba) ? libvgcode_convert_color_rgba(color_rgba) : libvgcode::DUMMY_COLOR;
}

// TODO: Convert colors: get_extruder_color_strings_from_plater_config, get_color_strings_for_color_print
libvgcode::GCodeInputData libvgcode_convert_input_data(const Slic3r::GCodeProcessorResult& result, const std::vector<std::string>& str_tool_colors,
                                  const std::vector<std::string>& str_color_print_colors, const libvgcode::Viewer& viewer) {
    libvgcode::GCodeInputData ret;

    // collect tool colors
    ret.tools_colors.reserve(str_tool_colors.size());
    for (const std::string& color : str_tool_colors) {
        ret.tools_colors.emplace_back(libvgcode_convert_color(color));
    }

    // collect color print colors
    const std::vector<std::string>& str_colors = str_color_print_colors.empty() ? str_tool_colors : str_color_print_colors;
    ret.color_print_colors.reserve(str_colors.size());
    for (const std::string& color : str_colors) {
        ret.color_print_colors.emplace_back(libvgcode_convert_color(color));
    }

    const std::vector<Slic3r::GCodeProcessorResult::MoveVertex>& moves = result.moves;
    ret.vertices.reserve(2 * moves.size());
    for (size_t i = 1; i < moves.size(); ++i) {
        const Slic3r::GCodeProcessorResult::MoveVertex& curr = moves[i];
        const Slic3r::GCodeProcessorResult::MoveVertex& prev = moves[i - 1];
        const libvgcode::EMoveType curr_type = libvgcode_convert_move_type(curr.type);
        const libvgcode::EOptionType option_type = move_type_to_option(curr_type);
        if (option_type == libvgcode::EOptionType::COUNT || option_type == libvgcode::EOptionType::Travels || option_type == libvgcode::EOptionType::Wipes) {
            if (ret.vertices.empty() || prev.type != curr.type || prev.extrusion_role != curr.extrusion_role) {
                // to allow libvgcode to properly detect the start/end of a path we need to add a 'phantom' vertex
                // equal to the current one with the exception of the position, which should match the previous move position,
                // and the times, which are set to zero
                const libvgcode::PathVertex vertex = { libvgcode_convert_vec3f(prev.position), curr.height, curr.width, curr.feedrate, prev.actual_feedrate,
                                                       curr.mm3_per_mm, curr.fan_speed, curr.temperature, libvgcode_convert_extrusion_role(curr.extrusion_role), curr_type,
                                                       static_cast<uint32_t>(curr.gcode_id), static_cast<uint32_t>(curr.layer_id),
                                                       static_cast<uint8_t>(curr.extruder_id), static_cast<uint8_t>(curr.cp_color_id),
                                                       curr.object_label_id, { 0.0f, 0.0f } };
                ret.vertices.emplace_back(vertex);
            }
        }

        const libvgcode::PathVertex vertex = { libvgcode_convert_vec3f(curr.position), curr.height, curr.width, curr.feedrate, curr.actual_feedrate,
                                               curr.mm3_per_mm, curr.fan_speed, curr.temperature, libvgcode_convert_extrusion_role(curr.extrusion_role), curr_type,
                                               static_cast<uint32_t>(curr.gcode_id), static_cast<uint32_t>(curr.layer_id),
                                               static_cast<uint8_t>(curr.extruder_id), static_cast<uint8_t>(curr.cp_color_id),
                                               curr.object_label_id, curr.time };
        ret.vertices.emplace_back(vertex);
    }
    ret.vertices.shrink_to_fit();

    ret.spiral_vase_mode = result.spiral_vase_mode;

    return ret;
}

#endif //SLICE_BEAM_LIBVGCODE_UTILS_HPP
