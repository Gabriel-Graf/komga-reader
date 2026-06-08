/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007 Vadim Lopatin <coolreader.org@gmail.com>           *
 *   Copyright (C) 2022 Aleksey Chernov <valexlin@gmail.com>               *
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

#ifndef __LVCONTAINERITEMINFO_H_INCLUDED__
#define __LVCONTAINERITEMINFO_H_INCLUDED__

#include <lvtypes.h>

/**
 * @brief The LVContainerItemInfo class
 *
 * Abstract storage class of container element information.
 */
class LVContainerItemInfo
{
public:
    /**
     * @brief Get item size
     * @return item size
     *
     * For an item in an archive container, this is the unpacked size.
     */
    virtual lvsize_t GetSize() const = 0;
    /**
     * @brief Get compressed item size if applicable (for archives).
     * @return compressed item size
     *
     * For an item in an archive container, this is the packed size; for other types of containers, it is undefined.
     */
    virtual lvsize_t GetPackSize() const = 0;
    /**
     * @brief Get item name
     * @return item name
     */
    virtual const lChar32* GetName() const = 0;
    /**
     * @brief Get item's flags.
     * @return Optional item flags. See implementation for details.
     */
    virtual lUInt32 GetFlags() const = 0;
    /**
     * @brief Get the status of an element's container.
     * @return true if this element is a child container; otherwise, false.
     */
    virtual bool IsContainer() const = 0;
    LVContainerItemInfo() { }
    virtual ~LVContainerItemInfo() { }
};

#endif // __LVCONTAINERITEMINFO_H_INCLUDED__
