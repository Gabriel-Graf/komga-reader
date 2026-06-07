/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2008,2009,2012 Vadim Lopatin <coolreader.org@gmail.com> *
 *   Copyright (C) 2020 poire-z <poire-z@users.noreply.github.com>         *
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

#ifndef __LDOMWORD_H_INCLUDED__
#define __LDOMWORD_H_INCLUDED__

#include <ldomxpointer.h>

/// range for word inside text node
class ldomWord
{
    ldomNode* _node;
    int _start;
    int _end;
public:
    ldomWord()
            : _node(NULL)
            , _start(0)
            , _end(0) { }
    ldomWord(ldomNode* node, int start, int end)
            : _node(node)
            , _start(start)
            , _end(end) { }
    ldomWord(const ldomWord& v)
            : _node(v._node)
            , _start(v._start)
            , _end(v._end) { }
    ldomWord& operator=(const ldomWord& v) {
        _node = v._node;
        _start = v._start;
        _end = v._end;
        return *this;
    }
    /// returns true if object doesn't point valid word
    bool isNull() {
        return _node == NULL || _start < 0 || _end <= _start;
    }
    /// get word text node pointer
    ldomNode* getNode() const {
        return _node;
    }
    /// get word start offset
    int getStart() const {
        return _start;
    }
    /// get word end offset
    int getEnd() const {
        return _end;
    }
    /// get word start XPointer
    ldomXPointer getStartXPointer() const {
        return ldomXPointer(_node, _start);
    }
    /// get word start XPointer
    ldomXPointer getEndXPointer() const {
        return ldomXPointer(_node, _end);
    }
    /// get word text
    lString32 getText() {
        if (isNull())
            return lString32::empty_str;
        lString32 txt = _node->getText();
        return txt.substr(_start, _end - _start);
    }
};

#endif // __LDOMWORD_H_INCLUDED__
