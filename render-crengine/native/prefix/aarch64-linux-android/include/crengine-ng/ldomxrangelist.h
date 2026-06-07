/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007,2008 Vadim Lopatin <coolreader.org@gmail.com>      *
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

#ifndef __LDOMXRANGELIST_H_INCLUDED__
#define __LDOMXRANGELIST_H_INCLUDED__

#include <ldomxrange.h>

class ldomMarkedText
{
public:
    lString32 text;
    lUInt32 flags;
    int offset;
    ldomMarkedText(lString32 s, lUInt32 flg, int offs)
            : text(s)
            , flags(flg)
            , offset(offs) {
    }
    ldomMarkedText(const ldomMarkedText& v)
            : text(v.text)
            , flags(v.flags) {
    }
};

typedef LVPtrVector<ldomMarkedText> ldomMarkedTextList;

class ldomXRangeList: public LVPtrVector<ldomXRange>
{
public:
    /// add ranges for words
    void addWords(const LVArray<ldomWord>& words) {
        for (int i = 0; i < words.length(); i++)
            LVPtrVector<ldomXRange>::add(new ldomXRange(words[i]));
    }
    ldomXRangeList(const LVArray<ldomWord>& words) {
        addWords(words);
    }
    /// create list splittiny existing list into non-overlapping ranges
    ldomXRangeList(ldomXRangeList& srcList, bool splitIntersections);
    /// create list by filtering existing list, to get only values which intersect filter range
    ldomXRangeList(ldomXRangeList& srcList, ldomXRange& filter);
    /// fill text selection list by splitting text into monotonic flags ranges
    void splitText(ldomMarkedTextList& dst, ldomNode* textNodeToSplit);
    /// fill marked ranges list
    void getRanges(ldomMarkedRangeList& dst);
    /// split into subranges using intersection
    void split(ldomXRange* r);
    /// default constructor for empty list
    ldomXRangeList() {};
};

#endif // __LDOMXRANGELIST_H_INCLUDED__
