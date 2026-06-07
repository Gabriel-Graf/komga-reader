/***************************************************************************
 *   book reader based on crengine-ng                                      *
 *   Copyright (C) 2024 by Aleksey Chernov <valexlin@gmail.com>            *
 *                                                                         *
 *   This program is free software: you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation, either version 3 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.*
 ***************************************************************************/

/***************************************************************************
 *   Based on CoolReader project code at                                   *
 *   https://github.com/buggins/coolreader                                 *
 *   Copyright (C) 2010-2021 by Vadim Lopatin <coolreader.org@gmail.com>   *
 ***************************************************************************/

#ifndef LVCOLORDRAWBUFEX_H
#define LVCOLORDRAWBUFEX_H

#include <lvcolordrawbuf.h>

class LVColorDrawBufEx : public LVColorDrawBuf {
public:
    LVColorDrawBufEx(int dx, int dy, lUInt8 *pixels, int bpp)
            : LVColorDrawBuf(dx, dy, pixels, bpp) {
    }

    lUInt8 *getData() {
        return _data;
    }

    void convert();
};

#endif  // LVCOLORDRAWBUFEX_H
