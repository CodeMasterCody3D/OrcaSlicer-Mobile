include(CheckIncludeFiles)
include(CheckFunctionExists)
include(CheckTypeSize)
include(CheckCCompilerFlag)
include(CheckCXXSymbolExists)
include(CheckCXXCompilerFlag)
include(CheckLibraryExists)
include(GenerateExportHeader)
include(CheckCSourceCompiles)

project(SliceBeam)
cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(IS_CLANG_CL TRUE)
set(CMAKE_INCLUDE_SYSTEM_FLAG_CXX "-imsvc")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wno-old-style-cast -Wno-reserved-id-macro -Wno-c++98-compat-pedantic -funwind-tables -fopenmp -static-openmp -Wl,--no-merge-exidx-entries")
add_compile_options(-fsigned-char)

# Suppress all warnings
add_definitions(-w)
add_definitions(-DNDEBUG)
add_definitions(-DSLIC3R_VERSION=${SLIC3R_VERSION})
add_definitions(-DSLIC3R_BUILD_ID=${SLIC3R_BUILD_ID})
add_definitions(-DSoftFever_VERSION=${SLIC3R_VERSION})

set(jni_imports ${CMAKE_SOURCE_DIR}/src/main/jniImports)
set(jni_libs ${CMAKE_SOURCE_DIR}/src/main/jniLibs)

# Boost
set(BOOST_VERSION "1_85")
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(BOOST_ARCH "a64")
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(BOOST_ARCH "a32")
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set(BOOST_ARCH "x64")
elseif (${ANDROID_ABI} STREQUAL "x86")
    set(BOOST_ARCH "x32")
endif()

set(BOOST_LIBS atomic charconv chrono container context contract coroutine date_time exception fiber
        filesystem graph iostreams json log log_setup math_c99 math_c99f math_c99l math_tr1 math_tr1f
        math_tr1l nowide prg_exec_monitor program_options random regex serialization stacktrace_basic
        stacktrace_noop system test_exec_moinotr thread timer type_erasure unit_test_framework url wave
        wserialization)

foreach (NAME IN LISTS BOOST_LIBS)
    add_library(boost_${NAME} STATIC IMPORTED)
    set_target_properties(boost_${NAME} PROPERTIES IMPORTED_LOCATION
            ${jni_imports}/boost/lib/${ANDROID_ABI}/lib/libboost_${NAME}-clang-mt-${BOOST_ARCH}-${BOOST_VERSION}.a)
endforeach()

# OCCT
set(OCCT_JNI_LIBS ${CMAKE_SOURCE_DIR}/src/main/occt/jniLibs)
set(OCCT_LIBS TKDESTEP TKXCAF TKCAF TKLCAF TKCDF TKV3d TKMesh TKXMesh TKBO TKPrim TKHLR TKShHealing TKTopAlgo TKGeomAlgo TKGeomBase TKBRep TKG3d TKG2d TKMath TKernel)

foreach (NAME IN LISTS OCCT_LIBS)
    add_library(occt_${NAME} SHARED IMPORTED)
    set_target_properties(occt_${NAME} PROPERTIES IMPORTED_LOCATION
            ${OCCT_JNI_LIBS}/${ANDROID_ABI}/lib${NAME}.so)
endforeach()

list(TRANSFORM OCCT_LIBS PREPEND "occt_")


add_library(tbb STATIC IMPORTED)
set_target_properties(tbb PROPERTIES IMPORTED_LOCATION
        ${jni_imports}/oneTBB/lib/${ANDROID_ABI}/libtbb.a)

add_library(tbbmalloc STATIC IMPORTED)
set_target_properties(tbbmalloc PROPERTIES IMPORTED_LOCATION
        ${jni_imports}/oneTBB/lib/${ANDROID_ABI}/libtbbmalloc.a)

add_library(gmp SHARED IMPORTED)
set_target_properties(gmp PROPERTIES IMPORTED_LOCATION
        ${jni_libs}/${ANDROID_ABI}/libgmp.so)

add_library(gmpxx SHARED IMPORTED)
set_target_properties(gmpxx PROPERTIES IMPORTED_LOCATION
        ${jni_libs}/${ANDROID_ABI}/libgmpxx.so)

add_library(mpfr SHARED IMPORTED)
set_target_properties(mpfr PROPERTIES IMPORTED_LOCATION
        ${jni_libs}/${ANDROID_ABI}/libmpfr.so)

include_directories(src/main/jni/eigen)
include_directories(src/main/jni/libigl)
include_directories(src/main/occt/include/${ANDROID_ABI})

# Expat config
set(EXPAT_CONTEXT_BYTES 1024)
set(EXPAT_DTD ON)
set(XML_DEV_URANDOM ON)
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/expat/expat_config.h.cmake ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/expat/expat_config.h)

# NanoSVG config
configure_file(src/main/jni/nanosvg/nanosvg.h ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nanosvg/nanosvg.c)
configure_file(src/main/jni/nanosvg/nanosvgrast.h ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nanosvg/nanosvgrast.c)

# NLOpt config
set (NLOPT_MAJOR_VERSION "2")
set (NLOPT_MINOR_VERSION "5")
set (NLOPT_BUGFIX_VERSION "0")
set (NLOPT_VERSION_STRING ${NLOPT_MAJOR_VERSION}.${NLOPT_MINOR_VERSION}.${NLOPT_BUGFIX_VERSION})

check_include_file(dlfcn.h HAVE_DLFCN_H)
check_include_file(getopt.h HAVE_GETOPT_H)
check_include_file(unistd.h HAVE_UNISTD_H)
check_include_file(string.h HAVE_STRING_H)
check_include_file(strings.h HAVE_STRINGS_H)
check_include_file(inttypes.h HAVE_INTTYPES_H)
check_include_file(memory.h HAVE_MEMORY_H)
check_include_file(stdlib.h HAVE_STDLIB_H)
check_include_file(stdint.h HAVE_STDINT_H)
check_include_file(time.h HAVE_TIME_H)
check_include_file(sys/types.h HAVE_SYS_TYPES_H)
check_include_file(sys/stat.h HAVE_SYS_STAT_H)
check_include_file(sys/time.h HAVE_SYS_TIME_H)
if(HAVE_TIME_H AND HAVE_SYS_TIME_H)
    set(TIME_WITH_SYS_TIME TRUE)
endif()
check_function_exists(getpid HAVE_GETPID)
check_function_exists(syscall HAVE_GETTID_SYSCALL)
check_function_exists(isinf HAVE_ISINF)
check_function_exists(isnan HAVE_ISNAN)
check_function_exists(gettimeofday HAVE_GETTIMEOFDAY)
check_function_exists(qsort_r HAVE_QSORT_R)
check_function_exists(time HAVE_TIME)
check_function_exists(copysign HAVE_COPYSIGN)
check_type_size("uint32_t" SIZEOF_UINT32_T)
set(HAVE_UINT32_T ${SIZEOF_UINT32_T})
check_type_size("unsigned int" SIZEOF_UNSIGNED_INT)
check_type_size("unsigned long" SIZEOF_UNSIGNED_LONG)

check_library_exists("m" sqrt "" HAVE_LIBM)
if (HAVE_LIBM)
    set(M_LIBRARY m)
endif()
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/nlopt_config.h.in ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/nlopt_config.h IMMEDIATE)

if (NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.f)
    file (WRITE ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.f "")
    file (STRINGS ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.h NLOPT_H_LINES REGEX "    NLOPT_[A-Z0-9_]+")
    set (i 0)
    foreach (NLOPT_H_LINE ${NLOPT_H_LINES})
        if (NOT NLOPT_H_LINE MATCHES "NLOPT_NUM_")
            string (REGEX REPLACE ".*NLOPT_([A-Z0-9_]+).*" "\\1" ENUM_STRING ${NLOPT_H_LINE})
            string (REGEX REPLACE ".*NLOPT_[A-Z0-9_]+ = (-?[0-9]+).*" "\\1" ENUM_VAL ${NLOPT_H_LINE})
            if (ENUM_VAL MATCHES "^-?[0-9]+$")
                set (i ${ENUM_VAL})
            endif ()
            set (ENUM_LINE "      integer NLOPT_${ENUM_STRING}\n      parameter (NLOPT_${ENUM_STRING}=${i})\n")
            file (APPEND ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.f "${ENUM_LINE}")

            # https://public.kitware.com/Bug/print_bug_page.php?bug_id=8996
            if (i MATCHES "^-")
                math (EXPR i "1 ${i}")
            else ()
                math (EXPR i "${i} + 1")
            endif ()
        endif ()
    endforeach ()
endif ()

