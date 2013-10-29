set -e

VERSION=`grep -E '<version>([0-9]+\.[0-9]+\.[0-9]+)</version>' pom.xml | sed 's/[\t \n]*<version>\(.*\)<\/version>[\t \n]*/\1/'`
if [ "$VERSION" != "$(cat $PWD/VERSION)" ]; then
  echo "Unable to release: make sure the versions in pom.xml and VERSION match"
  exit 1
fi

echo "Cleaning up"
rake killbill:clean ; rake build

echo "Pushing the gem to Rubygems"
rake release

echo "Building artifact"
rake killbill:package

ARTIFACT="$PWD/pkg/killbill-stripe-$VERSION.tar.gz"
echo "Pushing $ARTIFACT to Maven Central"
mvn gpg:sign-and-deploy-file \
    -DgroupId=com.ning.killbill.ruby \
    -DartifactId=stripe-plugin \
    -Dversion=$VERSION \
    -Dpackaging=tar.gz \
    -DrepositoryId=ossrh-releases \
    -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ \
    -Dfile=$ARTIFACT \
    -DpomFile=pom.xml
