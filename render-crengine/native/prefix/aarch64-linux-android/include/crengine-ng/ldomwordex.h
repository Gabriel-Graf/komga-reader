/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2011,2012 Vadim Lopatin <coolreader.org@gmail.com>      *
 *   Copyright (C) 2018 poire-z <poire-z@users.noreply.github.com>         *
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

#ifndef __LDOMWORDEX_H_INCLUDED__
#define __LDOMWORDEX_H_INCLUDED__

#include <ldomword.h>
#include <ldommarkedrange.h>
#include <ldomxrange.h>

class ldomWordEx: public ldomWord
{
    ldomWord _word;
    ldomMarkedRange _mark;
    ldomXRange _range;
    lString32 _text;
public:
    ldomWordEx(ldomWord& word)
            : _word(word)
            , _mark(word)
            , _range(word) {
        _text = removeSoftHyphens(_word.getText());
    }
    ldomWord& getWord() {
        return _word;
    }
    ldomXRange& getRange() {
        return _range;
    }
    ldomMarkedRange& getMark() {
        return _mark;
    }
    lString32& getText() {
        return _text;
    }
};

#endif // __LDOMWORDEX_H_INCLUDED__