if (NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp)
    file (WRITE ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp "")
    file (STRINGS ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt-in.hpp NLOPT_HPP_LINES)
    foreach (NLOPT_HPP_LINE ${NLOPT_HPP_LINES})
        list(LENGTH NLOPT_HPP_LINE line_len)
        # handling trailing backlashes in "file (STRINGS" is a little tricky
        if (line_len VERSION_LESS 8)
            file (APPEND ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp "${NLOPT_HPP_LINE}\n")
        else ()
            set (prev_inst FALSE)
            foreach(NLOPT_HPP_SUBLINE ${NLOPT_HPP_LINE})
                # test is we need to add the eaten semicolon
                if (NLOPT_HPP_SUBLINE MATCHES "\\)$" OR NLOPT_HPP_SUBLINE MATCHES "return")
                    set (new_inst TRUE)
                else ()
                    set (new_inst FALSE)
                endif ()
                if (NOT prev_inst)
                    file (APPEND ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp "${NLOPT_HPP_SUBLINE}")
                    if (new_inst)
                        file (APPEND ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp ";")
                    endif ()
                    list (FIND NLOPT_HPP_LINE "${NLOPT_HPP_SUBLINE}" index)
                    math (EXPR index "${index} + 1")
                    list (LENGTH NLOPT_HPP_LINE total)
                    if (NOT index STREQUAL total)
                        file (APPEND ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp " \\")
                    endif ()
                    file (APPEND ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp "\n")
                endif ()
                set (prev_inst ${new_inst})
            endforeach ()
        endif ()
        if (NLOPT_HPP_LINE MATCHES "GEN_ENUMS_HERE")
            file (APPEND ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp "  enum algorithm {\n")
            file (STRINGS ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.h NLOPT_H_LINES REGEX "    NLOPT_[A-Z0-9_]+")
            foreach (NLOPT_H_LINE ${NLOPT_H_LINES})
                string (REGEX REPLACE "NLOPT_" "" ENUM_LINE ${NLOPT_H_LINE})
                file (APPEND ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp "${ENUM_LINE}\n")
                if (NLOPT_H_LINE MATCHES "NLOPT_NUM_ALGORITHMS")
                    file (APPEND ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp "  };\n  enum result {\n")
                elseif (NLOPT_H_LINE MATCHES "NLOPT_MAXTIME_REACHED")
                    file (APPEND ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/nlopt/api/nlopt.hpp "  };\n")
                endif ()
            endforeach ()
        endif ()
    endforeach ()
endif ()

add_library(zlib
        STATIC
        src/main/jni/zlib/adler32.c
        src/main/jni/zlib/compress.c
        src/main/jni/zlib/crc32.c
        src/main/jni/zlib/deflate.c
        src/main/jni/zlib/gzclose.c
        src/main/jni/zlib/gzlib.c
        src/main/jni/zlib/gzread.c
        src/main/jni/zlib/gzwrite.c
        src/main/jni/zlib/inflate.c
        src/main/jni/zlib/infback.c
        src/main/jni/zlib/inftrees.c
        src/main/jni/zlib/inffast.c
        src/main/jni/zlib/trees.c
        src/main/jni/zlib/uncompr.c
        src/main/jni/zlib/zutil.c
)

add_library(libpng
        STATIC
        src/main/jni/libpng/png.c
        src/main/jni/libpng/pngerror.c
        src/main/jni/libpng/pngget.c
        src/main/jni/libpng/pngmem.c
        src/main/jni/libpng/pngpread.c
        src/main/jni/libpng/pngread.c
        src/main/jni/libpng/pngrio.c
        src/main/jni/libpng/pngrtran.c
        src/main/jni/libpng/pngrutil.c
        src/main/jni/libpng/pngset.c
        src/main/jni/libpng/pngtrans.c
        src/main/jni/libpng/pngwio.c
        src/main/jni/libpng/pngwrite.c
        src/main/jni/libpng/pngwtran.c
        src/main/jni/libpng/pngwutil.c
        src/main/jni/libpng/arm/arm_init.c
        src/main/jni/libpng/arm/filter_neon.S
        src/main/jni/libpng/arm/filter_neon_intrinsics.c
)

add_library(heatshrink
        STATIC
        src/main/jni/heatshrink/heatshrink_encoder.c
        src/main/jni/heatshrink/heatshrink_decoder.c
)

add_library(OCCTWrapper
        STATIC
        src/main/jni/occt_wrapper/OCCTWrapper.cpp)
target_include_directories(OCCTWrapper PRIVATE src/main/occt/include/${ANDROID_ABI})
generate_export_header(OCCTWrapper
        EXPORT_FILE_NAME ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/occt_wrapper/occtwrapper_export.h)

add_library(BGCode_core
        STATIC
        src/main/jni/LibBGCode/core/core.cpp
        src/main/jni/LibBGCode/core/core.hpp
        src/main/jni/LibBGCode/core/core_impl.hpp)
target_include_directories(BGCode_core PRIVATE src/main/jni/LibBGCode)
generate_export_header(BGCode_core
        EXPORT_FILE_NAME ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/LibBGCode/core/export.h)
target_link_libraries(BGCode_core heatshrink zlib)

add_library(BGCode_binarize
        STATIC
        src/main/jni/LibBGCode/binarize/binarize.cpp
        src/main/jni/LibBGCode/binarize/binarize.hpp
        src/main/jni/LibBGCode/binarize/meatpack.cpp
        src/main/jni/LibBGCode/binarize/meatpack.hpp)
target_include_directories(BGCode_binarize PRIVATE
        src/main/jni/LibBGCode
        src/main/jni)
generate_export_header(BGCode_binarize
        EXPORT_FILE_NAME ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/LibBGCode/binarize/export.h)
target_link_libraries(BGCode_binarize BGCode_core)

add_library(BGCode_convert
        STATIC
        src/main/jni/LibBGCode/convert/convert.cpp
        src/main/jni/LibBGCode/convert/convert.hpp)
target_include_directories(BGCode_convert PRIVATE
        src/main/jni/LibBGCode
        src/main/jni
        src/main/jniImports/boost/include)
generate_export_header(BGCode_convert
        EXPORT_FILE_NAME ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/LibBGCode/convert/export.h)
target_link_libraries(BGCode_convert BGCode_core boost_charconv boost_system)

# LibJPEG-Turbo
set(JPEG_LIB_VERSION 80)
set(SO_AGE 3)
set(DEFAULT_SO_MAJOR_VERSION 8)
set(DEFAULT_SO_MINOR_VERSION 2)
set(JPEG_LIB_VERSION_DECIMAL "8.0")

check_type_size("size_t" SIZE_T)
check_type_size("unsigned long" UNSIGNED_LONG)

# TODO: Build simd extensions
#macro(boolean_number var)
#    if(${var})
#        set(${var} 1 ${ARGN})
#    else()
#        set(${var} 0 ${ARGN})
#    endif()
#endmacro()
#
#if (${ANDROID_ABI} STREQUAL "arm64-v8a")
#    set(CPU_TYPE "arm64")
#    set(BITS 64)
#elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
#    set(CPU_TYPE "arm")
#    set(BITS 32)
#elseif (${ANDROID_ABI} STREQUAL "x86_64")
#    set(CPU_TYPE "x86_64")
#    set(BITS 64)
#elseif (${ANDROID_ABI} STREQUAL "x86")
#    set(CPU_TYPE "i386")
#    set(BITS 32)
#endif()
#
#add_subdirectory(src/main/jni/libjpeg-turbo/simd)
#
#if(NEON_INTRINSICS)
#    add_definitions(-DNEON_INTRINSICS)
#endif()

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/libjpeg-turbo/jconfig.h.in ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/libjpeg-turbo/jconfig.h IMMEDIATE)
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/libjpeg-turbo/jconfigint.h.in ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/libjpeg-turbo/jconfigint.h IMMEDIATE)
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/libjpeg-turbo/jversion.h.in ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/libjpeg-turbo/jversion.h IMMEDIATE)

set(JPEG16_SOURCES
        src/main/jni/libjpeg-turbo/jcapistd.c
        src/main/jni/libjpeg-turbo/jccolor.c
        src/main/jni/libjpeg-turbo/jcdiffct.c
        src/main/jni/libjpeg-turbo/jclossls.c
        src/main/jni/libjpeg-turbo/jcmainct.c
        src/main/jni/libjpeg-turbo/jcprepct.c
        src/main/jni/libjpeg-turbo/jcsample.c
        src/main/jni/libjpeg-turbo/jdapistd.c
        src/main/jni/libjpeg-turbo/jdcolor.c
        src/main/jni/libjpeg-turbo/jddiffct.c
        src/main/jni/libjpeg-turbo/jdlossls.c
        src/main/jni/libjpeg-turbo/jdmainct.c
        src/main/jni/libjpeg-turbo/jdpostct.c
        src/main/jni/libjpeg-turbo/jdsample.c
        src/main/jni/libjpeg-turbo/jutils.c)
set(JPEG12_SOURCES
        ${JPEG16_SOURCES}
        src/main/jni/libjpeg-turbo/jccoefct.c
        src/main/jni/libjpeg-turbo/jcdctmgr.c
        src/main/jni/libjpeg-turbo/jdcoefct.c
        src/main/jni/libjpeg-turbo/jddctmgr.c
        src/main/jni/libjpeg-turbo/jdmerge.c
        src/main/jni/libjpeg-turbo/jfdctfst.c
        src/main/jni/libjpeg-turbo/jfdctint.c
        src/main/jni/libjpeg-turbo/jidctflt.c
        src/main/jni/libjpeg-turbo/jidctfst.c
        src/main/jni/libjpeg-turbo/jidctint.c
        src/main/jni/libjpeg-turbo/jidctred.c
        src/main/jni/libjpeg-turbo/jquant1.c
        src/main/jni/libjpeg-turbo/jquant2.c)
