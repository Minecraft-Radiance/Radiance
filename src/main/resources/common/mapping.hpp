#ifdef __cplusplus
#    pragma once
#    include <cstddef>
#    include <glm/glm.hpp>
#    define T_VEC2 glm::vec2
#    define T_IVEC2 glm::ivec2
#    define T_VEC3 glm::vec3
#    define T_VEC4 glm::vec4
#    define T_IVEC4 glm::ivec4
#    define T_MAT4 glm::mat4
#    define T_FLOAT float
#    define T_UINT uint32_t
#    define T_INT int32_t
#    define T_BOOL bool
#    define T_DVEC4 glm::dvec4
#else
#    extension GL_EXT_shader_explicit_arithmetic_types_float64 : require
#    define T_VEC2 vec2
#    define T_IVEC2 ivec2
#    define T_VEC3 vec3
#    define T_VEC4 vec4
#    define T_IVEC4 ivec4
#    define T_MAT4 mat4
#    define T_FLOAT float
#    define T_UINT uint
#    define T_INT int
#    define T_BOOL bool
#    define T_DVEC4 dvec4
#    define WORLD_MASK 1
#    define PLAYER_MASK 2
#    define FISHING_BOBBER_MASK 4
#    define HAND_MASK 8
#    define WEATHER_MASK 16
#    define PARTICLE_MASK 32
#    define CLOUD_MASK 64
#    define BOAT_WATER_MASK 128
#endif