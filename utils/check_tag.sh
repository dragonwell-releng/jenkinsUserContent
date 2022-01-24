#!/bin/bash

# $1 = current version number
# $2 = upstream openjdk version number
# $3 = java home
# $4 = upstream tag
curr_version=$1
openjdk_version=$2
java_home=$3
upstream_tag=$4

err_exit()
{
  exit_msg=$1
  echo ${exit_msg}
  exit 1
}

if [ -z "$curr_version" ];then
  err_exit "no input version!"
elif [ -z "$openjdk_version" ];then
  err_exit "no input openjdk version!"
elif [ -z "$java_home" ];then
  if [ -n "$JAVA_HOME" ];then
    java_home=$JAVA_HOME
  else
    err_exit "no input java_home!"
  fi
fi

if [ ! -f "${java_home}/bin/java" ];then
  err_exit "invalid java_home!"
fi
java_bin=${java_home}/bin/java

if [ "${openjdk_version}" -eq 17 ];then
  if [ -z "`echo ${curr_version} | grep -E '^[1-9][0-9]*((\.0)*\.[1-9][0-9]*)*\+(0|[1-9][0-9]*)$'`" ];then
    err_exit "invalid version, does not conform to the named regular expression!"
  elif [ -z "`echo ${curr_version} | grep -E '^[1-9][0-9]*((\.0)*\.[1-9][0-9]*)*$'`" ];then
    err_exit "invalid version, does not conform to the named regular expression!"
  fi
fi

#upstream_tag=`curl -s -H "Accept: application/vnd.github.v3+json" https://api.github.com/repos/adoptium/temurin${openjdk_version}-binaries/releases/latest | grep tag_name | cut -d ':' -f 2 | cut -d '"' -f 2`
upstream_patch_num=`echo ${upstream_tag##*+}`
upstream_tag=`echo ${upstream_tag#*'jdk-'} | cut -d '+' -f 1`

java_version_info=`${java_bin} -version 2>&1`
java_version=`echo ${java_version_info##*'build '} | cut -d ',' -f 1`
java_patch_num=`echo ${java_version##*+}`
java_version=`echo ${java_version%%+*}`
if [ "$openjdk_version" -eq 8 ];then
  jdk_version=`echo ${java_version_info##*'Dragonwell '} | cut -d ')' -f 1`
  if [ "$jdk_version" != ${curr_version} ];then
    err_exit "current version is different from java version"
  fi
fi

if [ "${openjdk_version}" -eq 17 ];then
  curr_patch_num=`echo ${curr_version##*+}`
  curr_version=`echo ${curr_version%%+*}`
else
  curr_patch_num=0
fi

old_IFS=$IFS
IFS=$'.'
curr_list=(${curr_version})
upstream_list=(${upstream_tag})
java_list=(${java_version})
if [ "${#curr_list[@]}" -lt "${#java_list[@]}" ] || [ "${#curr_list[@]}" -lt "${#upstream_list[@]}" ];then
  err_exit "invalid version, the named version number has fewer digits than the previous version!"
fi
flag=0
for((idx=0; idx<${#java_list[@]}; idx++))
do
  ups_num=`eval echo '$'"{upstream_list[$idx]}"`
  java_num=`eval echo '$'"{java_list[$idx]}"`
  curr_num=`eval echo '$'"{curr_list[$idx]}"`
  if [ "$openjdk_version" -ne 8 ] && [ "$idx" -lt "${#upstream_list[@]}" ] && [ "${ups_num}" != "${curr_num}" ];then
    err_exit "invalid version, different from upstream version except patch number!"
  fi
  if [ "$openjdk_version" -ne 8 ] && [ "$flag" -eq 0 ] && [ "${curr_num}" -lt "${java_num}" ];then
    err_exit "invalid version, version number less than java version!"
  fi
  if [ "$openjdk_version" -ne 8 ] && [ "${curr_num}" -gt "${java_num}" ];then
    flag=1
  fi
done
IFS=$old_IFS
if [ "$openjdk_version" -eq 17 ];then
  if [ "${java_patch_num}" -ne "${curr_patch_num}" ] || [ "${upstream_patch_num}" -ne "${curr_patch_num}" ];then
    err_exit "build number should be the same!"
  fi
  if [ "$flag" -eq 0 ] && [ "${#curr_list[@]}" -eq "${#java_list[@]}" ] && [ "${curr_patch_num}" -le "${java_patch_num}" ];then
    err_exit "invalid version, version patch number less than java version!"
  fi
  if [ "${#curr_list[@]}" -eq "${#upstream_list[@]}" ] && [ "${curr_patch_num}" -le "${upstream_patch_num}" ];then
    err_exit "invalid version, version patch number less than upstream version!"
  fi
elif [ "$openjdk_version" -eq 11 ];then
  if [ "${java_patch_num}" -ne "${curr_patch_num}" ];then
    err_exit "build number should be the same!"
  fi
  if [ "$flag" -eq 0 ] && [ "${#curr_list[@]}" -eq "${#java_list[@]}" ] && [ "${curr_patch_num}" -lt "${java_patch_num}" ];then
    err_exit "invalid version, version patch number less than java version!"
  fi
else
  up_version=`echo ${upstream_tag##*jdk8u}`
  up_tag=`echo ${up_version%%-*}`
  cur_version=`echo ${java_version%%-*}`
  cur_tag=`echo ${cur_version##*.}`
  if [ "$up_tag" != "$cur_tag" ];then
    err_exit "tag is different, ${cur_tag} ${up_tag}"
  fi
fi

sys=`uname -a | grep -i cygwin`
res=0
if [ -n "$sys" ];then
  # system is windows
  if [ "$openjdk_version" -eq 17 ] && [ -n "`echo ${java_version_info} | grep 'OpenJDK 32-Bit'`" ];then
    let res++
  elif [ "$openjdk_version" -ne 17 ] && [ -n "`echo ${java_version_info} | grep 'OpenJDK 64-Bit'`" ];then
    let res++
  fi
else
  if [ -z "`echo ${java_version_info} | grep 'OpenJDK 64-Bit'`" ];then
    echo "Only 64-bit is supported on non Windows systems"
  else
    let res++
  fi
fi
if [ -z "`echo ${java_version_info} | grep 'Alibaba Dragonwell'`" ];then
  echo "not dragonwell jdk"
else
  let res++
fi
if [ "$res" -lt 2 ];then
  exit 1
fi
