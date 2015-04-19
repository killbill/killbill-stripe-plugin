set -e

BUNDLE=${BUNDLE-"bundle exec"}
MVN=${MVN-"mvn"}

if [ 'GNU' != "$(tar --help | grep GNU | head -1 | awk '{print $1}')" ]; then
  echo 'Unable to release: make sure to use GNU tar'
  exit 1
fi

if $(ruby -e'require "java"'); then
  # Good
  echo 'Detected JRuby'
else
  echo 'Unable to release: make sure to use JRuby'
  exit 1
fi

VERSION=`grep -E '<version>([0-9]+\.[0-9]+\.[0-9]+)</version>' pom.xml | sed 's/[\t \n]*<version>\(.*\)<\/version>[\t \n]*/\1/'`
if [[ -z "$NO_RELEASE" && "$VERSION" != "$(cat $PWD/VERSION)" ]]; then
  echo 'Unable to release: make sure the versions in pom.xml and VERSION match'
  exit 1
fi

echo 'Cleaning up'
$BUNDLE rake killbill:clean

echo 'Building gem'
$BUNDLE rake build

if [[ -z "$NO_RELEASE" ]]; then
  echo 'Pushing the gem to Rubygems'
  $BUNDLE rake release
fi

echo 'Building artifact'
$BUNDLE rake killbill:package

ARTIFACT="$PWD/pkg/killbill-stripe-$VERSION.tar.gz"
echo "Pushing $ARTIFACT to Maven Central"

if [[ -z "$NO_RELEASE" ]]; then
  GOAL=gpg:sign-and-deploy-file
  REPOSITORY_ID=ossrh-releases
  URL=https://oss.sonatype.org/service/local/staging/deploy/maven2/
else
  GOAL=deploy:deploy-file
  REPOSITORY_ID=sonatype-nexus-snapshots
  URL=https://oss.sonatype.org/content/repositories/snapshots/
  VERSION="$VERSION-SNAPSHOT"
fi

$MVN $GOAL \
     -DgroupId=org.kill-bill.billing.plugin.ruby \
     -DartifactId=stripe-plugin \
     -Dversion=$VERSION \
     -Dpackaging=tar.gz \
     -DrepositoryId=$REPOSITORY_ID \
     -Durl=$URL \
     -Dfile=$ARTIFACT \
     -DpomFile=pom.xml
