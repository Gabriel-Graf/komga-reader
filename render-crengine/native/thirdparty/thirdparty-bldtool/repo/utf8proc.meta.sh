
# Metadata & build instructions for deploy script

PN="utf8proc"
PV="2.11.0"
# package revision: when patchset is changed (but not version), increase it
# when version changed, reset to "1".
REV="2"
SRCFILE="v${PV}.tar.gz"
SHA512="bf9bfb20036e8b709449ee4a11592becf99e61f4c82d03519ab9de1a93ca47d6f8ed4b0bb471f7ca3ae06293275a391a9102ae810a9e07e914789d05ddbd25ab"

#URL="https://github.com/JuliaStrings/utf8proc/releases/download/v${PV}/${SRCFILE}"
URL="https://github.com/JuliaStrings/utf8proc/archive/refs/tags/${SRCFILE}"

SOURCESDIR="${PN}-${PV}"

PATCHES=

src_configure() {
	cd "${BUILDDIR}" || die "chdir failed!"
	cmake -G"${CMAKE_GENERATOR}" \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_INSTALL_PREFIX="${PREFIX}" \
		-DBUILD_SHARED_LIBS=OFF \
		-DUTF8PROC_INSTALL=ON \
		-DUTF8PROC_ENABLE_TESTING=OFF \
		${CMAKE_ADD_ARGS} \
		-DCMAKE_C_FLAGS="${CFLAGS}" \
		-DCMAKE_CXX_FLAGS="${CXXFLAGS}" \
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
