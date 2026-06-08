
# Metadata & build instructions for deploy script

PN="fribidi"
PV="1.0.16"
# package revision: when patchset is changed (but not version), increase it
# when version changed, reset to "1".
REV="2"
SRCFILE="${PN}-${PV}.tar.xz"
SHA512="e3a56f36155f6813e3609473639fc533de742309f561c463012dc90b412a1ac7694b765d92669b2cbfaee973ca0e92fa5e926e68a1a078921f26ef17d82ab651"

URL="https://github.com/fribidi/fribidi/releases/download/v${PV}/${SRCFILE}"

SOURCESDIR="${PN}-${PV}"

PATCHES="01-dont-check-native-c.patch"

src_configure() {
	cd "${BUILDDIR}" || die "chdir failed!"
	if [ -n "${CHOST}" ]
	then
		add_args="${add_args} --host=${CHOST} --target=${CHOST}"
	fi
	../${SOURCESDIR}/configure --prefix=${PREFIX} --enable-static --disable-shared \
		${add_args}
}

src_compile() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake || die "make failed!"
}

src_install() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake_install || die "make install failed!"
}
