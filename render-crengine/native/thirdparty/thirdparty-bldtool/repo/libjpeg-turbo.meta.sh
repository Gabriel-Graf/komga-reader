
# Metadata & build instructions for deploy script

PN="libjpeg-turbo"
PV="3.1.2"
# package revision: when patchset is changed (but not version), increase it
# when version changed, reset to "1".
REV="2"
SRCFILE="${PN}-${PV}.tar.gz"
SHA512="79271ae4ddc12e3753cc7323dc15617f1d82b2d554ef27b555712f6ab5de603323dd33747620815e3b55663a20e07b292a55172aee9f401f9fd3557145967abe"

URL="https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/${PV}/${SRCFILE}"

SOURCESDIR="${PN}-${PV}"

PATCHES=

src_configure() {
	cd "${BUILDDIR}" || die "chdir failed!"
	cmake -G"${CMAKE_GENERATOR}" \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_INSTALL_PREFIX="${PREFIX}" \
		${CMAKE_ADD_ARGS} \
		-DCMAKE_C_FLAGS="${CFLAGS}" \
		-DCMAKE_CXX_FLAGS="${CXXFLAGS}" \
		-DENABLE_SHARED=OFF \
		-DENABLE_STATIC=ON \
		-DWITH_TOOLS=OFF \
		-DWITH_JAVA=OFF \
		-DWITH_SIMD=ON \
		-DWITH_JPEG7=ON \
		-DWITH_JPEG8=ON \
		-DWITH_TURBOJPEG=ON \
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
