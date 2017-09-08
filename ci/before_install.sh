#!/usr/bin/env bash
# Unencrypt signing file
openssl aes-256-cbc -K $encrypted_258d8124f158_key -iv $encrypted_258d8124f158_iv -in ci/signing.gpg.enc -out ci/signing.gpg -d
gpg --fast-import ci/signing.gpg

# Workaround to using openjdk7 with Gradle due to security issue:
# https://github.com/gradle/gradle/issues/2421
BCPROV_FILENAME=bcprov-ext-jdk15on-158.jar
wget "https://bouncycastle.org/download/${BCPROV_FILENAME}"
sudo mv $BCPROV_FILENAME /usr/lib/jvm/java-7-openjdk-amd64/jre/lib/ext
sudo perl -pi.bak -e 's/^(security\.provider\.)([0-9]+)/$1.($2+1)/ge' /etc/java-7-openjdk/security/java.security
echo "security.provider.1=org.bouncycastle.jce.provider.BouncyCastleProvider" | sudo tee -a /etc/java-7-openjdk/security/java.security

# Install Thrift 0.9.3
sudo apt-get update -qq
sudo apt-get install libboost-dev libboost-test-dev libboost-program-options-dev libevent-dev automake libtool flex bison pkg-config g++ libssl-dev
pushd .
cd
wget http://www.us.apache.org/dist/thrift/0.9.3/thrift-0.9.3.tar.gz
tar xfz thrift-0.9.3.tar.gz
cd thrift-0.9.3 && ./configure && sudo make install
popd
