language: ruby

sudo: false
cache: bundler

script: 'bundle exec rake test:spec test:remote:spec'

notifications:
  email:
    - kill-bill-commits@googlegroups.com

env:
  global:
    - JRUBY_OPTS='-J-Xmx1024M'

rvm:
  - jruby-1.7.20
  - jruby-20mode # latest 1.7.x
  - jruby-head

gemfile:
  - Gemfile
  - Gemfile.head

jdk:
  - openjdk7
  - oraclejdk7
  - oraclejdk8

matrix:
  allow_failures:
    - rvm: jruby-head
    - jdk: oraclejdk8
    - gemfile: Gemfile.head
  fast_finish: true

after_success:
  - '[ "${TRAVIS_PULL_REQUEST}" = "false" ] && echo "<settings><servers><server><id>sonatype-nexus-snapshots</id><username>\${env.OSSRH_USER}</username><password>\${env.OSSRH_PASS}</password></server></servers></settings>" > ~/settings.xml && MVN="mvn --settings $HOME/settings.xml" NO_RELEASE=1 travis_retry travis_wait bash release.sh | egrep -v "Download|Install|Upload" ; rm -f ~/settings.xml'