set(JPEG_SOURCES
        ${JPEG12_SOURCES}
        src/main/jni/libjpeg-turbo/jcapimin.c
        src/main/jni/libjpeg-turbo/jchuff.c
        src/main/jni/libjpeg-turbo/jcicc.c
        src/main/jni/libjpeg-turbo/jcinit.c
        src/main/jni/libjpeg-turbo/jclhuff.c
        src/main/jni/libjpeg-turbo/jcmarker.c
        src/main/jni/libjpeg-turbo/jcmaster.c
        src/main/jni/libjpeg-turbo/jcomapi.c
        src/main/jni/libjpeg-turbo/jcparam.c
        src/main/jni/libjpeg-turbo/jcphuff.c
        src/main/jni/libjpeg-turbo/jctrans.c
        src/main/jni/libjpeg-turbo/jdapimin.c
        src/main/jni/libjpeg-turbo/jdatadst.c
        src/main/jni/libjpeg-turbo/jdatasrc.c
        src/main/jni/libjpeg-turbo/jdhuff.c
        src/main/jni/libjpeg-turbo/jdicc.c
        src/main/jni/libjpeg-turbo/jdinput.c
        src/main/jni/libjpeg-turbo/jdlhuff.c
        src/main/jni/libjpeg-turbo/jdmarker.c
        src/main/jni/libjpeg-turbo/jdmaster.c
        src/main/jni/libjpeg-turbo/jdphuff.c
        src/main/jni/libjpeg-turbo/jdtrans.c
        src/main/jni/libjpeg-turbo/jerror.c
        src/main/jni/libjpeg-turbo/jfdctflt.c
        src/main/jni/libjpeg-turbo/jmemmgr.c
        src/main/jni/libjpeg-turbo/jmemnobs.c)
add_library(jpeg12-static OBJECT ${JPEG12_SOURCES})
set_property(TARGET jpeg12-static PROPERTY COMPILE_FLAGS
        "-DBITS_IN_JSAMPLE=12")
add_library(jpeg16-static OBJECT ${JPEG16_SOURCES})
set_property(TARGET jpeg16-static PROPERTY COMPILE_FLAGS
        "-DBITS_IN_JSAMPLE=16")
add_library(jpeg-static STATIC ${JPEG_SOURCES} ${SIMD_TARGET_OBJECTS}
        ${SIMD_OBJS} $<TARGET_OBJECTS:jpeg12-static>
        $<TARGET_OBJECTS:jpeg16-static>)

# OpenVDB
set(OpenVDB_MAJOR_VERSION 8)
set(OpenVDB_MINOR_VERSION 2)
set(OpenVDB_PATCH_VERSION 0)
set(OpenVDB_VERSION "${OpenVDB_MAJOR_VERSION}.${OpenVDB_MINOR_VERSION}.${OpenVDB_PATCH_VERSION}")
set(OpenVDB_ABI ${OpenVDB_MAJOR_VERSION})
set(OPENVDB_ABI_VERSION_NUMBER ${OpenVDB_ABI} CACHE STRING [=[
Build for compatibility with version N of the OpenVDB Grid ABI, where N is 6, 7, 8 etc. (some newer features
will be disabled). If OPENVDB_BUILD_CORE is OFF, CMake attempts to query the installed vdb_print binary to
determine the ABI number. You may set this to force a given ABI number.]=] FORCE)
set(OPENVDB_USE_ZLIB 1)
math(EXPR OPENVDB_PACKED_VERSION "${OpenVDB_MAJOR_VERSION} << 24")
math(EXPR OPENVDB_PACKED_VERSION "${OPENVDB_PACKED_VERSION} | ((${OpenVDB_MINOR_VERSION} & 0xFF) << 16)")
math(EXPR OPENVDB_PACKED_VERSION "${OPENVDB_PACKED_VERSION} | (${OpenVDB_PATCH_VERSION} & 0xFFFF)")
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/openvdb/version.h.in ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/openvdb/version.h IMMEDIATE)
add_library(openvdb
        STATIC
        src/main/jni/openvdb/Grid.cc
        src/main/jni/openvdb/io/Archive.cc
        src/main/jni/openvdb/io/Compression.cc
        src/main/jni/openvdb/io/DelayedLoadMetadata.cc
        src/main/jni/openvdb/io/File.cc
        src/main/jni/openvdb/io/GridDescriptor.cc
        src/main/jni/openvdb/io/Queue.cc
        src/main/jni/openvdb/io/Stream.cc
        src/main/jni/openvdb/io/TempFile.cc
        src/main/jni/openvdb/math/Half.cc
        src/main/jni/openvdb/math/Maps.cc
        src/main/jni/openvdb/math/Proximity.cc
        src/main/jni/openvdb/math/QuantizedUnitVec.cc
        src/main/jni/openvdb/math/Transform.cc
        src/main/jni/openvdb/Metadata.cc
        src/main/jni/openvdb/MetaMap.cc
        src/main/jni/openvdb/openvdb.cc
        src/main/jni/openvdb/Platform.cc
        src/main/jni/openvdb/points/AttributeArray.cc
        src/main/jni/openvdb/points/AttributeArrayString.cc
        src/main/jni/openvdb/points/AttributeGroup.cc
        src/main/jni/openvdb/points/AttributeSet.cc
        src/main/jni/openvdb/points/StreamCompression.cc
        src/main/jni/openvdb/points/points.cc
        src/main/jni/openvdb/util/Formats.cc
        src/main/jni/openvdb/util/Util.cc)
target_include_directories(openvdb PRIVATE src/main/jni src/main/jniImports/boost/include src/main/jniImports/oneTBB/include)
set(MCUT_BUILD_AS_SHARED_LIB OFF)
set(MCUT_BUILD_TESTS OFF)
set(MCUT_BUILD_TUTORIALS OFF)
add_subdirectory(src/main/jni/mcut)

