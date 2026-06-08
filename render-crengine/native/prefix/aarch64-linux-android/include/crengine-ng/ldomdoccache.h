/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2009 Vadim Lopatin <coolreader.org@gmail.com>           *
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

#ifndef __LDOMDOCCACHE_H_INCLUDED__
#define __LDOMDOCCACHE_H_INCLUDED__

#include <lvstream.h>

/// document cache
class ldomDocCache
{
public:
    /// open existing cache file stream
    static LVStreamRef openExisting(lString32 filename, lUInt32 crc, lUInt32 docFlags, lString32& cachePath);
    /// create new cache file
    static LVStreamRef createNew(lString32 filename, lUInt32 crc, lUInt32 docFlags, lUInt32 fileSize, lString32& cachePath);
    /// init document cache
    static bool init(lString32 cacheDir, lvsize_t maxSize);
    /// close document cache manager
    static bool close();
    /// delete all cache files
    static bool clear();
    /// returns true if cache is enabled (successfully initialized)
    static bool enabled();
};

#endif // __LDOMDOCCACHE_H_INCLUDED__
