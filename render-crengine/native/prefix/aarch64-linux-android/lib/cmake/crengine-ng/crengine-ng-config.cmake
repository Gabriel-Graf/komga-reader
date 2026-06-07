
####### Expanded from @PACKAGE_INIT@ by configure_package_config_file() #######
####### Any changes to this file will be overwritten by the next CMake run ####
####### The input file was crengine-ng-config.cmake.in                            ########

get_filename_component(PACKAGE_PREFIX_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../" ABSOLUTE)

macro(set_and_check _var _file)
  set(${_var} "${_file}")
  if(NOT EXISTS "${_file}")
    message(FATAL_ERROR "File or directory ${_file} referenced by variable ${_var} does not exist !")
  endif()
endmacro()

macro(check_required_components _NAME)
  foreach(comp ${${_NAME}_FIND_COMPONENTS})
    if(NOT ${_NAME}_${comp}_FOUND)
      if(${_NAME}_FIND_REQUIRED_${comp})
        set(${_NAME}_FOUND FALSE)
      endif()
    endif()
  endforeach()
endmacro()

####################################################################################

include("${CMAKE_CURRENT_LIST_DIR}/crengine-ng-targets.cmake")

set_and_check(CRENGINE_NG_INCLUDE_DIR "${PACKAGE_PREFIX_DIR}/include/crengine-ng")
if(WIN32)
  set_and_check(CRENGINE_NG_DATA_DIR .)
else()
  set_and_check(CRENGINE_NG_DATA_DIR "${PACKAGE_PREFIX_DIR}/share/crengine-ng")
endif()

check_required_components(crengine-ng)
