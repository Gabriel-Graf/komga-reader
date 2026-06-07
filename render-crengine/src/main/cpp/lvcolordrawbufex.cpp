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

#include "lvcolordrawbufex.h"

void LVColorDrawBufEx::convert() {
    if (GetBitsPerPixel() == 32) {
        // convert crengine pixel format (from BGRX to RGBA)
        int sz = _rowsize * GetHeight() / 4;
        for (auto *p = (lUInt32 *) _data; --sz >= 0; p++)
            // invert alpha, swap R & B
            *p = (((*p) & 0xFF000000) ^ 0xFF000000) | (((*p) << 16) & 0x00FF0000) |
                 ((*p) & 0x0000FF00) | (((*p) >> 16) & 0x000000FF);
    }
}
