/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2011,2012 Vadim Lopatin <coolreader.org@gmail.com>      *
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

#ifndef __CACHELOADINGCALLBACK_H_INCLUDED__
#define __CACHELOADINGCALLBACK_H_INCLUDED__

#include <crbookformats.h>

class CacheLoadingCallback
{
public:
    /// called when format of document being loaded from cache became known
    virtual void OnCacheFileFormatDetected(doc_format_t) = 0;
    virtual ~CacheLoadingCallback() { }
};

#endif // __CACHELOADINGCALLBACK_H_INCLUDED__
