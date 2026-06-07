/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007-2011,2013,2018 Vadim Lopatin <coolreader.org@gmail.com>
 *   Copyright (C) 2020 Konstantin Potapov <pkbo@users.sourceforge.net>    *
 *   Copyright (C) 2018-2022 Aleksey Chernov <valexlin@gmail.com>          *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or         *
 *   modify it under the terms of the GNU General Public License           *
 *   as published by the Free Software Foundation; either version 2        *
 *   of the License, or (at your option) any later version.                *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the Free Software           *
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,            *
 *   MA 02110-1301, USA.                                                   *
 ***************************************************************************/

/**
 * \file crsetup.h
 * \brief CREngine options definitions
 */

#ifndef CRSETUP_H_INCLUDED
#define CRSETUP_H_INCLUDED

#include <crengine-ng-config.h>

//==================================================
// Linux/Unix/MacOS/Android
//==================================================
#if defined(_LINUX) || defined(LINUX)

#ifdef ANDROID
#define CR_USE_THREADS 1
#define USE_ATOMIC_REFCOUNT
#else
#define CR_USE_THREADS 0
#endif // ANDROID

#define USE_ANSI_FILES  0
#define GRAY_INVERSE    0
#define USE_FT_EMBOLDEN 1
#define MATHML_SUPPORT  1

#define ALLOW_KERNING            1
#define USE_GLYPHCACHE_HASHTABLE 1
#define GLYPH_CACHE_SIZE         0x40000
#define ZIP_STREAM_BUFFER_SIZE   0x40000
#define FILE_STREAM_BUFFER_SIZE  0x20000

#endif // defined(_LINUX) || defined (LINUX)

//==================================================
// WIN32
//==================================================
#if defined(_WIN32) && !defined(__SYMBIAN32__)

#define CR_USE_THREADS 0

#define USE_ANSI_FILES  0
#define GRAY_INVERSE    0
#define USE_FT_EMBOLDEN 1
#define MATHML_SUPPORT  1

#define ALLOW_KERNING            1
#define USE_GLYPHCACHE_HASHTABLE 1
#define GLYPH_CACHE_SIZE         0x20000
#define ZIP_STREAM_BUFFER_SIZE   0x80000
#define FILE_STREAM_BUFFER_SIZE  0x40000

#if USE_FREETYPE != 1
#define USE_WIN32_FONTS 1
#endif

#endif // defined(_WIN32) && !defined(__SYMBIAN32__)

// Common defines

#ifndef USE_ZLIB
///Compression using ZLIB
#define USE_ZLIB 0
#endif

#ifndef USE_LIBPNG
///allow PNG support via libpng
#define USE_LIBPNG 0
#endif

#ifndef USE_LIBJPEG
///allow JPEG support via libjpeg
#define USE_LIBJPEG 0
#endif

#ifndef USE_FREETYPE
#define USE_FREETYPE 0
#endif

#ifndef USE_HARFBUZZ
#define USE_HARFBUZZ 0
#endif

#ifndef USE_FRIBIDI
#define USE_FRIBIDI 0
#endif

#ifndef USE_LIBUNIBREAK
#define USE_LIBUNIBREAK 0
#endif

#ifndef USE_ZSTD
#define USE_ZSTD 0
#endif

#ifndef USE_UTF8PROC
#define USE_UTF8PROC 0
#endif

#ifndef USE_NANOSVG
#define USE_NANOSVG 0
#endif

#ifndef USE_CHM
#define USE_CHM 0
#endif

#ifndef USE_ANTIWORD
#define USE_ANTIWORD 0
#endif

#ifndef USE_GIF
///allow GIF support via embedded decoder
#define USE_GIF 0
#endif

#ifndef USE_FONTCONFIG
#define USE_FONTCONFIG 0
#endif

#ifndef USE_SHASUM
#define USE_SHASUM 0
#endif

#ifndef GRAY_INVERSE
#define GRAY_INVERSE 0
#endif

/** \def LVLONG_FILE_SUPPORT
    \brief define to 1 to use 64 bits for file position types
*/
#ifndef LVLONG_FILE_SUPPORT
#define LVLONG_FILE_SUPPORT 0
#endif

//#define USE_ANSI_FILES 1

