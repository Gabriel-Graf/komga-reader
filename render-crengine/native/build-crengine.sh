#!/bin/bash
###############################################################################
# Phase 1b — cross-build crengine-ng (static) for Android arm64-v8a.
#
# Reproduces the LxReader crengine-ng Android recipe
# (gitlab.com/coolreader-ng/lxreader, tools/crengine-ng-build/build-all.sh,
# GPL-3.0) for a single ABI, consuming the thirdparty dependency prefix built
# in Phase 1a and installing crengine-ng_static into that same prefix so that
# find_package(crengine-ng CONFIG) resolves in Phase 1c.
#
# crengine-ng is GPL-2.0-or-later. The whole app is already AGPL-3.0-or-later
# (MuPDF), so this is license-compatible.
#
# Decisive flag: -DUSE_FONTCONFIG=OFF (no fontconfig on Android; fonts are
# registered app-side later).
###############################################################################
set -euo pipefail

# --- Paths -------------------------------------------------------------------
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${script_dir}"

# Pinned crengine-ng source.
CRENGINE_REPO="https://gitlab.com/coolreader-ng/crengine-ng.git"
CRENGINE_COMMIT="ec57cc1d16c47237c10ac6f3cfa491791e23a952"

# Dependency + install prefix (Phase 1a output; crengine-ng installs here too).
prefix="${script_dir}/prefix/aarch64-linux-android"

# Transient source + build trees (gitignored; re-fetchable).
src_dir="${script_dir}/sources/crengine-ng"
build_dir="${script_dir}/build/crengine-ng-arm64-v8a"

# --- Toolchain (from make.conf, pinned NDK/SDK) ------------------------------
# shellcheck source=thirdparty/thirdparty-bldtool/make.conf
source "${script_dir}/thirdparty/thirdparty-bldtool/make.conf"

ndk_root="${ANDROID_NDK_ROOT}"
android_cmake="${ANDROID_CMAKE}"
toolchain_file="${ndk_root}/build/cmake/android.toolchain.cmake"

# The Android SDK CMake bundle ships both cmake and ninja; prefer it.
export PATH="${android_cmake}/bin:${PATH}"

die() {
	echo "[build-crengine] ERROR: $*" >&2
	exit 1
}

[[ -f "${toolchain_file}" ]] || die "NDK toolchain file not found: ${toolchain_file}"
[[ -d "${prefix}/lib" ]] || die "dependency prefix missing: ${prefix}/lib (run Phase 1a first)"

# --- Fetch / checkout source at the pinned commit ----------------------------
fetch_source() {
	if [[ -d "${src_dir}/.git" ]]; then
		local head
		head="$(git -C "${src_dir}" rev-parse HEAD)"
		if [[ "${head}" == "${CRENGINE_COMMIT}" ]]; then
			echo "[build-crengine] source already at ${CRENGINE_COMMIT}"
			return 0
		fi
		echo "[build-crengine] re-checking out ${CRENGINE_COMMIT}"
		git -C "${src_dir}" fetch --depth 1 origin "${CRENGINE_COMMIT}"
		git -C "${src_dir}" checkout --detach "${CRENGINE_COMMIT}"
		return 0
	fi
	echo "[build-crengine] cloning crengine-ng @ ${CRENGINE_COMMIT}"
	mkdir -p "$(dirname "${src_dir}")"
	rm -rf "${src_dir}"
	git init -q "${src_dir}"
	git -C "${src_dir}" remote add origin "${CRENGINE_REPO}"
	git -C "${src_dir}" fetch --depth 1 origin "${CRENGINE_COMMIT}"
	git -C "${src_dir}" checkout --detach "${CRENGINE_COMMIT}"
}

