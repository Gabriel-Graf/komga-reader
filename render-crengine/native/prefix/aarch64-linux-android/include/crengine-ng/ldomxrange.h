/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007-2011 Vadim Lopatin <coolreader.org@gmail.com>      *
 *   Copyright (C) 2016 Yifei(Frank) ZHU <fredyifei@gmail.com>             *
 *   Copyright (C) 2018,2020 poire-z <poire-z@users.noreply.github.com>    *
 *   Copyright (C) 2020 Aleksey Chernov <valexlin@gmail.com>               *
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

#ifndef __LDOMXRANGE_H_INCLUDED__
#define __LDOMXRANGE_H_INCLUDED__

#include <ldomxpointerex.h>
#include <ldomword.h>

class ldomNodeCallback;

/// DOM range
class ldomXRange
{
    ldomXPointerEx _start;
    ldomXPointerEx _end;
    /// _flags, only used by ldomXRangeList.getRanges() when making a ldomMarkedRangeList (for native
    //  highlighting of a text selection being made, and for crengine internal bookmarks):
    //  0: not shown (filtered out in LVDocView::updateSelections() by ldomXRangeList ranges(..., true))
    //  1,2,3: legacy drawing (will make a single ldomMarkedRange spanning multiple lines, assuming
    //         full width LTR paragraphs) (2 & 3 might be used for crengine internal bookmarks,
    //         see hist.h for enum bmk_type)
    //  0x11, 0x12, 0x13:  enhanced drawing (will make multiple segmented ldomMarkedRange,
    //                     each spanning a single line)
    lUInt32 _flags;
public:
    ldomXRange()
            : _flags(0) {
    }
    ldomXRange(const ldomXPointerEx& start, const ldomXPointerEx& end, lUInt32 flags = 0)
            : _start(start)
            , _end(end)
            , _flags(flags) {
    }
    ldomXRange(const ldomXPointer& start, const ldomXPointer& end)
            : _start(start)
            , _end(end)
            , _flags(0) {
    }
    /// copy constructor
    ldomXRange(const ldomXRange& v)
            : _start(v._start)
            , _end(v._end)
            , _flags(v._flags) {
    }
    ldomXRange(const ldomWord& word)
            : _start(word.getStartXPointer())
            , _end(word.getEndXPointer())
            , _flags(1) {
    }
    /// if start is after end, swap start and end
    void sort();
    /// create intersection of two ranges
    ldomXRange(const ldomXRange& v1, const ldomXRange& v2);
    /// copy constructor of full node range
    ldomXRange(ldomNode* p, bool fitEndToLastInnerChild = false);
    /// copy assignment
    ldomXRange& operator=(const ldomXRange& v) {
        _start = v._start;
        _end = v._end;
        return *this;
    }
    /// returns true if ranges are equal
    bool operator==(const ldomXRange& v) const {
        return _start == v._start && _end == v._end && _flags == v._flags;
    }
    /// returns true if interval is invalid or empty
    bool isNull() {
        if (_start.isNull() || _end.isNull())
            return true;
        if (_start.compare(_end) > 0)
            return true;
        return false;
    }
    /// makes range empty
    void clear() {
        _start.clear();
        _end.clear();
        _flags = 0;
    }
    /// returns true if pointer position is inside range
    bool isInside(const ldomXPointerEx& p) const {
        return (_start.compare(p) <= 0 && _end.compare(p) >= 0);
    }
    /// returns interval start point
    ldomXPointerEx& getStart() {
        return _start;
    }
    /// returns interval end point
    ldomXPointerEx& getEnd() {
        return _end;
    }
    /// sets interval start point
    void setStart(ldomXPointerEx& start) {
        _start = start;
    }
    /// sets interval end point
    void setEnd(ldomXPointerEx& end) {
        _end = end;
    }
    /// returns flags value
    lUInt32 getFlags() {
        return _flags;
    }
    /// sets new flags value
    void setFlags(lUInt32 flags) {
        _flags = flags;
    }
    /// returns true if this interval intersects specified interval
    bool checkIntersection(ldomXRange& v);
    /// returns text between two XPointer positions
    lString32 getRangeText(lChar32 blockDelimiter = '\n', int maxTextLen = 0);
    /// get all words from specified range
    void getRangeWords(LVArray<ldomWord>& list);
    /// returns href attribute of <A> element, null string if not found
    lString32 getHRef();
    /// returns href attribute of <A> element, plus xpointer of <A> element itself
    lString32 getHRef(ldomXPointer& a_xpointer);
    /// sets range to nearest word bounds, returns true if success
    static bool getWordRange(ldomXRange& range, ldomXPointer& p);
    /// run callback for each node in range
    void forEach(ldomNodeCallback* callback);
    /// returns rectangle (in doc coordinates) for range. Returns true if found.
    bool getRectEx(lvRect& rect, bool& isSingleLine);
    bool getRectEx(lvRect& rect) {
        bool isSingleLine;
        return getRectEx(rect, isSingleLine);
    };
    // returns multiple segments rects (one for each text line)
    // that the ldomXRange spans on the page.
    void getSegmentRects(LVArray<lvRect>& rects);
    /// returns nearest common element for start and end points
    ldomNode* getNearestCommonParent();
    /// returns HTML (serialized from the DOM, may be different from the source HTML)
    lString8 getHtml(lString32Collection& cssFiles, int wflags = 0, bool fromRootNode = false);
    lString8 getHtml(int wflags = 0, bool fromRootNode = false) {
        lString32Collection cssFiles;
        return getHtml(cssFiles, wflags, fromRootNode);
    };

    /// searches for specified text inside range
    bool findText(lString32 pattern, bool caseInsensitive, bool reverse, LVArray<ldomWord>& words, int maxCount, int maxHeight, int maxHeightCheckStartY = -1, bool checkMaxFromStart = false);
};

#endif // __LDOMXRANGE_H_INCLUDED__
