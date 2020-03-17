FROM adoptopenjdk:11-jre-hotspot
WORKDIR '/app'

RUN apt-get update
RUN apt-get install -y wget

COPY . .
RUN chmod +x start.sh

CMD ["./start.sh"]