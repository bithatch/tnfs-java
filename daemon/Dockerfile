#
# Copyright © 2025 Bithatch (brett@bithatch.co.uk)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this
# software and associated documentation files (the “Software”), to deal in the Software
# without restriction, including without limitation the rights to use, copy, modify,
# merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so, subject to the following
# conditions:
#
# The above copyright notice and this permission notice shall be included in all copies
# or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
# INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
# PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
# HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
# OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
#

#
# Build stage
#
FROM vegardit/graalvm-maven:latest-java24 AS build
ENV HOME=/usr/app
RUN mkdir -p $HOME
WORKDIR $HOME
ADD . $HOME
#RUN --mount=type=cache,target=/root/.m2 mvn -f $HOME/pom.xml clean package -Dbuild.native.daemon=true
RUN mvn -f $HOME/pom.xml clean package -Dbuild.native.daemon=true -DskipTests

#
# Package stage
#
FROM debian:stable-slim 
ENV TNFS_LOG_LEVEL INFO
COPY --from=build /usr/app/daemon/target/tnfsjd /app/tnfsjd
COPY --from=build /usr/app/daemon/target/tnfs-user /app/tnfs-user
COPY --from=build /usr/app/daemon/src/main/docker/configuration /etc/tnfsjd
COPY --from=build /usr/app/daemon/src/main/docker/public-files /public-files
EXPOSE 16384
EXPOSE 16384/udp
VOLUME /public-files
VOLUME /configuration
ENTRYPOINT /app/tnfsjd -L ${TNFS_LOG_LEVEL:-INFO} -C /etc -O /configuration