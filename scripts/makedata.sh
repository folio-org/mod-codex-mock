#!/bin/bash
#
# A simple script to make the mock.data from various marcXml files.
#

echo "Generating new mock data"
echo

DATAFILE=../src/main/resources/data/mock.data
RESFILE="mock.data"

# Get the original mock records
grep "Title of" $DATAFILE > 00_mockdata.json

# Generate some new ones
./convert.pl nlm.xml 1000
./convert.pl british_library.xml 2000

# Combine them
cat *.json > mock.data

echo "You must MANUALLY copy the data over to the source tree"
echo "   cp $RESFILE $DATAFILE"
