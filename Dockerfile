# Use Java 16 for building
FROM openjdk:16-jdk-slim

WORKDIR /app

# Copy project files
COPY . .

# Install Gradle
RUN apt-get update && apt-get install -y wget unzip
RUN wget https://services.gradle.org/distributions/gradle-7.6-bin.zip
RUN unzip gradle-7.6-bin.zip
RUN mv gradle-7.6 /opt/gradle
ENV PATH="/opt/gradle/bin:${PATH}"

# Build the project
RUN ./gradlew build

# Copy the built JAR
RUN cp build/libs/*.jar /app/bedwars-bot.jar

CMD ["echo", "Build complete! JAR file is ready."]

