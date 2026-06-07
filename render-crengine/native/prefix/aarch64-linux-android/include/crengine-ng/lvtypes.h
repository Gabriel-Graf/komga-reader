/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007-2009,2011-2013,2015 Vadim Lopatin <coolreader.org@gmail.com>
 *   Copyright (C) 2011 Konstantin Potapov <pkbo@users.sourceforge.net>    *
 *   Copyright (C) 2020 poire-z <poire-z@users.noreply.github.com>         *
 *   Copyright (C) 2018,2020-2022,2024 Aleksey Chernov <valexlin@gmail.com>*
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
 * \file lvtypes.h
 * \brief CREngine common types definition
 */

#ifndef LVTYPES_H_INCLUDED
#define LVTYPES_H_INCLUDED

#include <crsetup.h>

#include <stddef.h>

typedef int lInt32;           ///< signed 32 bit int
typedef unsigned int lUInt32; ///< unsigned 32 bit int

typedef short int lInt16;           ///< signed 16 bit int
typedef unsigned short int lUInt16; ///< unsigned 16 bit int

typedef signed char lInt8;    ///< signed 8 bit int
typedef unsigned char lUInt8; ///< unsigned 8 bit int

typedef char32_t lChar32; ///< 32 bit char
typedef char lChar8;      ///< 8 bit char

#ifdef _WIN32
typedef wchar_t lChar16; ///< 16 bit char, only for Windows
#else
typedef char16_t lChar16; ///< 16 bit char
#endif

#if defined(_WIN32) && defined(_MSC_VER)
typedef __int64 lInt64;           ///< signed 64 bit int
typedef unsigned __int64 lUInt64; ///< unsigned 64 bit int
#else
typedef long long int lInt64;           ///< signed 64 bit int
typedef unsigned long long int lUInt64; ///< unsigned 64 bit int
#endif

/// platform-dependent path separator
#if defined(_WIN32) && !defined(__WINE__)
#define PATH_SEPARATOR_CHAR '\\'
#elif __SYMBIAN32__
#define PATH_SEPARATOR_CHAR '\\'
#else
#define PATH_SEPARATOR_CHAR '/'
#endif

#if LVLONG_FILE_SUPPORT == 1
typedef lUInt64 lvsize_t;  ///< file size type
typedef lInt64 lvoffset_t; ///< file offset type
typedef lUInt64 lvpos_t;   ///< file position type
#else
typedef lUInt32 lvsize_t;  ///< file size type
typedef lInt32 lvoffset_t; ///< file offset type
typedef lUInt32 lvpos_t;   ///< file position type
#endif

#define LV_INVALID_SIZE ((lvsize_t)(-1))

/// Seek origins enum
enum lvseek_origin_t
{
    LVSEEK_SET = 0, ///< seek relatively to beginning of file
    LVSEEK_CUR = 1, ///< seek relatively to current position
    LVSEEK_END = 2  ///< seek relatively to end of file
};

/// I/O errors enum
enum lverror_t
{
    LVERR_OK = 0,   ///< no error
    LVERR_FAIL,     ///< failed (unknown error)
    LVERR_EOF,      ///< end of file reached
    LVERR_NOTFOUND, ///< file not found
    LVERR_NOTIMPL   ///< method is not implemented
};

/// File open modes enum
enum lvopen_mode_t
{
    LVOM_ERROR = 0, ///< to indicate error state
    LVOM_CLOSED,    ///< to indicate closed state
    LVOM_READ,      ///< readonly mode, use for r/o mmap
    LVOM_WRITE,     ///< writeonly mode
    LVOM_APPEND,    ///< append (readwrite) mode, use for r/w mmap
    LVOM_READWRITE  ///< readwrite mode
};
#define LVOM_MASK      7
#define LVOM_FLAG_SYNC 0x10 /// flag, use to sync data after each write operation

/// point
class lvPoint
{
public:
    int x;
    int y;
    lvPoint()
            : x(0)
            , y(0) { }
    lvPoint(int nx, int ny)
            : x(nx)
            , y(ny) { }
    lvPoint(const lvPoint& v)
            : x(v.x)
            , y(v.y) { }
    lvPoint& operator=(const lvPoint& v) {
        x = v.x;
        y = v.y;
        return *this;
    }
};

