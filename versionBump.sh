#!/bin/sh

# Use this script to bump the version accross all POMs.

PROGNAME=`basename "$0"`

if [ "$#" -ne 1 ]; then
    echo "Illegal number of arguments. Use '$PROGNAME <version>'"
else
    mvn versions:set -Pconsole -DnewVersion=$1
    find . -name "*.versionsBackup" -exec rm {} \;
fi
