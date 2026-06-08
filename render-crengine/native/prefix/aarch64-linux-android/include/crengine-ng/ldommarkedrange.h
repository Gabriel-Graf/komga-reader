/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007,2011,2012 Vadim Lopatin <coolreader.org@gmail.com> *
 *   Copyright (C) 2019,2020 poire-z <poire-z@users.noreply.github.com>    *
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

#ifndef __LDOMMARKEDRANGE_H_INCLUDED__
#define __LDOMMARKEDRANGE_H_INCLUDED__

#include <ldomxpointer.h>
#include <ldomword.h>

enum MoveDirection
{
    DIR_ANY,
    DIR_LEFT,
    DIR_RIGHT,
    DIR_UP,
    DIR_DOWN
};

/// range in document, marked with specified flags
class ldomMarkedRange
{
public:
    /// start document point
    lvPoint start;
    /// end document point
    lvPoint end;
    /// flags:
    //  0: not shown
    //  1,2,3: legacy drawing (a single mark may spans multiple lines, assuming full width
    //         LTR paragraphs) (2 & 3 might be used for crengine internal bookmarks,
    //         see hist.h for enum bmk_type)
    //  0x11, 0x12, 0x13:  enhanced drawing (segmented mark, spanning a single line)
    lUInt32 flags;
    bool empty() {
        return (start.y > end.y || (start.y == end.y && start.x >= end.x));
    }
    /// returns mark middle point for single line mark, or start point for multiline mark
    lvPoint getMiddlePoint();
    /// returns distance (dx+dy) from specified point to middle point
    int calcDistance(int x, int y, MoveDirection dir);
    /// returns true if intersects specified line rectangle
    bool intersects(lvRect& rc, lvRect& intersection);
    /// constructor
    ldomMarkedRange(lvPoint _start, lvPoint _end, lUInt32 _flags)
            : start(_start)
            , end(_end)
            , flags(_flags) {
    }
    /// constructor
    ldomMarkedRange(ldomWord& word) {
        ldomXPointer startPos(word.getNode(), word.getStart());
        ldomXPointer endPos(word.getNode(), word.getEnd());
        start = startPos.toPoint();
        end = endPos.toPoint();
    }
    /// copy constructor
    ldomMarkedRange(const ldomMarkedRange& v)
            : start(v.start)
            , end(v.end)
            , flags(v.flags) {
    }
};

/// list of marked ranges
class ldomMarkedRangeList: public LVPtrVector<ldomMarkedRange>
{
public:
    ldomMarkedRangeList() {
    }
    /// create bounded by RC list, with (0,0) coordinates at left top corner
    // crop/discard elements outside of rc (or outside of crop_rc instead if provided)
    ldomMarkedRangeList(const ldomMarkedRangeList* list, lvRect& rc, lvRect* crop_rc = NULL);
};

#endif // __LDOMMARKEDRANGE_H_INCLUDED__
