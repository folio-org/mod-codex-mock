#!/bin/bash
# Simple test script for mod-codex-mock
# Loads the module into Okapi, for the built-in supertenant


# Parameters
OKAPIPORT=9130
OKAPIURL="http://localhost:$OKAPIPORT"
CURL="curl -w\n -D - "
TENANT="supertenant"
TEN="-H X-Okapi-Tenant:$TENANT"

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
sleep 2 # give it time to start
echo

# Load the module
echo "Loading mod-codex-mock"
$CURL -X POST -d@target/ModuleDescriptor-standalone.json $OKAPIURL/_/proxy/modules
echo

echo "Deploying it"
$CURL -X POST \
   -d@target/DeploymentDescriptor.json \
   $OKAPIURL/_/discovery/modules
echo

echo "Enabling it (without specifying the version)"
$CURL -X POST \
   -d'{"id":"mod-codex-mock"}' \
   $OKAPIURL/_/proxy/tenants/$TENANT/modules
echo
sleep 1


# Various tests
echo Test 1: list some items
$CURL $TEN \
  $OKAPIURL/codex-instances?query=title=news
echo

echo Test 2: Get one item
$CURL $TEN \
  $OKAPIURL/codex-instances/11111111-1111-1111-1111-111111111111
echo

# Let it run
echo
echo "Hit enter to close"
read

# Clean up
echo "Cleaning up: Killing Okapi $PID"
kill $PID
for N in 1 2 3 4 5 6 7 8 9 10
do
  ps | grep java && ( echo $N ... ; sleep $N )
done
for N in 1 2 3 4 5 6 7 8 9 10
do
  ps | grep postgres && ( echo $N ... ; sleep $N )
done
ps | grep java | grep -v "grep java" && echo "OOPS - Still some java processes running"
ps | grep postgres | grep -v "grep postgres" && echo "OOPS - postgres is still running"
rm -rf /tmp/postgresql-embed*
echo bye