/// rectangle
class lvRect
{
public:
    int left;
    int top;
    int right;
    int bottom;
    /// returns true if rectangle is empty
    bool isEmpty() const {
        return left >= right || bottom <= top;
    }
    lvRect()
            : left(0)
            , top(0)
            , right(0)
            , bottom(0) { }
    lvRect(int x0, int y0, int x1, int y1)
            : left(x0)
            , top(y0)
            , right(x1)
            , bottom(y1) { }
    lvPoint topLeft() const {
        return lvPoint(left, top);
    }
    lvPoint bottomRight() const {
        return lvPoint(right, bottom);
    }
    void setTopLeft(const lvPoint& pt) {
        top = pt.y;
        left = pt.x;
    }
    void setBottomRight(const lvPoint& pt) {
        bottom = pt.y;
        right = pt.x;
    }
    /// returns true if rectangles are equal
    bool operator==(const lvRect& rc) const {
        return rc.left == left && rc.right == right && rc.top == top && rc.bottom == bottom;
    }
    /// returns true if rectangles are not equal
    bool operator!=(const lvRect& rc) const {
        return !(rc.left == left && rc.right == right && rc.top == top && rc.bottom == bottom);
    }
    /// returns non-NULL pointer to trimming values for 4 sides of rc, if clipping is necessary
    lvRect* clipBy(lvRect& cliprc) {
        if (intersects(cliprc) && !cliprc.isRectInside(*this)) {
            lvRect* res = new lvRect();
            if (cliprc.left > left)
                res->left = cliprc.left - left;
            if (cliprc.top > top)
                res->top = cliprc.top - top;
            if (right > cliprc.right)
                res->right = right - cliprc.right;
            if (bottom > cliprc.bottom)
                res->bottom = bottom - cliprc.bottom;
            return res;
        } else {
            return NULL;
        }
    }

    /// returns rectangle width
    int width() const {
        return right - left;
    }
    /// returns rectangle height
    int height() const {
        return bottom - top;
    }
    int minDimension() {
        return (right - left < bottom - top) ? right - left : bottom - top;
    }
    lvPoint size() const {
        return lvPoint(right - left, bottom - top);
    }
    void shrink(int delta) {
        left += delta;
        right -= delta;
        top += delta;
        bottom -= delta;
    }
    void shrinkBy(const lvRect& rc) {
        left += rc.left;
        right -= rc.right;
        top += rc.top;
        bottom -= rc.bottom;
    }
    void extend(int delta) {
        shrink(-delta);
    }
    void extendBy(const lvRect& rc) {
        left -= rc.left;
        right += rc.right;
        top -= rc.top;
        bottom += rc.bottom;
    }
    /// makes this rect to cover both this and specified rect (bounding box for two rectangles)
    void extend(lvRect rc) {
        if (rc.isEmpty())
            return;
        if (isEmpty()) {
            left = rc.left;
            top = rc.top;
            right = rc.right;
            bottom = rc.bottom;
            return;
        }
        if (left > rc.left)
            left = rc.left;
        if (top > rc.top)
            top = rc.top;
        if (right < rc.right)
            right = rc.right;
        if (bottom < rc.bottom)
            bottom = rc.bottom;
    }
    /// returns true if specified rectangle is fully covered by this rectangle
    bool isRectInside(lvRect rc) const {
        // This was wrong: a 0-height or 0-width rect can be inside another rect
        // if ( rc.isEmpty() || isEmpty() )
        //    return false;
        if (rc.left < left || rc.right > right || rc.top < top || rc.bottom > bottom)
            return false;
        return true;
    }

    /// returns true if specified rectangle has common part with this rectangle
    bool intersects(const lvRect& rc) const {
        if (rc.isEmpty() || isEmpty())
            return false;
        if (rc.right <= left || rc.left >= right || rc.bottom <= top || rc.top >= bottom)
            return false;
        return true;
    }

    /// returns true if point is inside this rectangle
    bool isPointInside(const lvPoint& pt) const {
        return left <= pt.x && top <= pt.y && right > pt.x && bottom > pt.y;
    }
    void clear() {
        left = right = top = bottom = 0;
    }

    bool intersect(const lvRect& rc) {
        if (left < rc.left)
            left = rc.left;
        if (right > rc.right)
            right = rc.right;
        if (top < rc.top)
            top = rc.top;
        if (bottom > rc.bottom)
            bottom = rc.bottom;
        bool ret = !isEmpty();
        if (!ret)
            clear();
        return ret;
    }
};