add_library(slic3r
        SHARED
        src/main/jni/semver/semver.c

        src/main/jni/admesh/connect.cpp
        src/main/jni/admesh/normals.cpp
        src/main/jni/admesh/shared.cpp
        src/main/jni/admesh/stl_io.cpp
        src/main/jni/admesh/stlinit.cpp
        src/main/jni/admesh/util.cpp

        src/main/jni/clipper/clipper_z.cpp
        src/main/jni/clipper/clipper_z.hpp

        src/main/jni/expat/xmlparse.c
        src/main/jni/expat/xmlrole.c
        src/main/jni/expat/xmltok.c

        src/main/jni/nanosvg/nanosvg.c

        src/main/jni/miniz/miniz.c

        src/main/jni/imgui/imconfig.h
        src/main/jni/imgui/imgui.cpp
        src/main/jni/imgui/imgui.h
        src/main/jni/imgui/imgui_demo.cpp
        src/main/jni/imgui/imgui_draw.cpp
        src/main/jni/imgui/imgui_internal.h
        src/main/jni/imgui/imgui_stdlib.cpp
        src/main/jni/imgui/imgui_stdlib.h
        src/main/jni/imgui/imgui_tables.cpp
        src/main/jni/imgui/imgui_widgets.cpp
        src/main/jni/imgui/imstb_rectpack.h
        src/main/jni/imgui/imstb_textedit.h
        src/main/jni/imgui/imstb_truetype.h

        src/main/jni/qoi/qoi.h
        src/main/jni/qoi/qoilib.c

        src/main/jni/qhull/libqhullcpp/Coordinates.cpp
        src/main/jni/qhull/libqhullcpp/PointCoordinates.cpp
        src/main/jni/qhull/libqhullcpp/Qhull.cpp
        src/main/jni/qhull/libqhullcpp/QhullFacet.cpp
        src/main/jni/qhull/libqhullcpp/QhullFacetList.cpp
        src/main/jni/qhull/libqhullcpp/QhullFacetSet.cpp
        src/main/jni/qhull/libqhullcpp/QhullHyperplane.cpp
        src/main/jni/qhull/libqhullcpp/QhullPoint.cpp
        src/main/jni/qhull/libqhullcpp/QhullPointSet.cpp
        src/main/jni/qhull/libqhullcpp/QhullPoints.cpp
        src/main/jni/qhull/libqhullcpp/QhullQh.cpp
        src/main/jni/qhull/libqhullcpp/QhullRidge.cpp
        src/main/jni/qhull/libqhullcpp/QhullSet.cpp
        src/main/jni/qhull/libqhullcpp/QhullStat.cpp
        src/main/jni/qhull/libqhullcpp/QhullUser.cpp
        src/main/jni/qhull/libqhullcpp/QhullVertex.cpp
        src/main/jni/qhull/libqhullcpp/QhullVertexSet.cpp
        src/main/jni/qhull/libqhullcpp/RboxPoints.cpp
        src/main/jni/qhull/libqhullcpp/RoadError.cpp
        src/main/jni/qhull/libqhullcpp/RoadLogEvent.cpp
        src/main/jni/qhull/libqhullcpp/Coordinates.h
        src/main/jni/qhull/libqhullcpp/functionObjects.h
        src/main/jni/qhull/libqhullcpp/PointCoordinates.h
        src/main/jni/qhull/libqhullcpp/Qhull.h
        src/main/jni/qhull/libqhullcpp/QhullError.h
        src/main/jni/qhull/libqhullcpp/QhullFacet.h
        src/main/jni/qhull/libqhullcpp/QhullFacetList.h
        src/main/jni/qhull/libqhullcpp/QhullFacetSet.h
        src/main/jni/qhull/libqhullcpp/QhullHyperplane.h
        src/main/jni/qhull/libqhullcpp/QhullIterator.h
        src/main/jni/qhull/libqhullcpp/QhullLinkedList.h
        src/main/jni/qhull/libqhullcpp/QhullPoint.h
        src/main/jni/qhull/libqhullcpp/QhullPoints.h
        src/main/jni/qhull/libqhullcpp/QhullPointSet.h
        src/main/jni/qhull/libqhullcpp/QhullQh.h
        src/main/jni/qhull/libqhullcpp/QhullRidge.h
        src/main/jni/qhull/libqhullcpp/QhullSet.h
        src/main/jni/qhull/libqhullcpp/QhullSets.h
        src/main/jni/qhull/libqhullcpp/QhullStat.h
        src/main/jni/qhull/libqhullcpp/QhullUser.h
        src/main/jni/qhull/libqhullcpp/QhullVertex.h
        src/main/jni/qhull/libqhullcpp/QhullVertexSet.h
        src/main/jni/qhull/libqhullcpp/RboxPoints.h
        src/main/jni/qhull/libqhullcpp/RoadError.h
        src/main/jni/qhull/libqhullcpp/RoadLogEvent.h

        src/main/jni/qhull/libqhull_r/global_r.c
        src/main/jni/qhull/libqhull_r/stat_r.c
        src/main/jni/qhull/libqhull_r/geom2_r.c
        src/main/jni/qhull/libqhull_r/poly2_r.c
        src/main/jni/qhull/libqhull_r/merge_r.c
        src/main/jni/qhull/libqhull_r/libqhull_r.c
        src/main/jni/qhull/libqhull_r/geom_r.c
        src/main/jni/qhull/libqhull_r/poly_r.c
        src/main/jni/qhull/libqhull_r/qset_r.c
        src/main/jni/qhull/libqhull_r/mem_r.c
        src/main/jni/qhull/libqhull_r/random_r.c
        src/main/jni/qhull/libqhull_r/usermem_r.c
#        src/main/jni/qhull/libqhull_r/userprintf_r.c
        src/main/jni/qhull/libqhull_r/io_r.c
        src/main/jni/qhull/libqhull_r/user_r.c
        src/main/jni/qhull/libqhull_r/accessors_r.c
        src/main/jni/qhull/libqhull_r/rboxlib_r.c
#        src/main/jni/qhull/libqhull_r/userprintf_rbox_r.c
        src/main/jni/qhull/libqhull_r/libqhull_r.h
        src/main/jni/qhull/libqhull_r/geom_r.h
        src/main/jni/qhull/libqhull_r/io_r.h
        src/main/jni/qhull/libqhull_r/mem_r.h
        src/main/jni/qhull/libqhull_r/merge_r.h
        src/main/jni/qhull/libqhull_r/poly_r.h
        src/main/jni/qhull/libqhull_r/qhull_ra.h
        src/main/jni/qhull/libqhull_r/qset_r.h
        src/main/jni/qhull/libqhull_r/random_r.h
        src/main/jni/qhull/libqhull_r/stat_r.h
        src/main/jni/qhull/libqhull_r/user_r.h

        src/main/jni/nlopt/algs/direct/DIRect.c
        src/main/jni/nlopt/algs/direct/direct_wrap.c
        src/main/jni/nlopt/algs/direct/DIRserial.c
        src/main/jni/nlopt/algs/direct/DIRsubrout.c
        src/main/jni/nlopt/algs/direct/direct-internal.h
        src/main/jni/nlopt/algs/direct/direct.h
        src/main/jni/nlopt/algs/cdirect/cdirect.c
        src/main/jni/nlopt/algs/cdirect/hybrid.c
        src/main/jni/nlopt/algs/cdirect/cdirect.h
        src/main/jni/nlopt/algs/praxis/praxis.c
        src/main/jni/nlopt/algs/praxis/praxis.h
        src/main/jni/nlopt/algs/luksan/plis.c
        src/main/jni/nlopt/algs/luksan/plip.c
        src/main/jni/nlopt/algs/luksan/pnet.c
        src/main/jni/nlopt/algs/luksan/mssubs.c
        src/main/jni/nlopt/algs/luksan/pssubs.c
        src/main/jni/nlopt/algs/luksan/luksan.h
        src/main/jni/nlopt/algs/crs/crs.c
        src/main/jni/nlopt/algs/crs/crs.h
        src/main/jni/nlopt/algs/mlsl/mlsl.c
        src/main/jni/nlopt/algs/mlsl/mlsl.h
        src/main/jni/nlopt/algs/mma/mma.c
        src/main/jni/nlopt/algs/mma/mma.h
        src/main/jni/nlopt/algs/mma/ccsa_quadratic.c
        src/main/jni/nlopt/algs/cobyla/cobyla.c
        src/main/jni/nlopt/algs/cobyla/cobyla.h
        src/main/jni/nlopt/algs/newuoa/newuoa.c
        src/main/jni/nlopt/algs/newuoa/newuoa.h
        src/main/jni/nlopt/algs/neldermead/nldrmd.c
        src/main/jni/nlopt/algs/neldermead/neldermead.h
        src/main/jni/nlopt/algs/neldermead/sbplx.c
        src/main/jni/nlopt/algs/auglag/auglag.c
        src/main/jni/nlopt/algs/auglag/auglag.h
        src/main/jni/nlopt/algs/bobyqa/bobyqa.c
        src/main/jni/nlopt/algs/bobyqa/bobyqa.h
        src/main/jni/nlopt/algs/isres/isres.c
        src/main/jni/nlopt/algs/isres/isres.h
        src/main/jni/nlopt/algs/slsqp/slsqp.c
        src/main/jni/nlopt/algs/slsqp/slsqp.h
        src/main/jni/nlopt/algs/esch/esch.c
        src/main/jni/nlopt/algs/esch/esch.h
        src/main/jni/nlopt/api/general.c
        src/main/jni/nlopt/api/options.c
        src/main/jni/nlopt/api/optimize.c
        src/main/jni/nlopt/api/deprecated.c
        src/main/jni/nlopt/api/nlopt-internal.h
        src/main/jni/nlopt/api/nlopt.h
        src/main/jni/nlopt/api/nlopt.f
        src/main/jni/nlopt/api/f77api.c
        src/main/jni/nlopt/api/f77funcs.h
        src/main/jni/nlopt/api/f77funcs_.h
        src/main/jni/nlopt/api/nlopt-in.hpp
        src/main/jni/nlopt/util/mt19937ar.c
        src/main/jni/nlopt/util/sobolseq.c
        src/main/jni/nlopt/util/soboldata.h
        src/main/jni/nlopt/util/timer.c
        src/main/jni/nlopt/util/stop.c
        src/main/jni/nlopt/util/nlopt-util.h
        src/main/jni/nlopt/util/redblack.c
        src/main/jni/nlopt/util/redblack.h
        src/main/jni/nlopt/util/qsort_r.c
        src/main/jni/nlopt/util/rescale.c
        # NLOpt CXX
        src/main/jni/nlopt/algs/stogo/global.cc
        src/main/jni/nlopt/algs/stogo/linalg.cc
        src/main/jni/nlopt/algs/stogo/local.cc
        src/main/jni/nlopt/algs/stogo/stogo.cc
        src/main/jni/nlopt/algs/stogo/tools.cc
        src/main/jni/nlopt/algs/stogo/global.h
        src/main/jni/nlopt/algs/stogo/linalg.h
        src/main/jni/nlopt/algs/stogo/local.h
        src/main/jni/nlopt/algs/stogo/stogo_config.h
        src/main/jni/nlopt/algs/stogo/stogo.h
        src/main/jni/nlopt/algs/stogo/tools.h

        src/main/jni/glu-libtess/src/dict-list.h
        src/main/jni/glu-libtess/src/dict.c
        src/main/jni/glu-libtess/src/dict.h
        src/main/jni/glu-libtess/src/geom.c
        src/main/jni/glu-libtess/src/geom.h
        src/main/jni/glu-libtess/src/gluos.h
        src/main/jni/glu-libtess/src/memalloc.c
        src/main/jni/glu-libtess/src/memalloc.h
        src/main/jni/glu-libtess/src/mesh.c
        src/main/jni/glu-libtess/src/mesh.h
        src/main/jni/glu-libtess/src/normal.c
        src/main/jni/glu-libtess/src/normal.h
        src/main/jni/glu-libtess/src/priorityq.c
        src/main/jni/glu-libtess/src/priorityq.h
        src/main/jni/glu-libtess/src/priorityq-heap.h
        src/main/jni/glu-libtess/src/priorityq-sort.h
        src/main/jni/glu-libtess/src/render.c
        src/main/jni/glu-libtess/src/render.h
        src/main/jni/glu-libtess/src/sweep.c
        src/main/jni/glu-libtess/src/sweep.h
        src/main/jni/glu-libtess/src/tess.c
        src/main/jni/glu-libtess/src/tess.h
        src/main/jni/glu-libtess/src/tessmono.c
        src/main/jni/glu-libtess/src/tessmono.h
        src/main/jni/glu-libtess/include/glu-libtess.h

        src/main/jni/libvgcode/include/ColorPrint.hpp
        src/main/jni/libvgcode/include/ColorRange.hpp
        src/main/jni/libvgcode/include/GCodeInputData.hpp
        src/main/jni/libvgcode/include/PathVertex.hpp
        src/main/jni/libvgcode/include/Types.hpp
        src/main/jni/libvgcode/include/Viewer.hpp
        src/main/jni/libvgcode/src/Bitset.hpp
        src/main/jni/libvgcode/src/Bitset.cpp
        src/main/jni/libvgcode/src/CogMarker.hpp
        src/main/jni/libvgcode/src/CogMarker.cpp
        src/main/jni/libvgcode/src/ColorPrint.cpp
        src/main/jni/libvgcode/src/ColorRange.cpp
        src/main/jni/libvgcode/src/ExtrusionRoles.hpp
        src/main/jni/libvgcode/src/ExtrusionRoles.cpp
        src/main/jni/libvgcode/src/GCodeInputData.cpp
        src/main/jni/libvgcode/src/Layers.hpp
        src/main/jni/libvgcode/src/Layers.cpp
        src/main/jni/libvgcode/src/OpenGLUtils.hpp
        src/main/jni/libvgcode/src/OpenGLUtils.cpp
        src/main/jni/libvgcode/src/OptionTemplate.hpp
        src/main/jni/libvgcode/src/OptionTemplate.cpp
        src/main/jni/libvgcode/src/PathVertex.cpp
        src/main/jni/libvgcode/src/Range.hpp
        src/main/jni/libvgcode/src/Range.cpp
        src/main/jni/libvgcode/src/SegmentTemplate.hpp
        src/main/jni/libvgcode/src/SegmentTemplate.cpp
        src/main/jni/libvgcode/src/Settings.hpp
        src/main/jni/libvgcode/src/Settings.cpp
        src/main/jni/libvgcode/src/Shaders.hpp
        src/main/jni/libvgcode/src/ShadersES.hpp
        src/main/jni/libvgcode/src/ToolMarker.hpp
        src/main/jni/libvgcode/src/ToolMarker.cpp
        src/main/jni/libvgcode/src/Types.cpp
        src/main/jni/libvgcode/src/Utils.hpp
        src/main/jni/libvgcode/src/Utils.cpp
        src/main/jni/libvgcode/src/Viewer.cpp
        src/main/jni/libvgcode/src/ViewerImpl.hpp
        src/main/jni/libvgcode/src/ViewerImpl.cpp
        src/main/jni/libvgcode/src/ViewRange.hpp
        src/main/jni/libvgcode/src/ViewRange.cpp

        src/main/jni/libslic3r/AABBMesh.cpp
        src/main/jni/libslic3r/AABBMesh.hpp
        src/main/jni/libslic3r/AABBTreeIndirect.hpp
        src/main/jni/libslic3r/AABBTreeLines.hpp
        src/main/jni/libslic3r/Algorithm/LineSplit.cpp
        src/main/jni/libslic3r/Algorithm/LineSplit.hpp
        src/main/jni/libslic3r/Algorithm/RegionExpansion.cpp
        src/main/jni/libslic3r/Algorithm/RegionExpansion.hpp
        src/main/jni/libslic3r/AnyPtr.hpp
        src/main/jni/libslic3r/AppConfig.cpp
        src/main/jni/libslic3r/AppConfig.hpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/BeadingStrategy.cpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/BeadingStrategyFactory.cpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/BeadingStrategyFactory.hpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/BeadingStrategy.hpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/DistributedBeadingStrategy.cpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/DistributedBeadingStrategy.hpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/LimitedBeadingStrategy.cpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/LimitedBeadingStrategy.hpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/OuterWallInsetBeadingStrategy.cpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/OuterWallInsetBeadingStrategy.hpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/RedistributeBeadingStrategy.cpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/RedistributeBeadingStrategy.hpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/WideningBeadingStrategy.cpp
        src/main/jni/libslic3r/Arachne/BeadingStrategy/WideningBeadingStrategy.hpp
        src/main/jni/libslic3r/Arachne/SkeletalTrapezoidation.cpp
        src/main/jni/libslic3r/Arachne/SkeletalTrapezoidationEdge.hpp
        src/main/jni/libslic3r/Arachne/SkeletalTrapezoidationGraph.cpp
        src/main/jni/libslic3r/Arachne/SkeletalTrapezoidationGraph.hpp
        src/main/jni/libslic3r/Arachne/SkeletalTrapezoidation.hpp
        src/main/jni/libslic3r/Arachne/SkeletalTrapezoidationJoint.hpp
        src/main/jni/libslic3r/Arachne/utils/ExtrusionJunction.hpp
        src/main/jni/libslic3r/Arachne/utils/ExtrusionLine.cpp
        src/main/jni/libslic3r/Arachne/utils/ExtrusionLine.hpp
        src/main/jni/libslic3r/Arachne/utils/HalfEdgeGraph.hpp
        src/main/jni/libslic3r/Arachne/utils/HalfEdge.hpp
        src/main/jni/libslic3r/Arachne/utils/HalfEdgeNode.hpp
        src/main/jni/libslic3r/Arachne/utils/PolygonsPointIndex.hpp
        src/main/jni/libslic3r/Arachne/utils/PolygonsSegmentIndex.hpp
        src/main/jni/libslic3r/Arachne/utils/PolylineStitcher.cpp
        src/main/jni/libslic3r/Arachne/utils/PolylineStitcher.hpp
        src/main/jni/libslic3r/Arachne/utils/SparseGrid.hpp
        src/main/jni/libslic3r/Arachne/utils/SparseLineGrid.hpp
        src/main/jni/libslic3r/Arachne/utils/SparsePointGrid.hpp
        src/main/jni/libslic3r/Arachne/utils/SquareGrid.cpp
        src/main/jni/libslic3r/Arachne/utils/SquareGrid.hpp
        src/main/jni/libslic3r/Arachne/WallToolPaths.cpp
        src/main/jni/libslic3r/Arachne/WallToolPaths.hpp
        src/main/jni/libslic3r/ArcFitter.cpp
        src/main/jni/libslic3r/ArcFitter.hpp
        src/main/jni/libslic3r/Arrange.cpp
        src/main/jni/libslic3r/Arrange.hpp
        src/main/jni/libslic3r/BlacklistedLibraryCheck.cpp
        src/main/jni/libslic3r/BlacklistedLibraryCheck.hpp
        src/main/jni/libslic3r/BoundingBox.cpp
        src/main/jni/libslic3r/BoundingBox.hpp
        src/main/jni/libslic3r/BridgeDetector.cpp
        src/main/jni/libslic3r/BridgeDetector.hpp
        src/main/jni/libslic3r/Brim.cpp
        src/main/jni/libslic3r/BrimEarsPoint.hpp
        src/main/jni/libslic3r/Brim.hpp
        src/main/jni/libslic3r/BuildVolume.cpp
        src/main/jni/libslic3r/BuildVolume.hpp
        src/main/jni/libslic3r/calib.cpp
        src/main/jni/libslic3r/calib.hpp
        src/main/jni/libslic3r/Circle.cpp
        src/main/jni/libslic3r/Circle.hpp
        src/main/jni/libslic3r/clipper.cpp
        src/main/jni/libslic3r/clipper.hpp
        src/main/jni/libslic3r/ClipperUtils.cpp
        src/main/jni/libslic3r/ClipperUtils.hpp

        src/main/jni/libslic3r/ClipperZUtils.hpp
        src/main/jni/libslic3r/Color.cpp
        src/main/jni/libslic3r/Color.hpp
        src/main/jni/libslic3r/CommonDefs.hpp
        src/main/jni/libslic3r/Config.cpp
        src/main/jni/libslic3r/Config.hpp
        src/main/jni/libslic3r/ContourZ.cpp
        src/main/jni/libslic3r/CustomGCode.cpp
        src/main/jni/libslic3r/CustomGCode.hpp
        src/main/jni/libslic3r/CutUtils.cpp
        src/main/jni/libslic3r/CutUtils.hpp
        src/main/jni/libslic3r/EdgeGrid.cpp
        src/main/jni/libslic3r/EdgeGrid.hpp
        src/main/jni/libslic3r/ElephantFootCompensation.cpp
        src/main/jni/libslic3r/ElephantFootCompensation.hpp
        src/main/jni/libslic3r/Emboss.cpp
        src/main/jni/libslic3r/Emboss.hpp
        src/main/jni/libslic3r/EmbossShape.hpp
        src/main/jni/libslic3r/enum_bitmask.hpp
        src/main/jni/libslic3r/Execution/Execution.hpp
        src/main/jni/libslic3r/Execution/ExecutionSeq.hpp
        src/main/jni/libslic3r/Execution/ExecutionTBB.hpp
        src/main/jni/libslic3r/ExPolygon.cpp
        src/main/jni/libslic3r/ExPolygon.hpp
        src/main/jni/libslic3r/ExPolygonSerialize.hpp
        src/main/jni/libslic3r/ExPolygonsIndex.cpp
        src/main/jni/libslic3r/ExPolygonsIndex.hpp
        src/main/jni/libslic3r/Extruder.cpp
        src/main/jni/libslic3r/Extruder.hpp
        src/main/jni/libslic3r/ExtrusionEntityCollection.cpp
        src/main/jni/libslic3r/ExtrusionEntityCollection.hpp
        src/main/jni/libslic3r/ExtrusionEntity.cpp
        src/main/jni/libslic3r/ExtrusionEntity.hpp
        src/main/jni/libslic3r/ExtrusionSimulator.cpp
        src/main/jni/libslic3r/ExtrusionSimulator.hpp
        src/main/jni/libslic3r/FaceDetector.cpp
        src/main/jni/libslic3r/FaceDetector.hpp
        src/main/jni/libslic3r/Feature/FuzzySkin/FuzzySkin.cpp
        src/main/jni/libslic3r/Feature/FuzzySkin/FuzzySkin.hpp
        src/main/jni/libslic3r/Feature/Interlocking/InterlockingGenerator.cpp
        src/main/jni/libslic3r/Feature/Interlocking/InterlockingGenerator.hpp
        src/main/jni/libslic3r/Feature/Interlocking/VoxelUtils.cpp
        src/main/jni/libslic3r/Feature/Interlocking/VoxelUtils.hpp
        src/main/jni/libslic3r/FileParserError.hpp
        src/main/jni/libslic3r/Fill/Fill3DHoneycomb.cpp
        src/main/jni/libslic3r/Fill/Fill3DHoneycomb.hpp
        src/main/jni/libslic3r/Fill/FillAdaptive.cpp
        src/main/jni/libslic3r/Fill/FillAdaptive.hpp
        src/main/jni/libslic3r/Fill/FillBase.cpp
        src/main/jni/libslic3r/Fill/FillBase.hpp
        src/main/jni/libslic3r/Fill/FillConcentric.cpp
        src/main/jni/libslic3r/Fill/FillConcentric.hpp
        src/main/jni/libslic3r/Fill/FillConcentricInternal.cpp
        src/main/jni/libslic3r/Fill/FillConcentricInternal.hpp
        src/main/jni/libslic3r/Fill/Fill.cpp
        src/main/jni/libslic3r/Fill/FillCrossHatch.cpp
        src/main/jni/libslic3r/Fill/FillCrossHatch.hpp
        src/main/jni/libslic3r/Fill/FillGyroid.cpp
        src/main/jni/libslic3r/Fill/FillGyroid.hpp
        src/main/jni/libslic3r/Fill/FillHoneycomb.cpp
        src/main/jni/libslic3r/Fill/FillHoneycomb.hpp
        src/main/jni/libslic3r/Fill/Fill.hpp
        src/main/jni/libslic3r/Fill/FillLightning.cpp
        src/main/jni/libslic3r/Fill/FillLightning.hpp
        src/main/jni/libslic3r/Fill/FillLine.cpp
        src/main/jni/libslic3r/Fill/FillLine.hpp
        src/main/jni/libslic3r/Fill/FillPlanePath.cpp
        src/main/jni/libslic3r/Fill/FillPlanePath.hpp
        src/main/jni/libslic3r/Fill/FillRectilinear.cpp
        src/main/jni/libslic3r/Fill/FillRectilinear.hpp
        src/main/jni/libslic3r/Fill/FillTpmsD.cpp
        src/main/jni/libslic3r/Fill/FillTpmsD.hpp
        src/main/jni/libslic3r/Fill/FillTpmsFK.cpp
        src/main/jni/libslic3r/Fill/FillTpmsFK.hpp
        src/main/jni/libslic3r/Fill/Lightning/DistanceField.cpp
        src/main/jni/libslic3r/Fill/Lightning/DistanceField.hpp
        src/main/jni/libslic3r/Fill/Lightning/Generator.cpp
        src/main/jni/libslic3r/Fill/Lightning/Generator.hpp
        src/main/jni/libslic3r/Fill/Lightning/Layer.cpp
        src/main/jni/libslic3r/Fill/Lightning/Layer.hpp
        src/main/jni/libslic3r/Fill/Lightning/TreeNode.cpp
        src/main/jni/libslic3r/Fill/Lightning/TreeNode.hpp
        src/main/jni/libslic3r/Flow.cpp
        src/main/jni/libslic3r/Flow.hpp
        src/main/jni/libslic3r/FlushVolCalc.cpp
        src/main/jni/libslic3r/FlushVolCalc.hpp
        src/main/jni/libslic3r/Format/3mf.cpp
        src/main/jni/libslic3r/Format/3mf.hpp
        src/main/jni/libslic3r/Format/AMF.cpp
        src/main/jni/libslic3r/Format/AMF.hpp
        src/main/jni/libslic3r/Format/bbs_3mf.cpp
        src/main/jni/libslic3r/Format/bbs_3mf.hpp
        src/main/jni/libslic3r/Format/STEP.cpp
        src/main/jni/libslic3r/Format/STEP.hpp
        src/main/jni/libslic3r/format.hpp
        src/main/jni/libslic3r/Format/OBJ.cpp
        src/main/jni/libslic3r/Format/OBJ.hpp
        src/main/jni/libslic3r/Format/objparser.cpp
        src/main/jni/libslic3r/Format/objparser.hpp
        src/main/jni/libslic3r/Format/SL1.cpp
        src/main/jni/libslic3r/Format/SL1.hpp
        src/main/jni/libslic3r/Format/STL.cpp
        src/main/jni/libslic3r/Format/STL.hpp
        src/main/jni/libslic3r/Format/svg.cpp
        src/main/jni/libslic3r/Format/svg.hpp
        src/main/jni/libslic3r/Format/ZipperArchiveImport.cpp
        src/main/jni/libslic3r/Format/ZipperArchiveImport.hpp
        src/main/jni/libslic3r/GCode/AdaptivePAInterpolator.cpp
        src/main/jni/libslic3r/GCode/AdaptivePAInterpolator.hpp
        src/main/jni/libslic3r/GCode/AdaptivePAProcessor.cpp
        src/main/jni/libslic3r/GCode/AdaptivePAProcessor.hpp
        src/main/jni/libslic3r/GCode/AvoidCrossingPerimeters.cpp
        src/main/jni/libslic3r/GCode/AvoidCrossingPerimeters.hpp
        src/main/jni/libslic3r/GCode/ConflictChecker.cpp
        src/main/jni/libslic3r/GCode/ConflictChecker.hpp
        src/main/jni/libslic3r/GCode/CoolingBuffer.cpp
        src/main/jni/libslic3r/GCode/CoolingBuffer.hpp
        src/main/jni/libslic3r/GCode/TimelapsePosPicker.cpp
        src/main/jni/libslic3r/GCode/TimelapsePosPicker.hpp
        src/main/jni/libslic3r/GCode.cpp
        src/main/jni/libslic3r/GCode/ExtrusionProcessor.hpp
        src/main/jni/libslic3r/GCode/FanMover.cpp
        src/main/jni/libslic3r/GCode/FanMover.hpp
        src/main/jni/libslic3r/GCode/GCodeProcessor.cpp
        src/main/jni/libslic3r/GCode/GCodeProcessor.hpp
        src/main/jni/libslic3r/GCode.hpp
        src/main/jni/libslic3r/GCode/PchipInterpolatorHelper.cpp
        src/main/jni/libslic3r/GCode/PchipInterpolatorHelper.hpp
        src/main/jni/libslic3r/GCode/PostProcessor.cpp
        src/main/jni/libslic3r/GCode/PostProcessor.hpp
        src/main/jni/libslic3r/GCode/PressureEqualizer.cpp
        src/main/jni/libslic3r/GCode/PressureEqualizer.hpp
        src/main/jni/libslic3r/GCode/PrintExtents.cpp
        src/main/jni/libslic3r/GCode/PrintExtents.hpp
        src/main/jni/libslic3r/GCodeReader.cpp
        src/main/jni/libslic3r/GCodeReader.hpp
        src/main/jni/libslic3r/GCode/RetractWhenCrossingPerimeters.cpp
        src/main/jni/libslic3r/GCode/RetractWhenCrossingPerimeters.hpp
        src/main/jni/libslic3r/GCode/SeamPlacer.cpp
        src/main/jni/libslic3r/GCode/SeamPlacer.hpp
        src/main/jni/libslic3r/GCode/SmallAreaInfillFlowCompensator.cpp
        src/main/jni/libslic3r/GCode/SmallAreaInfillFlowCompensator.hpp
        src/main/jni/libslic3r/GCode/SpiralVase.cpp
        src/main/jni/libslic3r/GCode/SpiralVase.hpp
        src/main/jni/libslic3r/GCode/ThumbnailData.cpp
        src/main/jni/libslic3r/GCode/ThumbnailData.hpp
        src/main/jni/libslic3r/GCode/Thumbnails.cpp
        src/main/jni/libslic3r/GCode/Thumbnails.hpp
        src/main/jni/libslic3r/GCode/ToolOrdering.cpp
        src/main/jni/libslic3r/GCode/ToolOrdering.hpp
        src/main/jni/libslic3r/GCode/WipeTower2.cpp
        src/main/jni/libslic3r/GCode/WipeTower2.hpp
        src/main/jni/libslic3r/GCode/WipeTower.cpp
        src/main/jni/libslic3r/GCode/WipeTower.hpp
        src/main/jni/libslic3r/GCodeWriter.cpp
        src/main/jni/libslic3r/GCodeWriter.hpp
        src/main/jni/libslic3r/Geometry/ArcWelder.hpp
        src/main/jni/libslic3r/Geometry/ArcWelder.cpp
        src/main/jni/libslic3r/Geometry/Bicubic.hpp
        src/main/jni/libslic3r/Geometry/Circle.cpp
        src/main/jni/libslic3r/Geometry/Circle.hpp
        src/main/jni/libslic3r/Geometry/ConvexHull.cpp
        src/main/jni/libslic3r/Geometry/ConvexHull.hpp
        src/main/jni/libslic3r/Geometry.cpp
        src/main/jni/libslic3r/Geometry/Curves.hpp
        src/main/jni/libslic3r/Geometry.hpp
        src/main/jni/libslic3r/Geometry/MedialAxis.cpp
        src/main/jni/libslic3r/Geometry/MedialAxis.hpp
        src/main/jni/libslic3r/Geometry/Voronoi.cpp
        src/main/jni/libslic3r/Geometry/Voronoi.hpp
        src/main/jni/libslic3r/Geometry/VoronoiOffset.cpp
        src/main/jni/libslic3r/Geometry/VoronoiOffset.hpp
        src/main/jni/libslic3r/Geometry/VoronoiUtilsCgal.cpp
        src/main/jni/libslic3r/Geometry/VoronoiUtilsCgal.hpp
        src/main/jni/libslic3r/Geometry/VoronoiUtils.cpp
        src/main/jni/libslic3r/Geometry/VoronoiUtils.hpp
        src/main/jni/libslic3r/Geometry/VoronoiVisualUtils.hpp
        src/main/jni/libslic3r/Int128.hpp
        src/main/jni/libslic3r/KDTreeIndirect.hpp
        src/main/jni/libslic3r/Layer.cpp
        src/main/jni/libslic3r/Layer.hpp
        src/main/jni/libslic3r/LayerRegion.cpp
        src/main/jni/libslic3r/libslic3r.cpp
        src/main/jni/libslic3r/libslic3r.h
        src/main/jni/libslic3r/Line.cpp
        src/main/jni/libslic3r/Line.hpp
        src/main/jni/libslic3r/LocalesUtils.cpp
        src/main/jni/libslic3r/LocalesUtils.hpp
        src/main/jni/libslic3r/MarchingSquares.hpp
        src/main/jni/libslic3r/Measure.cpp
        src/main/jni/libslic3r/Measure.hpp
        src/main/jni/libslic3r/MeasureUtils.hpp
        src/main/jni/libslic3r/MeshSplitImpl.hpp
        src/main/jni/libslic3r/MinAreaBoundingBox.cpp
        src/main/jni/libslic3r/MinAreaBoundingBox.hpp
        src/main/jni/libslic3r/MinimumSpanningTree.cpp
        src/main/jni/libslic3r/MinimumSpanningTree.hpp
        src/main/jni/libslic3r/miniz_extension.cpp
        src/main/jni/libslic3r/miniz_extension.hpp
        src/main/jni/libslic3r/ModelArrange.cpp
        src/main/jni/libslic3r/ModelArrange.hpp
        src/main/jni/libslic3r/Model.cpp
        src/main/jni/libslic3r/Model.hpp
        src/main/jni/libslic3r/MTUtils.hpp
        src/main/jni/libslic3r/MultiMaterialSegmentation.cpp
        src/main/jni/libslic3r/MultiMaterialSegmentation.hpp
        src/main/jni/libslic3r/MultiPoint.cpp
        src/main/jni/libslic3r/MultiPoint.hpp
        src/main/jni/libslic3r/MutablePolygon.cpp
        src/main/jni/libslic3r/MutablePolygon.hpp
        src/main/jni/libslic3r/MutablePriorityQueue.hpp
        src/main/jni/libslic3r/NormalUtils.cpp
        src/main/jni/libslic3r/NormalUtils.hpp
        src/main/jni/libslic3r/MeshBoolean.cpp
        src/main/jni/libslic3r/MeshBoolean.hpp
        src/main/jni/libslic3r/CutSurface.cpp
        src/main/jni/libslic3r/CutSurface.hpp
        src/main/jni/libslic3r/TryCatchSignal.cpp
        src/main/jni/libslic3r/TryCatchSignal.hpp
        src/main/jni/libslic3r/IntersectionPoints.cpp
        src/main/jni/libslic3r/IntersectionPoints.hpp
        src/main/jni/libslic3r/OpenVDBUtils.cpp
        src/main/jni/libslic3r/OpenVDBUtils.hpp
        src/main/jni/libslic3r/Triangulation.cpp
        src/main/jni/libslic3r/Triangulation.hpp
        src/main/jni/libslic3r/NSVGUtils.cpp
        src/main/jni/libslic3r/NSVGUtils.hpp
        src/main/jni/libslic3r/ObjectID.cpp
        src/main/jni/libslic3r/ObjectID.hpp
        src/main/jni/libslic3r/Optimize/BruteforceOptimizer.hpp
        src/main/jni/libslic3r/Optimize/NLoptOptimizer.hpp
        src/main/jni/libslic3r/Optimize/Optimizer.hpp
        src/main/jni/libslic3r/ParameterUtils.cpp
        src/main/jni/libslic3r/ParameterUtils.hpp
        src/main/jni/libslic3r/pchheader.cpp
        src/main/jni/libslic3r/pchheader.hpp
        src/main/jni/libslic3r/PerimeterGenerator.cpp
        src/main/jni/libslic3r/PerimeterGenerator.hpp
        src/main/jni/libslic3r/PlaceholderParser.cpp
        src/main/jni/libslic3r/PlaceholderParser.hpp
        src/main/jni/libslic3r/Platform.cpp
        src/main/jni/libslic3r/Platform.hpp
        src/main/jni/libslic3r/PNGReadWrite.cpp
        src/main/jni/libslic3r/PNGReadWrite.hpp
        src/main/jni/libslic3r/Point.cpp
        src/main/jni/libslic3r/Point.hpp
        src/main/jni/libslic3r/Polygon.cpp
        src/main/jni/libslic3r/Polygon.hpp
        src/main/jni/libslic3r/PolygonTrimmer.cpp
        src/main/jni/libslic3r/PolygonTrimmer.hpp
        src/main/jni/libslic3r/Polyline.cpp
        src/main/jni/libslic3r/Polyline.hpp
        src/main/jni/libslic3r/PresetBundle.cpp
        src/main/jni/libslic3r/PresetBundle.hpp
        src/main/jni/libslic3r/Preset.cpp
        src/main/jni/libslic3r/Preset.hpp
        src/main/jni/libslic3r/PrincipalComponents2D.cpp
        src/main/jni/libslic3r/PrincipalComponents2D.hpp
        src/main/jni/libslic3r/PrintApply.cpp
        src/main/jni/libslic3r/PrintBase.cpp
        src/main/jni/libslic3r/PrintBase.hpp
        src/main/jni/libslic3r/MaterialType.cpp
        src/main/jni/libslic3r/MaterialType.hpp
        src/main/jni/libslic3r/PrintConfig.cpp
        src/main/jni/libslic3r/PrintConfig.hpp
        src/main/jni/libslic3r/Print.cpp
        src/main/jni/libslic3r/Print.hpp
        src/main/jni/libslic3r/PrintObject.cpp
        src/main/jni/libslic3r/PrintObjectSlice.cpp
        src/main/jni/libslic3r/PrintRegion.cpp
        src/main/jni/libslic3r/ProjectTask.cpp
        src/main/jni/libslic3r/ProjectTask.hpp
        src/main/jni/libslic3r/QuadricEdgeCollapse.cpp
        src/main/jni/libslic3r/QuadricEdgeCollapse.hpp
        src/main/jni/libslic3r/Semver.cpp
        src/main/jni/libslic3r/ShortEdgeCollapse.cpp
        src/main/jni/libslic3r/ShortEdgeCollapse.hpp
        src/main/jni/libslic3r/ShortestPath.cpp
        src/main/jni/libslic3r/ShortestPath.hpp
        src/main/jni/libslic3r/SLA/AGGRaster.hpp
        src/main/jni/libslic3r/SLA/BoostAdapter.hpp
        src/main/jni/libslic3r/SLA/Clustering.cpp
        src/main/jni/libslic3r/SLA/Clustering.hpp
        src/main/jni/libslic3r/SLA/ConcaveHull.cpp
        src/main/jni/libslic3r/SLA/ConcaveHull.hpp
        src/main/jni/libslic3r/SLA/Concurrency.hpp
        src/main/jni/libslic3r/SLA/Hollowing.cpp
        src/main/jni/libslic3r/SLA/Hollowing.hpp
        src/main/jni/libslic3r/SLA/IndexedMesh.cpp
        src/main/jni/libslic3r/SLA/IndexedMesh.hpp
        src/main/jni/libslic3r/SLA/JobController.hpp
        src/main/jni/libslic3r/SLA/Pad.cpp
        src/main/jni/libslic3r/SLA/Pad.hpp
        src/main/jni/libslic3r/SLAPrint.cpp
        src/main/jni/libslic3r/SLAPrint.hpp
        src/main/jni/libslic3r/SLAPrintSteps.cpp
        src/main/jni/libslic3r/SLAPrintSteps.hpp
        src/main/jni/libslic3r/SLA/RasterBase.cpp
        src/main/jni/libslic3r/SLA/RasterBase.hpp
        src/main/jni/libslic3r/SLA/RasterToPolygons.cpp
        src/main/jni/libslic3r/SLA/RasterToPolygons.hpp
        src/main/jni/libslic3r/SLA/ReprojectPointsOnMesh.hpp
        src/main/jni/libslic3r/SLA/Rotfinder.cpp
        src/main/jni/libslic3r/SLA/Rotfinder.hpp
        src/main/jni/libslic3r/SLA/SpatIndex.cpp
        src/main/jni/libslic3r/SLA/SpatIndex.hpp
        src/main/jni/libslic3r/SLA/SupportPointGenerator.cpp
        src/main/jni/libslic3r/SLA/SupportPointGenerator.hpp
        src/main/jni/libslic3r/SLA/SupportPoint.hpp
        src/main/jni/libslic3r/SLA/SupportTreeBuilder.cpp
        src/main/jni/libslic3r/SLA/SupportTreeBuilder.hpp
        src/main/jni/libslic3r/SLA/SupportTreeBuildsteps.cpp
        src/main/jni/libslic3r/SLA/SupportTreeBuildsteps.hpp
        src/main/jni/libslic3r/SLA/SupportTree.cpp
        src/main/jni/libslic3r/SLA/SupportTree.hpp
        src/main/jni/libslic3r/SLA/SupportTreeMesher.cpp
        src/main/jni/libslic3r/SLA/SupportTreeMesher.hpp
        src/main/jni/libslic3r/SlicesToTriangleMesh.cpp
        src/main/jni/libslic3r/SlicesToTriangleMesh.hpp
        src/main/jni/libslic3r/SlicingAdaptive.cpp
        src/main/jni/libslic3r/SlicingAdaptive.hpp
        src/main/jni/libslic3r/Slicing.cpp
        src/main/jni/libslic3r/Slicing.hpp
        src/main/jni/libslic3r/Support/SupportCommon.cpp
        src/main/jni/libslic3r/Support/SupportCommon.hpp
        src/main/jni/libslic3r/Support/SupportLayer.hpp
        src/main/jni/libslic3r/Support/SupportMaterial.cpp
        src/main/jni/libslic3r/Support/SupportMaterial.hpp
        src/main/jni/libslic3r/Support/SupportParameters.hpp
        src/main/jni/libslic3r/Support/SupportSpotsGenerator.cpp
        src/main/jni/libslic3r/Support/SupportSpotsGenerator.hpp
        src/main/jni/libslic3r/Support/TreeModelVolumes.cpp
        src/main/jni/libslic3r/Support/TreeModelVolumes.hpp
        src/main/jni/libslic3r/Support/TreeSupport3D.cpp
        src/main/jni/libslic3r/Support/TreeSupport3D.hpp
        src/main/jni/libslic3r/Support/TreeSupportCommon.hpp
        src/main/jni/libslic3r/Support/TreeSupport.cpp
        src/main/jni/libslic3r/Support/TreeSupport.hpp
        src/main/jni/libslic3r/SurfaceCollection.cpp
        src/main/jni/libslic3r/SurfaceCollection.hpp
        src/main/jni/libslic3r/Surface.cpp
        src/main/jni/libslic3r/Surface.hpp
        src/main/jni/libslic3r/SurfaceMesh.hpp
        src/main/jni/libslic3r/SVG.cpp
        src/main/jni/libslic3r/SVG.hpp
        src/main/jni/libslic3r/Technologies.hpp
        src/main/jni/libslic3r/Tesselate.cpp
        src/main/jni/libslic3r/Tesselate.hpp
        src/main/jni/libslic3r/TextConfiguration.hpp
        src/main/jni/libslic3r/Thread.cpp
        src/main/jni/libslic3r/Thread.hpp
        src/main/jni/libslic3r/Time.cpp
        src/main/jni/libslic3r/Time.hpp
        src/main/jni/libslic3r/Timer.cpp
        src/main/jni/libslic3r/Timer.hpp
        src/main/jni/libslic3r/TriangleMesh.cpp
        src/main/jni/libslic3r/TriangleMesh.hpp
        src/main/jni/libslic3r/TriangleMeshSlicer.cpp
        src/main/jni/libslic3r/TriangleMeshSlicer.hpp
        src/main/jni/libslic3r/TriangleSelector.cpp
        src/main/jni/libslic3r/TriangleSelector.hpp
        src/main/jni/libslic3r/TriangleSetSampling.cpp
        src/main/jni/libslic3r/TriangleSetSampling.hpp
        src/main/jni/libslic3r/TriangulateWall.cpp
        src/main/jni/libslic3r/TriangulateWall.hpp
        src/main/jni/libslic3r/utils.cpp
        src/main/jni/libslic3r/Utils.hpp
        src/main/jni/libslic3r/VariableWidth.cpp
        src/main/jni/libslic3r/VariableWidth.hpp
        src/main/jni/libslic3r/Zipper.cpp
        src/main/jni/libslic3r/Zipper.hpp
        src/main/jni/libslic3r/FilamentGroup.hpp
        src/main/jni/libslic3r/FilamentGroup.cpp
        src/main/jni/libslic3r/FilamentGroupUtils.hpp
        src/main/jni/libslic3r/FilamentGroupUtils.cpp
        src/main/jni/libslic3r/GCode/ToolOrderUtils.hpp
        src/main/jni/libslic3r/GCode/ToolOrderUtils.cpp
        src/main/jni/libslic3r/FlushVolPredictor.hpp
        src/main/jni/libslic3r/FlushVolPredictor.cpp

        src/main/jni/libnest2d/src/libnest2d.cpp

        src/main/jni/bbl/Orient.cpp

        src/main/jni/slicebeam/beam_native.cpp
        src/main/jni/slicebeam/engine_compat.cpp
        src/main/jni/slicebeam/GLModel.cpp
        src/main/jni/slicebeam/GLShader.cpp

#        $<TARGET_OBJECTS:simd>
)
target_compile_definitions(slic3r PUBLIC -DUSE_TBB -DTBB_USE_CAPTURED_EXCEPTION=0 -DLIBNEST2D_GEOMETRIES_libslic3r
        -DLIBNEST2D_OPTIMIZER_nlopt -DLIBNEST2D_THREADING_tbb -DLIBNEST2D_STATIC -DENABLE_OPENGL_ES -DSLIC3R_OPENGL_ES
        -DUSE_CPP11_REGEX)