# --- Configure (LxReader flag set, single ABI, deps from our prefix) ----------
configure() {
	mkdir -p "${build_dir}"
	# CMAKE_FIND_ROOT_PATH points dependency discovery at our prefix; the pinned
	# commit already honors it for interface link dirs (no patch needed).
	#
	# The bundled SDK CMake (3.22.1) does not honor CMAKE_REQUIRED_LINK_DIRECTORIES
	# in CHECK_INCLUDE_FILE (added to the check modules in CMake 3.31, which the
	# LxReader recipe used). WebPConfig.cmake exports its libs as bare names
	# ("webp;webpdecoder;..."), so the hb-ft.h feature test links them as -lwebp
	# without a search path and fails. We add our prefix lib dir to the linker
	# search path globally so bare -l names resolve in both feature tests and the
	# real link. This is a host-CMake-version workaround, not a crengine-ng change.
	local prefix_lib="${prefix}/lib"
	cmake -S "${src_dir}" -B "${build_dir}" -G Ninja \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_TOOLCHAIN_FILE="${toolchain_file}" \
		-DANDROID_ABI=arm64-v8a \
		-DANDROID_PLATFORM=android-21 \
		-DANDROID_STL=c++_static \
		-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
		-DCMAKE_INSTALL_PREFIX="${prefix}" \
		-DCMAKE_FIND_ROOT_PATH="${prefix}" \
		-DCMAKE_PREFIX_PATH="${prefix}" \
		-DCMAKE_LIBRARY_PATH="${prefix_lib}" \
		-DCMAKE_EXE_LINKER_FLAGS="-L${prefix_lib}" \
		-DCMAKE_SHARED_LINKER_FLAGS="-L${prefix_lib}" \
		-DWebP_USE_STATIC_LIBS=ON \
		-DCRE_BUILD_SHARED=OFF \
		-DCRE_BUILD_STATIC=ON \
		-DADD_DEBUG_EXTRA_OPTS=OFF \
		-DDOC_DATA_COMPRESSION_LEVEL=3 \
		-DDOC_BUFFER_SIZE=0x1000000 \
		-DENABLE_LARGEFILE_SUPPORT=ON \
		-DUSE_COLOR_BACKBUFFER=ON \
		-DUSE_LOCALE_DATA=ON \
		-DLDOM_USE_OWN_MEM_MAN=OFF \
		-DWITH_LIBPNG=ON \
		-DWITH_LIBJPEG=ON \
		-DWITH_LIBWEBP=ON \
		-DWITH_LIBJXL=OFF \
		-DWITH_FREETYPE=ON \
		-DWITH_HARFBUZZ=ON \
		-DWITH_LIBUNIBREAK=ON \
		-DWITH_FRIBIDI=ON \
		-DWITH_ZSTD=ON \
		-DWITH_UTF8PROC=ON \
		-DUSE_GIF=ON \
		-DUSE_NANOSVG=ON \
		-DUSE_CHM=ON \
		-DUSE_ANTIWORD=ON \
		-DUSE_FONTCONFIG=OFF \
		-DUSE_SHASUM=ON \
		-DUSE_CMARK_GFM=OFF \
		-DUSE_MD4C=ON \
		-DBUILD_TOOLS=OFF \
		-DENABLE_UNITTESTING=OFF \
		-DENABLE_LTO=OFF
}

build_and_install() {
	cmake --build "${build_dir}" --target crengine-ng_static -- "${MAKEOPTS:--j8}"
	cmake --install "${build_dir}"
}

# Strip debug info from the installed archive (LxReader's release recipe does the
# same via do_strip). Shrinks the committed artifact ~39M -> ~8.5M; text symbols
# stay intact for linking in Phase 1c.
strip_debug() {
	local objcopy="${ndk_root}/toolchains/llvm/prebuilt/${ANDROID_NDK_HOST_TAG}/bin/llvm-objcopy"
	local installed="${prefix}/lib/libcrengine-ng.a"
	[[ -x "${objcopy}" ]] || die "llvm-objcopy not found: ${objcopy}"
	[[ -f "${installed}" ]] || die "installed archive missing: ${installed}"
	"${objcopy}" --strip-debug "${installed}"
}

fetch_source
configure
build_and_install
strip_debug

echo "[build-crengine] done. Installed into ${prefix}"
ls -la "${prefix}/lib/libcrengine-ng_static.a"