//1: use color backbuffer
//0: use gray backbuffer
#ifndef GRAY_BACKBUFFER_BITS
#define GRAY_BACKBUFFER_BITS 2
#endif // GRAY_BACKBUFFER_BITS

#ifndef COLOR_BACKBUFFER
#define COLOR_BACKBUFFER 0
#endif // COLOR_BACKBUFFER

/// zlib stream decode cache size, used to avoid restart of decoding from beginning to move back
#ifndef ZIP_STREAM_BUFFER_SIZE
#define ZIP_STREAM_BUFFER_SIZE 0x10000
#endif

/// document stream buffer size
#ifndef FILE_STREAM_BUFFER_SIZE
#define FILE_STREAM_BUFFER_SIZE 0x40000
#endif

#ifndef MATHML_SUPPORT
#define MATHML_SUPPORT 0
#endif

#ifndef USE_LOCALE_DATA
#define USE_LOCALE_DATA 0
#endif

#ifndef USE_FT_EMBOLDEN
#define USE_FT_EMBOLDEN 0
#endif

#ifndef USE_WIN32_FONTS
#define USE_WIN32_FONTS 0
#endif

#ifndef USE_WIN32DRAW_FONTS
// define to 1 to use LVWin32DrawFont instead of LVWin32Font
#define USE_WIN32DRAW_FONTS 0
#endif

#ifndef LDOM_USE_OWN_MEM_MAN
#define LDOM_USE_OWN_MEM_MAN 0
#endif

#ifndef USE_DOM_UTF8_STORAGE
#define USE_DOM_UTF8_STORAGE 0
#endif

/// Set to 1 to support CR internal rotation of screen, 0 for system rotation
#ifndef CR_INTERNAL_PAGE_ORIENTATION
#define CR_INTERNAL_PAGE_ORIENTATION 1
#endif

#ifndef USE_BITMAP_FONTS
#if (USE_WIN32_FONTS != 1) && (USE_FREETYPE != 1)
#define USE_BITMAP_FONTS 1
#endif
#endif // USE_BITMAP_FONTS

/// maximum picture zoom (1, 2, 3)
#ifndef MAX_IMAGE_SCALE_MUL
#define MAX_IMAGE_SCALE_MUL 2
#endif

// max unpacked size of skin image to hold in cache unpacked
#ifndef MAX_SKIN_IMAGE_CACHE_ITEM_UNPACKED_SIZE
#define MAX_SKIN_IMAGE_CACHE_ITEM_UNPACKED_SIZE 80 * 80 * 4
#endif

// max skin image file size to hold as a packed copy in memory
#ifndef MAX_SKIN_IMAGE_CACHE_ITEM_RAM_COPY_PACKED_SIZE
#define MAX_SKIN_IMAGE_CACHE_ITEM_RAM_COPY_PACKED_SIZE 10000
#endif

// Caching and MMAP options

/// minimal document size to enable caching for
#ifndef DOCUMENT_CACHING_MIN_SIZE
#define DOCUMENT_CACHING_MIN_SIZE 0x10000 // 64K
#endif

/// max ram data block usage, after which swapping to disk should occur
#ifndef DOCUMENT_CACHING_MAX_RAM_USAGE
#define DOCUMENT_CACHING_MAX_RAM_USAGE 0x800000 // 10Mb
#endif

/// Document caching file size threshold (bytes). For longer documents, swapping to disk should occur
#ifndef DOCUMENT_CACHING_SIZE_THRESHOLD
#define DOCUMENT_CACHING_SIZE_THRESHOLD 0x100000 // 1Mb
#endif

#ifndef ARBITRARY_IMAGE_SCALE_ENABLED
#define ARBITRARY_IMAGE_SCALE_ENABLED 0
#endif

#ifndef MAX_IMAGE_SCALE_MUL
#define MAX_IMAGE_SCALE_MUL 0
#endif

#ifndef USE_GLYPHCACHE_HASHTABLE
#define USE_GLYPHCACHE_HASHTABLE 0
#endif

// Maximum & minimum screen resolution
#ifndef SCREEN_SIZE_MIN
#define SCREEN_SIZE_MIN 80
#endif

#ifndef SCREEN_SIZE_MAX
#define SCREEN_SIZE_MAX 32767
#endif

#endif //CRSETUP_H_INCLUDED
