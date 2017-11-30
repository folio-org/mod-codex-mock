#!/bin/bash
# Test script to see that we can run two instances of the mock in the same Okapi
# installation.

# Each instance has its own ModuleDescriptor, and in it, its own moduleId,
# for example "mod-codex-mock-one-0.0.2-SNAPSHOT" or "-two-".

# It is not yet sure how the RMB will behave with this.


# Parameters
OKAPIPORT=9130
OKAPIURL="http://localhost:$OKAPIPORT"
CURL="curl -w\n -D - "
TENANT="supertenant"
TEN="-H X-Okapi-Tenant:$TENANT"

# Get the module-Ids from the ModuleDescriptors
MOD_ONE=`grep '"id"' target/ModuleDescriptor-one.json | head -1 | cut -d '"' -f4`
ONE="-H X-Okapi-Module-Id:$MOD_ONE"
MOD_TWO=`grep '"id"' target/ModuleDescriptor-two.json | head -1 | cut -d '"' -f4`
TWO="-H X-Okapi-Module-Id:$MOD_TWO"

# Check we have the fat jar
if [ ! -f target/mod-codex-mock-fat.jar ]
then
  echo No fat jar found, no point in trying to run
  exit 1
fi

# Start Okapi (in dev mode, no database)
OKAPIPATH="../okapi/okapi-core/target/okapi-core-fat.jar"
java -Dport=$OKAPIPORT -jar $OKAPIPATH dev > okapi.log 2>&1 &
PID=$!
echo Started okapi on port $OKAPIPORT. PID=$PID
sleep 1 # give it time to start
echo

# Load the module 'one'
echo "Loading mod-codex-mock-one"
$CURL -X POST -d@target/ModuleDescriptor-one.json $OKAPIURL/_/proxy/modules
echo

echo "Deploying mod-codex-mock-one"
$CURL -X POST \
   -d@target/DeploymentDescriptor-one.json \
   $OKAPIURL/_/discovery/modules
echo

echo "Enabling mod-codex-mock-one"
$CURL -X POST \
   -d'{"id":"mod-codex-mock-one"}' \
   $OKAPIURL/_/proxy/tenants/$TENANT/modules
echo

# Load the module 'two'
echo "Loading mod-codex-mock-two"
$CURL -X POST -d@target/ModuleDescriptor-two.json $OKAPIURL/_/proxy/modules
echo

echo "Deploying mod-codex-mock-two"
$CURL -X POST \
   -d@target/DeploymentDescriptor-two.json \
   $OKAPIURL/_/discovery/modules
echo

echo "Enabling mod-codex-mock-two"
$CURL -X POST \
   -d'{"id":"mod-codex-mock-two"}' \
   $OKAPIURL/_/proxy/tenants/$TENANT/modules
echo


sleep 1


# Various tests

echo Test 0: Check that the path itself finds nothing
echo This should fail with a 404
$CURL $TEN \
  $OKAPIURL/codex-instances
echo

echo Test 1: Check that we can call mock-one
$CURL $TEN $ONE\
  $OKAPIURL/codex-instances
echo
echo Test 2: Check that we can call mock-two
$CURL $TEN $TWO\
  $OKAPIURL/codex-instances
echo

# Various tests
echo Test 3: Get one item
$CURL $TEN $ONE \
  $OKAPIURL/codex-instances/11111111-1111-1111-1111-111111111111
echo

# Trick to skip some parts of this script.
# Copy the 'cat' line to where you want to start skipping, and leave
# this part as is.
cat > /dev/null <<SKIPTHIS
SKIPTHIS

# Let it run
echo
echo "Hit enter to close"
read

# Clean up
echo "Cleaning up: Killing Okapi $PID"
kill $PID
for N in 9 8 7 6 5 4 3 2 1
do
  ps | grep java && ( echo ... $N ... ; sleep 2  )
done
rm -rf /tmp/postgresql-embed*
ps | grep java | grep -v grep  && echo "OOPS - Still some processes running"
echo bye

