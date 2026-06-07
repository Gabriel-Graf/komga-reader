
# Metadata & build instructions for deploy script

PN="libpng"
PV="1.6.50"
# package revision: when patchset is changed (but not version), increase it
# when version changed, reset to "1".
REV="2"
SRCFILE="${PN}-${PV}.tar.xz"
SHA512="05adc94ef532bbddaae46e087088a23236e6528fd3fc705c8edfb5ff293983b790d4361d6b20c20df73632a9fbe55d2f394296385cd8efd646f58393ff21257d"

URL="https://download.sourceforge.net/${PN}/${PN}-${PV}.tar.xz"

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
		-DPNG_DEBUG=OFF \
		-DPNG_SHARED=OFF \
		-DPNG_STATIC=ON \
		-DPNG_FRAMEWORK=OFF \
		-DPNG_TESTS=OFF \
		-DPNG_HARDWARE_OPTIMIZATIONS=ON \
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
