/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007 Vadim Lopatin <coolreader.org@gmail.com>           *
 *   Copyright (C) 2021 Aleksey Chernov <valexlin@gmail.com>               *
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

/**
 * \file lvbyteorder.h
 * \brief byte order detector & convertor
 */

#ifndef LVBYTEORDER_H_INCLUDED
#define LVBYTEORDER_H_INCLUDED

#include <lvtypes.h>

/// byte order convertor
class lvByteOrderConv
{
    bool _lsf;
public:
    lvByteOrderConv() {
        union
        {
            lUInt16 word;
            lUInt8 bytes[2];
        } test;
        test.word = 1;
        _lsf = test.bytes[0] != 0;
    }
    /// reverse 64 bit word
    inline static lUInt64 rev(lUInt64 w) {
        return ((w & 0xFF00000000000000UL) >> 56) |
               ((w & 0x00FF000000000000UL) >> 40) |
               ((w & 0x0000FF0000000000UL) >> 32) |
               ((w & 0x000000FF00000000UL) >> 24) |
               ((w & 0x00000000FF000000UL) << 24) |
               ((w & 0x0000000000FF0000UL) << 32) |
               ((w & 0x000000000000FF00UL) << 40) |
               ((w & 0x00000000000000FFUL) << 56);
    }
    /// reverse 32 bit word
    inline static lUInt32 rev(lUInt32 w) {
        return ((w & 0xFF000000) >> 24) |
               ((w & 0x00FF0000) >> 8) |
               ((w & 0x0000FF00) << 8) |
               ((w & 0x000000FF) << 24);
    }
    /// reverse 16bit word
    inline static lUInt16 rev(lUInt16 w) {
        return (lUInt16)(((w & 0xFF00) >> 8) |
                         ((w & 0x00FF) << 8));
    }
    /// make 64 bit word least-significant-first byte order (Intel)
    lUInt64 lsf(lUInt64 w) {
        return (_lsf) ? w : rev(w);
    }
    /// make 64 bit word most-significant-first byte order (PPC)
    lUInt64 msf(lUInt64 w) {
        return (!_lsf) ? w : rev(w);
    }
    /// make 32 bit word least-significant-first byte order (Intel)
    lUInt32 lsf(lUInt32 w) {
        return (_lsf) ? w : rev(w);
    }
    /// make 32 bit word most-significant-first byte order (PPC)
    lUInt32 msf(lUInt32 w) {
        return (!_lsf) ? w : rev(w);
    }
    /// make 16 bit word least-significant-first byte order (Intel)
    lUInt16 lsf(lUInt16 w) {
        return (_lsf) ? w : rev(w);
    }
    /// make 16 bit word most-significant-first byte order (PPC)
    lUInt16 msf(lUInt16 w) {
        return (!_lsf) ? w : rev(w);
    }
    void rev(lUInt64* w) {
        *w = rev(*w);
    }
    void rev(lUInt32* w) {
        *w = rev(*w);
    }
    void rev(lUInt16* w) {
        *w = rev(*w);
    }
    void msf(lUInt64* w) {
        if (_lsf)
            *w = rev(*w);
    }
    void lsf(lUInt64* w) {
        if (!_lsf)
            *w = rev(*w);
    }
    void msf(lUInt64* w, int len) {
        if (_lsf) {
            for (int i = 0; i < len; i++)
                w[i] = rev(w[i]);
        }
    }
    void lsf(lUInt64* w, int len) {
        if (!_lsf) {
            for (int i = 0; i < len; i++)
                w[i] = rev(w[i]);
        }
    }
    void msf(lUInt32* w) {
        if (_lsf)
            *w = rev(*w);
    }
    void lsf(lUInt32* w) {
        if (!_lsf)
            *w = rev(*w);
    }
    void msf(lUInt32* w, int len) {
        if (_lsf) {
            for (int i = 0; i < len; i++)
                w[i] = rev(w[i]);
        }
    }
    void lsf(lUInt32* w, int len) {
        if (!_lsf) {
            for (int i = 0; i < len; i++)
                w[i] = rev(w[i]);
        }
    }
    void msf(lUInt16* w, int len) {
        if (_lsf) {
            for (int i = 0; i < len; i++)
                w[i] = rev(w[i]);
        }
    }
    void lsf(lUInt16* w, int len) {
        if (!_lsf) {
            for (int i = 0; i < len; i++)
                w[i] = rev(w[i]);
        }
    }
    void msf(lUInt16* w) {
        if (_lsf)
            *w = rev(*w);
    }
    void lsf(lUInt16* w) {
        if (!_lsf)
            *w = rev(*w);
    }
    bool lsf() {
        return (_lsf);
    }
    bool msf() {
        return (!_lsf);
    }
};

#endif // LVBYTEORDER_H_INCLUDED
