
# Metadata & build instructions for deploy script

PN="libwebp"
PV="1.6.0"
# package revision: when patchset is changed (but not version), increase it
# when version changed, reset to "1".
REV="2"
SRCFILE="${PN}-${PV}.tar.gz"
SHA512="5c159d9760efcb92749092536daada22c0a73c20926c76097a5f0448ddbf874cf761324ca97925ca5f578b30477564b2b072b47667e504673797128b31cafcbf"

URL="https://storage.googleapis.com/downloads.webmproject.org/releases/webp/${PN}-${PV}.tar.gz"

SOURCESDIR="${PN}-${PV}"

PATCHES="01-cmake-config-path.patch"

src_configure() {
	cd "${BUILDDIR}" || die "chdir failed!"
	cmake -G"${CMAKE_GENERATOR}" \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_INSTALL_PREFIX="${PREFIX}" \
		${CMAKE_ADD_ARGS} \
		-DCMAKE_C_FLAGS="${CFLAGS}" \
		-DCMAKE_CXX_FLAGS="${CXXFLAGS}" \
		-DBUILD_SHARED_LIBS=OFF \
		-DWEBP_ENABLE_SIMD=ON \
		-DWEBP_BUILD_ANIM_UTILS=OFF \
		-DWEBP_BUILD_CWEBP=OFF \
		-DWEBP_BUILD_DWEBP=OFF \
		-DWEBP_BUILD_GIF2WEBP=OFF \
		-DWEBP_BUILD_IMG2WEBP=OFF \
		-DWEBP_BUILD_WEBPINFO=OFF \
		-DWEBP_BUILD_WEBPMUX=OFF \
		-DWEBP_BUILD_VWEBP=OFF \
		-DWEBP_BUILD_EXTRAS=OFF \
		-DWEBP_USE_THREAD=ON \
		-DWEBP_BUILD_WEBP_JS=OFF \
		../${SOURCESDIR}
}

src_compile() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake || die "make failed!"
}

src_install() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake_install || die "make install failed!"
}