class lvInsets
{
public:
    int left;
    int top;
    int right;
    int bottom;
    lvInsets()
            : left(0)
            , top(0)
            , right(0)
            , bottom(0) { }
    lvInsets(int l, int t, int r, int b)
            : left(l)
            , top(t)
            , right(r)
            , bottom(b) { }
    lvInsets(const lvInsets& other)
            : left(other.left)
            , top(other.top)
            , right(other.right)
            , bottom(other.bottom) { }
    lvInsets& operator=(const lvInsets& other) {
        left = other.left;
        top = other.top;
        right = other.right;
        bottom = other.bottom;
        return *this;
    }
    /// returns true if two insets are equal
    bool operator==(const lvInsets& insets) const {
        return insets.left == left && insets.right == right && insets.top == top && insets.bottom == bottom;
    }
    /// returns true if two insets are not equal
    bool operator!=(const lvInsets& insets) const {
        return !(insets.left == left && insets.right == right && insets.top == top && insets.bottom == bottom);
    }
};

class lvColor
{
    lUInt32 value;
public:
    lvColor(lUInt32 cl)
            : value(cl) { }
    lvColor(lUInt32 r, lUInt32 g, lUInt32 b)
            : value(((r & 255) << 16) | ((g & 255) << 8) | (b & 255)) { }
    lvColor(lUInt32 r, lUInt32 g, lUInt32 b, lUInt32 a)
            : value(((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255)) { }
    operator lUInt32() const {
        return value;
    }
    lUInt32 get() const {
        return value;
    }
    lUInt8 r() const {
        return (lUInt8)(value >> 16) & 255;
    }
    lUInt8 g() const {
        return (lUInt8)(value >> 8) & 255;
    }
    lUInt8 b() const {
        return (lUInt8)(value) & 255;
    }
    lUInt8 a() const {
        return (lUInt8)(value >> 24) & 255;
    }
};

// MACROS to avoid UNUSED PARAM warning
#define CR_UNUSED(x) (void)x;
#define CR_UNUSED2(x, x2) \
    (void)x;              \
    (void)x2;
#define CR_UNUSED3(x, x2, x3) \
    (void)x;                  \
    (void)x2;                 \
    (void)x3;
#define CR_UNUSED4(x, x2, x3, x4) \
    (void)x;                      \
    (void)x2;                     \
    (void)x3;                     \
    (void)x4;
#define CR_UNUSED5(x, x2, x3, x4, x5) \
    (void)x;                          \
    (void)x2;                         \
    (void)x3;                         \
    (void)x4;                         \
    (void)x5;
#define CR_UNUSED6(x, x2, x3, x4, x5, x6) \
    (void)x;                              \
    (void)x2;                             \
    (void)x3;                             \
    (void)x4;                             \
    (void)x5;                             \
    (void)x6;
#define CR_UNUSED7(x, x2, x3, x4, x5, x6, x7) \
    (void)x;                                  \
    (void)x2;                                 \
    (void)x3;                                 \
    (void)x4;                                 \
    (void)x5;                                 \
    (void)x6;                                 \
    (void)x7;
#define CR_UNUSED8(x, x2, x3, x4, x5, x6, x7, x8) \
    (void)x;                                      \
    (void)x2;                                     \
    (void)x3;                                     \
    (void)x4;                                     \
    (void)x5;                                     \
    (void)x6;                                     \
    (void)x7;                                     \
    (void)x8;
#define CR_UNUSED9(x, x2, x3, x4, x5, x6, x7, x8, x9) \
    (void)x;                                          \
    (void)x2;                                         \
    (void)x3;                                         \
    (void)x4;                                         \
    (void)x5;                                         \
    (void)x6;                                         \
    (void)x7;                                         \
    (void)x8;                                         \
    (void)x9;
#define CR_UNUSED10(x, x2, x3, x4, x5, x6, x7, x8, x9, x10) \
    (void)x;                                                \
    (void)x2;                                               \
    (void)x3;                                               \
    (void)x4;                                               \
    (void)x5;                                               \
    (void)x6;                                               \
    (void)x7;                                               \
    (void)x8;                                               \
    (void)x9;                                               \
    (void)x10;
#define CR_UNUSED11(x, x2, x3, x4, x5, x6, x7, x8, x9, x11) \
    (void)x;                                                \
    (void)x2;                                               \
    (void)x3;                                               \
    (void)x4;                                               \
    (void)x5;                                               \
    (void)x6;                                               \
    (void)x7;                                               \
    (void)x8;                                               \
    (void)x9;                                               \
    (void)x10;                                              \
    (void)x11;

#endif //LVTYPES_H_INCLUDED