target_include_directories(slic3r PRIVATE
        src/main/jni
        src/main/jni/libslic3r
        src/main/jni/openvdb
        src/main/jni/mpfr
        src/main/jni/zlib
        src/main/jni/libpng
        src/main/jni/libjpeg-turbo
        src/main/jni/semver
        src/main/jni/eigen
        src/main/jni/fast_float
        src/main/jni/expat
        src/main/jni/heatshrink
        src/main/jni/miniz
        src/main/jni/LibBGCode
        src/main/jni/glu-libtess/include
        src/main/jni/qhull
        src/main/jni/nlopt
        src/main/jni/nlopt/algs/stogo
        src/main/jni/nlopt/algs/ags
        src/main/jni/nlopt/util
        src/main/jni/nlopt/algs/direct
        src/main/jni/nlopt/algs/cdirect
        src/main/jni/nlopt/algs/praxis
        src/main/jni/nlopt/algs/luksan
        src/main/jni/nlopt/algs/crs
        src/main/jni/nlopt/algs/mlsl
        src/main/jni/nlopt/algs/mma
        src/main/jni/nlopt/algs/cobyla
        src/main/jni/nlopt/algs/newuoa
        src/main/jni/nlopt/algs/neldermead
        src/main/jni/nlopt/algs/auglag
        src/main/jni/nlopt/algs/bobyqa
        src/main/jni/nlopt/algs/isres
        src/main/jni/nlopt/algs/slsqp
        src/main/jni/nlopt/algs/esch
        src/main/jni/nlopt/api
        src/main/jni/libnest2d/include
        src/main/jni/libvgcode/include
        src/main/jniImports/oneTBB/include
        src/main/jniImports/boost/include
        src/main/jniImports/gmp/include/${ANDROID_ABI}
)
find_library(log-lib log)

target_link_libraries(slic3r PRIVATE
        m
        dl
        mpfr
        gmp
        gmpxx
        libpng
        zlib
        tbb
        log
        EGL
        GLESv3
        openvdb
        tbbmalloc
        heatshrink
        jpeg-static
        OCCTWrapper
        mcut
        BGCode_core
        BGCode_binarize
        BGCode_convert
        boost_atomic
        boost_charconv
        boost_chrono
        boost_container
        boost_context
        boost_contract
        boost_coroutine
        boost_date_time
        boost_exception
        boost_fiber
        boost_filesystem
        boost_graph
        boost_iostreams
        boost_json
        boost_log
        boost_log_setup
        boost_math_c99
        boost_math_c99f
        boost_math_c99l
        boost_math_tr1
        boost_math_tr1f
        boost_math_tr1l
        boost_nowide
        boost_prg_exec_monitor
        boost_random
        boost_regex
        boost_serialization
        boost_stacktrace_basic
        boost_system
        boost_thread
        boost_timer
        boost_type_erasure
        boost_url
        boost_wave
        boost_wserialization
        ${OCCT_LIBS}
)