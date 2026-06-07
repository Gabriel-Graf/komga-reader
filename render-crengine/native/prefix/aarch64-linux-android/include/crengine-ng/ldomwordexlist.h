/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2011 Vadim Lopatin <coolreader.org@gmail.com>           *
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

#ifndef __LDOMWORDEXLIST_H_INCLUDED__
#define __LDOMWORDEXLIST_H_INCLUDED__

#include <ldomwordex.h>

/// list of extended words
class ldomWordExList: public LVPtrVector<ldomWordEx>
{
    int minx;
    int maxx;
    int miny;
    int maxy;
    int x;
    int y;
    ldomWordEx* selWord;
    lString32Collection pattern;
    void init();
    ldomWordEx* findWordByPattern();
public:
    ldomWordExList()
            : minx(-1)
            , maxx(-1)
            , miny(-1)
            , maxy(-1)
            , x(-1)
            , y(-1)
            , selWord(NULL) {
    }
    /// adds all visible words from range, returns number of added words
    int addRangeWords(ldomXRange& range, bool trimPunctuation);
    /// find word nearest to specified point
    ldomWordEx* findNearestWord(int x, int y, MoveDirection dir);
    /// select word
    void selectWord(ldomWordEx* word, MoveDirection dir);
    /// select next word in specified direction
    ldomWordEx* selectNextWord(MoveDirection dir, int moveBy = 1);
    /// select middle word in range
    ldomWordEx* selectMiddleWord();
    /// get selected word
    ldomWordEx* getSelWord() {
        return selWord;
    }
    /// try append search pattern and find word
    ldomWordEx* appendPattern(lString32 chars);
    /// remove last character from pattern and try to search
    ldomWordEx* reducePattern();
};

#endif // __LDOMWORDEXLIST_H_INCLUDED__
