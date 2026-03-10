#!/bin/bash

BUILD_RESULT=0

for directory in *Adapter*/; do
  ant -buildfile $directory/build.xml
  BUILD_RESULT=$(( $BUILD_RESULT + $? ))
done

ant -buildfile WebServiceClientImplementations/build.xml
BUILD_RESULT=$(( $BUILD_RESULT + $? ))

exit $BUILD_RESULT
